package com.sammy.catskincRemake.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryEntityRendererCompatTest {
    @Test
    void doesNotDependOnReflectedInventoryMethods() {
        boolean usesReflectedMethodFields = Arrays.stream(InventoryEntityRendererCompat.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(Method.class::equals);

        assertFalse(usesReflectedMethodFields, "preview rendering should call InventoryScreen directly");
    }

    @Test
    void layoutKeepsFullPreviewBoundsAndUsesNegativeYOffsetToRemoveHeadGap() {
        InventoryEntityRendererCompat.PreviewLayout layout =
                InventoryEntityRendererCompat.layoutForBounds(12, 24, 132, 224);

        assertEquals(12, layout.x1());
        assertEquals(24, layout.y1());
        assertEquals(132, layout.x2());
        assertEquals(224, layout.y2());
        assertEquals(42, layout.size());
        assertTrue(layout.yOffset() < 0.0F, "preview should shift upward instead of reserving space above the head");
    }
}
