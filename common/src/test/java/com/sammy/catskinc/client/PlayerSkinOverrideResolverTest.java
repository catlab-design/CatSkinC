package com.sammy.catskinc.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerSkinOverrideResolverTest {
    private static final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174111");

    @AfterEach
    void tearDown() {
        SkinOverrideStore.clear(TEST_UUID);
        SkinManagerClient.resetForTesting();
    }

    @Test
    void resolvesTextureAndModelFromTheOverrideStore() {
        PlayerSkin baseSkin = DefaultPlayerSkin.get(TEST_UUID);
        Identifier overrideTexture = Identifiers.mod("test/first-person");
        SkinOverrideStore.put(TEST_UUID, overrideTexture, true);

        PlayerSkin resolved = PlayerSkinOverrideResolver.resolvePlayerSkin(TEST_UUID, baseSkin);

        assertEquals(overrideTexture, PlayerSkinOverrideResolver.resolveTexture(TEST_UUID));
        assertEquals(overrideTexture, resolved.body().texturePath());
        assertEquals(PlayerModelType.SLIM, resolved.model());
    }
}


