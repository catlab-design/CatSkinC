package com.sammy.catskincRemake.client;

import java.net.URI;
import java.util.Locale;

public final class ClientConfig {
    public int openUiKey = 75;
    public long refreshIntervalMs = 1_000L;
    public int ensureIntervalTicks = 20;
    public int ensureLimitPerPass = 16;

    public float uiScale = 1.0F;

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
    public boolean debugLogging = false;
    public boolean traceLogging = false;

    public void sanitize() {
        openUiKey = clamp(openUiKey, -1, 512);
        refreshIntervalMs = clamp(refreshIntervalMs, 500L, 60_000L);
        ensureIntervalTicks = clamp(ensureIntervalTicks, 5, 200);
        ensureLimitPerPass = clamp(ensureLimitPerPass, 1, 128);
        uiScale = clamp(uiScale, 0.6F, 1.75F);
        timeoutMs = clamp(timeoutMs, 3_000, 60_000);
        selectedCacheTtlMs = clamp(selectedCacheTtlMs, 250L, 30_000L);
        pingCacheTtlMs = clamp(pingCacheTtlMs, 1_000L, 120_000L);

        apiBaseUrl = sanitizeUrl(apiBaseUrl, "https://storage-api.catskin.space", allowInsecureHttp);
        pathUpload = sanitizePath(pathUpload, "/upload");
        pathSelect = sanitizePath(pathSelect, "/select");
        pathSelected = sanitizePath(pathSelected, "/selected");
        pathPublic = sanitizePath(pathPublic, "/public/");
        pathEvents = sanitizePath(pathEvents, "/events");
        requestSigningKey = sanitizeSecret(requestSigningKey);
        tlsPinSha256 = sanitizeSha256Pin(tlsPinSha256);
        if (traceLogging) {
            debugLogging = true;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String sanitizeUrl(String value, String fallback, boolean allowInsecureHttp) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return fallback;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
                return fallback;
            }
            if ("http".equals(normalizedScheme) && !allowInsecureHttp && !isLoopbackHost(uri.getHost())) {
                return fallback;
            }
        } catch (Exception exception) {
            return fallback;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String sanitizePath(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    private static String sanitizeSecret(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String sanitizeSha256Pin(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.regionMatches(true, 0, "sha256/", 0, 7)) {
            trimmed = trimmed.substring(7);
        }
        trimmed = trimmed.replace(":", "").trim().toLowerCase(Locale.ROOT);
        if (trimmed.length() != 64) {
            return "";
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return "";
            }
        }
        return trimmed;
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }
}

