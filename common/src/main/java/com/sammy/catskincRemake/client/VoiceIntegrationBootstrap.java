package com.sammy.catskincRemake.client;

import dev.architectury.platform.Platform;

import java.lang.reflect.Method;
import java.util.UUID;

public final class VoiceIntegrationBootstrap {
    private static volatile boolean initialized;
    private static volatile Object plasmoAddon;
    private static volatile Method plasmoSpeakingProbeMethod;
    private static volatile long nextCaptureLogAtMs;
    private static volatile long nextCapturePollAtMs;

    private VoiceIntegrationBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (Platform.isModLoaded("voicechat")) {
            ModLog.info("Simple Voice Chat detected; voice bridge entrypoint will be used");
        }

        if (Platform.isModLoaded("plasmovoice") || classExists("su.plo.voice.api.client.PlasmoVoiceClient")) {
            try {
                Class<?> addonClass = Class.forName(
                        "com.sammy.catskincRemake.client.voice.PlasmoVoiceBridgeAddon");
                Object addon = addonClass.getDeclaredConstructor().newInstance();
                Class<?> clientApiClass = Class.forName("su.plo.voice.api.client.PlasmoVoiceClient");
                Method getAddonsLoader = clientApiClass.getMethod("getAddonsLoader");
                Object addonsLoader = getAddonsLoader.invoke(null);
                invokeAddonLoader(addonsLoader, "load", addon);
                plasmoAddon = addon;
                ModLog.info("Plasmo Voice bridge loaded");
            } catch (Throwable throwable) {
                ModLog.warn("Plasmo Voice detected but bridge failed to initialize", throwable);
            }
        }

        if (Platform.isModLoaded("figura")) {
            ModLog.info("Figura detected; enabling compatibility mode");
        }
        if (Platform.isModLoaded("emotecraft")) {
            ModLog.info("Emotecraft detected; enabling compatibility mode");
        }
    }

    public static synchronized void shutdown() {
        VoiceActivityTracker.clear();
        Object addon = plasmoAddon;
        plasmoAddon = null;
        if (addon != null) {
            try {
                Class<?> clientApiClass = Class.forName("su.plo.voice.api.client.PlasmoVoiceClient");
                Method getAddonsLoader = clientApiClass.getMethod("getAddonsLoader");
                Object addonsLoader = getAddonsLoader.invoke(null);
                invokeAddonLoader(addonsLoader, "unload", addon);
            } catch (Throwable throwable) {
                ModLog.trace("Failed to unload Plasmo Voice bridge cleanly: {}", throwable.getMessage());
            }
        }
        plasmoSpeakingProbeMethod = null;
        nextCaptureLogAtMs = 0L;
        nextCapturePollAtMs = 0L;
        initialized = false;
    }

    public static void tick() {
        if (!initialized) {
            return;
        }
        UUID currentUuid = VoiceActivityTracker.currentClientUuid();
        if (currentUuid == null) {
            return;
        }
        Object addon = plasmoAddon;
        if (addon == null) {
            return;
        }
        if (isPlasmoSpeaking(addon)) {
            VoiceActivityTracker.markSpeaking(currentUuid);
        } else {
            VoiceActivityTracker.clearLocalSpeaking(currentUuid);
        }
    }

    private static boolean isPlasmoSpeaking(Object addon) {
        if (addon == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < nextCapturePollAtMs) {
            return false;
        }
        nextCapturePollAtMs = now + 50L;
        try {
            Method probeMethod = plasmoSpeakingProbeMethod;
            if (probeMethod == null || !probeMethod.getDeclaringClass().isInstance(addon)) {
                probeMethod = addon.getClass().getMethod("isSpeakingNow");
                plasmoSpeakingProbeMethod = probeMethod;
            }
            Object active = probeMethod.invoke(addon);
            return Boolean.TRUE.equals(active);
        } catch (Throwable throwable) {
            plasmoSpeakingProbeMethod = null;
            if (now >= nextCaptureLogAtMs) {
                nextCaptureLogAtMs = now + 10_000L;
                ModLog.trace("Plasmo Voice capture polling failed: {}", throwable.getMessage());
            }
            return false;
        }
    }

    private static void invokeAddonLoader(Object addonsLoader, String methodName, Object addon) throws Exception {
        Method fallback = null;
        for (Method method : addonsLoader.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType.isInstance(addon) || parameterType.isAssignableFrom(addon.getClass())) {
                method.invoke(addonsLoader, addon);
                return;
            }
            fallback = method;
        }
        if (fallback != null) {
            fallback.invoke(addonsLoader, addon);
            return;
        }
        throw new NoSuchMethodException("No " + methodName + "(addon) method found on " + addonsLoader.getClass());
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, VoiceIntegrationBootstrap.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
