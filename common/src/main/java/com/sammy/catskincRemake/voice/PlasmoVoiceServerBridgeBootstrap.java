package com.sammy.catskincRemake.voice;

import com.sammy.catskincRemake.client.ModLog;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.platform.Platform;

import java.lang.reflect.Method;

public final class PlasmoVoiceServerBridgeBootstrap {
    private static volatile boolean initialized;
    private static volatile Object loadedAddon;

    private PlasmoVoiceServerBridgeBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        LifecycleEvent.SERVER_STARTED.register(server -> {
            VoiceStateChannel.bindServer(server);
            loadAddonIfPresent();
        });

        LifecycleEvent.SERVER_STOPPING.register(server -> {
            unloadAddonIfLoaded();
            VoiceStateChannel.unbindServer(server);
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (player != null) {
                VoiceStateChannel.broadcast(player.getUuid(), false);
            }
        });
    }

    private static synchronized void loadAddonIfPresent() {
        if (loadedAddon != null) {
            return;
        }
        if (!Platform.isModLoaded("plasmovoice") && !classExists("su.plo.voice.api.server.PlasmoVoiceServer")) {
            return;
        }
        try {
            Class<?> addonClass = Class.forName("com.sammy.catskincRemake.voice.PlasmoVoiceServerBridgeAddon");
            Object addon = addonClass.getDeclaredConstructor().newInstance();
            Class<?> serverApiClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
            Method getAddonsLoader = serverApiClass.getMethod("getAddonsLoader");
            Object addonsLoader = getAddonsLoader.invoke(null);
            invokeAddonLoader(addonsLoader, "load", addon);
            loadedAddon = addon;
            ModLog.info("Plasmo Voice server bridge loaded");
        } catch (Throwable throwable) {
            ModLog.warn("Plasmo Voice server bridge failed to load", throwable);
        }
    }

    private static synchronized void unloadAddonIfLoaded() {
        Object addon = loadedAddon;
        loadedAddon = null;
        if (addon == null) {
            return;
        }
        try {
            Class<?> serverApiClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
            Method getAddonsLoader = serverApiClass.getMethod("getAddonsLoader");
            Object addonsLoader = getAddonsLoader.invoke(null);
            invokeAddonLoader(addonsLoader, "unload", addon);
            ModLog.info("Plasmo Voice server bridge unloaded");
        } catch (Throwable throwable) {
            ModLog.trace("Failed to unload Plasmo Voice server bridge cleanly: {}", throwable.getMessage());
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
            Class.forName(className, false, PlasmoVoiceServerBridgeBootstrap.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
