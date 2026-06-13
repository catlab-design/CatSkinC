package com.sammy.catskinc.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class SkinTextureFactoryTest {
    @Test
    void withTextureAndModelOverridesTextureAndSlimModelOnPlayerSkin() {
        PlayerSkin baseSkin = DefaultPlayerSkin.get(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        Identifier overrideTexture = Identifiers.mod("test/override");

        Object patched = SkinTextureFactory.withTextureAndModel(baseSkin, overrideTexture, true);

        PlayerSkin patchedSkin = assertInstanceOf(PlayerSkin.class, patched);
        assertEquals(overrideTexture, patchedSkin.body().texturePath());
        assertEquals(PlayerModelType.SLIM, patchedSkin.model());
        assertEquals(baseSkin.cape(), patchedSkin.cape());
        assertEquals(baseSkin.elytra(), patchedSkin.elytra());
        assertEquals(baseSkin.secure(), patchedSkin.secure());
    }
}


