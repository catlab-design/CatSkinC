package com.sammy.catskincRemake.client;

public final class ClientConfig {
    public int openUiKey = 75;
    public long refreshIntervalMs = 15_000L;
    public int ensureIntervalTicks = 20;
    public int ensureLimitPerPass = 16;

    public float uiScale = 1.0F;
    public int voiceAmplitudeThreshold = 180;
    public long voiceHoldMs = 420L;

    public String apiBaseUrl = "https://storage-api.catskin.space";
    public String pathUpload = "/upload";
    public String pathSelect = "/select";
    public String pathSelected = "/selected";
    public String pathPublic = "/public/";
    public String pathEvents = "/events";
    public int timeoutMs = 15_000;
    public long selectedCacheTtlMs = 1_500L;
    public long pingCacheTtlMs = 10_000L;
    public boolean allowInsecureHttp = false;
    public String requestSigningKey = "";
    public String tlsPinSha256 = "";
    public String allowedAssetHosts = "";
    public int maxJsonBytes = 256 * 1024;
    public int maxImageBytes = 8 * 1024 * 1024;

    public boolean debugLogging = false;
    public boolean traceLogging = false;

    public void sanitize() {
        openUiKey = clamp(openUiKey, -1, 512);
        refreshIntervalMs = clamp(refreshIntervalMs, 500L, 60_000L);
        ensureIntervalTicks = clamp(ensureIntervalTicks, 5, 200);
        ensureLimitPerPass = clamp(ensureLimitPerPass, 1, 128);
        uiScale = clamp(uiScale, 0.6F, 1.75F);
        voiceAmplitudeThreshold = clamp(voiceAmplitudeThreshold, 10, 30_000);
        voiceHoldMs = clamp(voiceHoldMs, 60L, 2_000L);
        apiBaseUrl = sanitizeBaseUrl(apiBaseUrl);
        pathUpload = sanitizePath(pathUpload, "/upload");
        pathSelect = sanitizePath(pathSelect, "/select");
        pathSelected = sanitizePath(pathSelected, "/selected");
        pathPublic = sanitizePath(pathPublic, "/public/");
        if (!pathPublic.endsWith("/")) {
            pathPublic = pathPublic + "/";
        }
        pathEvents = sanitizePath(pathEvents, "/events");
        timeoutMs = clamp(timeoutMs, 1_000, 120_000);
        selectedCacheTtlMs = clamp(selectedCacheTtlMs, 250L, 60_000L);
        pingCacheTtlMs = clamp(pingCacheTtlMs, 1_000L, 120_000L);
        requestSigningKey = sanitizeNullableString(requestSigningKey);
        tlsPinSha256 = sanitizeNullableString(tlsPinSha256);
        allowedAssetHosts = sanitizeNullableString(allowedAssetHosts);
        maxJsonBytes = clamp(maxJsonBytes, 4 * 1024, 4 * 1024 * 1024);
        maxImageBytes = clamp(maxImageBytes, 64 * 1024, 32 * 1024 * 1024);
        if (traceLogging) {
            debugLogging = true;
        }
    }

    private static String sanitizeBaseUrl(String value) {
        String normalized = sanitizeNullableString(value);
        if (normalized.isEmpty()) {
            return "https://storage-api.catskin.space";
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return "https://storage-api.catskin.space";
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String sanitizePath(String value, String fallback) {
        String normalized = sanitizeNullableString(value);
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private static String sanitizeNullableString(String value) {
        return value == null ? "" : value.trim();
    }

    /* package-private for testing */ static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return clampInt(value, min, max);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
