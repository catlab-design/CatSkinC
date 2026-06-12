package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the texture-apply error path in SkinManagerClient. On exception it must
 * release only images not yet handed to a texture (which takes ownership), so it
 * neither leaks the merged idle/talking images nor double-frees the mouth
 * originals already closed by createOverlayImage. GL types can't run headless, so
 * we assert on the source like the other compat tests.
 */
class SkinManagerClientTextureOwnershipTest {
    @Test
    void catchPathReleasesOnlyUnconsumedImages() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("skinConsumed")
                        && source.contains("talkingConsumed"),
                "the apply path should track which images a texture took ownership of");
        assertTrue(source.contains("if (!skinConsumed)")
                        && source.contains("if (!talkingConsumed)"),
                "the catch block should release only images not yet consumed");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path moduleRelative = workingDirectory.resolve(Path.of(
                "src", "main", "java", "com", "sammy", "Catskinc", "client",
                "SkinManagerClient.java"));
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return workingDirectory.resolve(Path.of(
                "common", "src", "main", "java", "com", "sammy", "Catskinc", "client",
                "SkinManagerClient.java"));
    }
}

