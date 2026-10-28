package com.sammy.catskinc.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pure (Minecraft-free) S3TC block encoder for skin textures.
 *
 * <p>Pixels are packed ABGR ints ({@code 0xAABBGGRR}), the layout {@code NativeImage#getColor}
 * returns. The encoder uses a bounding-box endpoint fit with inset and diagonal selection
 * (the classic real-time DXT approach): fast enough to run on a background thread for
 * multi-megapixel skins, with quality close to offline encoders for smooth HD art.
 */
public final class DxtEncoder {
    public enum Format {
        /** DXT1 with 1-bit alpha: 8 bytes per 4x4 block (0.5 bytes per pixel). */
        BC1(8, 0x83F1),
        /** DXT5 with interpolated alpha: 16 bytes per 4x4 block (1 byte per pixel). */
        BC3(16, 0x83F3);

        private final int bytesPerBlock;
        private final int glInternalFormat;

        Format(int bytesPerBlock, int glInternalFormat) {
            this.bytesPerBlock = bytesPerBlock;
            this.glInternalFormat = glInternalFormat;
        }

        public int bytesPerBlock() {
            return bytesPerBlock;
        }

        /** {@code GL_COMPRESSED_RGBA_S3TC_DXT1_EXT} or {@code GL_COMPRESSED_RGBA_S3TC_DXT5_EXT}. */
        public int glInternalFormat() {
            return glInternalFormat;
        }
    }

    private DxtEncoder() {
    }

    public static int encodedSize(Format format, int width, int height) {
        return ((width + 3) / 4) * ((height + 3) / 4) * format.bytesPerBlock();
    }

    /** Encodes {@code abgr} into a new little-endian direct buffer, positioned at 0. */
    public static ByteBuffer encode(int[] abgr, int width, int height, Format format) {
        ByteBuffer out = ByteBuffer.allocateDirect(encodedSize(format, width, height)).order(ByteOrder.LITTLE_ENDIAN);
        int[] block = new int[16];
        for (int by = 0; by < height; by += 4) {
            for (int bx = 0; bx < width; bx += 4) {
                for (int i = 0; i < 16; i++) {
                    int x = Math.min(width - 1, bx + (i & 3));
                    int y = Math.min(height - 1, by + (i >> 2));
                    block[i] = abgr[y * width + x];
                }
                if (format == Format.BC3) {
                    encodeAlphaBlock(block, out);
                    encodeColorBlock(block, out, false);
                } else {
                    encodeColorBlock(block, out, true);
                }
            }
        }
        out.flip();
        return out;
    }

    /** Nearest-neighbour resample matching {@link SkinManagerClient#blitPixels}. */
    public static int[] resampleNearest(int[] src, int sw, int sh, int tw, int th) {
        if (sw == tw && sh == th) {
            return src.clone();
        }
        int[] dst = new int[tw * th];
        float xRatio = (float) sw / tw;
        float yRatio = (float) sh / th;
        for (int y = 0; y < th; y++) {
            int sy = Math.min(sh - 1, (int) (y * yRatio));
            for (int x = 0; x < tw; x++) {
                int sx = Math.min(sw - 1, (int) (x * xRatio));
                dst[y * tw + x] = src[sy * sw + sx];
            }
        }
        return dst;
    }

    private static void encodeColorBlock(int[] block, ByteBuffer out, boolean bc1) {
        boolean punchThrough = false;
        if (bc1) {
            for (int c : block) {
                if ((c >>> 24) < 128) {
                    punchThrough = true;
                    break;
                }
            }
        }

        // Fit endpoints to the pixels that will actually be visible.
        int minR = 255, minG = 255, minB = 255, maxR = 0, maxG = 0, maxB = 0;
        int count = 0;
        for (int c : block) {
            int a = c >>> 24;
            if (bc1 ? a < 128 : a == 0) {
                continue;
            }
            int r = c & 0xFF, g = (c >> 8) & 0xFF, b = (c >> 16) & 0xFF;
            minR = Math.min(minR, r); maxR = Math.max(maxR, r);
            minG = Math.min(minG, g); maxG = Math.max(maxG, g);
            minB = Math.min(minB, b); maxB = Math.max(maxB, b);
            count++;
        }
        if (count == 0) {
            // Fully transparent block: colour is irrelevant.
            out.putShort((short) 0);
            out.putShort((short) 0);
            out.putInt(bc1 ? 0xFFFFFFFF : 0);
            return;
        }

        // Pick the bounding-box diagonal that follows the colour trend.
        int midR = (minR + maxR) >> 1, midG = (minG + maxG) >> 1, midB = (minB + maxB) >> 1;
        long covRG = 0, covBG = 0;
        for (int c : block) {
            int a = c >>> 24;
            if (bc1 ? a < 128 : a == 0) {
                continue;
            }
            int dg = ((c >> 8) & 0xFF) - midG;
            covRG += (long) ((c & 0xFF) - midR) * dg;
            covBG += (long) (((c >> 16) & 0xFF) - midB) * dg;
        }
        if (covRG < 0) {
            int t = minR; minR = maxR; maxR = t;
        }
        if (covBG < 0) {
            int t = minB; minB = maxB; maxB = t;
        }

        // Inset by 1/16 of the range to reduce error at the extremes.
        int insetR = (maxR - minR) >> 4, insetG = (maxG - minG) >> 4, insetB = (maxB - minB) >> 4;
        int c0 = to565(maxR - insetR, maxG - insetG, maxB - insetB);
        int c1 = to565(minR + insetR, minG + insetG, minB + insetB);

        if (punchThrough ? c0 > c1 : c0 < c1) {
            int t = c0; c0 = c1; c1 = t;
        }

        int[] palette = new int[12];
        expand565(c0, palette, 0);
        expand565(c1, palette, 3);
        int paletteSize;
        if (punchThrough) {
            for (int ch = 0; ch < 3; ch++) {
                palette[6 + ch] = (palette[ch] + palette[3 + ch]) / 2;
            }
            paletteSize = 3;
        } else {
            for (int ch = 0; ch < 3; ch++) {
                palette[6 + ch] = (2 * palette[ch] + palette[3 + ch]) / 3;
                palette[9 + ch] = (palette[ch] + 2 * palette[3 + ch]) / 3;
            }
            paletteSize = c0 == c1 ? 1 : 4;
        }

        int indices = 0;
        for (int i = 0; i < 16; i++) {
            int c = block[i];
            int index;
            if (punchThrough && (c >>> 24) < 128) {
                index = 3;
            } else {
                index = nearest(palette, paletteSize, c & 0xFF, (c >> 8) & 0xFF, (c >> 16) & 0xFF);
            }
            indices |= index << (2 * i);
        }
        out.putShort((short) c0);
        out.putShort((short) c1);
        out.putInt(indices);
    }

    private static void encodeAlphaBlock(int[] block, ByteBuffer out) {
        int[] alpha = new int[16];
        int min = 255, max = 0;
        int innerMin = 255, innerMax = 0;
        boolean hasInner = false;
        for (int i = 0; i < 16; i++) {
            int a = block[i] >>> 24;
            alpha[i] = a;
            min = Math.min(min, a);
            max = Math.max(max, a);
            if (a != 0 && a != 255) {
                innerMin = Math.min(innerMin, a);
                innerMax = Math.max(innerMax, a);
                hasInner = true;
            }
        }

        // Six-value mode keeps exact 0 and 255, which is what most skin pixels use.
        int sixA0 = hasInner ? innerMin : 0;
        int sixA1 = hasInner ? innerMax : 0;
        int[] six = alphaPalette(sixA0, sixA1);
        long sixIdx = 0;
        int sixErr = 0;
        for (int i = 0; i < 16; i++) {
            int idx = nearestAlpha(six, alpha[i]);
            sixErr += Math.abs(six[idx] - alpha[i]);
            sixIdx |= (long) idx << (3 * i);
        }

        int a0 = sixA0, a1 = sixA1;
        long bits = sixIdx;
        if (max > min) {
            int[] eight = alphaPalette(max, min);
            long eightIdx = 0;
            int eightErr = 0;
            for (int i = 0; i < 16; i++) {
                int idx = nearestAlpha(eight, alpha[i]);
                eightErr += Math.abs(eight[idx] - alpha[i]);
                eightIdx |= (long) idx << (3 * i);
            }
            if (eightErr < sixErr) {
                a0 = max;
                a1 = min;
                bits = eightIdx;
            }
        }

        out.put((byte) a0);
        out.put((byte) a1);
        for (int i = 0; i < 6; i++) {
            out.put((byte) (bits >>> (8 * i)));
        }
    }

    /** BC3 alpha palette: eight interpolated values when a0 > a1, else six plus exact 0 and 255. */
    static int[] alphaPalette(int a0, int a1) {
        int[] p = new int[8];
        p[0] = a0;
        p[1] = a1;
        if (a0 > a1) {
            for (int i = 2; i < 8; i++) {
                p[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
            }
        } else {
            for (int i = 2; i < 6; i++) {
                p[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
            }
            p[6] = 0;
            p[7] = 255;
        }
        return p;
    }

    private static int nearestAlpha(int[] palette, int a) {
        int best = 0;
        int bestErr = Integer.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            int err = Math.abs(palette[i] - a);
            if (err < bestErr) {
                bestErr = err;
                best = i;
            }
        }
        return best;
    }

    private static int nearest(int[] palette, int size, int r, int g, int b) {
        int best = 0;
        int bestErr = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            int dr = palette[i * 3] - r;
            int dg = palette[i * 3 + 1] - g;
            int db = palette[i * 3 + 2] - b;
            int err = dr * dr + dg * dg + db * db;
            if (err < bestErr) {
                bestErr = err;
                best = i;
            }
        }
        return best;
    }

    private static int to565(int r, int g, int b) {
        return ((r * 31 + 127) / 255) << 11 | ((g * 63 + 127) / 255) << 5 | ((b * 31 + 127) / 255);
    }

    static void expand565(int c, int[] rgb, int offset) {
        int r = (c >> 11) & 31, g = (c >> 5) & 63, b = c & 31;
        rgb[offset] = (r << 3) | (r >> 2);
        rgb[offset + 1] = (g << 2) | (g >> 4);
        rgb[offset + 2] = (b << 3) | (b >> 2);
    }

    /** Reference decoder (used by tests to measure encoding error). Returns ABGR pixels. */
    static int[] decode(ByteBuffer data, int width, int height, Format format) {
        ByteBuffer in = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        in.position(0);
        int[] out = new int[width * height];
        int[] rgb = new int[12];
        for (int by = 0; by < height; by += 4) {
            for (int bx = 0; bx < width; bx += 4) {
                int[] alphas = null;
                long alphaBits = 0;
                if (format == Format.BC3) {
                    int a0 = in.get() & 0xFF;
                    int a1 = in.get() & 0xFF;
                    for (int i = 0; i < 6; i++) {
                        alphaBits |= (long) (in.get() & 0xFF) << (8 * i);
                    }
                    alphas = alphaPalette(a0, a1);
                }
                int c0 = in.getShort() & 0xFFFF;
                int c1 = in.getShort() & 0xFFFF;
                int indices = in.getInt();
                expand565(c0, rgb, 0);
                expand565(c1, rgb, 3);
                boolean fourColor = format == Format.BC3 || c0 > c1;
                for (int ch = 0; ch < 3; ch++) {
                    if (fourColor) {
                        rgb[6 + ch] = (2 * rgb[ch] + rgb[3 + ch]) / 3;
                        rgb[9 + ch] = (rgb[ch] + 2 * rgb[3 + ch]) / 3;
                    } else {
                        rgb[6 + ch] = (rgb[ch] + rgb[3 + ch]) / 2;
                        rgb[9 + ch] = 0;
                    }
                }
                for (int i = 0; i < 16; i++) {
                    int x = bx + (i & 3);
                    int y = by + (i >> 2);
                    if (x >= width || y >= height) {
                        continue;
                    }
                    int idx = (indices >>> (2 * i)) & 3;
                    int a;
                    if (alphas != null) {
                        a = alphas[(int) ((alphaBits >>> (3 * i)) & 7)];
                    } else {
                        a = !fourColor && idx == 3 ? 0 : 255;
                    }
                    out[y * width + x] = a << 24 | rgb[idx * 3 + 2] << 16 | rgb[idx * 3 + 1] << 8 | rgb[idx * 3];
                }
            }
        }
        return out;
    }
}
