package com.sammy.catskinc.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sammy.catskinc.client.ServerApiClient.UpdateEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * WebSocket client for CatSkinC real-time skin updates.
 * Uses Java 11+ standard library {@link java.net.http.WebSocket}.
 */
public final class WebSocketClient {
    /** Listener interface for WebSocket events */
    public interface Listener {
        default void onOpen() {}
        default void onClose(int statusCode, String reason) {}
        default void onError(Throwable error) {}
        default void onSkinUpdate(UpdateEvent event) {}
        default void onToast(String toastType, String uuid, String title) {}
    }

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "CatSkinC-WS");
        thread.setDaemon(true);
        return thread;
    });

    private static final int DEFAULT_TIMEOUT_SECONDS = 15;
    private static final long RECONNECT_BASE_DELAY_MS = 1_500L;
    private static final long RECONNECT_MAX_DELAY_MS = 60_000L;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final String HEADER_PROTOCOL = "x-catskinc-protocol";
    private static final int CLIENT_PROTOCOL_VERSION = ServerApiClient.WS_PROTOCOL_VERSION;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final String baseUrl;
    private final String requestSigningKey;
    private final int timeoutMs;
    private final Listener listener;

    private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    private final AtomicReference<HttpClient> httpClientRef = new AtomicReference<>();
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private volatile boolean stopped = false;
    private volatile String sessionToken = null;
    private volatile java.util.UUID localUuid = null;
    private volatile CompletableFuture<Void> connectFuture = null;
    private volatile Runnable onDisconnectFallback = null;

    private WebSocketClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.requestSigningKey = builder.requestSigningKey;
        this.timeoutMs = builder.timeoutMs;
        this.listener = builder.listener;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl = "https://storage-api.catskin.space";
        private String requestSigningKey = "";
        private int timeoutMs = DEFAULT_TIMEOUT_SECONDS * 1000;
        private Listener listener = new Listener() {};

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder requestSigningKey(String key) {
            this.requestSigningKey = key;
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder listener(Listener listener) {
            this.listener = listener;
            return this;
        }

        public WebSocketClient build() {
            return new WebSocketClient(this);
        }
    }

    /**
     * Connect to the WebSocket server.
     * Returns a future that completes when the connection is established or fails.
     */
    public CompletableFuture<Void> connect() {
        if (stopped) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is stopped"));
        }
        if (connectFuture != null) {
            return connectFuture;
        }

        // Check circuit breaker (shared with ServerApiClient)
        if (ServerApiClient.isCircuitOpen()) {
            long delay = ServerApiClient.getCircuitOpenUntilMs() - System.currentTimeMillis();
            return CompletableFuture.failedFuture(
                new IllegalStateException("Circuit breaker open, retry in " + delay + " ms"));
        }

        connectFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return doConnect();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR).thenCompose(f -> f).whenComplete((v, ex) -> {
            connectFuture = null;
            if (ex != null) {
                handleConnectionFailure(ex);
            }
        });

        return connectFuture;
    }

    private CompletableFuture<Void> doConnect() throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
        httpClientRef.set(client);

        String wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://");
        if (!wsUrl.endsWith("/")) {
            wsUrl += "/";
        }
        wsUrl += "ws";

        URI uri = URI.create(wsUrl);

        AtomicReference<CompletableFuture<Void>> connectionPromiseRef = new AtomicReference<>(new CompletableFuture<>());
        CompletableFuture<Void> connectionPromise = connectionPromiseRef.get();

        WebSocket.Listener wsListener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket ws) {
                ModLog.debug("WebSocket connected to {}", uri);
                reconnectAttempt.set(0);
                ServerApiClient.resetCircuitBreaker();

                // Send welcome/auth handshake
                sendAuth(ws);

                // Auto-subscribe to local UUID if set and authenticated
                if (localUuid != null && sessionToken != null && !sessionToken.isBlank()) {
                    subscribe(java.util.Set.of(localUuid));
                }

                listener.onOpen();
                connectionPromise.complete(null);
            }

            @Override
            public CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
                if (last) {
                    String text = data.toString();
                    ModLog.trace("WS received: {}", text);
                    handleMessage(ws, text);
                }
                ws.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<?> onBinary(WebSocket ws, java.nio.ByteBuffer data, boolean last) {
                ws.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                ModLog.warn("WebSocket error: {}", error.getMessage());
                ServerApiClient.recordFailure();
                listener.onError(error);
            }

            @Override
            public CompletableFuture<?> onClose(WebSocket ws, int statusCode, String reason) {
                ModLog.debug("WebSocket closed: code={}, reason={}", statusCode, reason);
                webSocketRef.set(null);
                listener.onClose(statusCode, reason);

                if (!stopped) {
                    // Trigger fallback to SSE for abnormal closures (not clean 1000)
                    if (statusCode != WebSocket.NORMAL_CLOSURE && onDisconnectFallback != null) {
                        ModLog.warn("WS abnormal closure (code={}), triggering fallback", statusCode);
                        onDisconnectFallback.run();
                    } else {
                        scheduleReconnect();
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        };

        client.newWebSocketBuilder()
            .header(HEADER_PROTOCOL, Integer.toString(CLIENT_PROTOCOL_VERSION))
            .buildAsync(uri, wsListener)
            .thenAccept(ws -> {
                webSocketRef.set(ws);
                ws.request(1);
            })
            .exceptionally(ex -> {
                ModLog.error("WebSocket connection failed", ex);
                connectionPromise.completeExceptionally(ex);
                return null;
            });

        return connectionPromise;
    }

    private void sendAuth(WebSocket ws) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            JsonObject authMsg = new JsonObject();
            authMsg.addProperty("type", "auth");
            authMsg.addProperty("session_token", sessionToken);
            sendMessage(ws, authMsg.toString());
        }
    }

    private void handleMessage(WebSocket ws, String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            String type = json.get("type") != null ? json.get("type").getAsString() : "";

            switch (type) {
                case "welcome" -> {
                    int protocolVersion = json.has("protocol_version") ? json.get("protocol_version").getAsInt() : 0;
                    if (protocolVersion != CLIENT_PROTOCOL_VERSION) {
                        ModLog.error("WS protocol mismatch: server={}, client={}", protocolVersion, CLIENT_PROTOCOL_VERSION);
                        try {
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Protocol version mismatch").join();
                        } catch (Exception ignored) {}
                        return;
                    }
                    ModLog.debug("WS welcome: protocol_version={}", protocolVersion);
                }
                case "skin_update" -> {
                    UpdateEvent event = parseUpdateEvent(json);
                    if (event != null) {
                        listener.onSkinUpdate(event);
                    }
                }
                case "toast" -> {
                    String toastType = json.has("toast_type") ? json.get("toast_type").getAsString() : "info";
                    String uuid = json.has("uuid") && !json.get("uuid").isJsonNull() ? json.get("uuid").getAsString() : null;
                    String title = json.has("title") ? json.get("title").getAsString() : "";
                    listener.onToast(toastType, uuid, title);
                }
                case "ping" -> {
                    // Server heartbeat ping - respond with pong
                    JsonObject pongMsg = new JsonObject();
                    pongMsg.addProperty("type", "pong");
                    sendMessage(ws, pongMsg.toString());
                }
                case "pong" -> {
                    // Heartbeat response from server (if server sends pong)
                }
                case "error" -> {
                    String code = json.has("code") ? json.get("code").getAsString() : "UNKNOWN";
                    String message = json.has("message") ? json.get("message").getAsString() : "";
                    ModLog.warn("WS server error: {} - {}", code, message);
                    listener.onError(new IOException(code + ": " + message));
                }
                default -> ModLog.trace("WS unknown message type: {}", type);
            }
        } catch (Exception e) {
            ModLog.warn("Failed to parse WS message: {}", e.getMessage());
        } finally {
            ws.request(1);
        }
    }

    private UpdateEvent parseUpdateEvent(JsonObject json) {
        try {
            String uuidString = json.has("uuid") && !json.get("uuid").isJsonNull() ? json.get("uuid").getAsString() : null;
            java.util.UUID uuid = null;
            if (uuidString != null) {
                uuid = parseUuidFlexible(uuidString);
            }
            String id = json.has("id") ? json.get("id").getAsString() : "";
            String url = json.has("url") ? json.get("url").getAsString() : "";
            String mouthOpenUrl = json.has("mouth_open_url") ? json.get("mouth_open_url").getAsString() : "";
            String mouthCloseUrl = json.has("mouth_close_url") ? json.get("mouth_close_url").getAsString() : "";
            Boolean slim = json.has("slim") ? json.get("slim").getAsBoolean() : false;
            return new UpdateEvent(uuid, id, url, mouthOpenUrl, mouthCloseUrl, slim);
        } catch (Exception e) {
            ModLog.trace("WS event parse failed: {}", e.getMessage());
            return null;
        }
    }

    private java.util.UUID parseUuidFlexible(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.replace("-", "");
        if (compact.length() == 32) {
            String dashed = compact.substring(0, 8) + "-" +
                    compact.substring(8, 12) + "-" +
                    compact.substring(12, 16) + "-" +
                    compact.substring(16, 20) + "-" +
                    compact.substring(20);
            try {
                return java.util.UUID.fromString(dashed);
            } catch (Exception ignored) {}
        }
        try {
            return java.util.UUID.fromString(value);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Set the session token for authentication.
     * If already connected, sends auth message immediately.
     */
    public void setSessionToken(String token) {
        this.sessionToken = token;
        WebSocket ws = webSocketRef.get();
        if (ws != null) {
            sendAuth(ws);
        }
    }

    /**
     * Set the local player UUID for auto-subscription after connection.
     */
    public void setLocalUuid(java.util.UUID uuid) {
        this.localUuid = uuid;
        WebSocket ws = webSocketRef.get();
        if (ws != null && sessionToken != null && !sessionToken.isBlank()) {
            subscribe(java.util.Set.of(uuid));
        }
    }

    /**
     * Set a callback to run when the connection is lost abnormally.
     * Used for fallback to SSE.
     */
    public void setOnDisconnectFallback(Runnable fallback) {
        this.onDisconnectFallback = fallback;
    }

    /**
     * Subscribe to skin updates for specific UUIDs.
     * Empty list or null means subscribe to all updates.
     */
    public void subscribe(java.util.Collection<java.util.UUID> uuids) {
        WebSocket ws = webSocketRef.get();
        if (ws == null) {
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "subscribe");
        if (uuids != null && !uuids.isEmpty()) {
            JsonObject uuidsArray = new JsonObject();
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (java.util.UUID uuid : uuids) {
                arr.add(uuid.toString());
            }
            msg.add("uuids", arr);
        }
        sendMessage(ws, msg.toString());
    }

    /**
     * Send a ping to keep the connection alive.
     */
    public void ping() {
        WebSocket ws = webSocketRef.get();
        if (ws != null) {
            JsonObject msg = new JsonObject();
            msg.addProperty("type", "ping");
            sendMessage(ws, msg.toString());
        }
    }

    private void sendMessage(WebSocket ws, String message) {
        ws.sendText(message, true).exceptionally(ex -> {
            ModLog.warn("Failed to send WS message: {}", ex.getMessage());
            return null;
        });
    }

    /**
     * Disconnect and stop the client.
     */
    public void disconnect() {
        stopped = true;
        WebSocket ws = webSocketRef.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect").join();
        }
        HttpClient client = httpClientRef.getAndSet(null);
        if (client != null) {
            // HttpClient doesn't need explicit shutdown
        }
    }

    /**
     * Check if the client is currently connected.
     */
    public boolean isConnected() {
        WebSocket ws = webSocketRef.get();
        return ws != null;
    }

    private void handleConnectionFailure(Throwable ex) {
        ServerApiClient.recordFailure();
        if (!stopped) {
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (stopped) return;

        int attempt = reconnectAttempt.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            ModLog.warn("WS max reconnect attempts reached, giving up");
            return;
        }

        long delay = Math.min(RECONNECT_BASE_DELAY_MS * (1L << Math.min(attempt, 5)), RECONNECT_MAX_DELAY_MS);
        ModLog.debug("WS reconnect attempt {} in {} ms", attempt, delay);

        EXECUTOR.schedule(() -> {
            if (!stopped) {
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = HEX_DIGITS[value >>> 4];
            out[i * 2 + 1] = HEX_DIGITS[value & 0x0F];
        }
        return new String(out);
    }

    /**
     * Reset state for testing.
     */
    static void resetForTesting() {
        // No static state to reset
    }
}