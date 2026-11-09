// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;

/// A pulsing row skeleton used while list pages load.
///
/// The skeleton keeps the same row height as an instance card so the list does not jump when real
/// content replaces it. When animations are disabled in launcher settings the skeleton is static.
@NotNullByDefault
public final class VnclSkeletonList extends VBox {

    /// Creates a skeleton with the given number of rows.
    ///
    /// @param rows the number of placeholder rows
    public VnclSkeletonList(int rows) {
        getStyleClass().add("vncl-skeleton");
        setPadding(new Insets(8, 0, 8, 0));

        for (int i = 0; i < rows; i++) {
            getChildren().add(createRow());
        }

        if (AnimationUtils.isAnimationEnabled()) {
            Timeline pulse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(opacityProperty(), 0.55)),
                    new KeyFrame(Duration.millis(1100), new KeyValue(opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(2200), new KeyValue(opacityProperty(), 0.55)));
            pulse.setCycleCount(Timeline.INDEFINITE);
            pulse.play();
        } else {
            setOpacity(0.7);
        }
    }

    /// Creates one skeleton row.
    ///
    /// @return the row node
    private static HBox createRow() {
        Region icon = new Region();
        icon.getStyleClass().add("vncl-skeleton-icon");

        Region title = new Region();
        title.getStyleClass().addAll("vncl-skeleton-bar", "vncl-skeleton-title");

        Region subtitle = new Region();
        subtitle.getStyleClass().addAll("vncl-skeleton-bar", "vncl-skeleton-subtitle");

        VBox text = new VBox(6, title, subtitle);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(10, icon, text);
        row.getStyleClass().add("vncl-skeleton-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
