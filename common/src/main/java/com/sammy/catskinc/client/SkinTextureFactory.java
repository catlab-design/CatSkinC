package com.sammy.catskinc.client;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.core.ClientAsset;

public final class SkinTextureFactory {
    private SkinTextureFactory() {
    }

    public static Object withTextureAndModel(Object baseSkinTextures, Identifier texture, Boolean slim) {
        if (baseSkinTextures == null || texture == null) {
            return baseSkinTextures;
        }
        if (!(baseSkinTextures instanceof PlayerSkin baseSkin)) {
            return baseSkinTextures;
        }

        PlayerModelType model = baseSkin.model();
        if (slim != null) {
            model = slim ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        }

        ClientAsset.Texture newBody = new ClientAsset.ResourceTexture(texture, texture);
        return new PlayerSkin(
            newBody,
            baseSkin.cape(),
            baseSkin.elytra(),
            model,
            baseSkin.secure()
        );
    }
}
