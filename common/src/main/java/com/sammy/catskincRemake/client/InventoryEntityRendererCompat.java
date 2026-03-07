package com.sammy.catskincRemake.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;

public final class InventoryEntityRendererCompat {
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
        try {
            int centerX = (x1 + x2) / 2;
            int centerY = y2 - 22;
            int size = Math.max(20, Math.min(x2 - x1, y2 - y1) / 3);
            InventoryScreen.drawEntity(
                    context,
                    centerX,
                    centerY,
                    size,
                    (float) (centerX - mouseX),
                    (float) (centerY - mouseY),
                    entity
            );
        } catch (Exception ignored) {
        }
    }
}

