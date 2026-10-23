package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a player switches to a skin of a different size (e.g. 64x64 -> HD), injectPixels must
 * recreate CatSkinC's own per-UUID texture rather than squash the new skin into the old one.
 * GL types can't run headless, so we assert on the source like the other compat tests.
 */
class SkinManagerClientOwnedTextureResizeTest {
    @Test
    void ownedTextureIsRecreatedWhenSkinSizeChanges() throws IOException {
        String source = Files.readString(sourcePath());
        int start = source.indexOf("public static void injectPixels(");
        int end = source.indexOf("private static String computeImageHash(", start);
        String body = source.substring(start, end);

        assertTrue(body.contains("boolean ownedSizeMismatch = FALLBACK_TEXTURES.contains(uuid)"),
                "injectPixels should detect a size change on its own per-UUID texture");
        assertTrue(body.contains("&& !ownedSizeMismatch) {"),
                "a size mismatch must skip the in-place write and fall through to recreation");
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
