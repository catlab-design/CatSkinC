package com.sammy.catskincRemake.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class PlayerHeadRendererCompat {
    private static final float HEAD_PITCH_DEGREES = 0.0F;
    private static final float HEAD_YAW_DEGREES = 192.0F;
    private static final long HEAD_SPIN_PERIOD_MS = 4000L;
    private static final float HEAD_MODEL_SCALE_FACTOR = 1.4F;
    private static final float HEAD_BASELINE_FACTOR = 0.92F;
    private static final float HEAD_X_FLIP = -1.0F;
    private static final float HEAD_Y_FLIP = 1.0F;
    private static final float HEAD_Z_DEPTH = 150.0F;
    private static SkullModel headModel;

    private PlayerHeadRendererCompat() {
    }

    public static void drawHead(GuiGraphics context, ResourceLocation texture, int x, int y, int size) {
        if (context == null || texture == null || size <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        SkullModel model = headModel(minecraft);
        HeadLayout layout = layoutForBounds(x, y, size, Util.getMillis());
        PoseStack poseStack = context.pose();

        context.flush();
        poseStack.pushPose();
        try {
            poseStack.translate(layout.centerX(), layout.baseY(), HEAD_Z_DEPTH);
            poseStack.scale(layout.modelScale() * layout.xFlip(), layout.modelScale() * layout.yFlip(), layout.modelScale());
            model.setupAnim(0.0F, layout.yawDegrees(), layout.pitchDegrees());

            Lighting.setupForEntityInInventory(Axis.XP.rotationDegrees(layout.pitchDegrees()));
            var vertexConsumer = context.bufferSource().getBuffer(model.renderType(texture));
            model.renderToBuffer(poseStack, vertexConsumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            context.flush();
        } finally {
            Lighting.setupFor3DItems();
            poseStack.popPose();
        }
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

    private static SkullModel headModel(Minecraft minecraft) {
        if (headModel == null) {
            headModel = new SkullModel(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_HEAD));
        }
        return headModel;
    }

    static record HeadLayout(int x, int y, int size, float centerX, float baseY, float modelScale, float pitchDegrees,
                             float yawDegrees, float xFlip, float yFlip) {
    }
}
