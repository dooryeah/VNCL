// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.vncl.VnclInstanceStyle;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// The optional cover banner shown above an instance detail page.
///
/// The banner stays hidden until the instance has a cover image, so instances without a custom
/// cover keep the original page layout. The accent strip uses the instance's solid/gradient style.
@NotNullByDefault
public final class VnclInstanceBanner extends StackPane {

    /// Cover image view.
    private final ImageView cover = new ImageView();

    /// Dark gradient scrim that keeps the title readable.
    private final Region scrim = new Region();

    /// Instance icon.
    private final ImageView icon = new ImageView();

    /// Instance title.
    private final Label title = new Label();

    /// Accent strip at the bottom.
    private final Region accent = new Region();

    /// Currently displayed instance.
    private @Nullable HMCLGameInstance instance;

    /// Listener for cover image changes.
    private final ChangeListener<Image> coverListener = (observable, oldValue, newValue) -> updateCover(newValue);

    /// Listener for accent changes.
    private final ChangeListener<VnclInstanceStyle> styleListener = (observable, oldValue, newValue) -> updateAccent(newValue);

    /// Creates a hidden banner.
    public VnclInstanceBanner() {
        getStyleClass().add("vncl-instance-banner");
        setMinHeight(132);
        setPrefHeight(132);
        setMaxWidth(Double.MAX_VALUE);
        setVisible(false);
        setManaged(false);

        cover.getStyleClass().add("vncl-instance-banner-cover");
        cover.setPreserveRatio(false);
        cover.setSmooth(true);
        cover.setManaged(false);
        cover.fitWidthProperty().bind(widthProperty());
        cover.fitHeightProperty().bind(heightProperty());
        scrim.getStyleClass().add("vncl-instance-banner-scrim");
        scrim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scrim.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(0, 0, 0, 0.74)),
                        new Stop(0.62, Color.rgb(0, 0, 0, 0.42)),
                        new Stop(1, Color.rgb(0, 0, 0, 0.08))),
                CornerRadii.EMPTY, Insets.EMPTY)));

        icon.setFitWidth(56);
        icon.setFitHeight(56);
        icon.setPreserveRatio(true);
        icon.getStyleClass().add("vncl-instance-banner-icon");

        title.getStyleClass().add("vncl-instance-banner-title");

        VBox text = new VBox(2, title);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox content = new HBox(12, icon, text);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(16));
        StackPane.setAlignment(content, Pos.BOTTOM_LEFT);

        accent.getStyleClass().add("vncl-instance-banner-accent");
        accent.prefWidthProperty().bind(widthProperty());
        accent.setMinHeight(4);
        accent.setPrefHeight(4);
        accent.setMaxHeight(4);
        StackPane.setAlignment(accent, Pos.BOTTOM_CENTER);

        getChildren().addAll(cover, scrim, content, accent);
    }

    /// Sets the instance displayed by this banner, or null to hide it.
    ///
    /// @param instance the instance, or null
    public void setInstance(@Nullable HMCLGameInstance instance) {
        if (this.instance != null) {
            this.instance.coverImageProperty().removeListener(coverListener);
            this.instance.vnclStyleProperty().removeListener(styleListener);
            icon.imageProperty().unbind();
        }

        this.instance = instance;
        if (instance == null) {
            setVisible(false);
            setManaged(false);
            return;
        }

        icon.imageProperty().bind(instance.iconImageProperty());
        title.setText(instance.getId().toString());
        instance.coverImageProperty().addListener(coverListener);
        instance.vnclStyleProperty().addListener(styleListener);
        updateCover(instance.getCoverImage());
        updateAccent(instance.getVnclStyle());
    }

    /// Updates the cover background and visibility.
    ///
    /// @param image the cover image, or null
    private void updateCover(@Nullable Image image) {
        if (image == null) {
            cover.setImage(null);
            setVisible(false);
            setManaged(false);
            return;
        }
        cover.setImage(image);
        setVisible(true);
        setManaged(true);
    }

    /// Updates the accent strip.
    ///
    /// @param style the instance style
    private void updateAccent(VnclInstanceStyle style) {
        Paint paint = style.paint();
        accent.setBackground(paint == null ? null : new Background(
                new BackgroundFill(paint, CornerRadii.EMPTY, Insets.EMPTY)));
    }
}
