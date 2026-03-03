package com.sammy.catskincRemake.client;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
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

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "CatSkinC-Api");
        thread.setDaemon(true);
        return thread;
    });
    private static final int BODY_PREVIEW_LIMIT = 220;
    private static final ProgressListener NO_OP_PROGRESS = new ProgressListener() {
    };
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    // private static final String BASE_URL = "https://storage-api.catskin.space";
    private static final String BASE_URL = "http://127.0.0.1:2555";
    private static final String PATH_UPLOAD = "/upload";
    private static final String PATH_SELECT = "/select";
    private static final String PATH_SELECTED = "/selected";
    private static final String PATH_PUBLIC = "/public/";
    private static final String PATH_EVENTS = "/events";
    private static final int TIMEOUT_MS = 15_000;
    private static final long SELECTED_CACHE_TTL_MS = 1_500L;
    private static final long PING_CACHE_TTL_MS = 10_000L;

    private static volatile String authToken;

    private static volatile Thread sseThread;
    private static volatile boolean sseStop;
    private static final ConcurrentHashMap<UUID, CachedSelected> SELECTED_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CompletableFuture<SelectedSkin>> SELECTED_IN_FLIGHT = new ConcurrentHashMap<>();
    private static volatile CachedPing cachedPing;

    private ServerApiClient() {
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
                String boundary = "----CatSkinC-" + System.nanoTime();
                connection = open("POST", PATH_UPLOAD, "multipart/form-data; boundary=" + boundary);

                boolean includeLegacyMouth = mouthOpenFile != null;
                long multipartOverhead = estimateMultipartOverhead(
                        boundary, playerUuid, slim, mouthOpenFile != null, mouthCloseFile != null, includeLegacyMouth);
                long totalBytes = file.length()
                        + (mouthOpenFile == null ? 0L : mouthOpenFile.length())
                        + (mouthCloseFile == null ? 0L : mouthCloseFile.length())
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

                    try (InputStream in = new FileInputStream(file)) {
                        byte[] buffer = new byte[8_192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }

                    if (mouthOpenFile != null) {
                        writer.append("\r\n--").append(boundary).append("\r\n");
                        writer.append(
                                "Content-Disposition: form-data; name=\"mouth_open\"; filename=\"mouth-open.png\"")
                                .append("\r\n");
                        writer.append("Content-Type: image/png").append("\r\n\r\n");
                        writer.flush();

                        try (InputStream in = new FileInputStream(mouthOpenFile)) {
                            byte[] buffer = new byte[8_192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }

                        // Legacy compatibility: older server builds expect "mouth" only.
                        writer.append("\r\n--").append(boundary).append("\r\n");
                        writer.append(
                                "Content-Disposition: form-data; name=\"mouth\"; filename=\"mouth-open.png\"")
                                .append("\r\n");
                        writer.append("Content-Type: image/png").append("\r\n\r\n");
                        writer.flush();

                        try (InputStream in = new FileInputStream(mouthOpenFile)) {
                            byte[] buffer = new byte[8_192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                    if (mouthCloseFile != null) {
                        writer.append("\r\n--").append(boundary).append("\r\n");
                        writer.append(
                                "Content-Disposition: form-data; name=\"mouth_close\"; filename=\"mouth-close.png\"")
                                .append("\r\n");
                        writer.append("Content-Type: image/png").append("\r\n\r\n");
                        writer.flush();

                        try (InputStream in = new FileInputStream(mouthCloseFile)) {
                            byte[] buffer = new byte[8_192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                        }
                    }

                    out.flush();
                    writer.append("\r\n--").append(boundary).append("--").append("\r\n");
                    writer.flush();
                }

                int code = responseCode(connection);
                String body = readBody(connection, code);
                ModLog.trace("Upload response: code={}, body={}", code, bodyPreview(body));
                if (code / 100 != 2) {
                    listener.onDone(false, httpErrorMessage(body, code));
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
                ModLog.debug("Selecting skin: uuid={}, value={}, slim={}", playerUuid, skinIdOrUrl, slim);
                connection = open("POST", PATH_SELECT, "application/json; charset=utf-8");
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
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = responseCode(connection);
                String responseBody = readBody(connection, code);
                if (code / 100 != 2) {
                    ModLog.warn("Select skin failed: code={}, body={}", code, bodyPreview(responseBody));
                } else {
                    invalidateSelectedCache(playerUuid);
                    ModLog.trace("Select skin ok: code={}, body={}", code, bodyPreview(responseBody));
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
            connection = open("POST", PATH_SELECT, "application/json; charset=utf-8");
            String body = "{\"uuid\":\"" + playerUuid + "\",\"clear\":\"" + mode + "\"}";
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = responseCode(connection);
            String responseBody = readBody(connection, code);
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

        long now = System.currentTimeMillis();
        CachedSelected cached = SELECTED_CACHE.get(playerUuid);
        if (cached != null && (now - cached.cachedAtMs) <= SELECTED_CACHE_TTL_MS) {
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
                String requestPath = PATH_SELECTED + (PATH_SELECTED.contains("?") ? "&uuid=" : "?uuid=") + playerUuid;
                ModLog.trace("Fetch selected request: {}", requestPath);
                connection = open("GET", requestPath, null);
                int code = responseCode(connection);
                String body = readBody(connection, code);
                if (code / 100 != 2) {
                    ModLog.warn("Fetch selected failed: uuid={}, code={}, body={}", playerUuid, code,
                            bodyPreview(body));
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
                ModLog.error("Fetch selected failed for uuid=" + playerUuid, exception);
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
                ModLog.trace("Downloading texture from {}", urlOrPath);
                connection = open("GET", urlOrPath, null);
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
        CachedPing ping = cachedPing;
        long now = System.currentTimeMillis();
        if (ping != null && (now - ping.cachedAtMs) <= PING_CACHE_TTL_MS) {
            return CompletableFuture.completedFuture(ping.ok);
        }

        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                ModLog.trace("Cloud ping start");
                connection = open("GET", "/", null);
                boolean ok = responseCode(connection) / 100 == 2;
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
        String cleanId = id.endsWith(".png") ? id.substring(0, id.length() - 4) : id;
        return join(BASE_URL, PATH_PUBLIC + cleanId + "/skin.png");
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
                    ModLog.debug("SSE connect attempt {} -> {}", attempt, PATH_EVENTS);
                    connection = openSse(PATH_EVENTS);
                    int code = responseCode(connection);
                    if (code / 100 != 2) {
                        String body = readBody(connection, code);
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

    private static HttpURLConnection open(String method, String pathOrUrl, String contentType) throws IOException {
        String requestUrl = isHttp(pathOrUrl) ? pathOrUrl : join(BASE_URL, pathOrUrl);
        ModLog.trace("HTTP {} {}", method, requestUrl);
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "catskinc-remake/ServerApiClient");
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        if (authToken != null && !authToken.isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
        }
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

    private static int responseCode(HttpURLConnection connection) throws IOException {
        return connection.getResponseCode();
    }

    /* package-private for testing */ static String readBody(HttpURLConnection connection, int code) {
        try (InputStream in = code / 100 == 2
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
            overhead +=
                    "Content-Disposition: form-data; name=\"mouth_open\"; filename=\"mouth-open.png\"\r\n".length();
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
            overhead +=
                    "Content-Disposition: form-data; name=\"mouth_close\"; filename=\"mouth-close.png\"\r\n".length();
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
        stopSse();
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
