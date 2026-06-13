package com.sammy.catskinc.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

public final class InventoryEntityRendererCompat {
    private static final float HEADROOM_Y_OFFSET = -0.25F;

    private InventoryEntityRendererCompat() {
    }

    public static void drawEntity(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int mouseX, int mouseY,
            LivingEntity entity) {
        if (entity == null) {
            return;
        }

        PreviewLayout layout = layoutForBounds(x1, y1, x2, y2);

        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    context,
                    layout.x1(),
                    layout.y1(),
                    layout.x2(),
                    layout.y2(),
                    layout.size(),
                    layout.yOffset(),
                    mouseX,
                    mouseY,
                    entity);
        } catch (Exception ignored) {
        }
    }

    static PreviewLayout layoutForBounds(int x1, int y1, int x2, int y2) {
        int width = Math.max(1, x2 - x1);
        int height = Math.max(1, y2 - y1);
        int size = Math.max(20, Math.round(Math.min(width, height) * 0.35F));
        return new PreviewLayout(x1, y1, x2, y2, size, HEADROOM_Y_OFFSET);
    }

    static record PreviewLayout(int x1, int y1, int x2, int y2, int size, float yOffset) {
    }
}
