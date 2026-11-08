// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
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
/// The video is shown in a window-sized overlay that covers the title bar as well. Any key press,
/// a mouse click, the end of the video, or the timeout closes it and reveals the launcher.
@NotNullByDefault
public final class VnclStartupAnimation {

    /// Extensions considered playable by the bundled JavaFX media stack.
    private static final List<String> EXTENSIONS = List.of("mp4", "m4v", "mov", "wmv");

    /// Hard limit so a broken media file can never keep the launcher hidden.
    private static final Duration TIMEOUT = Duration.seconds(20);
    /// Fade-out duration when the overlay is dismissed.
    private static final Duration FADE = Duration.millis(250);

    /// Returns the directory scanned for startup videos (next to the launcher executable).
    ///
    /// The launcher can be started as a jar or as the packaged exe, so the directory is derived from
    /// the code source instead of the process working directory.
    public static Path videosDirectory() {
        return launcherDirectory().resolve("videos");
    }

    /// Returns the directory that contains the launcher jar or exe.
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

    /// Installs the animation when it is enabled and a valid video is configured.
    public static void installIfEnabled(StackPane root) {
        if (!VnclUiConfig.startupAnimationEnabledProperty().get()) {
            return;
        }
        Path video = configuredVideo();
        if (video == null) {
            return;
        }
        show(root, video);
    }

    /// Plays the given video over the root pane; used by both startup and the preview button.
    public static void show(Pane root, Path video) {
        try {
            Overlay overlay = new Overlay(root, video);
            overlay.install();
            Platform.runLater(overlay::start);
        } catch (Throwable exception) {
            LOG.warning("Failed to start the startup animation", exception);
        }
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
        private final Pane root;
        private final StackPane pane = new StackPane();
        private final MediaPlayer player;
        private final PauseTransition timeout = new PauseTransition(TIMEOUT);
        private @Nullable Scene scene;
        private @Nullable EventHandler<KeyEvent> keyHandler;
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
            StackPane.setAlignment(hint, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(hint, new Insets(0, 18, 14, 0));

            pane.getStyleClass().add("vncl-startup-overlay");
            pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            pane.getChildren().setAll(view, hint);
            pane.setOnMouseClicked(event -> finish());

            timeout.setOnFinished(event -> finish());
            player.setOnEndOfMedia(this::finish);
            player.setOnError(() -> {
                LOG.warning("Startup animation failed: " + player.getError());
                finish();
            });
        }

        void install() {
            root.getChildren().add(pane);
        }

        void start() {
            scene = root.getScene();
            if (scene != null) {
                keyHandler = event -> finish();
                scene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }
            player.play();
            timeout.playFromStart();
        }

        void finish() {
            if (finished) {
                return;
            }
            finished = true;
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
