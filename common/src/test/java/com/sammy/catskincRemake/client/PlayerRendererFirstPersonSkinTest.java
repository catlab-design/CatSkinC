package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRendererFirstPersonSkinTest {
    @Test
    void firstPersonHandRenderingUsesTheSameResolvedSkinOverridePath() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("method = \"renderHand("),
                "player renderer mixin should patch the first-person hand render path");
        assertTrue(source.contains("method = \"renderRightHand("),
                "player renderer mixin should patch the public right-hand entry point");
        assertTrue(source.contains("method = \"renderLeftHand("),
                "player renderer mixin should patch the public left-hand entry point");
        assertTrue(source.contains("AbstractClientPlayer;getSkin()Lnet/minecraft/client/resources/PlayerSkin;")
                        || source.contains("PlayerSkin;texture()Lnet/minecraft/resources/ResourceLocation;"),
                "first-person hand rendering should intercept the skin or texture lookup used by renderHand");
        assertTrue(source.contains("PlayerSkinOverrideResolver.resolvePlayerSkin(")
                        || source.contains("PlayerSkinOverrideResolver.resolveTexture("),
                "first-person hands should resolve overrides through the shared skin resolver");
        assertTrue(source.contains("PlayerSkin;texture()Lnet/minecraft/resources/ResourceLocation;"),
                "first-person hand rendering should also intercept the PlayerSkin texture lookup directly");
        assertTrue(source.contains("RenderType.entitySolid("),
                "player renderer mixin should be able to render the arm directly with the override texture");
        assertTrue(!source.contains("@Shadow\n    public abstract PlayerModel<AbstractClientPlayer> getModel();"),
                "player renderer mixin must not shadow inherited getModel() because it crashes mixin application at runtime");
        assertTrue(source.contains("((PlayerRenderer) (Object) this).getModel()")
                        || source.contains("((PlayerRenderer)(Object)this).getModel()"),
                "player renderer mixin should read the inherited model through the concrete renderer instance instead");
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
                "PlayerRendererMixin.java"));
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
                "PlayerRendererMixin.java"));
    }
}
