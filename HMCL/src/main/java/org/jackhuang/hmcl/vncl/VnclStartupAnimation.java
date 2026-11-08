// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Plays the configurable VNCL startup animation over the launcher window.
///
/// The video is shown in a window-sized overlay that covers the title bar as well. Playback starts
/// only after the stage has been shown, so slow startup never lets the video end off-screen. A key
/// press, a left mouse click inside the window, the end of the video, or the timeout closes the
/// overlay and reveals the launcher.
@NotNullByDefault
public final class VnclStartupAnimation {

    /// Extensions considered playable by the bundled JavaFX media stack.
    private static final List<String> EXTENSIONS = List.of("mp4", "m4v", "mov", "wmv");

    /// Hard limit so a broken media file can never keep the launcher hidden.
    private static final Duration TIMEOUT = Duration.minutes(5);

    /// Fade-out duration when the overlay is dismissed.
    private static final Duration FADE = Duration.millis(250);

    /// Keys pressed within this grace period are ignored, so a carried-over key from launching
    /// the launcher cannot skip the animation before the first frame is visible.
    private static final long KEY_GRACE_NANOS = 300_000_000L;

    /// The overlay currently on screen, if any; prevents stacking multiple animations.
    private static @Nullable Overlay current;

    /// Returns the directory scanned for startup videos (next to the launcher executable).
    ///
    /// The launcher can be started as a jar or as the packaged exe, so the directory is derived from
    /// the code source instead of the process working directory.
    public static Path videosDirectory() {
        return launcherDirectory().resolve("videos");
    }

    /// Returns every supported video file in [#videosDirectory()], sorted by name.
    public static List<Path> listVideos() {
        Path directory = videosDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> EXTENSIONS.contains(extensionOf(path)))
                    .sorted()
                    .forEach(result::add);
        } catch (Exception exception) {
            LOG.warning("Failed to list startup videos", exception);
        }
        return result;
    }

    /// Returns the configured video file if it exists, otherwise null.
    public static @Nullable Path configuredVideo() {
        String fileName = VnclUiConfig.startupAnimationFileProperty().get();
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        if (fileName.contains("/") || fileName.contains("\\")) {
            return null;
        }
        Path file = videosDirectory().resolve(fileName);
        return Files.isRegularFile(file) ? file : null;
    }

    /// Plays the startup animation when it is enabled and a valid video is configured.
    ///
    /// Call this after the main stage has been shown; starting earlier can let the video play
    /// before the window becomes visible, which looked like a skipped animation.
    public static void playOnStartup(Stage stage) {
        if (!VnclUiConfig.startupAnimationEnabledProperty().get()) {
            return;
        }
        Path video = configuredVideo();
        if (video == null) {
            LOG.info("Startup animation skipped: no playable video configured");
            return;
        }
        Scene scene = stage.getScene();
        if (scene == null || !(scene.getRoot() instanceof Pane pane)) {
            LOG.warning("Startup animation skipped: the main window is not available yet");
            return;
        }
        if (current != null) {
            return;
        }
        startAnimation(pane, scene, video);
    }

    /// Plays the given video over the root pane; used by the settings preview button.
    public static void show(Pane root, Path video) {
        if (current != null) {
            return;
        }
        startAnimation(root, root.getScene(), video);
    }

    private static void startAnimation(Pane root, @Nullable Scene scene, Path video) {
        try {
            Overlay overlay = new Overlay(root, video);
            overlay.install();
            current = overlay;
            LOG.info("Startup animation: playing " + video.getFileName());
            // Defer one pulse so the window has painted its first frame before video playback starts.
            Platform.runLater(() -> overlay.start(scene));
        } catch (Throwable exception) {
            LOG.warning("Failed to start the startup animation", exception);
        }
    }

    private static Path launcherDirectory() {
        try {
            var codeSource = VnclStartupAnimation.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                Path path = Path.of(codeSource.getLocation().toURI());
                Path directory = Files.isDirectory(path) ? path : path.getParent();
                if (directory != null) {
                    return directory;
                }
            }
        } catch (Exception exception) {
            LOG.warning("Failed to resolve the launcher directory", exception);
        }
        return Metadata.CURRENT_DIRECTORY;
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1);
    }

    private VnclStartupAnimation() {
    }

    /// One running startup-animation overlay and its lifecycle.
    private static final class Overlay {
        /// The pane that receives the overlay; it is the window content root.
        private final Pane root;

        /// The overlay layer holding the video view and the skip hint.
        private final StackPane pane = new StackPane();

        /// The player owned by this overlay; disposed when the animation finishes.
        private final MediaPlayer player;

        /// Safety timeout that dismisses the overlay even if media events never arrive.
        private final PauseTransition timeout = new PauseTransition(TIMEOUT);

        /// Monotonic start timestamp used by the key-press grace period.
        private final long startedNanos = System.nanoTime();

        /// The scene that receives the skip key filter, or null when the overlay is detached.
        private @Nullable Scene scene;

        /// The installed key filter, kept for removal when the animation finishes.
        private @Nullable EventHandler<KeyEvent> keyHandler;

        /// Guards against double-finishing the overlay.
        private boolean finished;

        Overlay(Pane root, Path video) {
            this.root = root;
            this.player = new MediaPlayer(new Media(video.toUri().toString()));
            this.player.setVolume(clampVolume(VnclUiConfig.startupAnimationVolumeProperty().get()));

            MediaView view = new MediaView(player);
            view.setPreserveRatio(true);
            view.fitWidthProperty().bind(pane.widthProperty());
            view.fitHeightProperty().bind(pane.heightProperty());

            Label hint = new Label(i18n("vncl.startup.skip"));
            hint.getStyleClass().add("vncl-startup-hint");
            hint.setCursor(javafx.scene.Cursor.HAND);
            hint.setOnMouseClicked(event -> finish("hint"));
            StackPane.setAlignment(hint, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(hint, new Insets(0, 18, 14, 0));

            pane.getStyleClass().add("vncl-startup-overlay");
            pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            pane.getChildren().setAll(view, hint);
            // A left click anywhere inside the window closes the animation immediately.
            pane.setOnMousePressed(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    finish("click");
                }
            });

            timeout.setOnFinished(event -> finish("timeout"));
            player.setOnEndOfMedia(() -> finish("end"));
            player.setOnError(() -> {
                LOG.warning("Startup animation failed: " + player.getError());
                finish("error");
            });
        }

        /// Adds the overlay to the window and marks it as the active animation.
        void install() {
            root.getChildren().add(pane);
        }

        /// Starts playback and installs the skip handler on the given scene.
        void start(@Nullable Scene scene) {
            if (finished) {
                return;
            }
            this.scene = scene;
            if (scene != null) {
                keyHandler = event -> {
                    if (System.nanoTime() - startedNanos < KEY_GRACE_NANOS) {
                        return;
                    }
                    finish("key");
                };
                scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }
            player.play();
            timeout.playFromStart();
        }

        /// Dismisses the overlay with a fade-out and records why it ended.
        void finish(String reason) {
            if (finished) {
                return;
            }
            finished = true;
            LOG.info("Startup animation finished: " + reason);
            timeout.stop();
            player.stop();
            if (scene != null && keyHandler != null) {
                scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }

            FadeTransition fade = new FadeTransition(FADE, pane);
            fade.setToValue(0);
            fade.setOnFinished(event -> {
                root.getChildren().remove(pane);
                player.dispose();
                if (current == this) {
                    current = null;
                }
            });
            fade.play();
        }

        private static double clampVolume(double volume) {
            if (!Double.isFinite(volume)) {
                return 1.0;
            }
            return Math.max(0, Math.min(1, volume));
        }
    }
}

