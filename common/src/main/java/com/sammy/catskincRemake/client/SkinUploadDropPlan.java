package com.sammy.catskincRemake.client;

public final class SkinUploadDropPlan {
    public enum Mode {
        LIBRARY_IMPORT,
        PREVIEW_SELECTION
    }

    private SkinUploadDropPlan() {
    }

    public static Mode resolve(int mouseX, int mouseY, int libraryX, int libraryY, int libraryW, int libraryH, int pngCount) {
        if (pngCount > 3) {
            return Mode.LIBRARY_IMPORT;
        }
        if (isInside(mouseX, mouseY, libraryX, libraryY, libraryW, libraryH)) {
            return Mode.LIBRARY_IMPORT;
        }
        return Mode.PREVIEW_SELECTION;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
