package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the texture-apply error path in SkinManagerClient. On exception it must
 * release only images not yet handed to a DynamicTexture (which takes ownership),
 * so it neither leaks the merged idle/talking images nor double-frees the mouth
 * originals already closed by createOverlayImage. GL types can't run headless, so
 * we assert on the source like the other compat tests.
 */
class SkinManagerClientTextureOwnershipTest {
    @Test
    void catchPathReleasesOnlyUnconsumedImages() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("skinConsumed")
                        && source.contains("idleConsumed")
                        && source.contains("talkingConsumed"),
                "the apply path should track which images a DynamicTexture took ownership of");
        assertTrue(source.contains("if (!skinConsumed)")
                        && source.contains("if (!idleConsumed)")
                        && source.contains("if (!talkingConsumed)"),
                "the catch block should release only images not yet consumed");
        // The old buggy catch closed images.mouthOpenImage/mouthCloseImage again,
        // double-freeing what createOverlayImage already closed. Ensure that's gone.
        assertFalse(source.contains("closeQuietly(images.mouthOpenImage);\n                    closeQuietly(images.mouthCloseImage);\n                }\n            });"),
                "the catch block should not re-close the already-closed mouth originals");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path moduleRelative = workingDirectory.resolve(Path.of(
                "src", "main", "java", "com", "sammy", "catskincRemake", "client",
                "SkinManagerClient.java"));
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return workingDirectory.resolve(Path.of(
                "common", "src", "main", "java", "com", "sammy", "catskincRemake", "client",
                "SkinManagerClient.java"));
    }
}
