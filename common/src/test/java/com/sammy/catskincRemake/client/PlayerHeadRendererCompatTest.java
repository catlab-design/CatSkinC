package com.sammy.catskincRemake.client;

import net.minecraft.client.model.PlayerModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHeadRendererCompatTest {
    @Test
    void layoutFacesTheScreenWithoutTiltingUpward() {
        PlayerHeadRendererCompat.HeadLayout layout = PlayerHeadRendererCompat.layoutForBounds(14, 22, 28);

        assertEquals(14, layout.x());
        assertEquals(22, layout.y());
        assertEquals(28, layout.size());
        assertEquals(39.2F, layout.modelScale(), 0.0001F);
        assertTrue(layout.modelScale() > layout.size(), "head preview scale must account for the model being rendered in 1/16th block units");
        assertTrue(layout.modelScale() <= layout.size() * 1.4143F, "spinning head preview should stay narrow enough to fit the thumbnail at diagonal angles");
        assertEquals(0.0F, layout.pitchDegrees(), 0.0001F);
        assertEquals(192.0F, layout.yawDegrees(), 0.0001F);
        assertTrue(layout.yawDegrees() > 180.0F, "head preview should rotate the skull model back toward the camera");
        assertEquals(47.76F, layout.baseY(), 0.0001F);
        assertTrue(layout.baseY() > layout.y() + layout.size() * 0.75F, "head origin should sit in the lower part of the slot so the full head stays visible");
        assertEquals(-1.0F, layout.xFlip(), 0.0001F);
        assertEquals(1.0F, layout.yFlip(), 0.0001F);
    }

    @Test
    void rotatesHeadPreviewContinuouslyOverTime() throws Exception {
        Method timedLayoutMethod = Arrays.stream(PlayerHeadRendererCompat.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("layoutForBounds"))
                .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class, int.class, int.class, long.class}))
                .findFirst()
                .orElse(null);

        assertTrue(timedLayoutMethod != null, "head preview should expose a timed layout method for animation");
        if (timedLayoutMethod == null) {
            return;
        }

        timedLayoutMethod.setAccessible(true);
        PlayerHeadRendererCompat.HeadLayout start = (PlayerHeadRendererCompat.HeadLayout)timedLayoutMethod.invoke(null, 14, 22, 28, 0L);
        PlayerHeadRendererCompat.HeadLayout quarterTurn = (PlayerHeadRendererCompat.HeadLayout)timedLayoutMethod.invoke(null, 14, 22, 28, 1000L);
        PlayerHeadRendererCompat.HeadLayout fullTurn = (PlayerHeadRendererCompat.HeadLayout)timedLayoutMethod.invoke(null, 14, 22, 28, 4000L);

        assertEquals(192.0F, start.yawDegrees(), 0.0001F);
        assertEquals(282.0F, quarterTurn.yawDegrees(), 0.0001F);
        assertEquals(192.0F, fullTurn.yawDegrees(), 0.0001F);
    }

    @Test
    void doesNotDependOnFullPlayerModelForHeadPreview() {
        boolean usesPlayerModel = Arrays.stream(PlayerHeadRendererCompat.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(PlayerModel.class::equals);

        assertFalse(usesPlayerModel, "head thumbnails should use a dedicated head model instead of a full player model");
    }
}
