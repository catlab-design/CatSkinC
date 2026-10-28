package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Entry point used by the world player renderer to pick a distance-appropriate skin texture.
 * Everything outside the world renderer (tab list, GUIs, inventory) stays at full resolution.
 */
public final class MyopiaRenderHook {
    private MyopiaRenderHook() {
    }

    /**
     * Resolves the texture to render {@code player} with, applying Myopia LOD when enabled.
     * Returns {@code null} when there is no CatSkinC override for this player.
     */
    public static Identifier resolveWorldTexture(AbstractClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUuid();
        double distanceSq = distanceSqTo(player, uuid);
        MyopiaLod.Level level = levelFor(uuid, distanceSq);
        return PlayerSkinOverrideResolver.resolveTexture(uuid, level, distanceSq);
    }

    /** Squared distance to the local player; 0 for the local player itself, NaN when unknown. */
    static double distanceSqTo(AbstractClientPlayerEntity player, UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Double.NaN;
        }
        if (client.player == player || client.player.getUuid().equals(uuid)) {
            return 0.0;
        }
        return player.squaredDistanceTo(client.player);
    }

    static MyopiaLod.Level levelFor(UUID uuid, double distanceSq) {
        ModConfig config = ModConfig.get();
        // AUTO texture quality always follows distance, using the Myopia range and mode,
        // even when Myopia itself is switched off.
        boolean distanceAware = config.isMyopiaEnabled()
                || config.getTextureQuality().losslessWhenNear()
                || (CatskincClient.isDebugPreviewEnabled() && config.isDebugForceMyopia());
        if (!distanceAware || Double.isNaN(distanceSq) || distanceSq == 0.0) {
            return MyopiaLod.Level.L0;
        }
        if (!SkinManagerClient.hasManagedSkin(uuid)) {
            return MyopiaLod.Level.L0;
        }
        return MyopiaLodTracker.get().levelForSquared(uuid, distanceSq, config.getMyopiaDistance(), config.getMyopiaMode());
    }

    /** Called when the Myopia settings are applied so levels re-evaluate immediately. */
    public static void onSettingsApplied() {
        MyopiaLodTracker.get().reset();
        if (!ModConfig.get().isMyopiaEnabled()
                && !ModConfig.get().getTextureQuality().losslessWhenNear()
                && !(CatskincClient.isDebugPreviewEnabled() && ModConfig.get().isDebugForceMyopia())) {
            MyopiaTextureCache.clearAll();
        }
    }
}
