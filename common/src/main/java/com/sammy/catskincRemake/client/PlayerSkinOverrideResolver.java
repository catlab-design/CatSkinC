package com.sammy.catskincRemake.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class PlayerSkinOverrideResolver {
    private PlayerSkinOverrideResolver() {
    }

    public static ResourceLocation resolveTexture(UUID uuid) {
        ResolvedOverride override = resolveOverride(uuid);
        return override == null ? null : override.texture();
    }

    public static PlayerSkin resolvePlayerSkin(UUID uuid, PlayerSkin baseSkin) {
        ResolvedOverride override = resolveOverride(uuid);
        if (override == null || override.texture() == null) {
            return baseSkin;
        }

        PlayerSkin resolvedBase = baseSkin != null ? baseSkin : DefaultPlayerSkin.get(uuid);
        Object patched = SkinTextureFactory.withTextureAndModel(resolvedBase, override.texture(), override.slim());
        return patched instanceof PlayerSkin patchedSkin ? patchedSkin : resolvedBase;
    }

    private static ResolvedOverride resolveOverride(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        SkinOverrideStore.Entry entry = SkinOverrideStore.get(uuid);
        if (entry != null && entry.texture != null) {
            return new ResolvedOverride(entry.texture, entry.slim);
        }

        ResourceLocation cached = SkinManagerClient.getCached(uuid);
        if (cached != null) {
            return new ResolvedOverride(cached, SkinManagerClient.isSlimOrNull(uuid));
        }

        SkinManagerClient.ensureFetch(uuid);
        return null;
    }

    private record ResolvedOverride(ResourceLocation texture, Boolean slim) {
    }
}
