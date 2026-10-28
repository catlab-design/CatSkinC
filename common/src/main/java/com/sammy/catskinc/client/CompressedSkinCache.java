package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds S3TC-compressed skins for the lossy texture quality presets.
 *
 * <p>Every skin is encoded once (on the render-prep thread) at all four Myopia levels and kept in
 * RAM as compressed blocks only; no RGBA copy survives. GPU textures are created lazily, one per
 * (player, variant, level) actually being drawn, and evicted after {@link #IDLE_EVICT_MS} without
 * use, so VRAM tracks what is on screen rather than everyone ever seen.
 *
 * <p>Under {@link ModConfig.TextureQuality#AUTO}, the original PNG bytes are also kept. When a
 * player is drawn in the world at full-resolution level and wins one of the lossless slots
 * ({@link LosslessSlots}), the PNG is decoded in the background and uploaded as an uncompressed
 * texture; until it is ready, and for everyone without a slot, the compressed level is used.
 */
public final class CompressedSkinCache {
    /** Skins smaller than this stay RGBA: 4x4 block compression visibly damages 64px pixel art. */
    public static final int MIN_COMPRESS_SIZE = 256;
    static final long IDLE_EVICT_MS = 15_000L;
    private static final String LOSSLESS_LEVEL = "raw";

    private static final Map<UUID, Prepared> PREPARED = new ConcurrentHashMap<>();
    // Render thread only.
    private static final Map<String, GpuEntry> GPU = new HashMap<>();
    private static final Set<String> LOSSLESS_PENDING = new HashSet<>();
    private static final LosslessSlots LOSSLESS_SLOTS = new LosslessSlots(CompressedSkinCache::releaseLossless);
    private static volatile Boolean s3tcSupported;

    private static final ExecutorService LOSSLESS_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CatSkinC-LosslessDecode");
        thread.setDaemon(true);
        return thread;
    });

    private CompressedSkinCache() {
    }

    /** True when {@code quality} compresses and this skin is large enough to benefit. */
    static boolean shouldCompress(ModConfig.TextureQuality quality, int width, int height) {
        return quality.format() != null
                && Math.min(width, height) >= MIN_COMPRESS_SIZE
                && !Boolean.FALSE.equals(s3tcSupported);
    }

    /** Encodes the skin (and optional talking variant) at every Myopia level. Any thread. */
    static Prepared prepare(ModConfig.TextureQuality quality, NativeImage skin, NativeImage talking,
                            LosslessSource lossless) {
        Map<String, Mip[]> variants = new HashMap<>();
        variants.put(MyopiaTextureCache.VARIANT_SKIN, encodeLevels(quality, skin));
        if (talking != null) {
            variants.put(MyopiaTextureCache.VARIANT_TALKING, encodeLevels(quality, talking));
        }
        return new Prepared(variants, quality.losslessWhenNear() ? lossless : null);
    }

    static Mip[] encodeLevels(ModConfig.TextureQuality quality, NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = image.getColor(x, y);
            }
        }
        return encodeLevels(quality, pixels, w, h);
    }

    static Mip[] encodeLevels(ModConfig.TextureQuality quality, int[] pixels, int w, int h) {
        MyopiaLod.Level[] levels = MyopiaLod.Level.values();
        Mip[] mips = new Mip[levels.length];
        for (MyopiaLod.Level level : levels) {
            int lw = level.targetSize(w);
            int lh = level.targetSize(h);
            DxtEncoder.Format format = quality.formatFor(level);
            int[] scaled = DxtEncoder.resampleNearest(pixels, w, h, lw, lh);
            mips[level.ordinal()] = new Mip(lw, lh, format, DxtEncoder.encode(scaled, lw, lh, format));
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
     * {@code worldDistanceSq} is the squared distance to the local player when drawn in the world,
     * or NaN for GUIs (which never take a lossless slot).
     * Must be called on the render thread. Returns null when unavailable.
     */
    static Identifier get(UUID uuid, String variant, MyopiaLod.Level level, double worldDistanceSq) {
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
        long now = System.currentTimeMillis();

        if (level.isFull() && prepared.lossless != null && !Double.isNaN(worldDistanceSq)
                && LOSSLESS_SLOTS.acquire(uuid, worldDistanceSq, now, ModConfig.get().getMaxLosslessPlayers())) {
            Identifier lossless = getLossless(client, uuid, variant, prepared, now);
            if (lossless != null) {
                return lossless;
            }
            // Still decoding: show the compressed level meanwhile.
        }

        Mip mip = mips[level.ordinal()];
        String key = key(uuid, variant, String.valueOf(level.ordinal()));
        GpuEntry entry = GPU.get(key);
        if (entry != null && entry.source == mip && client.getTextureManager().getTexture(entry.id) == entry.texture) {
            entry.lastUsed = now;
            return entry.id;
        }
        if (entry != null) {
            GPU.remove(key);
            destroy(client, entry);
        }

        Identifier id = Identifiers.mod("skins/" + uuid + "/" + variant + "/bc" + level.ordinal());
        CompressedSkinTexture texture = new CompressedSkinTexture(mip.width, mip.height, mip.format, mip.data);
        client.getTextureManager().registerTexture(id, texture);
        if (!texture.upload()) {
            client.getTextureManager().destroyTexture(id);
            onUploadFailed();
            return null;
        }
        GPU.put(key, new GpuEntry(id, texture, texture.byteSize(), mip, now));
        ModLog.trace("Compressed skin uploaded for {} ({}, {}): {}x{} {} ({} KB)",
                uuid, variant, level, mip.width, mip.height, mip.format, texture.byteSize() / 1024);
        return id;
    }

    private static Identifier getLossless(MinecraftClient client, UUID uuid, String variant,
                                          Prepared prepared, long now) {
        String key = key(uuid, variant, LOSSLESS_LEVEL);
        GpuEntry entry = GPU.get(key);
        if (entry != null && entry.source == prepared.lossless
                && client.getTextureManager().getTexture(entry.id) == entry.texture) {
            entry.lastUsed = now;
            return entry.id;
        }
        if (entry != null) {
            GPU.remove(key);
            destroy(client, entry);
        }
        if (LOSSLESS_PENDING.add(key)) {
            LosslessSource source = prepared.lossless;
            boolean talking = MyopiaTextureCache.VARIANT_TALKING.equals(variant);
            LOSSLESS_EXECUTOR.execute(() -> {
                NativeImage image = null;
                try {
                    image = decodeLossless(uuid, source, talking);
                } catch (Exception exception) {
                    ModLog.warn("Lossless skin decode failed for {}", uuid, exception);
                }
                NativeImage decoded = image;
                client.execute(() -> uploadLossless(client, uuid, variant, key, source, decoded));
            });
        }
        return null;
    }

    private static void uploadLossless(MinecraftClient client, UUID uuid, String variant, String key,
                                       LosslessSource source, NativeImage image) {
        LOSSLESS_PENDING.remove(key);
        if (image == null) {
            return;
        }
        Prepared current = PREPARED.get(uuid);
        if (current == null || current.lossless != source || !LOSSLESS_SLOTS.holds(uuid)) {
            // Skin changed or the player moved away while decoding.
            image.close();
            return;
        }
        Identifier id = Identifiers.mod("skins/" + uuid + "/" + variant + "/" + LOSSLESS_LEVEL);
        RawSkinTexture texture = new RawSkinTexture(image);
        client.getTextureManager().registerTexture(id, texture);
        texture.upload();
        GPU.put(key, new GpuEntry(id, texture, texture.byteSize(), source, System.currentTimeMillis()));
        ModLog.trace("Lossless skin uploaded for {} ({}): {} KB", uuid, variant, texture.byteSize() / 1024);
    }

    /** Decodes the original PNG(s) on a background thread, rebuilding the talking variant if asked. */
    static NativeImage decodeLossless(UUID uuid, LosslessSource source, boolean talking) throws IOException {
        NativeImage skin = SkinManagerClient.downscaleToFit(uuid, readPng(source.skinPng), source.maxResolution);
        if (!talking || source.mouthOpenPng == null) {
            return skin;
        }
        NativeImage mouth = SkinManagerClient.downscaleToFit(uuid, readPng(source.mouthOpenPng), source.maxResolution);
        NativeImage merged = SkinManagerClient.createOverlayImage(uuid, skin, mouth, "mouth-open");
        if (merged == null) {
            return skin;
        }
        skin.close();
        return merged;
    }

    private static NativeImage readPng(byte[] bytes) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return NativeImage.read(in);
        }
    }

    /** Releases GPU textures that are stale or unused. Must be called on the render thread. */
    static void sweep() {
        LOSSLESS_SLOTS.releaseIdle(System.currentTimeMillis());
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
            LOSSLESS_PENDING.clear();
            LOSSLESS_SLOTS.clear();
            return;
        }
        if (client.isOnThread()) {
            destroyAllGpu(client);
        } else {
            client.execute(() -> destroyAllGpu(client));
        }
    }

    /** VRAM held by uploaded skins (compressed and lossless), in bytes. Render thread. */
    static long gpuBytes() {
        long total = 0;
        for (GpuEntry entry : GPU.values()) {
            total += entry.bytes;
        }
        return total;
    }

    /** RAM held by compressed skin data and kept PNG originals, in bytes. */
    static long ramBytes() {
        long total = 0;
        for (Prepared prepared : PREPARED.values()) {
            total += prepared.bytes;
        }
        return total;
    }

    /** Players currently rendering their lossless original. Render thread. */
    static int losslessPlayers() {
        return LOSSLESS_SLOTS.size();
    }

    /** Frees a player's lossless textures as soon as they lose their slot. Render thread. */
    private static void releaseLossless(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        String prefix = uuid + "/";
        List<String> keys = new ArrayList<>();
        for (String key : GPU.keySet()) {
            if (key.startsWith(prefix) && key.endsWith("/" + LOSSLESS_LEVEL)) {
                keys.add(key);
            }
        }
        for (String key : keys) {
            destroy(client, GPU.remove(key));
        }
    }

    private static String key(UUID uuid, String variant, String level) {
        return uuid + "/" + variant + "/" + level;
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
        if (entry.source == prepared.lossless) {
            return true;
        }
        for (Mip[] mips : prepared.variants.values()) {
            for (Mip mip : mips) {
                if (mip == entry.source) {
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
        LOSSLESS_PENDING.clear();
        LOSSLESS_SLOTS.clear();
    }

    private static void destroy(MinecraftClient client, GpuEntry entry) {
        if (entry == null) {
            return;
        }
        try {
            if (client != null) {
                client.getTextureManager().destroyTexture(entry.id);
            } else {
                entry.texture.close();
            }
        } catch (Exception ignored) {
        }
    }

    record Mip(int width, int height, DxtEncoder.Format format, ByteBuffer data) {
    }

    /** The original downloaded PNG(s), kept so the lossless texture can be rebuilt on demand. */
    record LosslessSource(byte[] skinPng, byte[] mouthOpenPng, int maxResolution) {
        long byteSize() {
            return skinPng.length + (mouthOpenPng == null ? 0 : mouthOpenPng.length);
        }
    }

    static final class Prepared {
        final Map<String, Mip[]> variants;
        final LosslessSource lossless;
        final long bytes;

        Prepared(Map<String, Mip[]> variants, LosslessSource lossless) {
            this.variants = Map.copyOf(variants);
            this.lossless = lossless;
            long total = lossless == null ? 0 : lossless.byteSize();
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
        final AbstractTexture texture;
        final long bytes;
        final Object source;
        long lastUsed;

        GpuEntry(Identifier id, AbstractTexture texture, long bytes, Object source, long lastUsed) {
            this.id = id;
            this.texture = texture;
            this.bytes = bytes;
            this.source = source;
            this.lastUsed = lastUsed;
        }
    }
}
