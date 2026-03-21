package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinUploadScreenHistoryPreviewTest {
    @Test
    void usesThreeDimensionalHeadPreviewWhenFullSkinTextureIsAvailable() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("PlayerHeadRendererCompat.drawHead(drawContext, historyEntry.previewSkinId"),
                "history list should call the 3d head renderer when a full skin texture is present");
        assertTrue(source.contains("historyEntry.previewSkinId ="),
                "history entries should keep a full skin texture for 3d previews");
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
                "client",
                "SkinUploadScreen.java"));
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
                "client",
                "SkinUploadScreen.java"));
    }
}
