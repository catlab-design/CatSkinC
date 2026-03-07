package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class IdentifiersTest {
    @Test
    void modBuildsValidIdentifierForSoundPath() {
        assertEquals("catskinc-remake:ui.upload", Identifiers.mod("ui.upload").toString());
    }
}
