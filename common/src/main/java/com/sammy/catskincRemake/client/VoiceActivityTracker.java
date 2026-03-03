package com.sammy.catskincRemake.client;

import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceActivityTracker {
    private static final Map<UUID, Long> LOCAL_ACTIVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LOCAL_SILENCE_SINCE = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerVoiceState> SERVER_ACTIVITY = new ConcurrentHashMap<>();
    private static final long LOCAL_RELEASE_DEBOUNCE_MS = 40L;
    private static volatile int amplitudeThreshold = 180;
    private static volatile long holdMs = 240L;
    private static volatile long staleMs = holdMs * 8L;
    private static volatile long serverPacketTimeoutMs = 3_000L;
    private static volatile long serverSpeakingStaleMs = 300L;
    private static volatile long nextCleanupAtMs;

    private VoiceActivityTracker() {
    }

    public static void configure(int threshold, long holdMillis) {
        amplitudeThreshold = Math.max(10, Math.min(threshold, 30_000));
        holdMs = Math.max(60L, Math.min(holdMillis, 2_000L));
        staleMs = Math.max(holdMs * 4L, holdMs + 500L);
        ModLog.debug("Voice tracker configured: threshold={}, holdMs={}", amplitudeThreshold, holdMs);
    }

    public static UUID currentClientUuid() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null ? client.player.getUuid() : null;
    }

    public static void markSpeaking(UUID uuid) {
        if (uuid == null) {
            return;
        }
        LOCAL_ACTIVITY.put(uuid, System.currentTimeMillis());
        LOCAL_SILENCE_SINCE.remove(uuid);
    }

    public static void markIfVoice(UUID uuid, short[] samples) {
        if (uuid == null || samples == null || samples.length == 0) {
            return;
        }
        if (hasVoice(samples)) {
            markSpeaking(uuid);
        }
    }

    public static boolean isSpeaking(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID currentUuid = currentClientUuid();
        boolean isLocalPlayer = uuid.equals(currentUuid);

        Long localLast = LOCAL_ACTIVITY.get(uuid);
        if (isLocalPlayer) {
            Long silenceSince = LOCAL_SILENCE_SINCE.get(uuid);
            if (silenceSince != null) {
                if (now - silenceSince < LOCAL_RELEASE_DEBOUNCE_MS) {
                    return true;
                }
                LOCAL_SILENCE_SINCE.remove(uuid);
                LOCAL_ACTIVITY.remove(uuid);
                return false;
            }
            return localLast != null && now - localLast <= holdMs;
        }

        if (localLast != null && now - localLast <= holdMs) {
            return true;
        }

        ServerVoiceState state = SERVER_ACTIVITY.get(uuid);
        if (state == null) {
            return false;
        }
        if (now - state.receivedAtMs() > serverPacketTimeoutMs) {
            return false;
        }
        if (!state.speaking()) {
            return false;
        }
        return now - state.receivedAtMs() <= serverSpeakingStaleMs;
    }

    public static void markServerState(UUID uuid, boolean speaking, int sequence, long sentAtMs) {
        if (uuid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        ServerVoiceState previous = SERVER_ACTIVITY.get(uuid);
        if (previous != null && sequence < previous.sequence()) {
            return;
        }
        SERVER_ACTIVITY.put(uuid, new ServerVoiceState(speaking, sequence, sentAtMs, now));
        if (speaking) {
            LOCAL_ACTIVITY.put(uuid, now);
        } else {
            LOCAL_ACTIVITY.remove(uuid);
        }
    }

    public static void clearLocalSpeaking(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (LOCAL_ACTIVITY.containsKey(uuid)) {
            LOCAL_SILENCE_SINCE.putIfAbsent(uuid, System.currentTimeMillis());
        } else {
            LOCAL_SILENCE_SINCE.remove(uuid);
        }
    }

    public static void clearServerState(UUID uuid) {
        if (uuid == null) {
            return;
        }
        SERVER_ACTIVITY.remove(uuid);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now < nextCleanupAtMs) {
            return;
        }
        nextCleanupAtMs = now + 2_000L;
        LOCAL_ACTIVITY.entrySet().removeIf(entry -> now - entry.getValue() > staleMs);
        LOCAL_SILENCE_SINCE.entrySet().removeIf(entry -> now - entry.getValue() > staleMs);
        long keepServerStateMs = Math.max(serverPacketTimeoutMs * 4L, serverSpeakingStaleMs * 4L);
        SERVER_ACTIVITY.entrySet().removeIf(entry -> now - entry.getValue().receivedAtMs() > keepServerStateMs);
    }

    public static void clear() {
        LOCAL_ACTIVITY.clear();
        LOCAL_SILENCE_SINCE.clear();
        SERVER_ACTIVITY.clear();
    }

    private static boolean hasVoice(short[] samples) {
        int threshold = amplitudeThreshold;
        int softThreshold = Math.max(8, threshold / 6);
        int mediumThreshold = Math.max(12, threshold / 3);
        long sum = 0L;
        int mediumHits = 0;
        for (short sample : samples) {
            int value = sample == Short.MIN_VALUE ? 32_768 : Math.abs(sample);
            sum += value;
            if (value >= threshold) {
                return true;
            }
            if (value >= mediumThreshold) {
                mediumHits++;
            }
        }
        if (samples.length == 0) {
            return false;
        }
        long average = sum / samples.length;
        if (average >= softThreshold) {
            return true;
        }
        int requiredHits = Math.max(3, samples.length / 40);
        return mediumHits >= requiredHits;
    }

    private record ServerVoiceState(boolean speaking, int sequence, long sentAtMs, long receivedAtMs) {
    }
}
