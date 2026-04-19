package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinManagerGuiCompatTest {
    @Test
    void skinManagerLookupInsecureIsHookedForGuiCompat() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("SkinManager.class"),
                "GUI skin compatibility should hook the shared skin manager");
        assertTrue(source.contains("lookupInsecure(Lcom/mojang/authlib/GameProfile;)Ljava/util/function/Supplier;"),
                "GUI skin compatibility should intercept the insecure profile lookup");
        assertTrue(source.contains("PlayerSkinOverrideResolver.resolvePlayerSkin("),
                "GUI skin compatibility should resolve CatSkin overrides through the shared player skin resolver");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path moduleRelativePath = workingDirectory.resolve(Path.of(
                "src",
                "main",
                "java",
                "com",
                "sammy",
                "catskincRemake",
                "mixin",
                "client",
                "SkinManagerMixin.java"));
        if (Files.exists(moduleRelativePath)) {
            return moduleRelativePath;
        }
        return workingDirectory.resolve(Path.of(
                "common",
                "src",
                "main",
                "java",
                "com",
                "sammy",
                "catskincRemake",
                "mixin",
                "client",
                "SkinManagerMixin.java"));
    }
}
