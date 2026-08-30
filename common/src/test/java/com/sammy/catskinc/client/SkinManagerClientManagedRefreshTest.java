package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinManagerClientManagedRefreshTest {
    @Test
    void refreshGuardChecksManagedOverride() throws IOException {
        String source = Files.readString(sourcePath());
        // Verify the guard condition is present to avoid destroying managed textures on refresh
        assertTrue(source.contains("if (!SkinOverrideStore.isManaged(uuid))"),
                "refresh should skip restorePixels/destroyTextures when the skin is a managed override");
    }

    private static Path sourcePath() {
        Path workingDirectory = Path.of("").toAbsolutePath();
        // Try multiple possible locations for the source file
        Path[] possiblePaths = {
            workingDirectory.resolve(Path.of("common", "src", "main", "java", "com", "sammy", "catskinc", "client", "SkinManagerClient.java")),
            workingDirectory.resolve(Path.of("src", "main", "java", "com", "sammy", "catskinc", "client", "SkinManagerClient.java")),
        };
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new RuntimeException("Could not find SkinManagerClient.java");
    }
}
