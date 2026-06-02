package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for the Figura compatibility probe. The reflective Figura lookup is not exercised
 * here (Figura is not on the test classpath); instead the injectable test probe verifies the
 * yield contract and the safe-default behaviour.
 */
class FiguraCompatTest {
    private static final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174333");

    @AfterEach
    void tearDown() {
        FiguraCompat.resetForTesting();
    }

    @Test
    void defaultsToFalseWithoutFigura() {
        assertFalse(FiguraCompat.hasActiveAvatar(TEST_UUID));
        assertFalse(FiguraCompat.hasActiveAvatar(null));
    }

    @Test
    void honoursTestProbe() {
        FiguraCompat.setAvatarProbeForTesting(uuid -> uuid.equals(TEST_UUID));
        assertTrue(FiguraCompat.hasActiveAvatar(TEST_UUID));
        assertFalse(FiguraCompat.hasActiveAvatar(UUID.randomUUID()));
        assertFalse(FiguraCompat.hasActiveAvatar(null), "null uuid must never be treated as active");
    }
}
