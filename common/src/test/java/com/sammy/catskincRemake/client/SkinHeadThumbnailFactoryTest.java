package com.sammy.catskincRemake.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SkinHeadThumbnailFactoryTest {
    @Test
    void createsIsometricHeadThumbnailWithDistinctTopFrontAndSideFaces() {
        NativeImage skin = new NativeImage(64, 64, true);
        try {
            fillRect(skin, 8, 8, 8, 8, 0xFFCC4444);
            fillRect(skin, 8, 0, 8, 8, 0xFF44CC44);
            fillRect(skin, 0, 8, 8, 8, 0xFF4444CC);

            NativeImage thumbnail = SkinHeadThumbnailFactory.createIsometricHeadThumbnail(skin, 32);
            try {
                int topPixel = thumbnail.getPixelRGBA(14, 8);
                int frontPixel = thumbnail.getPixelRGBA(16, 18);
                int sidePixel = thumbnail.getPixelRGBA(26, 18);

                assertEquals(0, thumbnail.getPixelRGBA(1, 1), "background should stay transparent");
                assertNotEquals(0, topPixel, "top face should be visible");
                assertNotEquals(0, frontPixel, "front face should be visible");
                assertNotEquals(0, sidePixel, "side face should be visible");
                assertNotEquals(topPixel, frontPixel, "top and front faces should sample different head regions");
                assertNotEquals(frontPixel, sidePixel, "front and side faces should sample different head regions");
            } finally {
                thumbnail.close();
            }
        } finally {
            skin.close();
        }
    }

    @Test
    void addsVisibleBackplateForVeryDarkSkins() {
        NativeImage skin = new NativeImage(64, 64, true);
        int darkColor = 0xFF050505;
        try {
            fillRect(skin, 8, 8, 8, 8, darkColor);
            fillRect(skin, 8, 0, 8, 8, darkColor);
            fillRect(skin, 0, 8, 8, 8, darkColor);

            NativeImage thumbnail = SkinHeadThumbnailFactory.createIsometricHeadThumbnail(skin, 32);
            try {
                boolean hasVisibleSupportPixel = false;
                for (int y = 0; y < thumbnail.getHeight() && !hasVisibleSupportPixel; ++y) {
                    for (int x = 0; x < thumbnail.getWidth(); ++x) {
                        int pixel = thumbnail.getPixelRGBA(x, y);
                        int red = (pixel >>> 16) & 0xFF;
                        int green = (pixel >>> 8) & 0xFF;
                        int blue = pixel & 0xFF;
                        if (pixel != 0 && (red >= 32 || green >= 32 || blue >= 32)) {
                            hasVisibleSupportPixel = true;
                            break;
                        }
                    }
                }

                assertNotEquals(false, hasVisibleSupportPixel, "dark skins should get an outline or plate bright enough to stand out");
            } finally {
                thumbnail.close();
            }
        } finally {
            skin.close();
        }
    }

    @Test
    void scalesHeadSamplingForHdSkins() {
        int scale = 16;
        NativeImage skin = new NativeImage(64 * scale, 64 * scale, true);
        try {
            fillRect(skin, 8 * scale, 8 * scale, 8 * scale, 8 * scale, 0xFFCC4444);
            fillRect(skin, 8 * scale, 0, 8 * scale, 8 * scale, 0xFF44CC44);
            fillRect(skin, 0, 8 * scale, 8 * scale, 8 * scale, 0xFF4444CC);

            NativeImage thumbnail = SkinHeadThumbnailFactory.createIsometricHeadThumbnail(skin, 32);
            try {
                int topPixel = thumbnail.getPixelRGBA(14, 8);
                int frontPixel = thumbnail.getPixelRGBA(16, 18);
                int sidePixel = thumbnail.getPixelRGBA(26, 18);

                assertDominantChannel(topPixel, 8, "top face should still sample the hd top region");
                assertDominantChannel(frontPixel, 16, "front face should still sample the hd front region");
                assertDominantChannel(sidePixel, 0, "side face should still sample the hd side region");
            } finally {
                thumbnail.close();
            }
        } finally {
            skin.close();
        }
    }

    private static void fillRect(NativeImage image, int x, int y, int width, int height, int rgba) {
        for (int dy = 0; dy < height; ++dy) {
            for (int dx = 0; dx < width; ++dx) {
                image.setPixelRGBA(x + dx, y + dy, rgba);
            }
        }
    }

    private static void assertDominantChannel(int color, int channelShift, String message) {
        int dominant = (color >>> channelShift) & 0xFF;
        int firstOther = (color >>> ((channelShift + 8) % 24)) & 0xFF;
        int secondOther = (color >>> ((channelShift + 16) % 24)) & 0xFF;
        assertNotEquals(0, color, message);
        assertNotEquals(false, dominant > firstOther && dominant > secondOther, message);
    }
}
