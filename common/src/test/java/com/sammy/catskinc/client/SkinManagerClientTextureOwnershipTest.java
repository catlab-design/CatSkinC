package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the texture-apply path in SkinManagerClient. When a cached skin is
 * replaced it must close the old image to avoid native memory leaks. GL types
 * can't run headless, so we assert on the source like the other compat tests.
 */
class SkinManagerClientTextureOwnershipTest {
    @Test
    void catchPathReleasesOnlyUnconsumedImages() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("closeQuietly(previousSkin)"),
                "the apply path should release the previous skin image when replaced");
        assertTrue(source.contains("closeQuietly(previousTalking)")
                        || source.contains("closeQuietly(removed)"),
                "the apply path should release the previous talking image when replaced or removed");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path moduleRelative = workingDirectory.resolve(Path.of(
                "src", "main", "java", "com", "sammy", "catskinc", "client",
                "SkinManagerClient.java"));
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return workingDirectory.resolve(Path.of(
                "common", "src", "main", "java", "com", "sammy", "catskinc", "client",
                "SkinManagerClient.java"));
    }
}
