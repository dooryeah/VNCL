// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A compact, line-art empty state for list pages.
///
/// The icon uses the existing SVG glyph as an outline: its fill is cleared and its stroke is drawn
/// with the theme's secondary text color, which keeps the "line art" look without adding assets.
@NotNullByDefault
public final class VnclEmptyState extends VBox {

    /// Creates an empty state with an optional action button.
    ///
    /// @param icon the outline glyph shown above the title
    /// @param title the short empty-state title
    /// @param description the supporting sentence
    /// @param action an optional action button
    public VnclEmptyState(SVG icon, String title, String description, @Nullable Node action) {
        getStyleClass().add("vncl-empty-state");
        setAlignment(Pos.CENTER);
        setSpacing(10);

        var iconNode = icon.createIcon(52);
        iconNode.getStyleClass().add("vncl-empty-icon");
        iconNode.setMouseTransparent(true);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("vncl-empty-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("vncl-empty-description");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(380);

        getChildren().addAll(iconNode, titleLabel, descriptionLabel);
        if (action != null) {
            action.getStyleClass().add("jfx-button-border");
            VBox.setMargin(action, new javafx.geometry.Insets(6, 0, 0, 0));
            getChildren().add(action);
        }
    }
}
