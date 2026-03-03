package com.sammy.catskincRemake.client.voice;

import com.sammy.catskincRemake.client.ModLog;
import com.sammy.catskincRemake.client.VoiceActivityTracker;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.source.ClientAudioSource;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.client.event.audio.source.AudioSourceWriteEvent;
import su.plo.voice.api.client.event.render.HudActivationRenderEvent;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;
import su.plo.voice.proto.data.player.VoicePlayerInfo;

import java.util.UUID;

@Addon(id = "catskinc_remake", name = "CatSkinC Remake Voice Bridge", scope = AddonLoaderScope.CLIENT, version = "2.0.0", authors = {
        "Q Team Studio" })
public final class PlasmoVoiceBridgeAddon implements AddonInitializer {
    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;
    private volatile long lastAudibleCaptureAtMs;
    private volatile long lastActiveActivationAtMs;

    @Override
    public void onAddonInitialize() {
        if (voiceClient == null) {
            ModLog.warn("Plasmo Voice bridge started without injected client instance");
            return;
        }
        voiceClient.getEventBus().register(this, this);
        voiceClient.getEventBus().register(
                this, AudioCaptureProcessedEvent.class, EventPriority.NORMAL, this::onAudioCaptureProcessed);
        voiceClient.getEventBus().register(
                this, AudioSourceWriteEvent.class, EventPriority.NORMAL, this::onAudioSourceWrite);
        voiceClient.getEventBus().register(
                this, HudActivationRenderEvent.class, EventPriority.NORMAL, this::onHudActivationRender);
        ModLog.info("Plasmo Voice bridge registered");
    }

    @Override
    public void onAddonShutdown() {
        if (voiceClient != null) {
            voiceClient.getEventBus().unregister(this);
        }
    }

    public boolean isSpeakingNow() {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (hasActiveActivation(client)) {
            lastActiveActivationAtMs = now;
            return true;
        }
        if (now - lastActiveActivationAtMs <= 80L || now - lastAudibleCaptureAtMs <= 80L) {
            return true;
        }
        return false;
    }

    @EventSubscribe
    public void onAudioCaptureProcessed(AudioCaptureProcessedEvent event) {
        if (event == null) {
            return;
        }
        UUID currentUuid = VoiceActivityTracker.currentClientUuid();
        short[] mono = event.getProcessed() == null ? null : event.getProcessed().getMono();
        short[] samples = mono != null ? mono : event.getRawSamples();
        if (currentUuid != null && hasActiveActivation(voiceClient)) {
            long now = System.currentTimeMillis();
            lastActiveActivationAtMs = now;
            if (hasAudibleSamples(samples)) {
                lastAudibleCaptureAtMs = now;
            }
            VoiceActivityTracker.markSpeaking(currentUuid);
        }
    }

    @EventSubscribe
    public void onAudioSourceWrite(AudioSourceWriteEvent event) {
        if (event == null) {
            return;
        }
        UUID speaker = resolveSpeakerUuid(event.getSource());
        short[] samples = event.getSamples();
        VoiceActivityTracker.markIfVoice(speaker, samples);
        if (speaker != null && hasAudibleSamples(samples)) {
            VoiceActivityTracker.markSpeaking(speaker);
        }
    }

    @EventSubscribe
    public void onHudActivationRender(HudActivationRenderEvent event) {
        if (event == null) {
            return;
        }
        ClientActivation activation = event.getActivation();
        if (!isActivationSpeaking(activation)) {
            return;
        }
        lastActiveActivationAtMs = System.currentTimeMillis();
        VoiceActivityTracker.markSpeaking(VoiceActivityTracker.currentClientUuid());
    }

    private static UUID resolveSpeakerUuid(ClientAudioSource<?> source) {
        if (source == null) {
            return null;
        }
        SourceInfo sourceInfo = source.getSourceInfo();
        if (sourceInfo instanceof PlayerSourceInfo playerSourceInfo) {
            VoicePlayerInfo playerInfo = playerSourceInfo.getPlayerInfo();
            return playerInfo == null ? null : playerInfo.getPlayerId();
        }
        return null;
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

    private static boolean hasActiveActivation(PlasmoVoiceClient client) {
        try {
            var manager = client.getActivationManager();
            if (manager == null) {
                return false;
            }
            var parent = manager.getParentActivation();
            if (parent.isPresent() && isActivationSpeaking(parent.get())) {
                return true;
            }
            for (ClientActivation activation : manager.getActivations()) {
                if (isActivationSpeaking(activation)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isActivationSpeaking(ClientActivation activation) {
        if (activation == null || activation.isDisabled()) {
            return false;
        }
        if (activation.isActive()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - activation.getLastActivation() <= 100L) {
            return true;
        }
        try {
            if (activation.getType() == ClientActivation.Type.PUSH_TO_TALK
                    && activation.getPttKey() != null
                    && activation.getPttKey().isPressed()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
