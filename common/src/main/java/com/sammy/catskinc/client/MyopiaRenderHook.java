package com.sammy.catskinc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

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
    public static ResourceLocation resolveWorldTexture(AbstractClientPlayer player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUUID();
        MyopiaLod.Level level = levelFor(player, uuid);
        return PlayerSkinOverrideResolver.resolveTexture(uuid, level);
    }

    static MyopiaLod.Level levelFor(AbstractClientPlayer player, UUID uuid) {
        ModConfig config = ModConfig.get();
        if (!config.isMyopiaEnabled() && !(CatskincClient.isDebugPreviewEnabled() && config.isDebugForceMyopia())) {
            return MyopiaLod.Level.L0;
        }
        if (!SkinManagerClient.hasManagedSkin(uuid)) {
            return MyopiaLod.Level.L0;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.player == player || client.player.getUUID().equals(uuid)) {
            return MyopiaLod.Level.L0;
        }
        double distanceSq = player.distanceToSqr(client.player);
        return MyopiaLodTracker.get().levelForSquared(uuid, distanceSq, config.getMyopiaDistance(), config.getMyopiaMode());
    }

    /** Called when the Myopia settings are applied so levels re-evaluate immediately. */
    public static void onSettingsApplied() {
        MyopiaLodTracker.get().reset();
        if (!ModConfig.get().isMyopiaEnabled()
                && !(CatskincClient.isDebugPreviewEnabled() && ModConfig.get().isDebugForceMyopia())) {
            MyopiaTextureCache.clearAll();
        }
    }
}