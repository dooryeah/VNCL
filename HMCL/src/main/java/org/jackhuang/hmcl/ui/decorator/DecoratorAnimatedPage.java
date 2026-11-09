// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Lays out a fixed-width sidebar and a growing content area as independent animation targets.
@NotNullByDefault
public class DecoratorAnimatedPage extends BorderPane {

    /// Width of the sidebar rail used when the navigation is collapsed.
    private static final double COLLAPSED_WIDTH = 60;
    /// Duration of the sidebar width animation.
    private static final Duration COLLAPSE_DURATION = Duration.millis(200);

    /// The fixed-width animation target containing navigation and optional bottom actions.
    private final BorderPane left = new BorderPane();
    /// The scrollable navigation list owned by this page.
    private final AdvancedListBox sidebar = new AdvancedListBox();
    /// The stable animation target containing the replaceable page content.
    private final StackPane center = new StackPane();

    /// The compact rail shown while the sidebar is collapsed.
    private @Nullable Node compactSidebar;
    /// Whether the sidebar currently shows the compact rail.
    private boolean sidebarCollapsed;
    /// Sidebar width captured before the last collapse, used when expanding again.
    private double expandedWidth = 200;

    /// Creates both animation targets without requiring CSS or skin initialization.
    public DecoratorAnimatedPage() {
        getStyleClass().add("gray-background");
        left.setPrefWidth(200);
        left.setMaxWidth(200);
        left.setMinWidth(0);
        sidebar.setMinSize(0, 0);
        left.setCenter(sidebar);
        setLeft(left);
        setCenter(center);
    }

    /// Returns the navigation list; its entries share the space above the bottom content.
    public AdvancedListBox getSidebar() {
        return sidebar;
    }

    /// Replaces the compact rail shown while the sidebar is collapsed.
    public void setCompactSidebar(@Nullable Node node) {
        compactSidebar = node;
        if (sidebarCollapsed) {
            left.setCenter(node);
        }
    }

    /// Returns whether the sidebar currently shows the compact rail.
    public boolean isSidebarCollapsed() {
        return sidebarCollapsed;
    }

    /// Collapses or expands the sidebar, swapping the navigation list for the compact rail.
    public void setSidebarCollapsed(boolean collapsed) {
        if (sidebarCollapsed == collapsed) {
            return;
        }
        sidebarCollapsed = collapsed;
        if (collapsed) {
            if (left.getWidth() > 0) {
                expandedWidth = left.getWidth();
            }
            left.setCenter(compactSidebar);
        } else {
            left.setCenter(sidebar);
        }

        double target = collapsed ? COLLAPSED_WIDTH : expandedWidth;
        Timeline timeline = new Timeline(new KeyFrame(COLLAPSE_DURATION,
                new KeyValue(left.prefWidthProperty(), target, Interpolator.EASE_BOTH)));
        timeline.play();
    }

    /// Returns the fixed bottom content, or null when none is installed.
    public @Nullable Node getSidebarBottom() {
        return left.getBottom();
    }

    /// Replaces the bottom content, reserving its preferred height and a 12-pixel bottom gap.
    /// Passing null removes it without replacing the sidebar animation target.
    public void setSidebarBottom(@Nullable Node node) {
        if (node != null) {
            BorderPane.setMargin(node, new Insets(0, 0, 12, 0));
        }
        left.setBottom(node);
    }

    /// Returns the page content, or null when no content is installed.
    public @Nullable Node getContent() {
        return center.getChildren().isEmpty() ? null : center.getChildren().get(0);
    }

    /// Replaces the page content; null clears it while preserving the animation target.
    public void setContent(@Nullable Node node) {
        if (node == null) {
            center.getChildren().clear();
        } else {
            center.getChildren().setAll(node);
        }
    }
}
