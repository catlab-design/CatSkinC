package com.sammy.catskincRemake.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

public final class ServerApiClient {
    public interface ProgressListener {
        default void onStart(long totalBytes) {
        }

        default void onProgress(long sent, long total) {
        }

        default void onDone(boolean ok, String messageOrSkinId) {
        }
    }

    public record SelectedSkin(String url, String mouthOpenUrl, String mouthCloseUrl, boolean slim) {
        public String mouthUrl() {
            return mouthOpenUrl;
        }
    }

    public record ClearResult(boolean ok, boolean changed, String mode, String message) {
    }

    public static final class UpdateEvent {
        public final UUID uuid;
        public final String id;
        public final String url;
        public final String mouthUrl;
        public final String mouthOpenUrl;
        public final String mouthCloseUrl;
        public final Boolean slim;

        public UpdateEvent(UUID uuid, String id, String url, String mouthOpenUrl, String mouthCloseUrl, Boolean slim) {
            this.uuid = uuid;
            this.id = id;
            this.url = url;
            this.mouthOpenUrl = mouthOpenUrl;
            this.mouthCloseUrl = mouthCloseUrl;
            this.mouthUrl = mouthOpenUrl;
            this.slim = slim;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "CatSkinC-Api");
        thread.setDaemon(true);
        return thread;
    });
    private static final int BODY_PREVIEW_LIMIT = 220;
    private static final ProgressListener NO_OP_PROGRESS = new ProgressListener() {
    };
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final String DEFAULT_BASE_URL = "https://storage-api.catskin.space";
    private static final String DEFAULT_PATH_UPLOAD = "/upload";
    private static final String DEFAULT_PATH_SELECT = "/select";
    private static final String DEFAULT_PATH_SELECTED = "/selected";
    private static final String DEFAULT_PATH_PUBLIC = "/public/";
    private static final String DEFAULT_PATH_EVENTS = "/events";
    private static final int DEFAULT_TIMEOUT_MS = 15_000;
    private static final long DEFAULT_SELECTED_CACHE_TTL_MS = 1_500L;
    private static final long DEFAULT_PING_CACHE_TTL_MS = 10_000L;
    private static final int DEFAULT_MAX_JSON_BYTES = 256 * 1024;
    private static final int DEFAULT_MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private static final String HEADER_REQUEST_ID = "x-catskinc-request-id";
    private static final String HEADER_CONTENT_SHA256 = "x-catskinc-content-sha256";
    private static final String HEADER_TIMESTAMP = "x-catskinc-timestamp";
    private static final String HEADER_NONCE = "x-catskinc-nonce";
    private static final String HEADER_SIGNATURE = "x-catskinc-signature";
    private static final String SHA256_EMPTY_HEX = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static volatile String authToken;

    private static volatile Thread sseThread;
    private static volatile boolean sseStop;
    private static final ConcurrentHashMap<UUID, CachedSelected> SELECTED_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CompletableFuture<SelectedSkin>> SELECTED_IN_FLIGHT = new ConcurrentHashMap<>();
    private static volatile CachedPing cachedPing;

    // Circuit breaker: stops polling when server is unreachable
    private static volatile int consecutiveFailures;
    private static volatile long circuitOpenUntilMs;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 30_000L;

    private record RuntimeConfig(
            String baseUrl,
            String pathUpload,
            String pathSelect,
            String pathSelected,
            String pathPublic,
            String pathEvents,
            int timeoutMs,
            long selectedCacheTtlMs,
            long pingCacheTtlMs,
            boolean allowInsecureHttp,
            String requestSigningKey,
            String tlsPinSha256,
            String apiHostPort,
            Set<String> trustedAssetHostPorts,
            int maxJsonBytes,
            int maxImageBytes) {
    }

    private ServerApiClient() {
    }

    private static RuntimeConfig runtimeConfig() {
        ClientConfig config = ConfigManager.get();
        config.sanitize();
        String baseUrl = normalizeBaseUrl(config.apiBaseUrl);
        URL parsedBase;
        try {
            parsedBase = parseUrl(baseUrl);
        } catch (IOException exception) {
            baseUrl = DEFAULT_BASE_URL;
            try {
                parsedBase = parseUrl(baseUrl);
            } catch (IOException ignored) {
                throw new IllegalStateException("Unable to parse default API base URL");
            }
        }
        String apiHostPort = hostPortKey(parsedBase);

        Set<String> trustedAssetHostPorts = new HashSet<>();
        trustedAssetHostPorts.add(apiHostPort);
        for (String part : config.allowedAssetHosts.split("[,;]")) {
            String cleaned = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty()) {
                if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                    try {
                        URL url = parseUrl(cleaned);
                        trustedAssetHostPorts.add(hostPortKey(url));
                        trustedAssetHostPorts.add(url.getHost().toLowerCase(Locale.ROOT));
                    } catch (IOException ignored) {
                        trustedAssetHostPorts.add(cleaned);
                    }
                } else {
                    trustedAssetHostPorts.add(cleaned);
                }
            }
        }

        String requestSigningKey = firstNonBlank(
                System.getProperty("catskinc.requestSigningKey"),
                System.getenv("CATSKINC_REQUEST_SIGNING_KEY"),
                config.requestSigningKey);
        String tlsPinSha256 = firstNonBlank(
                System.getProperty("catskinc.tlsPinSha256"),
                System.getenv("CATSKINC_TLS_PIN_SHA256"),
                config.tlsPinSha256).toLowerCase(Locale.ROOT);

        return new RuntimeConfig(
                baseUrl,
                normalizePath(config.pathUpload, DEFAULT_PATH_UPLOAD),
                normalizePath(config.pathSelect, DEFAULT_PATH_SELECT),
                normalizePath(config.pathSelected, DEFAULT_PATH_SELECTED),
                ensureTrailingSlash(normalizePath(config.pathPublic, DEFAULT_PATH_PUBLIC)),
                normalizePath(config.pathEvents, DEFAULT_PATH_EVENTS),
                clampInt(config.timeoutMs, 1_000, 120_000, DEFAULT_TIMEOUT_MS),
                clampLong(config.selectedCacheTtlMs, 250L, 60_000L, DEFAULT_SELECTED_CACHE_TTL_MS),
                clampLong(config.pingCacheTtlMs, 1_000L, 120_000L, DEFAULT_PING_CACHE_TTL_MS),
                config.allowInsecureHttp,
                requestSigningKey,
                normalizePin(tlsPinSha256),
                apiHostPort,
                trustedAssetHostPorts,
                clampInt(config.maxJsonBytes, 4 * 1024, 4 * 1024 * 1024, DEFAULT_MAX_JSON_BYTES),
                clampInt(config.maxImageBytes, 64 * 1024, 32 * 1024 * 1024, DEFAULT_MAX_IMAGE_BYTES));
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
        uploadSkinAsync(file, null, null, playerUuid, slim, callback);
    }

    public static void uploadSkinAsync(File file, File mouthFile, UUID playerUuid, boolean slim,
            ProgressListener callback) {
        uploadSkinAsync(file, mouthFile, null, playerUuid, slim, callback);
    }

    public static void uploadSkinAsync(File file, File mouthOpenFile, File mouthCloseFile, UUID playerUuid,
            boolean slim, ProgressListener callback) {
        ProgressListener listener = callback == null ? NO_OP_PROGRESS : callback;
        CompletableFuture.runAsync(() -> {
            RuntimeConfig cfg = runtimeConfig();
            if (file == null || !file.isFile()) {
                ModLog.warn("Upload aborted: invalid file={}", file);
                listener.onDone(false, "Invalid file");
                return;
            }
            if (mouthOpenFile != null && !mouthOpenFile.isFile()) {
                ModLog.warn("Upload aborted: invalid mouth_open file={}", mouthOpenFile);
                listener.onDone(false, "Invalid mouth_open file");
                return;
            }
            if (mouthCloseFile != null && !mouthCloseFile.isFile()) {
                ModLog.warn("Upload aborted: invalid mouth_close file={}", mouthCloseFile);
                listener.onDone(false, "Invalid mouth_close file");
                return;
            }
            ModLog.debug("Upload start: file='{}', mouthOpen='{}', mouthClose='{}', size={} bytes, uuid={}, slim={}",
                    safeFileName(file), safeFileName(mouthOpenFile), safeFileName(mouthCloseFile),
                    file.length(), playerUuid, slim);
            HttpURLConnection connection = null;
            try {
                byte[] skinBytes = readFileBytes(file, cfg.maxImageBytes, "skin");
                byte[] mouthOpenBytes = mouthOpenFile == null ? null
                        : readFileBytes(mouthOpenFile, cfg.maxImageBytes, "mouth_open");
                byte[] mouthCloseBytes = mouthCloseFile == null ? null
                        : readFileBytes(mouthCloseFile, cfg.maxImageBytes, "mouth_close");

                String boundary = "----CatSkinC-" + System.nanoTime();
                String requestId = newRequestId();
                String bodyHash = computeUploadContentHash(
                        playerUuid,
                        slim,
                        skinBytes,
                        mouthOpenBytes,
                        mouthCloseBytes);
                connection = open(
                        "POST",
                        cfg.pathUpload,
                        "multipart/form-data; boundary=" + boundary,
                        bodyHash,
                        requestId,
                        true);

                boolean includeLegacyMouth = mouthOpenBytes != null;
                long multipartOverhead = estimateMultipartOverhead(
                        boundary, playerUuid, slim, mouthOpenBytes != null, mouthCloseBytes != null,
                        includeLegacyMouth);
                long totalBytes = skinBytes.length
                        + (mouthOpenBytes == null ? 0L : mouthOpenBytes.length)
                        + (mouthCloseBytes == null ? 0L : mouthCloseBytes.length)
                        + multipartOverhead;
                listener.onStart(totalBytes);

                try (OutputStream baseOut = connection.getOutputStream();
                        CountingOutputStream out = new CountingOutputStream(baseOut, totalBytes, listener);
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                                true)) {

                    writer.append("--").append(boundary).append("\r\n");
                    if (playerUuid != null) {
                        writer.append("Content-Disposition: form-data; name=\"uuid\"").append("\r\n\r\n");
                        writer.append(playerUuid.toString()).append("\r\n");
                    }

                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"slim\"").append("\r\n\r\n");
                    writer.append(Boolean.toString(slim)).append("\r\n");

                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"")
                            .append("\r\n");
                    writer.append("Content-Type: image/png").append("\r\n\r\n");
                    writer.flush();

                    out.write(skinBytes);

                    if (mouthOpenBytes != null) {
                        writer.append("\r\n--").append(boundary).append("\r\n");
                        writer.append(
                                "Content-Disposition: form-data; name=\"mouth_open\"; filename=\"mouth-open.png\"")
                                .append("\r\n");
                        writer.append("Content-Type: image/png").append("\r\n\r\n");
                        writer.flush();
                        out.write(mouthOpenBytes);

                        // Legacy compatibility: older server builds expect "mouth" only.
                        writer.append("\r\n--").append(boundary).append("\r\n");
                        writer.append(
                                "Content-Disposition: form-data; name=\"mouth\"; filename=\"mouth-open.png\"")
                                .append("\r\n");
                        writer.append("Content-Type: image/png").append("\r\n\r\n");
                        writer.flush();
                        out.write(mouthOpenBytes);
                    }
                    if (mouthCloseBytes != null) {
                        writer.append("\r\n--").append(boundary).append("\r\n");
                        writer.append(
                                "Content-Disposition: form-data; name=\"mouth_close\"; filename=\"mouth-close.png\"")
                                .append("\r\n");
                        writer.append("Content-Type: image/png").append("\r\n\r\n");
                        writer.flush();
                        out.write(mouthCloseBytes);
                    }

                    out.flush();
                    writer.append("\r\n--").append(boundary).append("--").append("\r\n");
                    writer.flush();
                }

                int code = responseCode(connection, requestId);
                String body = readBody(connection, code, cfg.maxJsonBytes);
                ModLog.trace("Upload response: requestId={}, code={}, body={}", requestId, code, bodyPreview(body));
                if (code / 100 != 2) {
                    listener.onDone(false, httpErrorMessage(body, code));
                    ModLog.warn("Upload failed: requestId={}, code={}, message={}", requestId, code, bodyPreview(body));
                    return;
                }

                String id = jsonString(body, "id");
                if (id == null || id.isBlank()) {
                    String url = jsonString(body, "url");
                    id = (url != null && !url.isBlank()) ? url : (body == null ? "ok" : body.trim());
                }
                invalidateSelectedCache(playerUuid);
                listener.onDone(true, id);
                ModLog.info("event=upload.persisted request_id={} uuid={} slim={} result={}",
                        requestId, playerUuid, slim, id);
            } catch (Exception exception) {
                ModLog.error("Upload failed for file '" + safeFileName(file) + "'", exception);
                listener.onDone(false, messageOrDefault(exception));
            } finally {
                disconnectQuietly(connection);
            }
        }, EXECUTOR);
    }

    public static void selectSkin(UUID playerUuid, String skinIdOrUrl) {
        selectSkin(playerUuid, skinIdOrUrl, null);
    }

    public static void selectSkin(UUID playerUuid, String skinIdOrUrl, Boolean slim) {
        if (playerUuid == null || skinIdOrUrl == null || skinIdOrUrl.isBlank()) {
            ModLog.trace("Select skin skipped: uuid or skin value missing");
            return;
        }
        CompletableFuture.runAsync(() -> {
            HttpURLConnection connection = null;
            try {
                RuntimeConfig cfg = runtimeConfig();
                String requestId = newRequestId();
                ModLog.debug("Selecting skin: uuid={}, value={}, slim={}", playerUuid, skinIdOrUrl, slim);
                StringBuilder bodyBuilder = new StringBuilder(128)
                        .append("{\"uuid\":\"")
                        .append(playerUuid)
                        .append("\",\"skin\":\"")
                        .append(escapeJson(skinIdOrUrl))
                        .append("\"");
                if (slim != null) {
                    bodyBuilder.append(",\"slim\":").append(slim.booleanValue());
                }
                bodyBuilder.append('}');
                String body = bodyBuilder.toString();
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                connection = open(
                        "POST",
                        cfg.pathSelect,
                        "application/json; charset=utf-8",
                        sha256Hex(bodyBytes),
                        requestId,
                        true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bodyBytes);
                }
                int code = responseCode(connection, requestId);
                String responseBody = readBody(connection, code, cfg.maxJsonBytes);
                if (code / 100 != 2) {
                    ModLog.warn("Select skin failed: requestId={}, code={}, body={}", requestId, code,
                            bodyPreview(responseBody));
                } else {
                    invalidateSelectedCache(playerUuid);
                    ModLog.trace("Select skin ok: requestId={}, code={}, body={}", requestId, code,
                            bodyPreview(responseBody));
                }
            } catch (Exception exception) {
                ModLog.error("Select skin request failed for uuid=" + playerUuid, exception);
            } finally {
                disconnectQuietly(connection);
            }
        }, EXECUTOR);
    }

    public static void clearSelectionAsync(UUID playerUuid, String clearMode, Consumer<ClearResult> callback) {
        String mode = normalizeClearMode(clearMode);
        if (playerUuid == null || mode == null) {
            publishClearResult(callback,
                    new ClearResult(false, false, mode == null ? "" : mode, "Invalid clear request"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            publishClearResult(callback, sendClearSelection(playerUuid, mode));
        }, EXECUTOR);
    }

    private static ClearResult sendClearSelection(UUID playerUuid, String mode) {
        HttpURLConnection connection = null;
        try {
            RuntimeConfig cfg = runtimeConfig();
            String requestId = newRequestId();
            String body = "{\"uuid\":\"" + playerUuid + "\",\"clear\":\"" + mode + "\"}";
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            connection = open(
                    "POST",
                    cfg.pathSelect,
                    "application/json; charset=utf-8",
                    sha256Hex(bodyBytes),
                    requestId,
                    true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bodyBytes);
            }

            int code = responseCode(connection, requestId);
            String responseBody = readBody(connection, code, cfg.maxJsonBytes);
            if (code / 100 != 2) {
                String message = httpErrorMessage(responseBody, code);
                ModLog.warn("Clear selection failed: uuid={}, mode={}, code={}, body={}",
                        playerUuid, mode, code, bodyPreview(responseBody));
                return new ClearResult(false, false, mode, message);
            }

            if (!hasClearAck(responseBody)) {
                ModLog.warn("Clear selection response missing clear ack fields: uuid={}, mode={}, body={}",
                        playerUuid, mode, bodyPreview(responseBody));
                return new ClearResult(false, false, mode,
                        "Server API does not support Clear action yet. Rebuild and restart NewServer.");
            }

            invalidateSelectedCache(playerUuid);
            boolean changed = jsonBoolean(responseBody, "changed", true);
            String cleared = firstNonBlank(jsonString(responseBody, "cleared"), mode);
            ModLog.debug("Clear selection ok: uuid={}, mode={}, changed={}", playerUuid, cleared, changed);
            return new ClearResult(true, changed, cleared, responseBody);
        } catch (Exception exception) {
            ModLog.error("Clear selection request failed for uuid=" + playerUuid + ", mode=" + mode, exception);
            return new ClearResult(false, false, mode, messageOrDefault(exception));
        } finally {
            disconnectQuietly(connection);
        }
    }

    private static boolean hasClearAck(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        return responseBody.contains("\"cleared\"") || responseBody.contains("\"changed\"");
    }

    public static CompletableFuture<SelectedSkin> fetchSelectedAsync(UUID playerUuid) {
        if (playerUuid == null) {
            ModLog.trace("Fetch selected skipped: uuid is null");
            return CompletableFuture.completedFuture(null);
        }

        // Circuit breaker: skip if server is known to be unreachable
        long now = System.currentTimeMillis();
        if (circuitOpenUntilMs > now) {
            ModLog.trace("Fetch selected skipped: circuit breaker open (retry in {} ms)",
                    circuitOpenUntilMs - now);
            return CompletableFuture.completedFuture(null);
        }

        RuntimeConfig cfg = runtimeConfig();

        CachedSelected cached = SELECTED_CACHE.get(playerUuid);
        if (cached != null && (now - cached.cachedAtMs) <= cfg.selectedCacheTtlMs) {
            ModLog.trace("Fetch selected cache hit: {}", playerUuid);
            return CompletableFuture.completedFuture(cached.value);
        }

        CompletableFuture<SelectedSkin> inFlight = SELECTED_IN_FLIGHT.get(playerUuid);
        if (inFlight != null) {
            ModLog.trace("Fetch selected in-flight reuse: {}", playerUuid);
            return inFlight;
        }

        CompletableFuture<SelectedSkin> created = CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                String requestId = newRequestId();
                String requestPath = cfg.pathSelected + (cfg.pathSelected.contains("?") ? "&uuid=" : "?uuid=")
                        + playerUuid;
                ModLog.trace("Fetch selected request: {}", requestPath);
                connection = open("GET", requestPath, null, SHA256_EMPTY_HEX, requestId, true);
                int code = responseCode(connection, requestId);
                String body = readBody(connection, code, cfg.maxJsonBytes);
                if (code / 100 != 2) {
                    ModLog.warn("Fetch selected failed: uuid={}, code={}, body={}", playerUuid, code,
                            bodyPreview(body));
                    return null;
                }

                // Success: reset circuit breaker
                consecutiveFailures = 0;
                circuitOpenUntilMs = 0L;

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
                String mouthOpenUrl = firstNonBlank(
                        jsonString(body, "mouth_open_url"),
                        jsonString(body, "mouthOpenUrl"),
                        jsonString(body, "mouth_url"),
                        jsonString(body, "mouthUrl"));
                String mouthCloseUrl = firstNonBlank(
                        jsonString(body, "mouth_close_url"),
                        jsonString(body, "mouthCloseUrl"));
                boolean slim = jsonBoolean(body, "slim", false);
                SelectedSkin selectedSkin = new SelectedSkin(url, mouthOpenUrl, mouthCloseUrl, slim);
                SELECTED_CACHE.put(playerUuid, new CachedSelected(selectedSkin, System.currentTimeMillis()));
                ModLog.trace("Fetch selected ok: uuid={}, slim={}, url={}, mouthOpen={}, mouthClose={}",
                        playerUuid, slim, url, mouthOpenUrl, mouthCloseUrl);
                return selectedSkin;
            } catch (Exception exception) {
                int failures = ++consecutiveFailures;
                if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
                    circuitOpenUntilMs = System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS;
                    ModLog.warn("API unreachable ({} failures), circuit breaker open for {} s: {}",
                            failures, CIRCUIT_BREAKER_COOLDOWN_MS / 1000, exception.getMessage());
                } else {
                    ModLog.debug("Fetch selected failed ({}/{}): {}",
                            failures, CIRCUIT_BREAKER_THRESHOLD, exception.getMessage());
                }
                return null;
            } finally {
                disconnectQuietly(connection);
            }
        }, EXECUTOR).whenComplete((ignored, throwable) -> SELECTED_IN_FLIGHT.remove(playerUuid));

        CompletableFuture<SelectedSkin> existing = SELECTED_IN_FLIGHT.putIfAbsent(playerUuid, created);
        return existing != null ? existing : created;
    }

    public static CompletableFuture<NativeImage> downloadImageAsync(String urlOrPath) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                RuntimeConfig cfg = runtimeConfig();
                String requestId = newRequestId();
                ModLog.trace("Downloading texture from {}", urlOrPath);
                connection = open("GET", urlOrPath, null, SHA256_EMPTY_HEX, requestId, false);
                int code = responseCode(connection, requestId);
                if (code / 100 != 2) {
                    ModLog.warn("Texture download failed: code={}, url={}", code, urlOrPath);
                    return null;
                }
                byte[] bodyBytes;
                try (InputStream in = connection.getInputStream()) {
                    bodyBytes = readAllBytes(in, cfg.maxImageBytes);
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
                    if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                            || image.getWidth() > 8192 || image.getHeight() > 8192) {
                        ModLog.warn("Texture dimensions rejected for {}: {}x{}", urlOrPath,
                                image == null ? 0 : image.getWidth(),
                                image == null ? 0 : image.getHeight());
                        if (image != null) {
                            image.close();
                        }
                        return null;
                    }
                    ModLog.trace("Texture downloaded: {}x{} from {}", image.getWidth(), image.getHeight(), urlOrPath);
                    return image;
                }
            } catch (Exception exception) {
                ModLog.error("Texture download failed: " + urlOrPath, exception);
                return null;
            } finally {
                disconnectQuietly(connection);
            }
        }, EXECUTOR);
    }

    public static CompletableFuture<NativeImageBackedTexture> downloadTextureAsync(String urlOrPath) {
        return downloadImageAsync(urlOrPath).thenApply(image -> {
            if (image == null) {
                return null;
            }
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            texture.setFilter(false, false);
            return texture;
        });
    }

    public static CompletableFuture<Boolean> pingAsyncOk() {
        RuntimeConfig cfg = runtimeConfig();
        CachedPing ping = cachedPing;
        long now = System.currentTimeMillis();
        if (ping != null && (now - ping.cachedAtMs) <= cfg.pingCacheTtlMs) {
            return CompletableFuture.completedFuture(ping.ok);
        }

        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                String requestId = newRequestId();
                ModLog.trace("Cloud ping start");
                connection = open("GET", "/", null, SHA256_EMPTY_HEX, requestId, true);
                boolean ok = responseCode(connection, requestId) / 100 == 2;
                cachedPing = new CachedPing(ok, System.currentTimeMillis());
                ModLog.debug("Cloud ping result={}", ok);
                return ok;
            } catch (Exception exception) {
                ModLog.warn("Cloud ping failed: {}", exception.getMessage());
                return false;
            } finally {
                disconnectQuietly(connection);
            }
        }, EXECUTOR);
    }

    public static String endpointPublicPng(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        RuntimeConfig cfg = runtimeConfig();
        String cleanId = id.endsWith(".png") ? id.substring(0, id.length() - 4) : id;
        return join(cfg.baseUrl, cfg.pathPublic + cleanId + "/skin.png");
    }

    public static synchronized void startSse(Consumer<UpdateEvent> consumer) {
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
                    RuntimeConfig cfg = runtimeConfig();
                    String requestId = newRequestId();
                    ModLog.debug("SSE connect attempt {} -> {}", attempt, cfg.pathEvents);
                    connection = openSse(cfg.pathEvents, requestId);
                    int code = responseCode(connection, requestId);
                    if (code / 100 != 2) {
                        String body = readBody(connection, code, cfg.maxJsonBytes);
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
                                ModLog.trace("SSE event: uuid={}, id={}, slim={}, url={}, mouthOpen={}, mouthClose={}",
                                        event.uuid, event.id, event.slim, event.url,
                                        event.mouthOpenUrl, event.mouthCloseUrl);
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
                    disconnectQuietly(connection);
                }
            }
            ModLog.info("SSE thread stopped");
        }, "CatSkinC-SSE");
        sseThread.setDaemon(true);
        sseThread.start();
        ModLog.debug("SSE thread started");
    }

    public static synchronized void stopSse() {
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
            String mouthOpenUrl = firstNonBlank(
                    jsonString(json, "mouth_open_url"),
                    jsonString(json, "mouthOpenUrl"),
                    jsonString(json, "mouth_url"),
                    jsonString(json, "mouthUrl"));
            String mouthCloseUrl = firstNonBlank(
                    jsonString(json, "mouth_close_url"),
                    jsonString(json, "mouthCloseUrl"));
            Boolean slim = json.contains("\"slim\"") ? jsonBoolean(json, "slim", false) : null;
            return new UpdateEvent(uuid, id, url, mouthOpenUrl, mouthCloseUrl, slim);
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

    private static HttpURLConnection open(
            String method,
            String pathOrUrl,
            String contentType,
            String bodySha256Hex,
            String requestId,
            boolean apiRequest) throws IOException {
        RuntimeConfig cfg = runtimeConfig();
        URL requestUrl = resolveRequestUrl(cfg, pathOrUrl, apiRequest);
        enforceTransportPolicy(cfg, requestUrl);
        String requestUrlString = requestUrl.toString();
        ModLog.trace("HTTP {} {} requestId={}", method, requestUrlString, requestId);

        HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(cfg.timeoutMs);
        connection.setReadTimeout(cfg.timeoutMs);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "catskinc-remake/ServerApiClient");
        connection.setRequestProperty(HEADER_REQUEST_ID, requestId);
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }

        if (apiRequest && authToken != null && !authToken.isBlank()
                && hostPortKey(requestUrl).equals(cfg.apiHostPort)) {
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
        }

        if (apiRequest) {
            String normalizedBodyHash = normalizeContentHash(bodySha256Hex);
            if (normalizedBodyHash == null) {
                throw new IOException("Invalid request body hash");
            }
            connection.setRequestProperty(HEADER_CONTENT_SHA256, normalizedBodyHash);
            if (cfg.requestSigningKey != null && !cfg.requestSigningKey.isBlank()) {
                String timestamp = Long.toString(System.currentTimeMillis() / 1000L);
                String nonce = newRequestNonce();
                String target = requestUrl.getPath();
                if (target == null || target.isBlank()) {
                    target = "/";
                }
                if (requestUrl.getQuery() != null && !requestUrl.getQuery().isBlank()) {
                    target = target + "?" + requestUrl.getQuery();
                }
                String payload = method + "\n" + target + "\n" + timestamp + "\n" + nonce + "\n" + normalizedBodyHash;
                String signature = signRequest(payload, cfg.requestSigningKey);
                connection.setRequestProperty(HEADER_TIMESTAMP, timestamp);
                connection.setRequestProperty(HEADER_NONCE, nonce);
                connection.setRequestProperty(HEADER_SIGNATURE, signature);
            }
        }

        if ("POST".equals(method) || "PUT".equals(method)) {
            connection.setDoOutput(true);
        }
        return connection;
    }

    private static HttpURLConnection openSse(String pathOrUrl, String requestId) throws IOException {
        HttpURLConnection connection = open("GET", pathOrUrl, null, SHA256_EMPTY_HEX, requestId, true);
        connection.setReadTimeout(0);
        return connection;
    }

    private static URL resolveRequestUrl(RuntimeConfig cfg, String pathOrUrl, boolean apiRequest) throws IOException {
        if (isHttp(pathOrUrl)) {
            URL absolute = parseUrl(pathOrUrl);
            String hostPort = hostPortKey(absolute);
            if (apiRequest) {
                if (!hostPort.equals(cfg.apiHostPort)) {
                    throw new IOException("Refusing API request to untrusted host");
                }
            } else if (!cfg.trustedAssetHostPorts.contains(hostPort)
                    && !cfg.trustedAssetHostPorts.contains(absolute.getHost().toLowerCase(Locale.ROOT))) {
                throw new IOException("Refusing asset request to untrusted host");
            }
            return absolute;
        }
        return parseUrl(join(cfg.baseUrl, pathOrUrl));
    }

    private static void enforceTransportPolicy(RuntimeConfig cfg, URL requestUrl) throws IOException {
        String protocol = requestUrl.getProtocol() == null ? "" : requestUrl.getProtocol().toLowerCase(Locale.ROOT);
        String host = requestUrl.getHost() == null ? "" : requestUrl.getHost();
        if ("http".equals(protocol) && !cfg.allowInsecureHttp && !isLocalHost(host)) {
            throw new IOException("Insecure HTTP blocked for non-localhost host");
        }
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

    private static int responseCode(HttpURLConnection connection, String requestId) throws IOException {
        int code = connection.getResponseCode();
        verifyTlsPinIfNeeded(connection, runtimeConfig(), requestId);
        if (isRedirectCode(code)) {
            String location = connection.getHeaderField("Location");
            if (location != null && !location.isBlank()) {
                ModLog.warn("Redirect blocked by policy: requestId={}, location={}", requestId, location);
            }
        }
        return code;
    }

    /* package-private for testing */ static String readBody(HttpURLConnection connection, int code, int maxBytes) {
        try (InputStream in = code / 100 == 2
                ? connection.getInputStream()
                : connection.getErrorStream()) {
            if (in == null) {
                return null;
            }
            return new String(readAllBytes(in, maxBytes), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            ModLog.trace("Failed reading HTTP body: {}", exception.getMessage());
            return null;
        }
    }

    private static void disconnectQuietly(HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    static long estimateMultipartOverhead(
            String boundary,
            UUID playerUuid,
            boolean slim,
            boolean includeMouthOpen,
            boolean includeMouthClose,
            boolean includeLegacyMouth) {
        long overhead = 0;
        // uuid part
        if (playerUuid != null) {
            overhead += ("--" + boundary + "\r\n").length();
            overhead += "Content-Disposition: form-data; name=\"uuid\"\r\n\r\n".length();
            overhead += (playerUuid.toString() + "\r\n").length();
        }
        // slim part
        overhead += ("--" + boundary + "\r\n").length();
        overhead += "Content-Disposition: form-data; name=\"slim\"\r\n\r\n".length();
        overhead += (Boolean.toString(slim) + "\r\n").length();
        // file part header
        overhead += ("--" + boundary + "\r\n").length();
        overhead += "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n".length();
        overhead += "Content-Type: image/png\r\n\r\n".length();
        if (includeMouthOpen) {
            overhead += "\r\n".length();
            overhead += ("--" + boundary + "\r\n").length();
            overhead += "Content-Disposition: form-data; name=\"mouth_open\"; filename=\"mouth-open.png\"\r\n".length();
            overhead += "Content-Type: image/png\r\n\r\n".length();
        }
        if (includeLegacyMouth) {
            overhead += "\r\n".length();
            overhead += ("--" + boundary + "\r\n").length();
            overhead += "Content-Disposition: form-data; name=\"mouth\"; filename=\"mouth-open.png\"\r\n".length();
            overhead += "Content-Type: image/png\r\n\r\n".length();
        }
        if (includeMouthClose) {
            overhead += "\r\n".length();
            overhead += ("--" + boundary + "\r\n").length();
            overhead += "Content-Disposition: form-data; name=\"mouth_close\"; filename=\"mouth-close.png\"\r\n"
                    .length();
            overhead += "Content-Type: image/png\r\n\r\n".length();
        }
        // closing boundary
        overhead += ("\r\n--" + boundary + "--\r\n").length();
        return overhead;
    }

    /**
     * Resets all internal state for testing purposes.
     * This method is package-private and should only be used in tests.
     */
    /**
     * Resets all internal state for testing purposes.
     * This method is package-private and should only be used in tests.
     */
    static void resetForTesting() {
        authToken = null;
        SELECTED_CACHE.clear();
        SELECTED_IN_FLIGHT.clear();
        cachedPing = null;
        consecutiveFailures = 0;
        circuitOpenUntilMs = 0L;
        stopSse();
    }

    private static byte[] readAllBytes(InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("response too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256Hex(byte[] value) throws IOException {
        return toHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(byte[] value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value);
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

    private static String httpErrorMessage(String body, int code) {
        String parsed = firstNonBlank(
                jsonString(body, "error"),
                jsonString(body, "message"),
                jsonString(body, "detail"));
        if (parsed != null && !parsed.isBlank()) {
            return parsed;
        }
        if (body == null || body.isBlank()) {
            return "HTTP " + code;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")) {
            return "HTTP " + code;
        }
        return trimmed;
    }

    private static String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String normalizeClearMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "all" -> "all";
            case "skin" -> "skin";
            case "mouth" -> "mouth";
            default -> null;
        };
    }

    private static String normalizeBaseUrl(String value) {
        String base = value == null ? "" : value.trim();
        if (base.isEmpty()) {
            base = DEFAULT_BASE_URL;
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = DEFAULT_BASE_URL;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String normalizePath(String value, String fallback) {
        String path = value == null ? "" : value.trim();
        if (path.isEmpty()) {
            path = fallback;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private static String ensureTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private static String normalizePin(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String pin = value.trim();
        if (pin.startsWith("sha256/")) {
            pin = pin.substring("sha256/".length());
        }
        String compact = pin.replace(":", "").replace("-", "");
        if (compact.length() == 64 && isHex(compact)) {
            return compact.toLowerCase(Locale.ROOT);
        }
        return pin;
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max, long fallback) {
        if (value <= 0L) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static URL parseUrl(String url) throws IOException {
        try {
            return new URL(url);
        } catch (MalformedURLException exception) {
            throw new IOException("Invalid URL: " + url, exception);
        }
    }

    private static String hostPortKey(URL url) {
        if (url == null || url.getHost() == null) {
            return "";
        }
        int port = url.getPort();
        if (port < 0) {
            port = url.getDefaultPort();
        }
        return url.getHost().toLowerCase(Locale.ROOT) + ":" + port;
    }

    private static boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private static String normalizeContentHash(String hash) {
        String value = hash == null ? SHA256_EMPTY_HEX : hash.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            value = SHA256_EMPTY_HEX;
        }
        if (value.length() != 64) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return null;
            }
        }
        return value;
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    private static String newRequestNonce() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return raw + Long.toHexString(System.nanoTime());
    }

    private static String signRequest(String payload, String rawKey) throws IOException {
        byte[] keyBytes = decodeKeyMaterial(rawKey);
        if (keyBytes.length < 16) {
            throw new IOException("request signing key too short");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to sign request", exception);
        }
    }

    private static byte[] decodeKeyMaterial(String rawValue) throws IOException {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return new byte[0];
        }
        try {
            if (value.startsWith("hex:")) {
                String hex = value.substring(4).trim();
                if ((hex.length() & 1) != 0) {
                    throw new IOException("Invalid hex key length");
                }
                byte[] out = new byte[hex.length() / 2];
                for (int i = 0; i < out.length; i++) {
                    int hi = Character.digit(hex.charAt(i * 2), 16);
                    int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
                    if (hi < 0 || lo < 0) {
                        throw new IOException("Invalid hex signing key");
                    }
                    out[i] = (byte) ((hi << 4) + lo);
                }
                return out;
            }
            if (value.startsWith("b64:")) {
                return Base64.getDecoder().decode(value.substring(4).trim());
            }
            return value.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid signing key encoding", exception);
        }
    }

    private static byte[] readFileBytes(File file, int maxBytes, String label) throws IOException {
        if (file == null) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (bytes.length > maxBytes) {
            throw new IOException(label + " file is too large");
        }
        return bytes;
    }

    private static String computeUploadContentHash(
            UUID playerUuid,
            boolean slim,
            byte[] skinBytes,
            byte[] mouthOpenBytes,
            byte[] mouthCloseBytes) throws IOException {
        ByteArrayOutputStream canonical = new ByteArrayOutputStream();
        appendHashField(canonical, "uuid", playerUuid == null ? new byte[0]
                : playerUuid.toString().getBytes(StandardCharsets.UTF_8));
        appendHashField(canonical, "slim", (slim ? "true" : "false").getBytes(StandardCharsets.UTF_8));
        appendHashField(canonical, "file", skinBytes == null ? new byte[0] : skinBytes);
        appendHashField(canonical, "mouth_open", mouthOpenBytes == null ? new byte[0] : mouthOpenBytes);
        appendHashField(canonical, "mouth_close", mouthCloseBytes == null ? new byte[0] : mouthCloseBytes);
        return sha256Hex(canonical.toByteArray());
    }

    private static void appendHashField(ByteArrayOutputStream output, String name, byte[] value) throws IOException {
        byte[] data = value == null ? new byte[0] : value;
        output.write(name.getBytes(StandardCharsets.UTF_8));
        output.write(':');
        output.write(Integer.toString(data.length).getBytes(StandardCharsets.UTF_8));
        output.write('\n');
        output.write(data);
        output.write('\n');
    }

    private static void verifyTlsPinIfNeeded(HttpURLConnection connection, RuntimeConfig cfg, String requestId)
            throws IOException {
        if (!(connection instanceof HttpsURLConnection httpsConnection)) {
            return;
        }
        String pin = cfg.tlsPinSha256;
        if (pin == null || pin.isBlank()) {
            return;
        }
        try {
            Certificate[] certs = httpsConnection.getServerCertificates();
            if (certs == null || certs.length == 0) {
                throw new IOException("TLS pin verification failed: no certificates");
            }
            byte[] keyBytes = certs[0].getPublicKey().getEncoded();
            String actualHex = sha256Hex(keyBytes).toLowerCase(Locale.ROOT);
            String actualB64 = Base64.getEncoder().encodeToString(sha256Bytes(keyBytes));
            boolean ok = pin.equals(actualHex) || pin.equals(actualB64) || ("sha256/" + actualB64).equals(pin);
            if (!ok) {
                throw new IOException("TLS pin mismatch for request " + requestId);
            }
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("TLS pin verification failed", exception);
        }
    }

    private static boolean isRedirectCode(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static void publishClearResult(Consumer<ClearResult> callback, ClearResult result) {
        if (callback == null) {
            return;
        }
        try {
            callback.accept(result);
        } catch (Exception exception) {
            ModLog.warn("Clear callback failed: {}", exception.getMessage());
        }
    }

    private static String jsonString(String body, String key) {
        JsonObject object = parseJsonObject(body);
        if (object == null || key == null || key.isBlank() || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsJsonPrimitive().getAsString();
        }
        return element.toString();
    }

    private static boolean jsonBoolean(String body, String key, boolean defaultValue) {
        JsonObject object = parseJsonObject(body);
        if (object == null || key == null || key.isBlank() || !object.has(key)) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt() != 0;
            }
            if (element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString();
                if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
                    return false;
                }
            }
        }
        return defaultValue;
    }

    private static JsonObject parseJsonObject(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
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
