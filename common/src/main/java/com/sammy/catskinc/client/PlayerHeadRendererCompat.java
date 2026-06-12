package com.sammy.catskinc.client;

import net.minecraft.block.SkullBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Map;

public final class PlayerHeadRendererCompat {
    private static final float HEAD_PITCH_DEGREES = 0.0F;
    private static final float HEAD_YAW_DEGREES = 192.0F;
    private static final long HEAD_SPIN_PERIOD_MS = 4000L;
    private static final float HEAD_MODEL_SCALE_FACTOR = 1.4F;
    private static final float HEAD_BASELINE_FACTOR = 0.92F;
    private static final float HEAD_X_FLIP = -1.0F;
    private static final float HEAD_Y_FLIP = 1.0F;
    private static final float HEAD_Z_DEPTH = 150.0F;
    private static SkullBlockEntityModel headModel;

    private PlayerHeadRendererCompat() {
    }

    public static void drawHead(DrawContext context, Identifier texture, int x, int y, int size) {
        if (context == null || texture == null || size <= 0) {
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            return;
        }
        SkullBlockEntityModel model = headModel(minecraft);
        if (model == null) {
            return;
        }
        HeadLayout layout = layoutForBounds(x, y, size, System.currentTimeMillis());
        MatrixStack matrices = context.getMatrices();

        context.draw();
        matrices.push();
        try {
            matrices.translate(layout.centerX(), layout.baseY(), HEAD_Z_DEPTH);
            matrices.scale(layout.modelScale() * layout.xFlip(), layout.modelScale() * layout.yFlip(), layout.modelScale());
            model.setHeadRotation(0.0F, layout.yawDegrees(), layout.pitchDegrees());
            model.render(
                    matrices,
                    context.getVertexConsumers().getBuffer(RenderLayer.getEntityTranslucent(texture)),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
            context.draw();
        } finally {
            matrices.pop();
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
        return new HeadLayout(
                x,
                y,
                clampedSize,
                centerX,
                baseY,
                modelScale,
                HEAD_PITCH_DEGREES,
                animatedYawDegrees(animationTimeMs),
                HEAD_X_FLIP,
                HEAD_Y_FLIP
        );
    }

    private static float animatedYawDegrees(long animationTimeMs) {
        long loopTimeMs = Math.floorMod(animationTimeMs, HEAD_SPIN_PERIOD_MS);
        float rotationDegrees = loopTimeMs * 360.0F / HEAD_SPIN_PERIOD_MS;
        float yawDegrees = (HEAD_YAW_DEGREES + rotationDegrees) % 360.0F;
        return yawDegrees < 0.0F ? yawDegrees + 360.0F : yawDegrees;
    }

    private static SkullBlockEntityModel headModel(MinecraftClient minecraft) {
        if (headModel == null) {
            Map<SkullBlock.SkullType, SkullBlockEntityModel> models = SkullBlockEntityRenderer.getModels(minecraft.getEntityModelLoader());
            headModel = models.get(SkullBlock.Type.PLAYER);
        }
        return headModel;
    }

    static record HeadLayout(
            int x,
            int y,
            int size,
            float centerX,
            float baseY,
            float modelScale,
            float pitchDegrees,
            float yawDegrees,
            float xFlip,
            float yFlip
    ) {
    }
}

