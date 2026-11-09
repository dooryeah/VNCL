// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.vncl.VnclInstanceStyle;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameAdvancedListItem extends AdvancedListItem {
    private final ImageContainer imageContainer;
    private final javafx.scene.layout.StackPane imageFrame;
    private final WeakListenerHolder holder = new WeakListenerHolder();

    /// Strongly held so [WeakChangeListener] keeps delivering icon updates.
    private final ChangeListener<Image> iconListener;

    private @Nullable WeakChangeListener<Image> weakIconListener;
    private @Nullable ReadOnlyObjectProperty<Image> observedIcon;

    /// Weak listener for the selected instance's VNCL accent style.
    private @Nullable WeakChangeListener<VnclInstanceStyle> weakStyleListener;

    /// The observed style property, or null.
    private @Nullable ReadOnlyObjectProperty<VnclInstanceStyle> observedStyle;

    /// Creates an item that follows the selected game instance and its icon.
    public GameAdvancedListItem() {
        this.imageContainer = new ImageContainer(LEFT_GRAPHIC_SIZE);
        this.iconListener = (observable, oldImage, newImage) -> imageContainer.setImage(newImage);
        imageContainer.setMouseTransparent(true);
        this.imageFrame = new javafx.scene.layout.StackPane(imageContainer);
        setLeftGraphic(imageFrame);

        holder.add(FXUtils.onWeakChangeAndOperate(GameDirectoryManager.selectedInstanceProperty(), this::loadInstance));
    }

    private void loadInstance(@Nullable HMCLGameInstance instance) {
        unbindIcon();
        unbindStyle();
        if (instance != null) {
            setTitle(i18n("instance.manage.manage"));
            setSubtitle(instance.getId().toString());
            observedIcon = instance.iconImageProperty();
            weakIconListener = new WeakChangeListener<>(iconListener);
            observedIcon.addListener(weakIconListener);
            imageContainer.setImage(instance.getIconImage());

            observedStyle = instance.vnclStyleProperty();
            weakStyleListener = new WeakChangeListener<>((observable, oldValue, newValue) -> applyAccent(newValue));
            observedStyle.addListener(weakStyleListener);
            applyAccent(instance.getVnclStyle());
            return;
        }

        imageContainer.setStyle("");
        imageFrame.setStyle("");
        setTitle(i18n("instance.empty"));
        setSubtitle(i18n("instance.empty.add"));
        imageContainer.setImage(GameInstanceIconType.DEFAULT.getIcon());
    }

    /// Applies the instance accent as a thin ring around the sidebar icon.
    ///
    /// @param style the instance style
    private void applyAccent(VnclInstanceStyle style) {
        String cssPaint = style.cssPaint();
        if (cssPaint == null) {
            imageFrame.setStyle("");
        } else {
            imageFrame.setStyle("-fx-border-color: " + cssPaint
                    + "; -fx-border-width: 2; -fx-border-radius: 8;");
        }
    }

    private void unbindStyle() {
        if (observedStyle != null && weakStyleListener != null) {
            observedStyle.removeListener(weakStyleListener);
        }
        observedStyle = null;
        weakStyleListener = null;
    }

    private void unbindIcon() {
        if (observedIcon != null && weakIconListener != null) {
            observedIcon.removeListener(weakIconListener);
        }
        observedIcon = null;
        weakIconListener = null;
    }
}
