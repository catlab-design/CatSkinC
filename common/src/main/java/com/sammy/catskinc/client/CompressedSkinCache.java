package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds S3TC-compressed skins for the lossy texture quality presets.
 *
 * <p>Every skin is encoded once (on the render-prep thread) at all four Myopia levels and kept in
 * RAM as compressed blocks only; no RGBA copy survives. GPU textures are created lazily, one per
 * (player, variant, level) actually being drawn, and evicted after {@link #IDLE_EVICT_MS} without
 * use, so VRAM tracks what is on screen rather than everyone ever seen.
 */
public final class CompressedSkinCache {
    /** Skins smaller than this stay RGBA: 4x4 block compression visibly damages 64px pixel art. */
    public static final int MIN_COMPRESS_SIZE = 256;
    static final long IDLE_EVICT_MS = 15_000L;

    private static final Map<UUID, Prepared> PREPARED = new ConcurrentHashMap<>();
    // Render thread only.
    private static final Map<String, GpuEntry> GPU = new HashMap<>();
    private static volatile Boolean s3tcSupported;

    private CompressedSkinCache() {
    }

    /** True when {@code quality} compresses and this skin is large enough to benefit. */
    static boolean shouldCompress(ModConfig.TextureQuality quality, int width, int height) {
        return quality.format() != null
                && Math.min(width, height) >= MIN_COMPRESS_SIZE
                && !Boolean.FALSE.equals(s3tcSupported);
    }

    /** Encodes the skin (and optional talking variant) at every Myopia level. Any thread. */
    static Prepared prepare(DxtEncoder.Format format, NativeImage skin, NativeImage talking) {
        Map<String, Mip[]> variants = new HashMap<>();
        variants.put(MyopiaTextureCache.VARIANT_SKIN, encodeLevels(format, skin));
        if (talking != null) {
            variants.put(MyopiaTextureCache.VARIANT_TALKING, encodeLevels(format, talking));
        }
        return new Prepared(format, variants);
    }

    static Mip[] encodeLevels(DxtEncoder.Format format, NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = image.getColor(x, y);
            }
        }
        return encodeLevels(format, pixels, w, h);
    }

    static Mip[] encodeLevels(DxtEncoder.Format format, int[] pixels, int w, int h) {
        MyopiaLod.Level[] levels = MyopiaLod.Level.values();
        Mip[] mips = new Mip[levels.length];
        for (MyopiaLod.Level level : levels) {
            int lw = level.targetSize(w);
            int lh = level.targetSize(h);
            int[] scaled = DxtEncoder.resampleNearest(pixels, w, h, lw, lh);
            mips[level.ordinal()] = new Mip(lw, lh, DxtEncoder.encode(scaled, lw, lh, format));
        }
        return mips;
    }

    static void put(UUID uuid, Prepared prepared) {
        if (uuid != null && prepared != null) {
            PREPARED.put(uuid, prepared);
        }
    }

    static boolean has(UUID uuid) {
        return uuid != null && PREPARED.containsKey(uuid);
    }

    static boolean hasVariant(UUID uuid, String variant) {
        Prepared prepared = uuid == null ? null : PREPARED.get(uuid);
        return prepared != null && prepared.variants.containsKey(variant);
    }

    /** Drops one player's compressed skin. GPU textures are released on the next sweep. Any thread. */
    static void invalidate(UUID uuid) {
        if (uuid != null) {
            PREPARED.remove(uuid);
        }
    }

    /**
     * Returns a texture for this player at {@code level}, uploading it if needed.
     * Must be called on the render thread. Returns null when unavailable.
     */
    static Identifier get(UUID uuid, String variant, MyopiaLod.Level level) {
        Prepared prepared = uuid == null ? null : PREPARED.get(uuid);
        if (prepared == null) {
            return null;
        }
        Mip[] mips = prepared.variants.get(variant);
        if (mips == null) {
            variant = MyopiaTextureCache.VARIANT_SKIN;
            mips = prepared.variants.get(variant);
        }
        if (mips == null) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !probeSupport()) {
            return null;
        }
        Mip mip = mips[level.ordinal()];
        String key = uuid + "/" + variant + "/" + level.ordinal();
        GpuEntry entry = GPU.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.mip == mip && client.getTextureManager().getTexture(entry.id) == entry.texture) {
            entry.lastUsed = now;
            return entry.id;
        }
        if (entry != null) {
            GPU.remove(key);
            destroy(client, entry);
        }

        Identifier id = Identifiers.mod("skins/" + uuid + "/" + variant + "/bc" + level.ordinal());
        CompressedSkinTexture texture = new CompressedSkinTexture(mip.width, mip.height, prepared.format, mip.data);
        client.getTextureManager().registerTexture(id, texture);
        if (!texture.upload()) {
            client.getTextureManager().destroyTexture(id);
            onUploadFailed();
            return null;
        }
        GPU.put(key, new GpuEntry(id, texture, mip, now));
        ModLog.trace("Compressed skin uploaded for {} ({}, {}): {}x{} {} ({} KB)",
                uuid, variant, level, mip.width, mip.height, prepared.format, texture.byteSize() / 1024);
        return id;
    }

    /** Releases GPU textures that are stale or unused. Must be called on the render thread. */
    static void sweep() {
        if (GPU.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, GpuEntry>> it = GPU.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, GpuEntry> e = it.next();
            GpuEntry entry = e.getValue();
            if (now - entry.lastUsed > IDLE_EVICT_MS || !isCurrent(e.getKey(), entry)) {
                it.remove();
                destroy(client, entry);
            }
        }
    }

    /** Drops everything. GPU work is deferred to the render thread when called elsewhere. */
    static void clearAll() {
        PREPARED.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            GPU.clear();
            return;
        }
        if (client.isOnThread()) {
            destroyAllGpu(client);
        } else {
            client.execute(() -> destroyAllGpu(client));
        }
    }

    /** VRAM held by uploaded compressed skins, in bytes. Render thread. */
    static long gpuBytes() {
        long total = 0;
        for (GpuEntry entry : GPU.values()) {
            total += entry.texture.byteSize();
        }
        return total;
    }

    /** RAM held by compressed skin data, in bytes. */
    static long ramBytes() {
        long total = 0;
        for (Prepared prepared : PREPARED.values()) {
            total += prepared.bytes;
        }
        return total;
    }

    private static boolean isCurrent(String key, GpuEntry entry) {
        int slash = key.indexOf('/');
        UUID uuid;
        try {
            uuid = UUID.fromString(key.substring(0, slash));
        } catch (RuntimeException e) {
            return false;
        }
        Prepared prepared = PREPARED.get(uuid);
        if (prepared == null) {
            return false;
        }
        for (Mip[] mips : prepared.variants.values()) {
            for (Mip mip : mips) {
                if (mip == entry.mip) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean probeSupport() {
        Boolean supported = s3tcSupported;
        if (supported == null) {
            try {
                supported = GL.getCapabilities().GL_EXT_texture_compression_s3tc;
            } catch (RuntimeException e) {
                supported = false;
            }
            s3tcSupported = supported;
            ModLog.info("S3TC texture compression {}", supported ? "available" : "unavailable; skins stay lossless");
            if (!supported) {
                reloadAsLossless();
            }
        }
        return supported;
    }

    private static void onUploadFailed() {
        ModLog.warn("Compressed skin upload failed; falling back to lossless textures");
        s3tcSupported = false;
        reloadAsLossless();
    }

    private static void reloadAsLossless() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            // Re-download everything; prepare() is skipped from now on because support is false.
            client.execute(SkinManagerClient::clearAll);
        }
    }

    private static void destroyAllGpu(MinecraftClient client) {
        for (GpuEntry entry : GPU.values()) {
            destroy(client, entry);
        }
        GPU.clear();
    }

    private static void destroy(MinecraftClient client, GpuEntry entry) {
        try {
            if (client != null) {
                client.getTextureManager().destroyTexture(entry.id);
            } else {
                entry.texture.close();
            }
        } catch (Exception ignored) {
        }
    }

    record Mip(int width, int height, ByteBuffer data) {
    }

    static final class Prepared {
        final DxtEncoder.Format format;
        final Map<String, Mip[]> variants;
        final long bytes;

        Prepared(DxtEncoder.Format format, Map<String, Mip[]> variants) {
            this.format = format;
            this.variants = Map.copyOf(variants);
            long total = 0;
            for (Mip[] mips : variants.values()) {
                for (Mip mip : mips) {
                    total += mip.data.capacity();
                }
            }
            this.bytes = total;
        }
    }

    private static final class GpuEntry {
        final Identifier id;
        final CompressedSkinTexture texture;
        final Mip mip;
        long lastUsed;

        GpuEntry(Identifier id, CompressedSkinTexture texture, Mip mip, long lastUsed) {
            this.id = id;
            this.texture = texture;
            this.mip = mip;
            this.lastUsed = lastUsed;
        }
    }
}
