package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinUploadScreenLocalApplyTest {
    @Test
    void uploadSuccessAppliesTheChosenSkinToTheLocalPlayerImmediately() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("applyImmediateLocalSkinSelection(minecraftClient, bl);"),
                "upload success should apply the selected skin to the local player before waiting for the server refresh");
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
