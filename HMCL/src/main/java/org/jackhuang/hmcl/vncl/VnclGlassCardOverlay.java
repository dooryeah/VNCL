// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Adds a per-card glass rim light and a pointer-following specular highlight.
///
/// The overlay sits above a component list card but is mouse-transparent. The rim is a softly
/// blurred white outline that approximates light refracted at the glass edge; the specular circle
/// brightens the card around the pointer, giving the surface a lit, liquid feel.
@NotNullByDefault
public final class VnclGlassCardOverlay extends Pane {

    /// Diameter of the pointer highlight.
    private static final double SPECULAR_RADIUS = 220;

    /// Soft refracted rim drawn slightly inside the card edge.
    private final Rectangle glow = new Rectangle();

    /// Crisp highlight stroke on the card edge.
    private final Rectangle rim = new Rectangle();

    /// Pointer-following light spot.
    private final Circle specular = new Circle(SPECULAR_RADIUS);

    /// The scene mouse filter, kept for removal when the node is detached.
    private @Nullable EventHandler<MouseEvent> mouseHandler;

    /// Creates the overlay; add it as the topmost child of a card container.
    public VnclGlassCardOverlay() {
        setMouseTransparent(true);
        setPickOnBounds(false);
        setCursor(Cursor.DEFAULT);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        setClip(clip);

        glow.setFill(Color.TRANSPARENT);
        glow.setStroke(Color.rgb(255, 255, 255, 0.20));
        glow.setStrokeWidth(6);
        glow.setEffect(new GaussianBlur(4));
        glow.setOpacity(0.65);
        glow.setX(3);
        glow.setY(3);
        glow.widthProperty().bind(widthProperty().subtract(6));
        glow.heightProperty().bind(heightProperty().subtract(6));
        glow.setArcWidth(26);
        glow.setArcHeight(26);

        rim.setFill(Color.TRANSPARENT);
        rim.setStroke(Color.rgb(255, 255, 255, 0.55));
        rim.setStrokeWidth(1.2);
        rim.setX(0.6);
        rim.setY(0.6);
        rim.widthProperty().bind(widthProperty().subtract(1.2));
        rim.heightProperty().bind(heightProperty().subtract(1.2));
        rim.setArcWidth(28);
        rim.setArcHeight(28);

        specular.setCenterX(-1000);
        specular.setCenterY(-1000);
        specular.setMouseTransparent(true);
        specular.setBlendMode(BlendMode.SCREEN);
        specular.setFill(new RadialGradient(0, 0, 0.5, 0.5, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.12)),
                new Stop(0.55, Color.rgb(255, 255, 255, 0.045)),
                new Stop(1, Color.TRANSPARENT)));

        getChildren().setAll(glow, rim, specular);

        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && mouseHandler != null) {
                oldScene.removeEventFilter(MouseEvent.MOUSE_MOVED, mouseHandler);
                mouseHandler = null;
            }
            if (newScene != null) {
                mouseHandler = event -> {
                    var point = sceneToLocal(event.getSceneX(), event.getSceneY());
                    specular.setCenterX(point.getX());
                    specular.setCenterY(point.getY());
                };
                newScene.addEventFilter(MouseEvent.MOUSE_MOVED, mouseHandler);
            }
        });
    }
}
