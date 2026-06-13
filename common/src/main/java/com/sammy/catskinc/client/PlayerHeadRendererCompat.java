package com.sammy.catskinc.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.resources.Identifier;

public final class PlayerHeadRendererCompat {
    private static final float HEAD_PITCH_DEGREES = 0.0F;
    private static final float HEAD_YAW_DEGREES = 192.0F;
    private static final long HEAD_SPIN_PERIOD_MS = 4000L;
    private static final float HEAD_MODEL_SCALE_FACTOR = 1.4F;
    private static final float HEAD_BASELINE_FACTOR = 0.92F;
    private static final float HEAD_X_FLIP = -1.0F;
    private static final float HEAD_Y_FLIP = 1.0F;

    private PlayerHeadRendererCompat() {
    }

    public static void drawHead(GuiGraphicsExtractor context, Identifier texture, int x, int y, int size) {
        if (context == null || texture == null || size <= 0) {
            return;
        }
        PlayerFaceExtractor.extractRenderState(context, texture, x, y, size, true, false, -1);
    }

    static HeadLayout layoutForBounds(int x, int y, int size) {
        return layoutForBounds(x, y, size, 0L);
    }

    static HeadLayout layoutForBounds(int x, int y, int size, long animationTimeMs) {
        int clampedSize = Math.max(8, size);
        float modelScale = clampedSize * HEAD_MODEL_SCALE_FACTOR;
        float centerX = x + clampedSize / 2.0F;
        float baseY = y + clampedSize * HEAD_BASELINE_FACTOR;
        return new HeadLayout(x, y, clampedSize, centerX, baseY, modelScale, HEAD_PITCH_DEGREES, animatedYawDegrees(animationTimeMs), HEAD_X_FLIP, HEAD_Y_FLIP);
    }

    private static float animatedYawDegrees(long animationTimeMs) {
        long loopTimeMs = Math.floorMod(animationTimeMs, HEAD_SPIN_PERIOD_MS);
        float rotationDegrees = loopTimeMs * 360.0F / HEAD_SPIN_PERIOD_MS;
        float yawDegrees = (HEAD_YAW_DEGREES + rotationDegrees) % 360.0F;
        return yawDegrees < 0.0F ? yawDegrees + 360.0F : yawDegrees;
    }

    static record HeadLayout(int x, int y, int size, float centerX, float baseY, float modelScale, float pitchDegrees,
                             float yawDegrees, float xFlip, float yFlip) {
    }
}
