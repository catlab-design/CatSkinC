package com.sammy.catskincRemake.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression coverage for the Figura compatibility yield.
 *
 * <p>When a player has an active Figura avatar, CatSkinC must perform no skin/model override at
 * all — otherwise (on 1.21.1) the shared {@code getSkin()} chokepoint leaves the player with the
 * CatSkinC texture but the wrong arm model and overwritten Figura parts. When no avatar is active,
 * the normal slim override must still apply.
 */
class FiguraCompatYieldTest {
    private static final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174222");

    @AfterEach
    void tearDown() {
        FiguraCompat.resetForTesting();
        SkinOverrideStore.clear(TEST_UUID);
        SkinManagerClient.resetForTesting();
    }

    @Test
    void yieldsToFiguraWhenAvatarActive() {
        PlayerSkin baseSkin = DefaultPlayerSkin.get(TEST_UUID);
        ResourceLocation overrideTexture = Identifiers.mod("test/figura-yield");
        // A slim CatSkinC override is registered for this player...
        SkinOverrideStore.put(TEST_UUID, overrideTexture, true);
        // ...but Figura reports an active avatar, so CatSkinC must step aside.
        FiguraCompat.setAvatarProbeForTesting(uuid -> uuid.equals(TEST_UUID));

        assertNull(PlayerSkinOverrideResolver.resolveTexture(TEST_UUID),
                "no texture override should be reported while a Figura avatar is active");
        PlayerSkin resolved = PlayerSkinOverrideResolver.resolvePlayerSkin(TEST_UUID, baseSkin);
        assertSame(baseSkin, resolved,
                "the untouched base skin (Figura's) must be returned while an avatar is active");
    }

    @Test
    void appliesSlimOverrideWhenNoFiguraAvatar() {
        PlayerSkin baseSkin = DefaultPlayerSkin.get(TEST_UUID);
        ResourceLocation overrideTexture = Identifiers.mod("test/figura-absent");
        SkinOverrideStore.put(TEST_UUID, overrideTexture, true);
        // Figura present but this player has no active avatar.
        FiguraCompat.setAvatarProbeForTesting(uuid -> false);

        assertEquals(overrideTexture, PlayerSkinOverrideResolver.resolveTexture(TEST_UUID),
                "the CatSkinC texture override should apply when no Figura avatar is active");
        PlayerSkin resolved = PlayerSkinOverrideResolver.resolvePlayerSkin(TEST_UUID, baseSkin);
        assertEquals(overrideTexture, resolved.texture());
        assertEquals(PlayerSkin.Model.SLIM, resolved.model(),
                "slim model must still be applied when CatSkinC is not yielding to Figura");
    }

    @Test
    void probeDefaultsToFalseWithoutFigura() {
        // No probe set and Figura not loaded in the test runtime → never yields.
        assertEquals(false, FiguraCompat.hasActiveAvatar(TEST_UUID));
        assertEquals(false, FiguraCompat.hasActiveAvatar(null));
    }
}
