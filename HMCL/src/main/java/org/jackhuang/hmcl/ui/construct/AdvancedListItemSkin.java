// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.Nullable;

/// Lays out a two-line item, forwards background actions, and registers optional preparation.
public class AdvancedListItemSkin extends SkinBase<AdvancedListItem> {
    /// The pseudo-class reflecting the item's active state.
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /// The background node opacity when the item is fully selected.
    private static final double SELECTED_BACKGROUND_OPACITY = 0.5;

    /// The feedback wrapper receiving the item's preparation registration.
    private final RipplerContainer container;

    /// The theme-colored selection background, faded independently of content and feedback.
    private final Region selectionBackground = new Region();

    /// The reusable selection fade, allocated on the first animated state change.
    private @Nullable FadeTransition selectionTransition;

    /// The short primary-colored underline that reinforces the selected row.
    private final Region selectionUnderline = new Region();

    /// The underline fade, allocated on the first animated state change.
    private @Nullable FadeTransition underlineFade;

    /// The underline scale animation, allocated on the first animated state change.
    private @Nullable ScaleTransition underlineScale;

    /// Creates the content bindings and installs preparation only when configured by the item.
    public AdvancedListItemSkin(AdvancedListItem skinnable) {
        super(skinnable);

        TwoLineListItem item = new TwoLineListItem();
        item.getStyleClass().add("container");

        container = new RipplerContainer(item);
        container.setOnAction(event -> {
            event.consume();
            skinnable.fire();
        });

        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());

        item.leadingProperty().bind(skinnable.leftGraphicProperty());
        item.trailingProperty().bind(skinnable.rightGraphicProperty());

        selectionBackground.setMouseTransparent(true);
        getChildren().setAll(selectionBackground, container);

        selectionUnderline.getStyleClass().add("advanced-list-item-underline");
        selectionUnderline.setMouseTransparent(true);
        selectionUnderline.setMinHeight(2);
        selectionUnderline.setPrefHeight(2);
        selectionUnderline.setMaxHeight(2);
        selectionUnderline.setMinWidth(32);
        selectionUnderline.setPrefWidth(32);
        selectionUnderline.setMaxWidth(32);
        selectionUnderline.setOpacity(0);
        selectionUnderline.setScaleX(0.7);
        StackPane.setAlignment(selectionUnderline, Pos.BOTTOM_CENTER);
        container.getChildren().add(selectionUnderline);

        updateSelectionFill();
        registerChangeListener(skinnable.selectedBackgroundFillProperty(), observable -> updateSelectionFill());
        updateSelection(false);
        registerChangeListener(skinnable.activeProperty(), observable -> updateSelection(true));
        registerChangeListener(skinnable.sceneProperty(), observable -> updateSelection(false));
        registerChangeListener(JFXNodeUtils.treeVisibleProperty(skinnable), observable -> updateSelection(false));
        updateActionDisabled();
    }

    /// Applies the control's selected background paint without changing animation progress.
    private void updateSelectionFill() {
        @Nullable Paint fill = getSkinnable().getSelectedBackgroundFill();
        selectionBackground.setBackground(fill == null ? null
                : new Background(new BackgroundFill(fill, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    /// Updates selection styling and fades from the displayed opacity when the item is showing.
    /// Initial, detached, hidden, and animation-disabled states are applied immediately.
    private void updateSelection(boolean animate) {
        AdvancedListItem item = getSkinnable();
        boolean active = item.isActive();
        item.pseudoClassStateChanged(SELECTED, active);
        double target = active ? SELECTED_BACKGROUND_OPACITY : 0;
        double current = selectionBackground.getOpacity();
        if (selectionTransition != null)
            selectionTransition.stop();
        if (!animate || !AnimationUtils.isAnimationEnabled() || !JFXNodeUtils.isTreeShowing(item) || current == target) {
            selectionBackground.setOpacity(target);
            updateUnderline(active, false);
            return;
        }

        if (selectionTransition == null) {
            selectionTransition = new FadeTransition(Motion.SHORT4, selectionBackground);
            selectionTransition.setInterpolator(Motion.LINEAR);
        }
        // Preserve the displayed opacity when reversing an unfinished fade.
        selectionBackground.setOpacity(current);
        selectionTransition.setFromValue(current);
        selectionTransition.setToValue(target);
        selectionTransition.setOnFinished(event -> selectionBackground.setOpacity(target));
        selectionTransition.playFromStart();

        updateUnderline(active, true);
    }

    /// Fades and grows the selection underline, or applies the final state when motion is disabled.
    private void updateUnderline(boolean active, boolean animate) {
        double targetOpacity = active ? 1 : 0;
        double targetScale = active ? 1 : 0.7;
        double currentOpacity = selectionUnderline.getOpacity();
        if (underlineFade != null)
            underlineFade.stop();
        if (underlineScale != null)
            underlineScale.stop();
        if (!animate || !AnimationUtils.isAnimationEnabled() || !JFXNodeUtils.isTreeShowing(getSkinnable())) {
            selectionUnderline.setOpacity(targetOpacity);
            selectionUnderline.setScaleX(targetScale);
            return;
        }

        if (underlineFade == null) {
            underlineFade = new FadeTransition(Motion.SHORT3, selectionUnderline);
            underlineFade.setInterpolator(Motion.STANDARD);
        }
        if (underlineScale == null) {
            underlineScale = new ScaleTransition(Motion.SHORT3, selectionUnderline);
            underlineScale.setInterpolator(Motion.EMPHASIZED_DECELERATE);
        }

        selectionUnderline.setOpacity(currentOpacity);
        underlineFade.setFromValue(currentOpacity);
        underlineFade.setToValue(targetOpacity);
        underlineFade.setOnFinished(event -> selectionUnderline.setOpacity(targetOpacity));
        underlineFade.playFromStart();

        underlineScale.setFromX(selectionUnderline.getScaleX());
        underlineScale.setToX(targetScale);
        underlineScale.setOnFinished(event -> selectionUnderline.setScaleX(targetScale));
        underlineScale.playFromStart();
    }

    /// Updates main-action feedback and preparation without allocating the control's property.
    void updateActionDisabled() {
        AdvancedListItem item = getSkinnable();
        container.setPressFeedbackEnabled(!item.isActionDisabled());
        setPrepareAction(item.getPrepareAction());
    }

    /// Suspends preparation while the main action is disabled and consumes it before execution.
    void setPrepareAction(@Nullable Runnable action) {
        AdvancedListItem item = getSkinnable();
        container.setPrepareAction(action == null || item.isActionDisabled() ? null : () -> {
            if (item.getPrepareAction() == action) {
                item.setPrepareAction(null);
                action.run();
            }
        });
    }

    /// Stops the selection fade, removes background activation, and releases graphics.
    /// Unconsumed preparation remains on the control.
    @Override
    public void dispose() {
        if (selectionTransition != null)
            selectionTransition.stop();
        if (underlineFade != null)
            underlineFade.stop();
        if (underlineScale != null)
            underlineScale.stop();
        container.setOnAction(null);
        container.setPrepareAction(null);
        TwoLineListItem item = (TwoLineListItem) container.getContainer();
        item.leadingProperty().unbind();
        item.trailingProperty().unbind();
        item.setLeading(null);
        item.setTrailing(null);
        super.dispose();
    }
}
