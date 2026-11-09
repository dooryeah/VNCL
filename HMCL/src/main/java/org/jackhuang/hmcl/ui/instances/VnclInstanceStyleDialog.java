// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXRadioButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.vncl.VnclInstanceStyle;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Dialog for VNCL-only instance personalization: cover image, icon entry, and a solid/gradient accent.
@NotNullByDefault
public final class VnclInstanceStyleDialog extends JFXDialogLayout {

    /// The instance being edited.
    private final HMCLGameInstance instance;

    /// Called after the dialog saves.
    private final Runnable onFinish;

    /// Preview banner background.
    private final Region preview = new Region();

    /// Accent strip under the preview.
    private final Region accentStrip = new Region();

    /// Custom icon preview.
    private final ImageView iconPreview = new ImageView();

    /// Primary accent picker.
    private final ColorPicker firstColor = new ColorPicker();

    /// Secondary accent picker.
    private final ColorPicker secondColor = new ColorPicker();

    /// Solid/gradient switch.
    private final JFXRadioButton solidChoice = new JFXRadioButton(i18n("vncl.instance.accent.solid"));
    private final JFXRadioButton gradientChoice = new JFXRadioButton(i18n("vncl.instance.accent.gradient"));

    /// Creates the personalization dialog.
    ///
    /// @param instance the instance to edit
    /// @param onFinish called after saving
    public VnclInstanceStyleDialog(HMCLGameInstance instance, Runnable onFinish) {
        this.instance = instance;
        this.onFinish = onFinish;

        setHeading(new Label(i18n("vncl.instance.personalize")));
        setBody(createBody());
        setActions(createActions());
        refreshPreview();
    }

    /// Builds the dialog body.
    private VBox createBody() {
        preview.setMinHeight(150);
        preview.setPrefHeight(150);
        preview.getStyleClass().add("vncl-instance-preview");

        iconPreview.setFitWidth(44);
        iconPreview.setFitHeight(44);
        iconPreview.setPreserveRatio(true);

        Label name = new Label(instance.getId().toString());
        name.getStyleClass().add("vncl-instance-preview-title");
        HBox title = new HBox(8, iconPreview, name);
        title.setAlignment(Pos.CENTER_LEFT);
        title.setPadding(new Insets(10));

        accentStrip.setMinHeight(4);
        accentStrip.setPrefHeight(4);
        StackPane previewBox = new StackPane(preview, title, accentStrip);
        StackPane.setAlignment(title, Pos.TOP_LEFT);
        StackPane.setAlignment(accentStrip, Pos.BOTTOM_CENTER);

        JFXButton chooseCover = new JFXButton(i18n("vncl.instance.cover.choose"));
        chooseCover.getStyleClass().add("jfx-button-border");
        chooseCover.setOnAction(e -> chooseCover());

        JFXButton removeCover = new JFXButton(i18n("vncl.instance.cover.remove"));
        removeCover.getStyleClass().add("jfx-button-border");
        removeCover.setOnAction(e -> {
            instance.deleteCoverFile();
            refreshPreview();
        });

        JFXButton changeIcon = new JFXButton(i18n("vncl.instance.icon.change"));
        changeIcon.getStyleClass().add("jfx-button-border");
        changeIcon.setOnAction(e -> Controllers.dialog(new GameInstanceIconDialog(instance, this::refreshPreview)));

        HBox coverActions = new HBox(8, chooseCover, removeCover, changeIcon);
        coverActions.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup mode = new ToggleGroup();
        solidChoice.setToggleGroup(mode);
        gradientChoice.setToggleGroup(mode);
        HBox modeBox = new HBox(12, solidChoice, gradientChoice);
        modeBox.setAlignment(Pos.CENTER_LEFT);

        firstColor.setPrefWidth(160);
        secondColor.setPrefWidth(160);
        HBox colors = new HBox(12, new Label(i18n("vncl.instance.accent.color1")), firstColor,
                new Label(i18n("vncl.instance.accent.color2")), secondColor);
        colors.setAlignment(Pos.CENTER_LEFT);

        solidChoice.selectedProperty().addListener((observable, oldValue, selected) -> {
            secondColor.setVisible(!selected);
            secondColor.setManaged(!selected);
        });

        firstColor.valueProperty().addListener(observable -> updateAccentPreview());
        secondColor.valueProperty().addListener(observable -> updateAccentPreview());
        mode.selectedToggleProperty().addListener(observable -> updateAccentPreview());

        VBox body = new VBox(12,
                new Label(i18n("vncl.instance.cover")), previewBox, coverActions,
                new Label(i18n("vncl.instance.accent")), modeBox, colors);
        body.setPadding(new Insets(8, 0, 0, 0));
        return body;
    }

    /// Builds the dialog actions.
    private HBox createActions() {
        JFXButton reset = new JFXButton(i18n("button.reset"));
        reset.getStyleClass().add("dialog-cancel");
        reset.setOnAction(e -> {
            try {
                instance.setVnclStyle(VnclInstanceStyle.DEFAULT);
                refreshPreview();
            } catch (IOException exception) {
                LOG.warning("Failed to reset instance style", exception);
            }
        });

        JFXButton cancel = new JFXButton(i18n("button.cancel"));
        cancel.getStyleClass().add("dialog-cancel");
        cancel.setOnAction(e -> fireEvent(new DialogCloseEvent()));

        JFXButton save = new JFXButton(i18n("button.save"));
        save.getStyleClass().add("dialog-accept");
        save.setOnAction(e -> save());

        HBox actions = new HBox(8, reset, cancel, save);
        actions.setAlignment(Pos.CENTER_RIGHT);
        onEscPressed(this, cancel::fire);
        return actions;
    }

    /// Opens the image chooser and copies the selected cover.
    private void chooseCover() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(FXUtils.getImageExtensionFilter());
        Path selected = Controllers.showOpenDialog(chooser);
        if (selected == null) {
            return;
        }
        try {
            instance.setCoverFile(selected);
            refreshPreview();
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Failed to set instance cover", e);
        }
    }

    /// Saves the accent style and closes the dialog.
    private void save() {
        boolean gradient = gradientChoice.isSelected();
        String first = toHex(firstColor.getValue());
        String second = toHex(secondColor.getValue());
        try {
            instance.setVnclStyle(new VnclInstanceStyle(gradient, first, second));
            onFinish.run();
            fireEvent(new DialogCloseEvent());
        } catch (IOException e) {
            LOG.warning("Failed to save instance style", e);
        }
    }

    /// Refreshes the cover, icon, and accent preview from the instance.
    private void refreshPreview() {
        Image cover = instance.getCoverImage();
        if (cover != null) {
            preview.setBackground(new Background(new BackgroundImage(cover,
                    BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(1.0, 1.0, true, true, false, true))));
        } else {
            preview.setBackground(null);
        }

        Image icon = instance.getIconImage();
        iconPreview.setImage(icon);

        VnclInstanceStyle style = instance.getVnclStyle();
        Paint paint = style.paint();
        accentStrip.setBackground(paint == null ? null : new Background(
                new javafx.scene.layout.BackgroundFill(paint, javafx.scene.layout.CornerRadii.EMPTY, Insets.EMPTY)));

        if (style.color1() != null) {
            firstColor.setValue(Color.web(style.color1()));
        }
        if (style.color2() != null) {
            secondColor.setValue(Color.web(style.color2()));
        }
        if (style.gradient()) {
            gradientChoice.setSelected(true);
        } else {
            solidChoice.setSelected(true);
        }
    }

    /// Updates the accent strip preview from the pickers.
    private void updateAccentPreview() {
        Paint paint;
        if (gradientChoice.isSelected()) {
            paint = new VnclInstanceStyle(true, toHex(firstColor.getValue()), toHex(secondColor.getValue())).paint();
        } else {
            paint = new VnclInstanceStyle(false, toHex(firstColor.getValue()), null).paint();
        }
        accentStrip.setBackground(paint == null ? null : new Background(
                new javafx.scene.layout.BackgroundFill(paint, javafx.scene.layout.CornerRadii.EMPTY, Insets.EMPTY)));
    }

    /// Formats a color as #RRGGBB.
    private static String toHex(Color color) {
        return "#%02X%02X%02X".formatted(
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
