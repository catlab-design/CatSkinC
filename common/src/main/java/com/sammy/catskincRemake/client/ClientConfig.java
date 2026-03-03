package com.sammy.catskincRemake.client;

public final class ClientConfig {
    public int openUiKey = 75;
    public long refreshIntervalMs = 1_000L;
    public int ensureIntervalTicks = 20;
    public int ensureLimitPerPass = 16;

    public float uiScale = 1.0F;
    public int voiceAmplitudeThreshold = 180;
    public long voiceHoldMs = 420L;

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
        if (traceLogging) {
            debugLogging = true;
        }
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
