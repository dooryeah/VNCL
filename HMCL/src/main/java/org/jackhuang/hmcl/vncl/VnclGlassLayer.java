// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;

/// Draws a correctly positioned blurred copy of the launcher wallpaper behind translucent surfaces.
///
/// JavaFX has no CSS `backdrop-filter`, so the frosted-glass look is approximated by placing a
/// blurred copy of the background image between the wallpaper and the UI, clipped to every node
/// marked with `vncl-glass-flat` or `vncl-glass-card`. Surfaces themselves are made translucent by
/// the `vncl-glass-mode` CSS rules, so the blurred backdrop shows through while the rest of the
/// wallpaper stays sharp.
@NotNullByDefault
public final class VnclGlassLayer extends Region {

    /// Corner radius used for card-like glass surfaces, in pixels.
    private static final double CARD_RADIUS = 14;

    /// How often the clip is refreshed to follow layout and scrolling, in milliseconds.
    private static final double REFRESH_MILLIS = 80;

    /// The pane whose children are searched for glass surfaces.
    private final Pane root;

    /// The wallpaper region whose background is blurred.
    private final Region source;

    /// Rounded rectangles describing the areas where the blurred backdrop is visible.
    private final Group clipGroup = new Group();

    /// Periodic clip refresh; scrolling also requests an immediate refresh.
    private final Timeline refreshTimeline;

    /// Creates and installs the blurred backdrop layer.
    ///
    /// @param root the window content pane that contains the wallpaper and the UI
    /// @param source the wallpaper region
    public VnclGlassLayer(Pane root, Region source) {
        this.root = root;
        this.source = source;

        setMouseTransparent(true);
        setFocusTraversable(false);
        setClip(clipGroup);
        backgroundProperty().bind(source.backgroundProperty());
        opacityProperty().bind(source.opacityProperty());

        double blurRadius = Math.max(0, VnclUiConfig.glassBlurProperty().get());
        if (blurRadius > 0.5) {
            setEffect(new GaussianBlur(blurRadius));
        }

        refreshTimeline = new Timeline(new KeyFrame(Duration.millis(REFRESH_MILLIS), event -> rebuildClip()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);

        root.layoutBoundsProperty().addListener(observable -> rebuildClip());
        root.addEventFilter(ScrollEvent.ANY, event -> Platform.runLater(this::rebuildClip));
        refreshTimeline.play();
        rebuildClip();
    }

    /// Returns whether the graphics pipeline can render the blur without severe jank.
    ///
    /// The software pipeline implements effects on the CPU; the frosted backdrop would slow the
    /// whole launcher down, so glass is disabled there and callers fall back to opaque surfaces.
    public static boolean isSupported() {
        try {
            String pipeline = com.sun.prism.GraphicsPipeline.getPipeline().getClass().getName();
            return !pipeline.contains(".sw.");
        } catch (Throwable ignored) {
            return true;
        }
    }

    /// Rebuilds the clip from all currently visible glass surfaces.
    private void rebuildClip() {
        List<Rectangle> shapes = new ArrayList<>();
        collect("vncl-glass-flat", true, shapes);
        collect("options-list", false, shapes);
        collect("jfx-dialog-layout", false, shapes);
        clipGroup.getChildren().setAll(shapes);
    }

    /// Adds rounded rectangles for every visible node with the given style class.
    ///
    /// @param styleClass the surface marker style class
    /// @param flat whether the surface should have square corners
    /// @param shapes the destination list
    private void collect(String styleClass, boolean flat, List<Rectangle> shapes) {
        for (Node node : root.lookupAll("." + styleClass)) {
            if (node.getScene() == null || !isShowing(node)) {
                continue;
            }
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
                continue;
            }
            Point2D origin = sceneToLocal(bounds.getMinX(), bounds.getMinY());
            Rectangle shape = new Rectangle(origin.getX(), origin.getY(), bounds.getWidth(), bounds.getHeight());
            if (!flat) {
                shape.setArcWidth(CARD_RADIUS);
                shape.setArcHeight(CARD_RADIUS);
            }
            shapes.add(shape);
        }
    }

    /// Returns whether the node and all its ancestors are visible.
    private static boolean isShowing(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible() || current.getOpacity() <= 0.01) {
                return false;
            }
        }
        return true;
    }
}
