package com.sammy.catskinc.client;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DxtEncoderTest {
    private static int abgr(int a, int r, int g, int b) {
        return a << 24 | b << 16 | g << 8 | r;
    }

    @Test
    void encodedSizeMatchesBlockCount() {
        assertEquals(2048 * 2048 / 2, DxtEncoder.encodedSize(DxtEncoder.Format.BC1, 2048, 2048));
        assertEquals(2048 * 2048, DxtEncoder.encodedSize(DxtEncoder.Format.BC3, 2048, 2048));
        // Partial blocks round up.
        assertEquals(4 * 16, DxtEncoder.encodedSize(DxtEncoder.Format.BC3, 6, 6));
    }

    @Test
    void solidColourRoundTripsWithinQuantisation() {
        int color = abgr(255, 200, 100, 50);
        int[] pixels = filled(16, 16, color);
        for (DxtEncoder.Format format : DxtEncoder.Format.values()) {
            int[] decoded = roundTrip(pixels, 16, 16, format);
            for (int px : decoded) {
                assertEquals(255, px >>> 24);
                assertTrue(Math.abs((px & 0xFF) - 200) <= 4);
                assertTrue(Math.abs(((px >> 8) & 0xFF) - 100) <= 2);
                assertTrue(Math.abs(((px >> 16) & 0xFF) - 50) <= 4);
            }
        }
    }

    @Test
    void bc1KeepsCutoutTransparency() {
        int[] pixels = new int[16 * 16];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (i % 3 == 0) ? 0 : abgr(255, 20, 220, 90);
        }
        int[] decoded = roundTrip(pixels, 16, 16, DxtEncoder.Format.BC1);
        for (int i = 0; i < pixels.length; i++) {
            assertEquals(pixels[i] >>> 24 == 0 ? 0 : 255, decoded[i] >>> 24, "alpha at " + i);
        }
    }

    @Test
    void bc3KeepsExactZeroAndFullAlphaAndApproximatesTranslucency() {
        int[] pixels = new int[8 * 8];
        for (int i = 0; i < pixels.length; i++) {
            int a = switch (i % 4) {
                case 0 -> 0;
                case 1 -> 255;
                case 2 -> 128;
                default -> 96;
            };
            pixels[i] = abgr(a, 255, 255, 255);
        }
        int[] decoded = roundTrip(pixels, 8, 8, DxtEncoder.Format.BC3);
        for (int i = 0; i < pixels.length; i++) {
            int expected = pixels[i] >>> 24;
            int actual = decoded[i] >>> 24;
            if (expected == 0 || expected == 255) {
                assertEquals(expected, actual, "alpha at " + i);
            } else {
                assertTrue(Math.abs(expected - actual) <= 8, "alpha at " + i + " was " + actual);
            }
        }
    }

    @Test
    void smoothGradientHasLowAverageError() {
        int w = 64, h = 64;
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = abgr(255, x * 4, y * 4, 128);
            }
        }
        int[] decoded = roundTrip(pixels, w, h, DxtEncoder.Format.BC3);
        assertTrue(meanAbsError(pixels, decoded) < 4.0, "mean error " + meanAbsError(pixels, decoded));
    }

    @Test
    void randomNoiseStaysBounded() {
        Random random = new Random(42);
        int w = 32, h = 32;
        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = abgr(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
        }
        int[] decoded = roundTrip(pixels, w, h, DxtEncoder.Format.BC1);
        // Noise is the worst case for block compression; just ensure the fit is sane.
        assertTrue(meanAbsError(pixels, decoded) < 64.0, "mean error " + meanAbsError(pixels, decoded));
    }

    @Test
    void resampleNearestHalvesPowerOfTwoImagesExactly() {
        int[] pixels = new int[4 * 4];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = i;
        }
        int[] half = DxtEncoder.resampleNearest(pixels, 4, 4, 2, 2);
        assertEquals(0, half[0]);
        assertEquals(2, half[1]);
        assertEquals(8, half[2]);
        assertEquals(10, half[3]);
    }

    @Test
    void compressedSkinLevelsFollowMyopiaSizes() {
        int[] pixels = filled(512, 512, abgr(255, 10, 20, 30));
        CompressedSkinCache.Mip[] mips = CompressedSkinCache.encodeLevels(ModConfig.TextureQuality.BALANCED, pixels, 512, 512);
        assertEquals(MyopiaLod.Level.values().length, mips.length);
        assertEquals(512, mips[0].width());
        assertEquals(256, mips[1].width());
        assertEquals(128, mips[2].width());
        assertEquals(64, mips[3].width());
        assertEquals(512 * 512, mips[0].data().capacity());
    }

    @Test
    void onlyLossyPresetsWithHdSkinsCompress() {
        assertTrue(!CompressedSkinCache.shouldCompress(ModConfig.TextureQuality.LOSSLESS, 2048, 2048));
        assertTrue(!CompressedSkinCache.shouldCompress(ModConfig.TextureQuality.BALANCED, 64, 64));
        assertTrue(CompressedSkinCache.shouldCompress(ModConfig.TextureQuality.BALANCED, 2048, 2048));
        assertEquals(2048, ModConfig.TextureQuality.BALANCED.effectiveMaxResolution(8192));
        assertEquals(512, ModConfig.TextureQuality.FASTEST.effectiveMaxResolution(512));
        assertEquals(8192, ModConfig.TextureQuality.LOSSLESS.effectiveMaxResolution(8192));
    }

    private static int[] roundTrip(int[] pixels, int w, int h, DxtEncoder.Format format) {
        ByteBuffer encoded = DxtEncoder.encode(pixels, w, h, format);
        assertEquals(DxtEncoder.encodedSize(format, w, h), encoded.remaining());
        return DxtEncoder.decode(encoded, w, h, format);
    }

    private static int[] filled(int w, int h, int color) {
        int[] pixels = new int[w * h];
        java.util.Arrays.fill(pixels, color);
        return pixels;
    }

    private static double meanAbsError(int[] a, int[] b) {
        long total = 0;
        for (int i = 0; i < a.length; i++) {
            for (int shift = 0; shift < 24; shift += 8) {
                total += Math.abs(((a[i] >> shift) & 0xFF) - ((b[i] >> shift) & 0xFF));
            }
        }
        return total / (a.length * 3.0);
    }
}
