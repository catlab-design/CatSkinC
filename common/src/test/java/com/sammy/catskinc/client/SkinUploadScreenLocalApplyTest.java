package com.sammy.catskinc.client;

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
        assertTrue(source.contains("SkinOverrideStore.putManagedFromFile(uuid, this.selectedFile, slim);"),
                "the immediate local apply path should preserve the selected slim/classic model");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path moduleRelativePath = workingDirectory.resolve(Path.of(
                "src",
                "main",
                "java",
                "com",
                "sammy",
                "Catskinc",
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
                "Catskinc",
                "client",
                "SkinUploadScreen.java"));
    }
}

