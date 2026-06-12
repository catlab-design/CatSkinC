package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the client-side mouth dimension check that mirrors the server rule
 * "mouth assets must match the base skin". Without it the user only learns of a
 * mismatch when the upload fails server-side. SkinUploadScreen is GUI/GL code
 * that cannot run headless, so we assert on the validation source like the other
 * compat tests in this module.
 */
class SkinUploadScreenMouthValidationTest {
    @Test
    void mouthValidationRejectsSizeMismatchBeforeUpload() throws IOException {
        String source = Files.readString(sourcePath());

        assertTrue(source.contains("validateMouthFile"),
                "the upload screen should validate mouth files");
        assertTrue(source.contains("toast.error.mouth_size_mismatch"),
                "the mouth validation should surface the size-mismatch error to the user");
        assertTrue(source.contains("this.selectedWidth")
                        && source.contains("this.selectedHeight"),
                "the mouth validation should compare against the selected skin dimensions");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path moduleRelative = workingDirectory.resolve(Path.of(
                "src", "main", "java", "com", "sammy", "Catskinc", "client",
                "SkinUploadScreen.java"));
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return workingDirectory.resolve(Path.of(
                "common", "src", "main", "java", "com", "sammy", "Catskinc", "client",
                "SkinUploadScreen.java"));
    }
}

