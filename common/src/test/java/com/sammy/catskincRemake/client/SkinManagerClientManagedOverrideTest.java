package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinManagerClientManagedOverrideTest {
    @Test
    void fetchedTexturesYieldBackToTheRemoteCacheAfterATemporaryLocalOverride() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("SkinOverrideStore.isManaged(uuid)"),
                "the skin manager should detect temporary managed overrides after a real cache update lands");
        assertTrue(source.contains("SkinOverrideStore.clear(uuid);"),
                "the skin manager should clear temporary managed overrides once cached textures are ready");
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
                "SkinManagerClient.java"));
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
                "SkinManagerClient.java"));
    }
}
