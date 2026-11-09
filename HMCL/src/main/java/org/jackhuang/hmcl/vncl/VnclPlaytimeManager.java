// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Tracks the total time spent playing Minecraft, aggregated across every instance launched by VNCL.
///
/// A session starts when the game window appears and ends when the game process exits. Running
/// sessions are persisted with a heartbeat every 30 seconds, so a launcher crash or an early exit
/// only loses at most half a minute of playtime. The file stores only the accumulated total, which
/// keeps the feature minimal; the sidebar shows it rounded down to whole minutes.
@NotNullByDefault
public final class VnclPlaytimeManager {

    /// How often running sessions get their heartbeat persisted, in seconds.
    private static final long HEARTBEAT_SECONDS = 30;

    /// How often the observable total is refreshed while at least one session is running.
    private static final Duration REFRESH_INTERVAL = Duration.seconds(10);

    /// Serializer for the playtime file.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /// The total playtime visible to the UI, including currently running sessions.
    private static final SimpleLongProperty totalMillis = new SimpleLongProperty();

    /// Whether at least one Minecraft session is currently running.
    private static final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);

    /// The persisted part of the total (finished sessions plus recovered sessions).
    private static long storedTotalMillis;

    /// Sessions that are currently running, guarded by [#lock].
    private static final List<ActiveSession> activeSessions = new ArrayList<>();

    /// Serializes persistence and session state updates.
    private static final Object lock = new Object();

    /// Identifier generator for new sessions; never reused within a process.
    private static final AtomicLong nextSessionId = new AtomicLong(1);

    /// Background heartbeat task; daemon so it never keeps the launcher alive.
    private static final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VNCL-Playtime-Heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    /// Periodic UI refresh while a session is running, or null before [#initialize()].
    private static @Nullable Timeline refreshTimeline;

    /// Whether [#initialize()] has completed.
    private static boolean initialized;

    /// Loads the stored total, recovers interrupted sessions, and starts the heartbeat worker.
    ///
    /// Must be called on the JavaFX application thread once the launcher starts.
    public static void initialize() {
        synchronized (lock) {
            if (initialized)
                return;

            Data data = load();
            recover(data);
            storedTotalMillis = data.totalMs;
            initialized = true;
        }

        refreshTotal();

        refreshTimeline = new Timeline(new KeyFrame(REFRESH_INTERVAL, event -> refreshTotal()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                synchronized (lock) {
                    if (activeSessions.isEmpty())
                        return;
                    long now = System.currentTimeMillis();
                    for (ActiveSession session : activeSessions)
                        session.heartbeat = now;
                    save();
                }
                refreshTotal();
            } catch (Throwable exception) {
                LOG.warning("Failed to update playtime heartbeat", exception);
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /// Starts a new playtime session and returns its identifier.
    ///
    /// Call this when the Minecraft window becomes visible, or at process launch when window
    /// detection is unavailable.
    public static long beginSession() {
        ensureInitialized();
        long id = nextSessionId.getAndIncrement();
        long now = System.currentTimeMillis();
        synchronized (lock) {
            activeSessions.add(new ActiveSession(id, now));
            save();
        }
        running.set(true);
        refreshTotal();
        return id;
    }

    /// Ends the session with the given identifier and adds its duration to the total.
    ///
    /// Passing a negative identifier (no session was started) is a no-op.
    public static void endSession(long sessionId) {
        if (sessionId < 0)
            return;
        ensureInitialized();
        long now = System.currentTimeMillis();
        synchronized (lock) {
            for (int i = 0; i < activeSessions.size(); i++) {
                ActiveSession session = activeSessions.get(i);
                if (session.id == sessionId) {
                    activeSessions.remove(i);
                    storedTotalMillis += Math.max(0, now - session.startedAt);
                    save();
                    break;
                }
            }
            running.set(!activeSessions.isEmpty());
        }
        refreshTotal();
    }

    /// Returns whether a Minecraft session is currently running.
    public static ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    /// Returns the observable total playtime in milliseconds.
    public static ReadOnlyLongProperty totalMillisProperty() {
        return totalMillis;
    }

    /// Formats a duration as whole hours and minutes, or as minutes when under one hour.
    public static String formatDuration(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long hours = minutes / 60;
        return hours > 0
                ? i18n("vncl.playtime.hours_minutes", hours, minutes % 60)
                : i18n("vncl.playtime.minutes", minutes);
    }

    /// Persists running session heartbeats and stops background workers.
    public static void shutdown() {
        synchronized (lock) {
            if (initialized)
                save();
        }
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
        heartbeatExecutor.shutdownNow();
    }

    /// Loads the playtime file, falling back to an empty dataset on any error.
    private static Data load() {
        Path file = file();
        if (!Files.isRegularFile(file))
            return new Data();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null)
                return new Data();
            if (data.active == null)
                data.active = new ArrayList<>();
            return data;
        } catch (Exception exception) {
            LOG.warning("Failed to read playtime data", exception);
            return new Data();
        }
    }

    /// Adds the elapsed time of dangling sessions (launcher closed or crashed) and clears them.
    private static void recover(Data data) {
        if (data.active.isEmpty())
            return;

        long now = System.currentTimeMillis();
        int recovered = data.active.size();
        for (ActiveSession session : data.active) {
            long end = Math.min(now, Math.max(session.startedAt, session.heartbeat));
            long elapsed = Math.max(0, end - session.startedAt);
            data.totalMs += elapsed;
            nextSessionId.accumulateAndGet(session.id + 1, Math::max);
        }
        data.active.clear();
        LOG.info("Recovered playtime from " + recovered + " interrupted session(s)");
        try {
            synchronized (lock) {
                saveUnlocked(data);
            }
        } catch (Exception exception) {
            LOG.warning("Failed to persist recovered playtime", exception);
        }
    }

    /// Writes the current state atomically; must be called while holding [#lock].
    private static void save() {
        Data data = new Data();
        data.totalMs = storedTotalMillis;
        data.active = new ArrayList<>(activeSessions);
        saveUnlocked(data);
    }

    /// Writes the given dataset to disk; caller decides whether locking is needed.
    private static void saveUnlocked(Data data) {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOG.warning("Failed to save playtime data", exception);
        }
    }

    /// Recomputes the observable total, adding the elapsed time of running sessions.
    private static void refreshTotal() {
        long total;
        synchronized (lock) {
            total = storedTotalMillis;
            long now = System.currentTimeMillis();
            for (ActiveSession session : activeSessions)
                total += Math.max(0, now - session.startedAt);
        }
        long value = total;
        if (Platform.isFxApplicationThread()) {
            totalMillis.set(value);
        } else {
            try {
                Platform.runLater(() -> totalMillis.set(value));
            } catch (IllegalStateException ignored) {
                // The toolkit is shutting down; the last value is good enough.
            }
        }
    }

    /// Ensures the manager is usable even if callers forget to call [#initialize()].
    private static void ensureInitialized() {
        synchronized (lock) {
            if (initialized)
                return;
        }
        initialize();
    }

    /// Returns the playtime data file.
    private static Path file() {
        return Metadata.HMCL_USER_HOME.resolve("config").resolve("playtime.json");
    }

    /// One running play session, persisted so an interrupted launcher can recover its elapsed time.
    private static final class ActiveSession {
        /// Session identifier.
        private long id;
        /// Wall-clock time when the game window appeared.
        private long startedAt;
        /// Wall-clock time of the last heartbeat.
        private long heartbeat;

        /// Creates a session starting now with an initial heartbeat.
        private ActiveSession(long id, long startedAt) {
            this.id = id;
            this.startedAt = startedAt;
            this.heartbeat = startedAt;
        }

        /// Creates an empty session for Gson deserialization.
        private ActiveSession() {
            this(0, 0);
        }
    }

    /// On-disk representation of the playtime total and running sessions.
    private static final class Data {
        /// Schema version, reserved for future migrations.
        private int version = 1;
        /// Total playtime of finished and recovered sessions in milliseconds.
        private long totalMs;
        /// Sessions that were running when the file was last written.
        private List<ActiveSession> active = new ArrayList<>();
    }

    private VnclPlaytimeManager() {
    }
}
