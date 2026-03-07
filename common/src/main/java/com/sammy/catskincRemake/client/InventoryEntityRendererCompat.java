package com.sammy.catskincRemake.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

public final class InventoryEntityRendererCompat {
    private static final Method DRAW_ENTITY_NEW = findNewDrawMethod();
    private static final Method DRAW_ENTITY_LEGACY = findLegacyDrawMethod();

    private InventoryEntityRendererCompat() {
    }

    public static void drawEntity(DrawContext context, int x1, int y1, int x2, int y2, int mouseX, int mouseY, LivingEntity entity) {
        if (entity == null) {
            return;
        }
        int previewHeight = Math.max(1, y2 - y1);
        int verticalOffset = Math.max(4, previewHeight / 14);
        int appliedOffset = Math.min(verticalOffset, Math.max(0, y1));
        int y1Adjusted = y1 - appliedOffset;
        int y2Adjusted = y2 - appliedOffset;

        if (tryDrawNew(context, x1, y1Adjusted, x2, y2Adjusted, mouseX, mouseY, entity)) {
            return;
        }
        tryDrawLegacy(context, x1, y1Adjusted, x2, y2Adjusted, mouseX, mouseY, entity);
    }

    private static boolean tryDrawNew(DrawContext context, int x1, int y1, int x2, int y2, int mouseX, int mouseY, LivingEntity entity) {
        if (DRAW_ENTITY_NEW == null) {
            return false;
        }
        try {
            float centerX = (x1 + x2) * 0.5F;
            float centerY = (y1 + y2) * 0.5F;
            float size = Math.max(20.0F, Math.min(x2 - x1, y2 - y1) * 0.35F);

            float targetYaw = (float) Math.atan((centerX - mouseX) / 40.0F);
            float targetPitch = (float) Math.atan((centerY - mouseY) / 40.0F);

            Quaternionf baseRot = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf cameraRot = new Quaternionf().rotateX(targetPitch * 20.0F * ((float) Math.PI / 180.0F));
            baseRot.mul(cameraRot);

            Vector3f translate = new Vector3f(0.0F, entity.getHeight() * 0.5F, 0.0F);
            DRAW_ENTITY_NEW.invoke(null, context, centerX, centerY, size, translate, baseRot, cameraRot, entity);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Method findNewDrawMethod() {
        for (Method method : InventoryScreen.class.getMethods()) {
            if (!"drawEntity".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 8) {
                continue;
            }
            if (!DrawContext.class.isAssignableFrom(params[0])) {
                continue;
            }
            if (params[1] != float.class || params[2] != float.class || params[3] != float.class) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static boolean tryDrawLegacy(DrawContext context, int x1, int y1, int x2, int y2, int mouseX, int mouseY, LivingEntity entity) {
        if (DRAW_ENTITY_LEGACY == null) {
            return false;
        }
        try {
            int centerX = (x1 + x2) / 2;
            int centerY = y2 - 22;
            int size = Math.max(20, Math.min(x2 - x1, y2 - y1) / 3);
            DRAW_ENTITY_LEGACY.invoke(null, context, centerX, centerY, size,
                    (float) (centerX - mouseX), (float) (centerY - mouseY), entity);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Method findLegacyDrawMethod() {
        for (Method method : InventoryScreen.class.getMethods()) {
            if (!"drawEntity".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 7) {
                continue;
            }
            if (!DrawContext.class.isAssignableFrom(params[0])) {
                continue;
            }
            if (params[1] != int.class || params[2] != int.class || params[3] != int.class) {
                continue;
            }
            if (params[4] != float.class || params[5] != float.class) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }
}

