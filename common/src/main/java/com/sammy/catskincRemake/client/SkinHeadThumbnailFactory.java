package com.sammy.catskincRemake.client;

import com.mojang.blaze3d.platform.NativeImage;

final class SkinHeadThumbnailFactory {
    private static final int MODEL_TEXTURE_SIZE = 64;
    private static final int HEAD_FACE_SIZE = 8;
    private static final int SUPPORT_PLATE_COLOR = 0xAAB8C2CF;
    private static final int OUTLINE_COLOR = 0xD8EEF2F7;

    private SkinHeadThumbnailFactory() {
    }

    static NativeImage createIsometricHeadThumbnail(NativeImage skin, int size) {
        int outputSize = Math.max(16, size);
        int faceSize = Math.max(8, Math.round(outputSize * 0.5F));
        int depth = Math.max(3, faceSize / 4);
        int frontX = Math.max(1, (outputSize - faceSize - depth) / 2);
        int frontY = Math.max(1, (outputSize - faceSize) / 2 + depth / 2);

        NativeImage thumbnail = new NativeImage(outputSize, outputSize, true);
        drawSupportPlate(thumbnail, frontX, frontY, faceSize, depth);
        drawTopFace(thumbnail, skin, frontX, frontY, faceSize, depth);
        drawSideFace(thumbnail, skin, frontX, frontY, faceSize, depth);
        drawFrontFace(thumbnail, skin, frontX, frontY, faceSize, depth);
        drawOutline(thumbnail, OUTLINE_COLOR);
        return thumbnail;
    }

    private static void drawSupportPlate(NativeImage output, int frontX, int frontY, int faceSize, int depth) {
        int expandedFaceSize = faceSize + 2;
        for (int dy = 0; dy < expandedFaceSize; ++dy) {
            for (int dx = 0; dx < expandedFaceSize; ++dx) {
                plot(output, frontX + depth - 1 + dx, frontY - 1 + dy, SUPPORT_PLATE_COLOR);
            }
        }
        for (int dy = 0; dy < depth + 2; ++dy) {
            int sourceRow = Math.min(depth - 1, Math.max(0, dy - 1));
            int rowShift = depth - 1 - sourceRow;
            for (int dx = 0; dx < expandedFaceSize; ++dx) {
                plot(output, frontX + rowShift - 1 + dx, frontY - depth - 1 + dy, SUPPORT_PLATE_COLOR);
            }
        }
        for (int dx = 0; dx < depth + 2; ++dx) {
            int sourceColumn = Math.min(depth - 1, Math.max(0, dx - 1));
            int columnShift = depth - 1 - sourceColumn;
            for (int dy = 0; dy < expandedFaceSize; ++dy) {
                plot(output, frontX + depth + faceSize + dx, frontY - columnShift - 1 + dy, SUPPORT_PLATE_COLOR);
            }
        }
    }

    private static void drawOutline(NativeImage image, int color) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] snapshot = new int[width * height];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                snapshot[y * width + x] = image.getPixelRGBA(x, y);
            }
        }
        for (int y = 1; y < height - 1; ++y) {
            for (int x = 1; x < width - 1; ++x) {
                if (alpha(snapshot[y * width + x]) != 0) {
                    continue;
                }
                if (hasOpaqueNeighbor(snapshot, width, height, x, y)) {
                    image.setPixelRGBA(x, y, color);
                }
            }
        }
    }

    private static void drawFrontFace(NativeImage output, NativeImage skin, int frontX, int frontY, int faceSize, int depth) {
        for (int dy = 0; dy < faceSize; ++dy) {
            int sourceY = dy * HEAD_FACE_SIZE / faceSize;
            for (int dx = 0; dx < faceSize; ++dx) {
                int sourceX = dx * HEAD_FACE_SIZE / faceSize;
                int color = sampleHeadPixel(skin, 8, 8, 40, 8, sourceX, sourceY);
                plot(output, frontX + depth + dx, frontY + dy, color);
            }
        }
    }

    private static void drawTopFace(NativeImage output, NativeImage skin, int frontX, int frontY, int faceSize, int depth) {
        for (int dy = 0; dy < depth; ++dy) {
            int sourceY = dy * HEAD_FACE_SIZE / depth;
            int rowShift = depth - 1 - dy;
            for (int dx = 0; dx < faceSize; ++dx) {
                int sourceX = dx * HEAD_FACE_SIZE / faceSize;
                int color = shade(sampleHeadPixel(skin, 8, 0, 40, 0, sourceX, sourceY), 1.06F);
                plot(output, frontX + rowShift + dx, frontY - depth + dy, color);
            }
        }
    }

    private static void drawSideFace(NativeImage output, NativeImage skin, int frontX, int frontY, int faceSize, int depth) {
        for (int dx = 0; dx < depth; ++dx) {
            int sourceX = dx * HEAD_FACE_SIZE / depth;
            int columnShift = depth - 1 - dx;
            for (int dy = 0; dy < faceSize; ++dy) {
                int sourceY = dy * HEAD_FACE_SIZE / faceSize;
                int color = shade(sampleHeadPixel(skin, 0, 8, 32, 8, sourceX, sourceY), 0.82F);
                plot(output, frontX + depth + faceSize + dx, frontY - columnShift + dy, color);
            }
        }
    }

    private static int sampleHeadPixel(NativeImage skin, int baseX, int baseY, int overlayX, int overlayY, int x, int y) {
        int color = getModelPixel(skin, baseX + x, baseY + y);
        int overlay = getModelPixel(skin, overlayX + x, overlayY + y);
        return alpha(overlay) == 0 ? color : blend(color, overlay);
    }

    private static int getModelPixel(NativeImage image, int modelX, int modelY) {
        int pixelX = mapModelCoordinate(modelX, image.getWidth());
        int pixelY = mapModelCoordinate(modelY, image.getHeight());
        return getPixelSafe(image, pixelX, pixelY);
    }

    private static int getPixelSafe(NativeImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return 0;
        }
        return image.getPixelRGBA(x, y);
    }

    private static void plot(NativeImage image, int x, int y, int color) {
        if (alpha(color) == 0) {
            return;
        }
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        image.setPixelRGBA(x, y, color);
    }

    private static int shade(int color, float factor) {
        int a = alpha(color);
        int r = clamp((int)(((color >>> 16) & 0xFF) * factor));
        int g = clamp((int)(((color >>> 8) & 0xFF) * factor));
        int b = clamp((int)((color & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int base, int overlay) {
        int alpha = alpha(overlay);
        if (alpha == 255) {
            return overlay;
        }
        float weight = alpha / 255.0F;
        int baseAlpha = alpha(base);
        int r = clamp((int)(((base >>> 16) & 0xFF) * (1.0F - weight) + ((overlay >>> 16) & 0xFF) * weight));
        int g = clamp((int)(((base >>> 8) & 0xFF) * (1.0F - weight) + ((overlay >>> 8) & 0xFF) * weight));
        int b = clamp((int)((base & 0xFF) * (1.0F - weight) + (overlay & 0xFF) * weight));
        return (Math.max(baseAlpha, alpha) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int alpha(int color) {
        return color >>> 24;
    }

    private static boolean hasOpaqueNeighbor(int[] snapshot, int width, int height, int x, int y) {
        for (int offsetY = -1; offsetY <= 1; ++offsetY) {
            for (int offsetX = -1; offsetX <= 1; ++offsetX) {
                if (offsetX == 0 && offsetY == 0) {
                    continue;
                }
                int neighborX = x + offsetX;
                int neighborY = y + offsetY;
                if (neighborX < 0 || neighborY < 0 || neighborX >= width || neighborY >= height) {
                    continue;
                }
                if (alpha(snapshot[neighborY * width + neighborX]) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int mapModelCoordinate(int modelCoordinate, int imageSize) {
        float scale = imageSize / (float)MODEL_TEXTURE_SIZE;
        int pixelCoordinate = Math.round((modelCoordinate + 0.5F) * scale - 0.5F);
        return Math.max(0, Math.min(imageSize - 1, pixelCoordinate));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
