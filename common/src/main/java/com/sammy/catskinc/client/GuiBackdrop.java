package com.sammy.catskinc.client;

import net.minecraft.client.gui.GuiGraphics;

/** Figura-inspired edge vignette shared by CatSkinC menus. */
public final class GuiBackdrop {
    private static final int EDGE = 56;

    private GuiBackdrop() { }

    public static void drawVignette(GuiGraphics context, int width, int height) {
        context.fillGradient(0, 0, width, EDGE, 0xB8000000, 0x00000000);
        context.fillGradient(0, height - EDGE, width, height, 0x00000000, 0xB8000000);
        context.fillGradient(0, 0, EDGE, height, 0x88000000, 0x00000000);
        context.fillGradient(width - EDGE, 0, width, height, 0x00000000, 0x88000000);
    }
}