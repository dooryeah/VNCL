// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.vncl.VnclRelayManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Standalone page for creating and joining VNCL relay rooms.
@NotNullByDefault
public final class VnclRelayPage extends StackPane implements DecoratorPage {

    /// Background executor for room API and frpc setup.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VNCL-Relay-UI");
        thread.setDaemon(true);
        return thread;
    });

    /// Page title state.
    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("vncl.relay.title")));

    /// Minecraft LAN port input.
    private final JFXTextField hostPortField = new JFXTextField();

    /// Room code input.
    private final JFXTextField joinCodeField = new JFXTextField();

    /// Host status text.
    private final Label hostStatus = new Label(i18n("vncl.relay.status.idle"));

    /// Join status text.
    private final Label joinStatus = new Label(i18n("vncl.relay.status.idle"));

    /// Created room code.
    private final Label roomCode = new Label();

    /// Local address shown to the guest.
    private final Label localAddress = new Label();

    /// Host start button.
    private final JFXButton hostStart = new JFXButton(i18n("vncl.relay.host.start"));

    /// Host stop button.
    private final JFXButton hostStop = new JFXButton(i18n("vncl.relay.host.stop"));

    /// Join button.
    private final JFXButton joinStart = new JFXButton(i18n("vncl.relay.join.start"));

    /// Disconnect button.
    private final JFXButton joinStop = new JFXButton(i18n("vncl.relay.join.stop"));

    /// Creates the relay page.
    public VnclRelayPage() {
        getStyleClass().add("vncl-relay-page");

        VBox content = new VBox(16);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(createIntro(), createHostCard(), createJoinCard());

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        FXUtils.smoothScrolling(scrollPane);
        getChildren().add(scrollPane);

        hostPortField.setPrefWidth(120);
        joinCodeField.setPrefWidth(160);
        hostPortField.setPromptText("25565");
        joinCodeField.setPromptText(i18n("vncl.relay.join.code"));

        int detected = VnclRelayManager.detectedLanPortProperty().get();
        hostPortField.setText(Integer.toString(detected > 0 ? detected : 25565));
        VnclRelayManager.detectedLanPortProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.intValue() > 0) {
                hostPortField.setText(newValue.toString());
            }
        });

        hostStart.getStyleClass().add("jfx-button-raised");
        hostStop.getStyleClass().add("jfx-button-border");
        joinStart.getStyleClass().add("jfx-button-raised");
        joinStop.getStyleClass().add("jfx-button-border");

        hostStop.setDisable(true);
        joinStop.setDisable(true);
        roomCode.setVisible(false);
        roomCode.setManaged(false);
        localAddress.setVisible(false);
        localAddress.setManaged(false);

        hostStart.setOnAction(event -> createHost());
        hostStop.setOnAction(event -> stopHost());
        joinStart.setOnAction(event -> joinRoom());
        joinStop.setOnAction(event -> stopGuest());

        hostStatus.getStyleClass().add("vncl-relay-status");
        joinStatus.getStyleClass().add("vncl-relay-status");
        roomCode.getStyleClass().add("vncl-relay-code");
        localAddress.getStyleClass().add("vncl-relay-address");
    }

    /// Builds the page introduction.
    private Label createIntro() {
        Label label = new Label(i18n("vncl.relay.intro"));
        label.getStyleClass().add("vncl-relay-intro");
        label.setWrapText(true);
        return label;
    }

    /// Builds the host card.
    private VBox createHostCard() {
        HBox portRow = new HBox(8, new Label(i18n("vncl.relay.host.port")), hostPortField, hostStart, hostStop);
        portRow.setAlignment(Pos.CENTER_LEFT);

        HBox codeRow = new HBox(8, new Label(i18n("vncl.relay.room_code")), roomCode,
                createCopyButton(roomCode));
        codeRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = createCard(i18n("vncl.relay.host"), i18n("vncl.relay.host.hint"),
                portRow, codeRow, hostStatus);
        HBox.setHgrow(codeRow, Priority.NEVER);
        return card;
    }

    /// Builds the join card.
    private VBox createJoinCard() {
        HBox codeRow = new HBox(8, new Label(i18n("vncl.relay.join.code")), joinCodeField, joinStart, joinStop);
        codeRow.setAlignment(Pos.CENTER_LEFT);

        HBox addressRow = new HBox(8, new Label(i18n("vncl.relay.local_address")), localAddress,
                createCopyButton(localAddress));
        addressRow.setAlignment(Pos.CENTER_LEFT);

        return createCard(i18n("vncl.relay.join"), i18n("vncl.relay.join.hint"), codeRow, addressRow, joinStatus);
    }

    /// Creates a card with a title and hint.
    private VBox createCard(String title, String hint, javafx.scene.Node... children) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("vncl-relay-card-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("vncl-relay-card-hint");
        hintLabel.setWrapText(true);

        VBox card = new VBox(8);
        card.getStyleClass().add("vncl-relay-card");
        card.getChildren().addAll(titleLabel, hintLabel);
        card.getChildren().addAll(children);
        return card;
    }

    /// Creates a button that copies a label's text to the clipboard.
    private static JFXButton createCopyButton(Label source) {
        JFXButton button = new JFXButton(i18n("vncl.relay.copy"));
        button.getStyleClass().add("jfx-button-border");
        button.setOnAction(event -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(source.getText());
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        });
        return button;
    }

    /// Creates a host room and starts frpc.
    private void createHost() {
        int port;
        try {
            port = Integer.parseInt(hostPortField.getText().trim());
        } catch (NumberFormatException exception) {
            hostStatus.setText(i18n("vncl.relay.error.port"));
            return;
        }
        if (port < 1 || port > 65535) {
            hostStatus.setText(i18n("vncl.relay.error.port"));
            return;
        }

        setBusy(hostStart, true);
        hostStop.setDisable(true);
        hostStatus.setText(i18n("vncl.relay.status.creating"));

        CompletableFuture.supplyAsync(() -> {
            try {
                VnclRelayManager.HostRoom room = VnclRelayManager.createRoom();
                VnclRelayManager.startHost(room, port);
                return room;
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, EXECUTOR).whenComplete((room, error) -> Platform.runLater(() -> {
            setBusy(hostStart, false);
            if (error != null) {
                hostStatus.setText(errorMessage(error));
                return;
            }
            roomCode.setText(room.code());
            roomCode.setVisible(true);
            roomCode.setManaged(true);
            hostStatus.setText(i18n("vncl.relay.status.hosting"));
            hostStop.setDisable(false);
        }));
    }

    /// Stops the host relay.
    private void stopHost() {
        VnclRelayManager.stopHost();
        hostStop.setDisable(true);
        hostStatus.setText(i18n("vncl.relay.status.idle"));
        roomCode.setText("");
        roomCode.setVisible(false);
        roomCode.setManaged(false);
    }

    /// Joins a room and starts the visitor frpc.
    private void joinRoom() {
        String code = joinCodeField.getText().trim().toUpperCase();
        if (code.isEmpty()) {
            joinStatus.setText(i18n("vncl.relay.error.code"));
            return;
        }

        setBusy(joinStart, true);
        joinStop.setDisable(true);
        joinStatus.setText(i18n("vncl.relay.status.joining"));

        CompletableFuture.supplyAsync(() -> {
            try {
                int localPort = findFreePort();
                VnclRelayManager.GuestRoom room = VnclRelayManager.joinRoom(code);
                VnclRelayManager.startGuest(room, localPort);
                return new Joined(room, localPort);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, EXECUTOR).whenComplete((joined, error) -> Platform.runLater(() -> {
            setBusy(joinStart, false);
            if (error != null) {
                joinStatus.setText(errorMessage(error));
                return;
            }
            localAddress.setText("127.0.0.1:" + joined.localPort);
            localAddress.setVisible(true);
            localAddress.setManaged(true);
            joinStatus.setText(i18n("vncl.relay.status.joined"));
            joinStop.setDisable(false);
        }));
    }

    /// Stops the guest visitor.
    private void stopGuest() {
        VnclRelayManager.stopGuest();
        joinStop.setDisable(true);
        joinStatus.setText(i18n("vncl.relay.status.idle"));
        localAddress.setText("");
        localAddress.setVisible(false);
        localAddress.setManaged(false);
    }

    /// Formats an async error for the status label.
    private static String errorMessage(Throwable error) {
        @Nullable Throwable cause = error.getCause() != null ? error.getCause() : error;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? i18n("vncl.relay.error") : message;
    }

    /// Chooses a local port for the visitor, preferring 25566.
    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(25566, 1, java.net.InetAddress.getLoopbackAddress())) {
            socket.setReuseAddress(true);
            return 25566;
        } catch (IOException ignored) {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                return socket.getLocalPort();
            }
        }
    }

    /// Enables or disables a busy button.
    private static void setBusy(JFXButton button, boolean busy) {
        button.setDisable(busy);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /// Result of a successful guest join.
    private record Joined(VnclRelayManager.GuestRoom room, int localPort) {
    }
}
