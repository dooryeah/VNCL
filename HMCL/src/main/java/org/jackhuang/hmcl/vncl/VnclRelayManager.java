// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import org.jackhuang.hmcl.Metadata;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Manages the VNCL relay session: room creation/lookup and the optional frpc host/visitor processes.
///
/// The frpc component is downloaded on first use and verified against a pinned SHA-256 checksum.
/// Room authentication is per room: the frps HTTP plugin validates the host and guest tokens issued
/// by the room service. No global frps token is stored in the client.
@NotNullByDefault
public final class VnclRelayManager {

    /// Room service base URL.
    private static final String API_BASE = "https://relay.nekoinn.top/vncl/api/rooms";

    /// Download URL for the optional frpc component.
    private static final String FRPC_DOWNLOAD_URL = "https://relay.nekoinn.top/vncl/download/frpc.exe";

    /// Expected size of the downloaded frpc build.
    private static final long FRPC_SIZE = 16_442_368L;

    /// SHA-256 of the downloaded frpc build, used to reject corrupted or tampered downloads.
    private static final String FRPC_SHA256 = "332B1CFDFEC48C6745F94904026DEDC0BF3872D887E161B9E555C610554AAB49";

    /// Last frpc file whose checksum was verified, to avoid rehashing 16 MB for every start.
    private static @Nullable Path verifiedFrpcPath;
    private static long verifiedFrpcSize = -1;
    private static long verifiedFrpcModified = -1;

    /// HTTP client for the room service.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /// Room created by this launcher, used for heartbeat and deletion.
    private static @Nullable HostRoom hostRoom;

    /// Room joined by this launcher.
    private static @Nullable GuestRoom guestRoom;

    /// Host frpc process.
    private static @Nullable Process hostProcess;

    /// Guest frpc process.
    private static @Nullable Process guestProcess;

    /// Heartbeat scheduler for the host room.
    private static @Nullable ScheduledExecutorService heartbeatExecutor;

    /// Background executor for room cleanup.
    private static final java.util.concurrent.ExecutorService CLEANUP_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "VNCL-Relay-Cleanup");
                thread.setDaemon(true);
                return thread;
            });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(VnclRelayManager::shutdown, "VNCL-Relay-Shutdown"));
    }

    /// The LAN port detected from the Minecraft log, or -1 when unknown.
    private static final ReadOnlyIntegerWrapper detectedLanPort = new ReadOnlyIntegerWrapper(-1);

    /// Returns the detected Minecraft LAN port, or -1.
    public static ReadOnlyIntegerProperty detectedLanPortProperty() {
        return detectedLanPort.getReadOnlyProperty();
    }

    /// Records a LAN port detected from the game log.
    ///
    /// @param port the port, or -1 to clear
    public static void setDetectedLanPort(int port) {
        Platform.runLater(() -> detectedLanPort.set(port));
    }

    /// Creates a relay room through the room service.
    ///
    /// @return the created room
    /// @throws IOException          if the service is unreachable
    /// @throws InterruptedException if the request is interrupted
    public static HostRoom createRoom() throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(API_BASE))
                        .timeout(Duration.ofSeconds(25))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("room service returned " + response.statusCode() + ": " + response.body());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return new HostRoom(
                json.get("code").getAsString(),
                json.get("proxyName").getAsString(),
                json.get("secret").getAsString(),
                json.get("hostToken").getAsString(),
                json.get("frpServer").getAsString(),
                json.get("frpPort").getAsInt());
    }

    /// Looks up a relay room by code.
    ///
    /// @param code the room code
    /// @return the room join information
    /// @throws IOException          if the room is missing or the service is unreachable
    /// @throws InterruptedException if the request is interrupted
    public static GuestRoom joinRoom(String code) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(API_BASE + "/" + code.trim().toUpperCase()))
                        .timeout(Duration.ofSeconds(25))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("room service returned " + response.statusCode() + ": " + response.body());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return new GuestRoom(
                json.get("code").getAsString(),
                json.get("proxyName").getAsString(),
                json.get("secret").getAsString(),
                json.get("guestToken").getAsString(),
                json.get("frpServer").getAsString(),
                json.get("frpPort").getAsInt());
    }

    /// Starts the host frpc for a room.
    ///
    /// @param room      the room descriptor
    /// @param localPort the Minecraft LAN port
    /// @throws IOException if frpc cannot be started
    public static synchronized void startHost(HostRoom room, int localPort) throws IOException {
        stopHost();
        hostRoom = room;
        Path config = relayDirectory().resolve("host-" + room.code() + ".toml");
        Files.writeString(config, hostConfig(room, localPort), StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(frpcPath().toString(), "-c", config.toString());
        builder.redirectErrorStream(true);
        hostProcess = builder.start();
        consumeOutput(hostProcess, "relay-host");

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "VNCL-Relay-Heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/" + room.code() + "/heartbeat"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + room.hostToken())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception exception) {
                LOG.warning("VNCL relay heartbeat failed", exception);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /// Starts the guest visitor for a room.
    ///
    /// @param room      the room descriptor
    /// @param localPort the local port Minecraft connects to
    /// @throws IOException if frpc cannot be started
    public static synchronized void startGuest(GuestRoom room, int localPort) throws IOException {
        stopGuest();
        guestRoom = room;
        Path config = relayDirectory().resolve("guest-" + room.code() + ".toml");
        Files.writeString(config, guestConfig(room, localPort), StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(frpcPath().toString(), "-c", config.toString());
        builder.redirectErrorStream(true);
        guestProcess = builder.start();
        consumeOutput(guestProcess, "relay-guest");
    }

    /// Stops the host process, heartbeat, and deletes the room when possible.
    public static synchronized void stopHost() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        if (hostProcess != null) {
            hostProcess.destroyForcibly();
            hostProcess = null;
        }
        HostRoom room = hostRoom;
        hostRoom = null;
        if (room != null) {
            CLEANUP_EXECUTOR.execute(() -> {
                try {
                    HTTP.send(HttpRequest.newBuilder(URI.create(API_BASE + "/" + room.code()))
                                    .timeout(Duration.ofSeconds(15))
                                    .header("Authorization", "Bearer " + room.hostToken())
                                    .DELETE()
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());
                } catch (Exception exception) {
                    LOG.warning("VNCL relay room deletion failed", exception);
                }
            });
        }
    }

    /// Stops the guest visitor.
    public static synchronized void stopGuest() {
        if (guestProcess != null) {
            guestProcess.destroyForcibly();
            guestProcess = null;
        }
        guestRoom = null;
    }

    /// Stops both relay roles.
    public static synchronized void shutdown() {
        stopHost();
        stopGuest();
    }

    /// Returns whether a host relay is currently running.
    public static synchronized boolean isHosting() {
        return hostProcess != null && hostProcess.isAlive();
    }

    /// Returns whether a guest visitor is currently running.
    public static synchronized boolean isJoined() {
        return guestProcess != null && guestProcess.isAlive();
    }

    /// Returns the current host room, or null.
    public static synchronized @Nullable HostRoom getHostRoom() {
        return hostRoom;
    }

    /// Returns the current guest room, or null.
    public static synchronized @Nullable GuestRoom getGuestRoom() {
        return guestRoom;
    }

    private static String hostConfig(HostRoom room, int localPort) {
        return """
                serverAddr = "%s"
                serverPort = %d
                transport.tls.enable = true

                user = "vncl-%s-host"
                metadatas.token = "%s"

                [[proxies]]
                name = "%s"
                type = "stcp"
                secretKey = "%s"
                localIP = "127.0.0.1"
                localPort = %d
                allowUsers = ["*"]
                """.formatted(room.frpServer(), room.frpPort(), room.code(), room.hostToken(),
                room.proxyName(), room.secret(), localPort);
    }

    private static String guestConfig(GuestRoom room, int localPort) {
        return """
                serverAddr = "%s"
                serverPort = %d
                transport.tls.enable = true

                user = "vncl-%s-guest"
                metadatas.token = "%s"

                [[visitors]]
                name = "%s-visitor"
                type = "stcp"
                serverUser = "vncl-%s-host"
                serverName = "%s"
                secretKey = "%s"
                bindAddr = "127.0.0.1"
                bindPort = %d
                """.formatted(room.frpServer(), room.frpPort(), room.code(), room.guestToken(),
                room.proxyName(), room.code(), room.proxyName(), room.secret(), localPort);
    }

    private static Path relayDirectory() throws IOException {
        Path directory = Metadata.HMCL_USER_HOME.resolve("cache").resolve("relay");
        Files.createDirectories(directory);
        return directory;
    }

    private static Path frpcPath() throws IOException {
        Path directory = relayDirectory();
        Path target = directory.resolve("frpc.exe");
        if (isValidFrpc(target)) {
            return target;
        }

        Path temporary = Files.createTempFile(directory, "frpc-", ".exe");
        try {
            HttpResponse<Path> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(FRPC_DOWNLOAD_URL))
                            .timeout(Duration.ofMinutes(5))
                            .GET()
                            .build(),
                    BodyHandlers.ofFile(temporary));
            if (response.statusCode() / 100 != 2) {
                throw new IOException("frpc download returned " + response.statusCode());
            }
            if (Files.size(temporary) != FRPC_SIZE || !FRPC_SHA256.equalsIgnoreCase(sha256(temporary))) {
                throw new IOException("frpc download failed integrity check");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("frpc download interrupted", exception);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /// Returns whether the cached frpc has the expected size and checksum.
    private static synchronized boolean isValidFrpc(Path file) {
        try {
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (file.equals(verifiedFrpcPath) && size == verifiedFrpcSize && modified == verifiedFrpcModified) {
                return true;
            }
            if (!Files.isRegularFile(file) || size != FRPC_SIZE || !FRPC_SHA256.equalsIgnoreCase(sha256(file))) {
                return false;
            }
            verifiedFrpcPath = file;
            verifiedFrpcSize = size;
            verifiedFrpcModified = modified;
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    /// Computes the SHA-256 hex digest of a file.
    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest()).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is not available", exception);
        }
    }

    private static void consumeOutput(Process process, String threadName) {
        Thread thread = new Thread(() -> {
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOG.info("[" + threadName + "] " + line);
                }
            } catch (IOException ignored) {
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /// A room created by this launcher.
    public record HostRoom(String code, String proxyName, String secret, String hostToken,
                           String frpServer, int frpPort) {
    }

    /// A room joined by this launcher.
    public record GuestRoom(String code, String proxyName, String secret, String guestToken,
                            String frpServer, int frpPort) {
    }

    private VnclRelayManager() {
    }
}
