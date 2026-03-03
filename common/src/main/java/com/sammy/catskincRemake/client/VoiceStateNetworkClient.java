package com.sammy.catskincRemake.client;

import com.sammy.catskincRemake.voice.VoiceStateChannel;
import dev.architectury.networking.NetworkManager;

import java.util.UUID;

public final class VoiceStateNetworkClient {
    private static volatile boolean initialized;

    private VoiceStateNetworkClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NetworkManager.registerReceiver(NetworkManager.s2c(), VoiceStateChannel.VOICE_STATE_ID, (buf, context) -> {
            UUID uuid = buf.readUuid();
            boolean speaking = buf.readBoolean();
            int sequence = buf.readVarInt();
            long sentAtMs = buf.readLong();
            context.queue(() -> VoiceActivityTracker.markServerState(uuid, speaking, sequence, sentAtMs));
        });
        ModLog.info("Registered server voice state receiver");
    }
}
