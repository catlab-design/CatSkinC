package com.sammy.catskincRemake.voice;

import com.sammy.catskincRemake.client.ModLog;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.connection.UdpClientDisconnectedEvent;
import su.plo.voice.api.server.player.VoicePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Addon(
        id = "catskinc_remake",
        name = "CatSkinC Remake Voice Bridge",
        scope = AddonLoaderScope.SERVER,
        version = "2.0.2",
        authors = { "Q Team Studio" }
)
public final class PlasmoVoiceServerBridgeAddon implements AddonInitializer {
    private static final long SPEAK_BROADCAST_INTERVAL_MS = 100L;

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;
    private final Map<UUID, Long> lastSpeakBroadcastAtMs = new ConcurrentHashMap<>();

    @Override
    public void onAddonInitialize() {
        lastSpeakBroadcastAtMs.clear();
        if (voiceServer == null) {
            ModLog.warn("Plasmo Voice server bridge loaded without injected server instance");
            return;
        }
        ModLog.info("Plasmo Voice server bridge initialized");
    }

    @Override
    public void onAddonShutdown() {
        for (UUID uuid : lastSpeakBroadcastAtMs.keySet()) {
            VoiceStateChannel.broadcast(uuid, false);
        }
        lastSpeakBroadcastAtMs.clear();
        ModLog.info("Plasmo Voice server bridge stopped");
    }

    @EventSubscribe(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerSpeak(@NotNull PlayerSpeakEvent event) {
        updateVoiceState(event.getPlayer(), true, false);
    }

    @EventSubscribe(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerSpeakEnd(@NotNull PlayerSpeakEndEvent event) {
        updateVoiceState(event.getPlayer(), false, true);
    }

    @EventSubscribe(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClientDisconnected(@NotNull UdpClientDisconnectedEvent event) {
        updateVoiceState(event.getConnection().getPlayer(), false, true);
    }

    private void updateVoiceState(VoicePlayer player, boolean speaking, boolean force) {
        UUID uuid = resolveUuid(player);
        if (uuid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (speaking) {
            if (!force) {
                long last = lastSpeakBroadcastAtMs.getOrDefault(uuid, 0L);
                if (now - last < SPEAK_BROADCAST_INTERVAL_MS) {
                    return;
                }
            }
            lastSpeakBroadcastAtMs.put(uuid, now);
        } else {
            lastSpeakBroadcastAtMs.remove(uuid);
        }
        VoiceStateChannel.broadcast(uuid, speaking);
    }

    private static UUID resolveUuid(VoicePlayer player) {
        if (player == null) {
            return null;
        }
        try {
            return player.getInstance().getUuid();
        } catch (Exception exception) {
            ModLog.trace("Failed to resolve voice player UUID: {}", exception.getMessage());
            return null;
        }
    }
}
