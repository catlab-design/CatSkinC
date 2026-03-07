package com.sammy.catskincRemake.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sammy.catskincRemake.CatskincRemake;
import dev.architectury.platform.Platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModrinthVersionChecker {
    private static final String PROJECT_SLUG = "catskinc";
    private static final String API_BASE_URL = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
    private static final String USER_AGENT = "catskinc-remake/ModrinthVersionChecker";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CatSkinC-Modrinth");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile CompletableFuture<UpdateCheckResult> inFlight;
    private static volatile String notifiedVersion;

    private ModrinthVersionChecker() {
    }

    public static CompletableFuture<UpdateCheckResult> checkForUpdatesAsync() {
        CompletableFuture<UpdateCheckResult> future = inFlight;
        if (future != null) {
            return future;
        }

        synchronized (ModrinthVersionChecker.class) {
            future = inFlight;
            if (future != null) {
                return future;
            }

            String currentVersion = currentVersion();
            if (currentVersion.isBlank()) {
                future = CompletableFuture.completedFuture(UpdateCheckResult.none(currentVersion));
            } else {
                future = CompletableFuture.supplyAsync(() -> fetchUpdate(currentVersion), EXECUTOR)
                        .exceptionally(exception -> {
                            Throwable cause = unwrap(exception);
                            ModLog.debug("Modrinth version check failed: {}", cause.getMessage());
                            ModLog.trace("Modrinth version check failed", cause);
                            return UpdateCheckResult.none(currentVersion);
                        });
            }

            inFlight = future;
            return future;
        }
    }

    public static synchronized boolean tryMarkNotified(UpdateCheckResult result) {
        if (result == null || !result.updateAvailable()) {
            return false;
        }
        if (Objects.equals(notifiedVersion, result.latestVersion())) {
            return false;
        }
        notifiedVersion = result.latestVersion();
        return true;
    }

    static UpdateCheckResult parseResponse(String body, String currentVersion) {
        String current = blankToEmpty(currentVersion);
        if (current.isBlank()) {
            return UpdateCheckResult.none(currentVersion);
        }

        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonArray()) {
            return UpdateCheckResult.none(currentVersion);
        }

        RemoteVersion latestListed = null;
        RemoteVersion latestRelease = null;
        for (JsonElement element : parsed.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String versionNumber = getString(object, "version_number");
            if (versionNumber.isBlank()) {
                continue;
            }
            String status = getString(object, "status");
            if (!status.isBlank() && !"listed".equalsIgnoreCase(status)) {
                continue;
            }

            RemoteVersion candidate = new RemoteVersion(
                    versionNumber,
                    firstNonBlank(getString(object, "name"), versionNumber),
                    getString(object, "version_type"),
                    parseInstant(getString(object, "date_published")));

            if (latestListed == null || compareRemoteVersions(candidate, latestListed) > 0) {
                latestListed = candidate;
            }
            if ("release".equalsIgnoreCase(candidate.versionType())
                    && (latestRelease == null || compareRemoteVersions(candidate, latestRelease) > 0)) {
                latestRelease = candidate;
            }
        }

        RemoteVersion latest = latestRelease != null ? latestRelease : latestListed;
        if (latest == null) {
            return UpdateCheckResult.none(currentVersion);
        }

        return new UpdateCheckResult(
                current,
                latest.versionNumber(),
                latest.name(),
                compareVersions(latest.versionNumber(), current) > 0);
    }

    static int compareVersions(String left, String right) {
        ParsedVersion leftVersion = ParsedVersion.parse(left);
        ParsedVersion rightVersion = ParsedVersion.parse(right);

        int maxMain = Math.max(leftVersion.main().size(), rightVersion.main().size());
        for (int i = 0; i < maxMain; i++) {
            String leftPart = i < leftVersion.main().size() ? leftVersion.main().get(i) : "0";
            String rightPart = i < rightVersion.main().size() ? rightVersion.main().get(i) : "0";
            int comparison = compareIdentifier(leftPart, rightPart, true);
            if (comparison != 0) {
                return comparison;
            }
        }

        boolean leftHasPrerelease = !leftVersion.prerelease().isEmpty();
        boolean rightHasPrerelease = !rightVersion.prerelease().isEmpty();
        if (leftHasPrerelease != rightHasPrerelease) {
            return leftHasPrerelease ? -1 : 1;
        }

        int maxPrerelease = Math.max(leftVersion.prerelease().size(), rightVersion.prerelease().size());
        for (int i = 0; i < maxPrerelease; i++) {
            if (i >= leftVersion.prerelease().size()) {
                return -1;
            }
            if (i >= rightVersion.prerelease().size()) {
                return 1;
            }
            int comparison = compareIdentifier(leftVersion.prerelease().get(i), rightVersion.prerelease().get(i), false);
            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    private static UpdateCheckResult fetchUpdate(String currentVersion) {
        String loader = currentLoader();
        String gameVersion = blankToEmpty(Platform.getMinecraftVersion());
        if (loader.isBlank() || gameVersion.isBlank()) {
            ModLog.debug("Skipping Modrinth version check; loader='{}', gameVersion='{}'", loader, gameVersion);
            return UpdateCheckResult.none(currentVersion);
        }

        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(buildRequestUrl(gameVersion, loader));
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(requestTimeoutMs());
            connection.setReadTimeout(requestTimeoutMs());
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);

            int code = connection.getResponseCode();
            String body = readBody(connection, code, MAX_RESPONSE_BYTES);
            if (code < 200 || code >= 300) {
                ModLog.debug("Modrinth version check returned HTTP {} for {}", code, requestUrl);
                return UpdateCheckResult.none(currentVersion);
            }

            UpdateCheckResult result = parseResponse(body, currentVersion);
            if (result.updateAvailable()) {
                ModLog.info("Mod update available: current={}, latest={}", result.currentVersion(), result.latestVersion());
            } else {
                ModLog.debug("Mod is up to date: {}", result.currentVersion());
            }
            return result;
        } catch (IOException exception) {
            throw new RuntimeException("Failed to query Modrinth", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String buildRequestUrl(String gameVersion, String loader) {
        String encodedGameVersions = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
        String encodedLoaders = URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8);
        return API_BASE_URL
                + "?game_versions=" + encodedGameVersions
                + "&loaders=" + encodedLoaders
                + "&include_changelog=false";
    }

    private static String currentVersion() {
        try {
            return blankToEmpty(Platform.getMod(CatskincRemake.MOD_ID).getVersion());
        } catch (Exception exception) {
            ModLog.debug("Unable to resolve current mod version: {}", exception.getMessage());
            ModLog.trace("Unable to resolve current mod version", exception);
            return "";
        }
    }

    private static String currentLoader() {
        if (Platform.isFabric()) {
            return "fabric";
        }
        if (platformFlag("isNeoForge")) {
            return "neoforge";
        }
        if (platformFlag("isForge")) {
            return "forge";
        }
        return "";
    }

    private static int requestTimeoutMs() {
        ClientConfig config = ConfigManager.get();
        config.sanitize();
        return Math.max(1_000, Math.min(config.timeoutMs, 30_000));
    }

    private static int compareRemoteVersions(RemoteVersion left, RemoteVersion right) {
        int versionComparison = compareVersions(left.versionNumber(), right.versionNumber());
        if (versionComparison != 0) {
            return versionComparison;
        }
        return left.publishedAt().compareTo(right.publishedAt());
    }

    private static int compareIdentifier(String left, String right, boolean mainSegment) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            return compareNumericStrings(left, right);
        }
        if (leftNumeric != rightNumeric) {
            return mainSegment
                    ? (leftNumeric ? 1 : -1)
                    : (leftNumeric ? -1 : 1);
        }
        return left.compareToIgnoreCase(right);
    }

    private static int compareNumericStrings(String left, String right) {
        String normalizedLeft = stripLeadingZeros(left);
        String normalizedRight = stripLeadingZeros(right);
        int lengthComparison = Integer.compare(normalizedLeft.length(), normalizedRight.length());
        if (lengthComparison != 0) {
            return lengthComparison;
        }
        return normalizedLeft.compareTo(normalizedRight);
    }

    private static String stripLeadingZeros(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        return value.substring(index);
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.EPOCH;
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            return blankToEmpty(element.getAsString());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String readBody(HttpURLConnection connection, int code, int maxBytes) throws IOException {
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4_096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Modrinth response exceeded " + maxBytes + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean platformFlag(String methodName) {
        try {
            Object value = Platform.class.getMethod(methodName).invoke(null);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        String left = blankToEmpty(first);
        return left.isBlank() ? blankToEmpty(second) : left;
    }

    public record UpdateCheckResult(String currentVersion, String latestVersion, String latestName, boolean updateAvailable) {
        private static UpdateCheckResult none(String currentVersion) {
            String normalized = blankToEmpty(currentVersion);
            return new UpdateCheckResult(normalized, normalized, "", false);
        }
    }

    private record RemoteVersion(String versionNumber, String name, String versionType, Instant publishedAt) {
    }

    private record ParsedVersion(List<String> main, List<String> prerelease) {
        private static ParsedVersion parse(String rawVersion) {
            String normalized = blankToEmpty(rawVersion);
            if (normalized.startsWith("v") || normalized.startsWith("V")) {
                normalized = normalized.substring(1);
            }
            int buildSeparator = normalized.indexOf('+');
            if (buildSeparator >= 0) {
                normalized = normalized.substring(0, buildSeparator);
            }

            String mainPart = normalized;
            String prereleasePart = "";
            int prereleaseSeparator = normalized.indexOf('-');
            if (prereleaseSeparator >= 0) {
                mainPart = normalized.substring(0, prereleaseSeparator);
                prereleasePart = normalized.substring(prereleaseSeparator + 1);
            }

            return new ParsedVersion(split(mainPart, "[._]"), split(prereleasePart, "[.-]"));
        }

        private static List<String> split(String value, String pattern) {
            List<String> parts = new ArrayList<>();
            for (String token : blankToEmpty(value).split(pattern)) {
                String normalized = blankToEmpty(token);
                if (!normalized.isBlank()) {
                    parts.add(normalized);
                }
            }
            return parts;
        }
    }
}
