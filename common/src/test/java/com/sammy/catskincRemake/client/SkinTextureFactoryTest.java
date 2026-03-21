package com.sammy.catskincRemake.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class SkinTextureFactoryTest {
    @Test
    void withTextureAndModelOverridesTextureAndSlimModelOnPlayerSkin() {
        PlayerSkin baseSkin = DefaultPlayerSkin.get(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        ResourceLocation overrideTexture = Identifiers.mod("test/override");

        Object patched = SkinTextureFactory.withTextureAndModel(baseSkin, overrideTexture, true);

        PlayerSkin patchedSkin = assertInstanceOf(PlayerSkin.class, patched);
        assertEquals(overrideTexture, patchedSkin.texture());
        assertEquals(PlayerSkin.Model.SLIM, patchedSkin.model());
        assertEquals(baseSkin.textureUrl(), patchedSkin.textureUrl());
        assertEquals(baseSkin.capeTexture(), patchedSkin.capeTexture());
        assertEquals(baseSkin.elytraTexture(), patchedSkin.elytraTexture());
        assertEquals(baseSkin.secure(), patchedSkin.secure());
    }
}
