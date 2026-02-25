package com.sammy.catskincRemake.client;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ServerApiClient {
    public interface ProgressListener {
        default void onStart(long totalBytes) {
        }

        default void onProgress(long sent, long total) {
        }

        default void onDone(boolean ok, String messageOrSkinId) {
        }
    }

    public record SelectedSkin(String url, boolean slim) {
    }

    public static final class UpdateEvent {
        public final UUID uuid;
        public final String id;
        public final String url;
        public final Boolean slim;

        public UpdateEvent(UUID uuid, String id, String url, Boolean slim) {
            this.uuid = uuid;
            this.id = id;
            this.url = url;
            this.slim = slim;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "CatSkinC-Api");
        thread.setDaemon(true);
        return thread;
    });
    private static final int BODY_PREVIEW_LIMIT = 220;
    private static final ProgressListener NO_OP_PROGRESS = new ProgressListener() {
    };
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final String HEADER_SIGNATURE_TIMESTAMP = "X-CatSkin-Timestamp";
    private static final String HEADER_SIGNATURE_NONCE = "X-CatSkin-Nonce";
    private static final String HEADER_SIGNATURE = "X-CatSkin-Signature";

    private static volatile String baseUrl = "https://storage-api.catskin.space";
    private static volatile String pathUpload = "/upload";
    private static volatile String pathSelect = "/select";
    private static volatile String pathSelected = "/selected";
    private static volatile String pathPublic = "/public/";
    private static volatile String pathEvents = "/events";
    private static volatile int timeoutMs = 15_000;
    private static volatile long selectedCacheTtlMs = 1_500L;
    private static volatile long pingCacheTtlMs = 10_000L;

    private static volatile String authToken;
    private static volatile String requestSigningKey;
    private static volatile String tlsPinSha256;

    private static volatile Thread sseThread;
    private static volatile boolean sseStop;
    private static final ConcurrentHashMap<UUID, CachedSelected> SELECTED_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CompletableFuture<SelectedSkin>> SELECTED_IN_FLIGHT = new ConcurrentHashMap<>();
    private static volatile CachedPing cachedPing;

    private ServerApiClient() {
    }

    public static void reloadFromConfig() {
        ClientConfig config = ConfigManager.get();
        config.sanitize();
        baseUrl = config.apiBaseUrl;
        pathUpload = config.pathUpload;
        pathSelect = config.pathSelect;
        pathSelected = config.pathSelected;
        pathPublic = config.pathPublic.endsWith("/") ? config.pathPublic : (config.pathPublic + "/");
        pathEvents = config.pathEvents;
        timeoutMs = config.timeoutMs;
        selectedCacheTtlMs = config.selectedCacheTtlMs;
        pingCacheTtlMs = config.pingCacheTtlMs;
        requestSigningKey = resolveSecret(config.requestSigningKey, "CATSKINC_REQUEST_SIGNING_KEY", "catskinc.requestSigningKey");
        tlsPinSha256 = resolveTlsPin(config.tlsPinSha256);
        ModLog.debug("API config applied: baseUrl={}, upload={}, select={}, selected={}, public={}, events={}, timeoutMs={}",
                baseUrl, pathUpload, pathSelect, pathSelected, pathPublic, pathEvents, timeoutMs);
        SELECTED_CACHE.clear();
        SELECTED_IN_FLIGHT.clear();
        cachedPing = null;
        ModLog.debug("API security config: requestSigning={}, tlsPin={}",
                requestSigningKey != null && !requestSigningKey.isBlank(),
                tlsPinSha256 != null && !tlsPinSha256.isBlank());
    }

    public static void setAuthToken(String token) {
        authToken = token;
        cachedPing = null;
        if (token == null || token.isBlank()) {
            ModLog.debug("API auth token cleared");
        } else {
            ModLog.debug("API auth token updated ({} chars)", token.length());
        }
    }

    public static void uploadSkinAsync(File file, UUID playerUuid, boolean slim, ProgressListener callback) {
        ProgressListener listener = callback == null ? NO_OP_PROGRESS : callback;
        CompletableFuture.runAsync(() -> {
            if (file == null || !file.isFile()) {
                ModLog.warn("Upload aborted: invalid file={}", file);
                listener.onDone(false, "Invalid file");
                return;
            }
            ModLog.debug("Upload start: file='{}', size={} bytes, uuid={}, slim={}",
                    safeFileName(file), file.length(), playerUuid, slim);
            try {
                String boundary = "----CatSkinC-" + System.nanoTime();
                HttpURLConnection connection = open("POST", pathUpload, "multipart/form-data; boundary=" + boundary);

                long totalBytes = file.length();
                listener.onStart(totalBytes);

                try (OutputStream baseOut = connection.getOutputStream();
                     CountingOutputStream out = new CountingOutputStream(baseOut, totalBytes, listener);
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true)) {

                    writer.append("--").append(boundary).append("\r\n");
                    if (playerUuid != null) {
                        writer.append("Content-Disposition: form-data; name=\"uuid\"").append("\r\n\r\n");
                        writer.append(playerUuid.toString()).append("\r\n");
                    }

                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"slim\"").append("\r\n\r\n");
                    writer.append(Boolean.toString(slim)).append("\r\n");

                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"").append("\r\n");
                    writer.append("Content-Type: image/png").append("\r\n\r\n");
                    writer.flush();

                    try (InputStream in = new FileInputStream(file)) {
                        byte[] buffer = new byte[8_192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }

                    out.flush();
                    writer.append("\r\n--").append(boundary).append("--").append("\r\n");
                    writer.flush();
                }

                int code = responseCode(connection);
                String body = readBody(connection);
                ModLog.trace("Upload response: code={}, body={}", code, bodyPreview(body));
                if (code / 100 != 2) {
                    listener.onDone(false, body == null || body.isBlank() ? ("HTTP " + code) : body);
                    ModLog.warn("Upload failed: code={}, message={}", code, bodyPreview(body));
                    return;
                }

                String id = jsonString(body, "id");
                if (id == null || id.isBlank()) {
                    String url = jsonString(body, "url");
                    id = (url != null && !url.isBlank()) ? url : (body == null ? "ok" : body.trim());
                }
                invalidateSelectedCache(playerUuid);
                listener.onDone(true, id);
                ModLog.info("Upload success: uuid={}, slim={}, result={}", playerUuid, slim, id);
            } catch (Exception exception) {
                ModLog.error("Upload failed for file '" + safeFileName(file) + "'", exception);
                listener.onDone(false, messageOrDefault(exception));
            }
        }, EXECUTOR);
    }

    public static void selectSkin(UUID playerUuid, String skinIdOrUrl) {
        if (playerUuid == null || skinIdOrUrl == null || skinIdOrUrl.isBlank()) {
            ModLog.trace("Select skin skipped: uuid or skin value missing");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                ModLog.debug("Selecting skin: uuid={}, value={}", playerUuid, skinIdOrUrl);
                HttpURLConnection connection = open("POST", pathSelect, "application/json; charset=utf-8");
                String body = "{\"uuid\":\"" + playerUuid + "\",\"skin\":\"" + escapeJson(skinIdOrUrl) + "\"}";
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = responseCode(connection);
                String responseBody = readBody(connection);
                if (code / 100 != 2) {
                    ModLog.warn("Select skin failed: code={}, body={}", code, bodyPreview(responseBody));
                } else {
                    invalidateSelectedCache(playerUuid);
                    ModLog.trace("Select skin ok: code={}, body={}", code, bodyPreview(responseBody));
                }
            } catch (Exception exception) {
                ModLog.error("Select skin request failed for uuid=" + playerUuid, exception);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<SelectedSkin> fetchSelectedAsync(UUID playerUuid) {
        if (playerUuid == null) {
            ModLog.trace("Fetch selected skipped: uuid is null");
            return CompletableFuture.completedFuture(null);
        }

        long now = System.currentTimeMillis();
        CachedSelected cached = SELECTED_CACHE.get(playerUuid);
        if (cached != null && (now - cached.cachedAtMs) <= selectedCacheTtlMs) {
            ModLog.trace("Fetch selected cache hit: {}", playerUuid);
            return CompletableFuture.completedFuture(cached.value);
        }

        CompletableFuture<SelectedSkin> inFlight = SELECTED_IN_FLIGHT.get(playerUuid);
        if (inFlight != null) {
            ModLog.trace("Fetch selected in-flight reuse: {}", playerUuid);
            return inFlight;
        }

        CompletableFuture<SelectedSkin> created = CompletableFuture.supplyAsync(() -> {
            try {
                String requestPath = pathSelected + (pathSelected.contains("?") ? "&uuid=" : "?uuid=") + playerUuid;
                ModLog.trace("Fetch selected request: {}", requestPath);
                HttpURLConnection connection = open("GET", requestPath, null);
                int code = responseCode(connection);
                String body = readBody(connection);
                if (code / 100 != 2) {
                    ModLog.warn("Fetch selected failed: uuid={}, code={}, body={}", playerUuid, code, bodyPreview(body));
                    return null;
                }

                String url = jsonString(body, "url");
                if (url == null || url.isBlank()) {
                    String id = jsonString(body, "id");
                    if (id != null && !id.isBlank()) {
                        url = endpointPublicPng(id);
                    }
                }
                if (url == null || url.isBlank()) {
                    ModLog.trace("Fetch selected returned no URL for uuid={}", playerUuid);
                    return null;
                }
                boolean slim = jsonBoolean(body, "slim", false);
                SelectedSkin selectedSkin = new SelectedSkin(url, slim);
                SELECTED_CACHE.put(playerUuid, new CachedSelected(selectedSkin, System.currentTimeMillis()));
                ModLog.trace("Fetch selected ok: uuid={}, slim={}, url={}", playerUuid, slim, url);
                return selectedSkin;
            } catch (Exception exception) {
                ModLog.error("Fetch selected failed for uuid=" + playerUuid, exception);
                return null;
            }
        }, EXECUTOR).whenComplete((ignored, throwable) -> SELECTED_IN_FLIGHT.remove(playerUuid));

        CompletableFuture<SelectedSkin> existing = SELECTED_IN_FLIGHT.putIfAbsent(playerUuid, created);
        return existing != null ? existing : created;
    }

    public static CompletableFuture<NativeImageBackedTexture> downloadTextureAsync(String urlOrPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModLog.trace("Downloading texture from {}", urlOrPath);
                HttpURLConnection connection = open("GET", urlOrPath, null);
                int code = responseCode(connection);
                if (code / 100 != 2) {
                    ModLog.warn("Texture download failed: code={}, url={}", code, urlOrPath);
                    return null;
                }
                byte[] bodyBytes;
                try (InputStream in = connection.getInputStream()) {
                    bodyBytes = readAllBytes(in);
                }
                String expectedHash = connection.getHeaderField("X-CatSkin-Sha256");
                if (expectedHash != null && !expectedHash.isBlank()) {
                    String actualHash = sha256Hex(bodyBytes);
                    if (!expectedHash.trim().equalsIgnoreCase(actualHash)) {
                        ModLog.warn("Texture hash mismatch for {} (expected={}, actual={})",
                                urlOrPath, expectedHash.trim(), actualHash);
                        return null;
                    }
                }
                try (ByteArrayInputStream imageInput = new ByteArrayInputStream(bodyBytes)) {
                    NativeImage image = NativeImage.read(imageInput);
                    ModLog.trace("Texture downloaded: {}x{} from {}", image.getWidth(), image.getHeight(), urlOrPath);
                    return new NativeImageBackedTexture(image);
                }
            } catch (Exception exception) {
                ModLog.error("Texture download failed: " + urlOrPath, exception);
                return null;
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<Boolean> pingAsyncOk() {
        CachedPing ping = cachedPing;
        long now = System.currentTimeMillis();
        if (ping != null && (now - ping.cachedAtMs) <= pingCacheTtlMs) {
            return CompletableFuture.completedFuture(ping.ok);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ModLog.trace("Cloud ping start");
                HttpURLConnection connection = open("GET", "/", null);
                boolean ok = responseCode(connection) / 100 == 2;
                cachedPing = new CachedPing(ok, System.currentTimeMillis());
                ModLog.debug("Cloud ping result={}", ok);
                return ok;
            } catch (Exception exception) {
                ModLog.warn("Cloud ping failed: {}", exception.getMessage());
                return false;
            }
        }, EXECUTOR);
    }

    public static String endpointPublicPng(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String fileName = id.endsWith(".png") ? id : (id + ".png");
        return join(baseUrl, pathPublic + fileName);
    }

    public static void startSse(Consumer<UpdateEvent> consumer) {
        if (sseThread != null) {
            ModLog.trace("SSE start skipped: already running");
            return;
        }
        sseStop = false;
        sseThread = new Thread(() -> {
            int attempt = 0;
            while (!sseStop) {
                attempt++;
                HttpURLConnection connection = null;
                try {
                    ModLog.debug("SSE connect attempt {} -> {}", attempt, pathEvents);
                    connection = openSse(pathEvents);
                    int code = responseCode(connection);
                    if (code / 100 != 2) {
                        String body = readBody(connection);
                        ModLog.warn("SSE connect failed: code={}, body={}", code, bodyPreview(body));
                        sleepQuietly(1_500L);
                        continue;
                    }
                    ModLog.info("SSE connected");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (!sseStop && (line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                ModLog.trace("SSE ignore line={}", bodyPreview(line));
                                continue;
                            }
                            String payload = line.substring(5).trim();
                            UpdateEvent event = parseUpdateEvent(payload);
                            if (event != null && event.uuid != null && consumer != null) {
                                ModLog.trace("SSE event: uuid={}, id={}, slim={}, url={}",
                                        event.uuid, event.id, event.slim, event.url);
                                consumer.accept(event);
                            } else {
                                ModLog.trace("SSE event skipped: {}", bodyPreview(payload));
                            }
                        }
                    }
                    if (!sseStop) {
                        ModLog.warn("SSE stream closed, reconnecting");
                    }
                } catch (Exception exception) {
                    if (!sseStop) {
                        ModLog.warn("SSE error on attempt {}: {}", attempt, exception.getMessage());
                        ModLog.trace("SSE exception details", exception);
                        sleepQuietly(1_500L);
                    }
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
            ModLog.info("SSE thread stopped");
        }, "CatSkinC-SSE");
        sseThread.setDaemon(true);
        sseThread.start();
        ModLog.debug("SSE thread started");
    }

    public static void stopSse() {
        sseStop = true;
        Thread thread = sseThread;
        sseThread = null;
        ModLog.debug("Stopping SSE thread");
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void invalidateSelectedCache(UUID uuid) {
        if (uuid == null) {
            return;
        }
        SELECTED_CACHE.remove(uuid);
        SELECTED_IN_FLIGHT.remove(uuid);
    }

    private static UpdateEvent parseUpdateEvent(String json) {
        try {
            String uuidString = jsonString(json, "uuid");
            UUID uuid = parseUuidFlexible(uuidString);
            String id = jsonString(json, "id");
            String url = jsonString(json, "url");
            Boolean slim = json.contains("\"slim\"") ? jsonBoolean(json, "slim", false) : null;
            return new UpdateEvent(uuid, id, url, slim);
        } catch (Exception exception) {
            ModLog.trace("SSE payload parse failed: {}", bodyPreview(json));
            ModLog.trace("SSE parse exception", exception);
            return null;
        }
    }

    private static UUID parseUuidFlexible(String value) {
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
                return UUID.fromString(dashed);
            } catch (Exception exception) {
                ModLog.trace("UUID parse failed for compact value '{}': {}", value, exception.getMessage());
            }
        }
        try {
            return UUID.fromString(value);
        } catch (Exception exception) {
            ModLog.trace("UUID parse failed for value '{}': {}", value, exception.getMessage());
            return null;
        }
    }

    private static HttpURLConnection open(String method, String pathOrUrl, String contentType) throws IOException {
        String requestUrl = isHttp(pathOrUrl) ? pathOrUrl : join(baseUrl, pathOrUrl);
        ModLog.trace("HTTP {} {}", method, requestUrl);
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "catskinc-remake/ServerApiClient");
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        if (authToken != null && !authToken.isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        applyRequestSignature(connection, method);
        if ("POST".equals(method) || "PUT".equals(method)) {
            connection.setDoOutput(true);
        }
        return connection;
    }

    private static HttpURLConnection openSse(String pathOrUrl) throws IOException {
        HttpURLConnection connection = open("GET", pathOrUrl, null);
        connection.setReadTimeout(0);
        return connection;
    }

    private static String join(String base, String path) {
        String left = trimSlash(base);
        String right = (path == null || path.isBlank()) ? "/" : path;
        if (right.startsWith("http://") || right.startsWith("https://")) {
            return right;
        }
        if (!right.startsWith("/")) {
            right = "/" + right;
        }
        return left + right;
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isHttp(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private static String resolveSecret(String preferred, String envKey, String propertyKey) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String property = System.getProperty(propertyKey);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        return null;
    }

    private static String resolveTlsPin(String preferred) {
        String candidate = preferred;
        if (candidate == null || candidate.isBlank()) {
            candidate = System.getenv("CATSKINC_TLS_PIN_SHA256");
        }
        if ((candidate == null || candidate.isBlank())) {
            candidate = System.getProperty("catskinc.tlsPinSha256");
        }
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.regionMatches(true, 0, "sha256/", 0, 7)) {
            trimmed = trimmed.substring(7);
        }
        trimmed = trimmed.replace(":", "").trim().toLowerCase();
        if (trimmed.length() != 64) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return null;
            }
        }
        return trimmed;
    }

    private static int responseCode(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        verifyTlsPin(connection);
        return code;
    }

    private static void applyRequestSignature(HttpURLConnection connection, String method) throws IOException {
        String signingKey = requestSigningKey;
        if (signingKey == null || signingKey.isBlank()) {
            return;
        }
        URL requestUrl = connection.getURL();
        if (!isBaseServerUrl(requestUrl)) {
            return;
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        String nonce = randomNonce();
        String target = canonicalTarget(requestUrl);
        String payload = method + "\n" + target + "\n" + timestamp + "\n" + nonce;
        String signature = hmacSha256Hex(signingKey, payload);

        connection.setRequestProperty(HEADER_SIGNATURE_TIMESTAMP, Long.toString(timestamp));
        connection.setRequestProperty(HEADER_SIGNATURE_NONCE, nonce);
        connection.setRequestProperty(HEADER_SIGNATURE, signature);
        ModLog.trace("Signed request {} {}", method, target);
    }

    private static void verifyTlsPin(HttpURLConnection connection) throws IOException {
        String pin = tlsPinSha256;
        if (pin == null || pin.isBlank()) {
            return;
        }
        if (!(connection instanceof HttpsURLConnection httpsConnection)) {
            throw new IOException("TLS pin is configured but connection is not HTTPS");
        }
        try {
            Certificate[] certificates = httpsConnection.getServerCertificates();
            if (certificates == null || certificates.length == 0) {
                throw new SSLPeerUnverifiedException("No server certificates");
            }
            if (!(certificates[0] instanceof X509Certificate cert)) {
                throw new SSLPeerUnverifiedException("Unsupported certificate type");
            }
            String actualPin = sha256Hex(cert.getPublicKey().getEncoded());
            if (!pin.equalsIgnoreCase(actualPin)) {
                throw new SSLPeerUnverifiedException("TLS pin mismatch");
            }
        } catch (SSLPeerUnverifiedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("TLS pin verification failed", exception);
        }
    }

    private static boolean isBaseServerUrl(URL url) {
        if (url == null) {
            return false;
        }
        try {
            URI base = URI.create(baseUrl);
            if (base.getHost() == null || url.getHost() == null) {
                return false;
            }
            if (!base.getHost().equalsIgnoreCase(url.getHost())) {
                return false;
            }

            int basePort = normalizePort(base.getScheme(), base.getPort());
            int urlPort = normalizePort(url.getProtocol(), url.getPort());
            return basePort == urlPort;
        } catch (Exception exception) {
            return false;
        }
    }

    private static int normalizePort(String scheme, int explicitPort) {
        if (explicitPort > 0) {
            return explicitPort;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    private static String canonicalTarget(URL url) {
        if (url == null) {
            return "/";
        }
        String path = url.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String query = url.getQuery();
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + query;
    }

    private static String randomNonce() {
        byte[] nonce = new byte[18];
        SECURE_RANDOM.nextBytes(nonce);
        return toHex(nonce);
    }

    private static String hmacSha256Hex(String key, String payload) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IOException("Failed to sign request", exception);
        }
    }

    private static String readBody(HttpURLConnection connection) {
        try (InputStream in = responseCode(connection) / 100 == 2
                ? connection.getInputStream()
                : connection.getErrorStream()) {
            if (in == null) {
                return null;
            }
            return new String(readAllBytes(in), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            ModLog.trace("Failed reading HTTP body: {}", exception.getMessage());
            return null;
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256Hex(byte[] value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value));
        } catch (Exception exception) {
            throw new IOException("SHA-256 unavailable", exception);
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

    private static String bodyPreview(String body) {
        if (body == null) {
            return "<null>";
        }
        String cleaned = body.replace('\r', ' ').replace('\n', ' ').trim();
        if (cleaned.isEmpty()) {
            return "<empty>";
        }
        if (cleaned.length() <= BODY_PREVIEW_LIMIT) {
            return cleaned;
        }
        return cleaned.substring(0, BODY_PREVIEW_LIMIT) + "...(" + cleaned.length() + " chars)";
    }

    private static String jsonString(String body, String key) {
        if (body == null) {
            return null;
        }
        String pattern = "\"" + key + "\"";
        int start = body.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        start = body.indexOf(':', start);
        if (start < 0) {
            return null;
        }
        int q1 = body.indexOf('"', start + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return body.substring(q1 + 1, q2);
    }

    private static boolean jsonBoolean(String body, String key, boolean defaultValue) {
        if (body == null) {
            return defaultValue;
        }
        String pattern = "\"" + key + "\"";
        int start = body.indexOf(pattern);
        if (start < 0) {
            return defaultValue;
        }
        start = body.indexOf(':', start);
        if (start < 0) {
            return defaultValue;
        }
        int i = start + 1;
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
            i++;
        }
        if (i >= body.length()) {
            return defaultValue;
        }
        if (body.regionMatches(true, i, "true", 0, 4) || body.charAt(i) == '1') {
            return true;
        }
        if (body.regionMatches(true, i, "false", 0, 5) || body.charAt(i) == '0') {
            return false;
        }
        return defaultValue;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String messageOrDefault(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return throwable.getClass().getSimpleName();
    }

    private static String safeFileName(File file) {
        return file == null ? "<null>" : file.getName();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long total;
        private final ProgressListener callback;
        private long sent;

        private CountingOutputStream(OutputStream delegate, long total, ProgressListener callback) {
            this.delegate = delegate;
            this.total = total;
            this.callback = callback;
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            sent++;
            callback.onProgress(sent, total);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            sent += len;
            callback.onProgress(sent, total);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private record CachedSelected(SelectedSkin value, long cachedAtMs) {
    }

    private record CachedPing(boolean ok, long cachedAtMs) {
    }
}

