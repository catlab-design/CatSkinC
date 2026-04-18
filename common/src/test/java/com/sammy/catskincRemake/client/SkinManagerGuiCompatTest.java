package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinManagerGuiCompatTest {
    @Test
    void playerSkinProviderLoadSkinIsHookedForGuiCompat() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("PlayerSkinProvider.class"),
                "GUI skin compatibility should hook the shared player skin provider");
        assertTrue(source.contains("loadSkin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/util/Identifier;"),
                "GUI skin compatibility should intercept the profile-based skin lookup");
        assertTrue(source.contains("SkinManagerClient.ensureFetch(uuid);"),
                "GUI skin compatibility should still trigger async CatSkin fetches");
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
