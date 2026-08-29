package com.sammy.catskinc.client;

import net.minecraft.util.Identifier;

import java.util.UUID;

public final class PlayerSkinOverrideResolver {
    private PlayerSkinOverrideResolver() {
    }

    public static Identifier resolveTexture(UUID uuid) {
        ResolvedOverride override = resolveOverride(uuid);
        return override == null ? null : override.texture();
    }

    public static Object resolvePlayerSkin(UUID uuid, Object baseSkinTextures) {
        ResolvedOverride override = resolveOverride(uuid);
        if (override == null || override.texture() == null) {
            return baseSkinTextures;
        }

        Object resolvedBase = baseSkinTextures;
        Object patched = SkinTextureFactory.withTextureAndModel(resolvedBase, override.texture(), override.slim());
        return patched;
    }

    private static ResolvedOverride resolveOverride(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        SkinOverrideStore.Entry entry = SkinOverrideStore.get(uuid);
        if (entry != null && entry.texture != null) {
            return new ResolvedOverride(entry.texture, entry.slim);
        }

        Identifier cached = SkinManagerClient.getCached(uuid);
        if (cached != null) {
            return new ResolvedOverride(cached, SkinManagerClient.isSlimOrNull(uuid));
        }

        SkinManagerClient.ensureFetch(uuid);
        return null;
    }

    private record ResolvedOverride(Identifier texture, Boolean slim) {
    }
}