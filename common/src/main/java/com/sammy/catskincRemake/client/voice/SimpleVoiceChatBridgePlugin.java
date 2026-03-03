package com.sammy.catskincRemake.client.voice;

import com.sammy.catskincRemake.client.ModLog;
import com.sammy.catskincRemake.client.VoiceActivityTracker;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

@ForgeVoicechatPlugin
public final class SimpleVoiceChatBridgePlugin implements VoicechatPlugin {
    private static final String PLUGIN_ID = "catskinc_remake";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, event -> {
            var currentUuid = VoiceActivityTracker.currentClientUuid();
            short[] rawAudio = event.getRawAudio();
            VoiceActivityTracker.markIfVoice(currentUuid, rawAudio);
            if (currentUuid != null && hasAudibleSamples(rawAudio)) {
                VoiceActivityTracker.markSpeaking(currentUuid);
            }
        });
        registration.registerEvent(ClientReceiveSoundEvent.class, event -> {
            short[] rawAudio = event.getRawAudio();
            VoiceActivityTracker.markIfVoice(event.getId(), rawAudio);
            if (event.getId() != null && hasAudibleSamples(rawAudio)) {
                VoiceActivityTracker.markSpeaking(event.getId());
            }
        });
        ModLog.info("Simple Voice Chat bridge registered");
    }

    private static boolean hasAudibleSamples(short[] samples) {
        if (samples == null || samples.length == 0) {
            return false;
        }
        for (short sample : samples) {
            int value = sample == Short.MIN_VALUE ? 32_768 : Math.abs(sample);
            if (value >= 8) {
                return true;
            }
        }
        return false;
    }
}
