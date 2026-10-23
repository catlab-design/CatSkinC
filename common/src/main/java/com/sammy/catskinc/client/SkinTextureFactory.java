package com.sammy.catskinc.client;

import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds a patched {@link PlayerSkin} with a CatSkinC texture and model.
 *
 * <p>Uses direct calls rather than reflection: on Fabric, production runs on intermediary names
 * ({@code comp_1911}, {@code field_41122}, ...), so looking up {@code "textureUrl"} or matching
 * the {@code "SLIM"} enum constant by name fails there and the vanilla model was silently kept.
 * Direct calls are remapped by Loom for every loader.
 */
public final class SkinTextureFactory {
    private SkinTextureFactory() {
    }

    public static Object withTextureAndModel(Object baseSkinTextures, ResourceLocation texture, Boolean slim) {
        if (!(baseSkinTextures instanceof PlayerSkin base) || texture == null) {
            return baseSkinTextures;
        }
        PlayerSkin.Model model = slim == null
                ? base.model()
                : (slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE);
        return new PlayerSkin(texture, base.textureUrl(), base.capeTexture(), base.elytraTexture(),
                model, base.secure());
    }
}
