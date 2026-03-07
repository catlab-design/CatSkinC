package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewPlayerEntityTest {
    @Test
    void overridesNameRenderingForPreviewPlayers() throws Exception {
        assertEquals(
                PreviewPlayerEntity.class,
                PreviewPlayerEntity.class.getDeclaredMethod("shouldRenderName").getDeclaringClass()
        );
    }
}
