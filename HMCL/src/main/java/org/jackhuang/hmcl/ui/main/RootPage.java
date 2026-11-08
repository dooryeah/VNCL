// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.terracotta.TerracottaMetadata;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.account.AccountListPopupMenu;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.instances.GameAdvancedListItem;
import org.jackhuang.hmcl.ui.instances.GameListPopupMenu;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.ui.nbt.NBTFileType;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.vncl.VnclUiConfig;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Displays the home content and launcher navigation sidebar.
public class RootPage extends DecoratorAnimatedPage implements DecoratorPage {
    /// The home content, created on first access.
    private @Nullable MainPage mainPage;

    /// Creates the home page and its navigation entries.
    public RootPage() {
        getStyleClass().remove("gray-background");
        getLeft().getStyleClass().add("gray-background");

        // first item in left sidebar
        AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
        accountListItem.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));
        FXUtils.onSecondaryButtonClicked(accountListItem, () -> AccountListPopupMenu.show(accountListItem, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, accountListItem.getWidth(), 0));
        accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());

        // second item in left sidebar
        GameAdvancedListItem gameInstanceItem = new GameAdvancedListItem();
        gameInstanceItem.setOnAction(e -> {
            @Nullable HMCLGameInstance instance = GameDirectoryManager.getSelectedRepository().getSelectedInstance();
            if (instance == null) {
                Controllers.navigate(Controllers.getGameListPage());
            } else {
                Instances.modifyGameSettings(instance);
            }
        });
        FXUtils.onScroll(gameInstanceItem, getMainPage().getInstances(), list -> {
            @Nullable HMCLGameInstance currentGame = getMainPage().getCurrentGame();
            @Nullable GameInstanceID currentId = currentGame != null ? currentGame.getId() : null;
            return Lang.indexWhere(list, instance -> instance.getId().equals(currentId));
        }, instance -> instance.getRepository().setSelectedInstance(instance));
        FXUtils.onSecondaryButtonClicked(gameInstanceItem, () -> showGameListPopupMenu(gameInstanceItem));

        if (AnimationUtils.isAnimationEnabled()) {
            gameInstanceItem.setPrepareAction(Controllers::prepareGameInstancePage);
        }

        // VNCL: the launch action lives in the sidebar, right below the instance selector.
        JFXButton launchButton = new JFXButton();
        launchButton.getStyleClass().add("vncl-sidebar-launch");
        launchButton.setMaxWidth(Double.MAX_VALUE);
        launchButton.setText(i18n("instance.launch.empty"));
        FXUtils.setOnActionWithCooldown(launchButton, getMainPage()::launchSelected);
        getMainPage().currentGameProperty().addListener((observable, oldValue, newValue) ->
                launchButton.setText(newValue == null ? i18n("instance.launch.empty") : i18n("instance.launch")));
        VBox launchBox = new VBox(launchButton);
        launchBox.setPadding(new Insets(8, 12, 6, 12));

        // VNCL: the instance list is reachable from the instance selector, so no duplicate sidebar entry.

        // third item in left sidebar
        AdvancedListItem downloadItem = new AdvancedListItem();
        downloadItem.setLeftIcon(SVG.DOWNLOAD);
        downloadItem.setTitle(i18n("download"));
        downloadItem.setOnAction(e -> Controllers.navigate(Controllers.getDownloadPage()));

        if (AnimationUtils.isAnimationEnabled()) {
            downloadItem.setPrepareAction(Controllers::prepareDownloadPage);
        }

        // VNCL: Terracotta (HMCL's multiplayer service) is intentionally not exposed in this fork.

        // VNCL: compact rail shown while the sidebar is collapsed.
        JFXButton railAccount = createRailButton(SVG.PERSON, i18n("account"),
                () -> Controllers.navigate(Controllers.getAccountListPage()));
        JFXButton railInstance = createRailButton(SVG.FORMAT_LIST_BULLETED, i18n("instance"), () -> {
            @Nullable HMCLGameInstance instance = GameDirectoryManager.getSelectedRepository().getSelectedInstance();
            if (instance == null) {
                Controllers.navigate(Controllers.getGameListPage());
            } else {
                Instances.modifyGameSettings(instance);
            }
        });
        JFXButton railLaunch = createRailButton(SVG.ARROW_FORWARD, i18n("instance.launch"), getMainPage()::launchSelected);
        JFXButton railDownload = createRailButton(SVG.DOWNLOAD, i18n("download"),
                () -> Controllers.navigate(Controllers.getDownloadPage()));
        Region railSpacer = new Region();
        VBox.setVgrow(railSpacer, Priority.ALWAYS);
        VBox rail = new VBox(6, railAccount, railInstance, railLaunch, railDownload, railSpacer);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setPadding(new Insets(10, 6, 0, 6));
        rail.getStyleClass().add("vncl-compact-sidebar");
        setCompactSidebar(rail);

        // VNCL: the collapse toggle lives at the sidebar bottom in both states.
        JFXButton collapseToggle = new JFXButton();
        collapseToggle.getStyleClass().add("vncl-sidebar-toggle");
        collapseToggle.setGraphic(SVG.ARROW_BACK.createIcon());
        FXUtils.installFastTooltip(collapseToggle, i18n("vncl.sidebar.toggle"));
        collapseToggle.setOnAction(event -> {
            boolean collapsed = !isSidebarCollapsed();
            setSidebarCollapsed(collapsed);
            VnclUiConfig.sidebarCollapsedProperty().set(collapsed);
            collapseToggle.setGraphic((collapsed ? SVG.ARROW_FORWARD : SVG.ARROW_BACK).createIcon());
        });
        HBox bottomBar = new HBox(collapseToggle);
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.getStyleClass().add("vncl-sidebar-toggle-bar");
        setSidebarBottom(bottomBar);

        if (VnclUiConfig.sidebarCollapsedProperty().get()) {
            Platform.runLater(() -> {
                setSidebarCollapsed(true);
                collapseToggle.setGraphic(SVG.ARROW_FORWARD.createIcon());
            });
        }

        // the left sidebar
        // VNCL: the launcher settings entry moved to the title-bar gear; feedback stays in the settings page.
        getSidebar()
                .startCategory(i18n("account").toUpperCase(Locale.ROOT))
                .add(accountListItem)
                .startCategory(i18n("instance").toUpperCase(Locale.ROOT))
                .add(gameInstanceItem)
                .add(launchBox)
                .add(downloadItem);

        // the root page, with the sidebar in left, navigator in center.
        setContent(getMainPage());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return getMainPage().stateProperty();
    }

    /// Returns the home content with repository bindings and file-drop handling installed.
    public MainPage getMainPage() {
        if (mainPage == null) {
            MainPage mainPage = new MainPage();
            FXUtils.applyDragListener(mainPage,
                    file -> ModpackHelper.isFileModpackByExtension(file) || NBTFileType.isNBTFileByExtension(file) || "json".equalsIgnoreCase(FileUtils.getExtension(file)),
                    modpacks -> {
                        Path file = modpacks.get(0);
                        if (ModpackHelper.isFileModpackByExtension(file)) {
                            Controllers.getDecorator().startWizard(
                                    new ModpackInstallWizardProvider(GameDirectoryManager.getSelectedRepository(), file),
                                    i18n("install.modpack"));
                        } else if (NBTFileType.isNBTFileByExtension(file)) {
                            try {
                                Controllers.navigate(new NBTEditorPage(file));
                            } catch (Throwable e) {
                                LOG.warning("Fail to open nbt file", e);
                                Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                            }
                        } else if ("json".equalsIgnoreCase(FileUtils.getExtension(file))) {
                            Instances.installFromJson(GameDirectoryManager.getSelectedRepository(), file);
                        }
                    });

            FXUtils.onChangeAndOperate(GameDirectoryManager.selectedInstanceProperty(), mainPage::setCurrentGame);
            mainPage.latestVersionProperty().bind(UpdateChecker.latestVersionProperty());
            this.mainPage = mainPage;
        }
        return mainPage;
    }

    /// Opens the instance selection menu beside the sidebar entry.
    private void showGameListPopupMenu(Region gameListItem) {
        GameListPopupMenu.show(gameListItem,
                JFXPopup.PopupVPosition.TOP,
                JFXPopup.PopupHPosition.LEFT,
                gameListItem.getWidth(),
                0,
                getMainPage().getInstances());
    }

    /// Builds an icon-only button for the collapsed sidebar rail.
    private static JFXButton createRailButton(SVG icon, String tooltip, Runnable action) {
        JFXButton button = new JFXButton();
        button.getStyleClass().add("vncl-rail-button");
        button.setGraphic(icon.createIcon());
        FXUtils.installFastTooltip(button, tooltip);
        button.setOnAction(event -> action.run());
        return button;
    }
}
