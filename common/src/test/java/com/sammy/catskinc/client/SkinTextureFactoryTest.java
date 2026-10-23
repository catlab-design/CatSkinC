package com.sammy.catskinc.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void withTextureAndModelAppliesWideModel() {
        PlayerSkin baseSkin = DefaultPlayerSkin.get(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        PlayerSkin slimBase = new PlayerSkin(baseSkin.texture(), baseSkin.textureUrl(), baseSkin.capeTexture(),
                baseSkin.elytraTexture(), PlayerSkin.Model.SLIM, baseSkin.secure());

        Object patched = SkinTextureFactory.withTextureAndModel(slimBase, Identifiers.mod("test/override"), false);

        assertEquals(PlayerSkin.Model.WIDE, assertInstanceOf(PlayerSkin.class, patched).model());
    }

    @Test
    void withTextureAndModelKeepsBaseModelWhenSlimUnknown() {
        PlayerSkin baseSkin = DefaultPlayerSkin.get(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        Object patched = SkinTextureFactory.withTextureAndModel(baseSkin, Identifiers.mod("test/override"), null);

        assertEquals(baseSkin.model(), assertInstanceOf(PlayerSkin.class, patched).model());
    }

    @Test
    void withTextureAndModelReturnsInputForNonPlayerSkin() {
        Object notASkin = new Object();
        assertSame(notASkin, SkinTextureFactory.withTextureAndModel(notASkin, Identifiers.mod("test/override"), true));
    }

    /**
     * Tests run on Mojang names, so a reflective lookup like getMethod("textureUrl") passes here but
     * fails in production Fabric (intermediary names), silently keeping the vanilla arm model.
     */
    @Test
    void doesNotLookUpMinecraftMembersByName() throws IOException {
        String source = Files.readString(sourcePath());
        assertFalse(source.contains("getMethod(") || source.contains("getDeclaredConstructors(")
                        || source.contains("getEnumConstants("),
                "SkinTextureFactory must not use name-based reflection on Minecraft types");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path moduleRelative = workingDirectory.resolve(Path.of(
                "src", "main", "java", "com", "sammy", "catskinc", "client",
                "SkinTextureFactory.java"));
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return workingDirectory.resolve(Path.of(
                "common", "src", "main", "java", "com", "sammy", "catskinc", "client",
                "SkinTextureFactory.java"));
    }
}
