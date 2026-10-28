package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SkinManagerClient {
    private static final Map<UUID, NativeImage> SKIN_IMAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, NativeImage> TALKING_IMAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SLIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PREFERRED_SLIM = new ConcurrentHashMap<>();
    private static final FetchCoordinator<UUID> FETCH_COORDINATOR = new FetchCoordinator<>();
    private static final Map<UUID, Long> LAST_CHECK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_SKIN_URL = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_MOUTH_OPEN_URL = new ConcurrentHashMap<>();

    private static final Map<UUID, Identifier> VANILLA_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, NativeImage> ORIGINAL_PIXELS = new ConcurrentHashMap<>();
    private static final Set<UUID> FALLBACK_TEXTURES = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, NativeImageBackedTexture> SHARED_DYNAMIC_TEXTURE = new ConcurrentHashMap<>();

    // Content-hash based injection cache (replaces object-reference LAST_INJECTED_IMAGE)
    private static final ConcurrentMap<UUID, String> INJECTED_IMAGE_HASH = new ConcurrentHashMap<>();

    private static volatile long refreshIntervalMs = 5_000L;
    private static final long FAST_RETRY_MS = 2_000L;
    private static final Map<UUID, Long> FAST_RETRY_SCHEDULED = new ConcurrentHashMap<>();

    // Separate executors: download pool (parallel) vs render prep pool (single-threaded)
    private static final ExecutorService DOWNLOAD_EXECUTOR = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "CatSkinC-Download"); t.setDaemon(true); return t; });
    private static final ExecutorService RENDER_PREP_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CatSkinC-RenderPrep");
        thread.setDaemon(true);
        return thread;
    });

    private SkinManagerClient() {
    }

    public static void onSkinLookup(UUID uuid, Identifier vanillaId) {
        if (uuid == null || vanillaId == null) {
            return;
        }
        VANILLA_TEXTURES.putIfAbsent(uuid, vanillaId);
        // Process any pending managed injection for this UUID
        SkinOverrideStore.processPendingInjection(uuid, vanillaId);
    }

    static Identifier getVanillaIdentifier(UUID uuid) {
        return uuid == null ? null : VANILLA_TEXTURES.get(uuid);
    }

    public static void setRefreshIntervalMs(long intervalMs) {
        refreshIntervalMs = Math.max(500L, intervalMs);
        ModLog.debug("Skin refresh interval set to {} ms", refreshIntervalMs);
    }

    public static Identifier getOrFetch(AbstractClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        return getOrFetch(player.getUuid());
    }

    public static Identifier getOrFetch(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Identifier rendered = resolveRenderTexture(uuid, MyopiaLod.Level.L0, Double.NaN);
        if (rendered == null) {
            Long scheduledAt = FAST_RETRY_SCHEDULED.get(uuid);
            long now = System.currentTimeMillis();
            if (scheduledAt == null || now - scheduledAt >= FAST_RETRY_MS) {
                FAST_RETRY_SCHEDULED.put(uuid, now);
                fetchAndApplyFor(uuid);
            }
            return null;
        }
        FAST_RETRY_SCHEDULED.remove(uuid);
        if (shouldPoll(uuid)) {
            fetchAndApplyFor(uuid);
        }
        return rendered;
    }

    public static Identifier getCached(UUID uuid) {
        return uuid == null ? null : resolveRenderTexture(uuid, MyopiaLod.Level.L0, Double.NaN);
    }

    /**
     * Like {@link #getCached(UUID)} but returns a reduced-resolution variant for the
     * given Myopia LOD level. {@code L0} (or {@code null}) yields the full-resolution texture.
     */
    public static Identifier getCached(UUID uuid, MyopiaLod.Level level) {
        return getCached(uuid, level, Double.NaN);
    }

    /**
     * World-render variant: {@code distanceSq} is the squared distance to the local player and lets
     * the AUTO texture quality pick the lossless original for the nearest players.
     */
    public static Identifier getCached(UUID uuid, MyopiaLod.Level level, double distanceSq) {
        return uuid == null ? null
                : resolveRenderTexture(uuid, level == null ? MyopiaLod.Level.L0 : level, distanceSq);
    }

    /** True when this player has a CatSkinC-managed skin image loaded (i.e. eligible for Myopia LOD). */
    public static boolean hasManagedSkin(UUID uuid) {
        return uuid != null && (SKIN_IMAGES.containsKey(uuid) || CompressedSkinCache.has(uuid));
    }

    /** Installs a small synthetic skin for the development Myopia preview player. */
    static void installDebugSkin(UUID uuid) {
        if (uuid == null) return;
        NativeImage image = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                boolean stripe = ((x / 8) + (y / 8)) % 2 == 0;
                image.setColor(x, y, stripe ? 0xFFFF4D9D : 0xFF293B9F);
            }
        }
        NativeImage previous = SKIN_IMAGES.put(uuid, image);
        closeQuietly(previous);
        SLIM.put(uuid, false);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            Identifier id = Identifiers.mod("debug/" + uuid);
            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(copyImage(image)));
            VANILLA_TEXTURES.put(uuid, id);
            FALLBACK_TEXTURES.add(uuid);
        }
        MyopiaTextureCache.invalidate(uuid);
    }

    public static void ensureFetch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (!hasManagedSkin(uuid) || shouldPoll(uuid)) {
            if (!hasManagedSkin(uuid)) {
                Long scheduledAt = FAST_RETRY_SCHEDULED.get(uuid);
                long now = System.currentTimeMillis();
                if (scheduledAt == null || now - scheduledAt >= FAST_RETRY_MS) {
                    FAST_RETRY_SCHEDULED.put(uuid, now);
                    fetchAndApplyFor(uuid);
                }
            } else {
                fetchAndApplyFor(uuid);
            }
        }
    }

    public static void clearTexture(UUID uuid) {
        if (uuid == null) {
            return;
        }
        restorePixels(uuid);
        destroyTextures(MinecraftClient.getInstance(), uuid);
        LAST_SKIN_URL.remove(uuid);
        LAST_MOUTH_OPEN_URL.remove(uuid);
        LAST_CHECK.remove(uuid);
        ModLog.debug("Skin texture cleared for {}", uuid);
    }

    public static void forceFetch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        LAST_CHECK.remove(uuid);
        FAST_RETRY_SCHEDULED.remove(uuid);
        ServerApiClient.invalidateSelectedCache(uuid);
        if (!FETCH_COORDINATOR.force(uuid)) {
            return;
        }
        fetchAndApplyForStarted(uuid);
    }

    public static void refresh(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (!SkinOverrideStore.isManaged(uuid)) {
            restorePixels(uuid);
            destroyTextures(MinecraftClient.getInstance(), uuid);
        }
        LAST_SKIN_URL.remove(uuid);
        LAST_MOUTH_OPEN_URL.remove(uuid);
        if (!hasManagedSkin(uuid)) {
            Long scheduledAt = FAST_RETRY_SCHEDULED.get(uuid);
            long now = System.currentTimeMillis();
            if (scheduledAt == null || now - scheduledAt >= FAST_RETRY_MS) {
                FAST_RETRY_SCHEDULED.put(uuid, now);
                fetchAndApplyFor(uuid);
            }
        } else {
            fetchAndApplyFor(uuid);
        }
    }

    public static void fetchAndApplyFor(UUID uuid) {
        if (uuid == null || !FETCH_COORDINATOR.tryStart(uuid)) {
            if (uuid != null) {
                ModLog.trace("Fetch skipped (already in flight): {}", uuid);
            }
            return;
        }
        fetchAndApplyForStarted(uuid);
    }

    private static void fetchAndApplyForStarted(UUID uuid) {
        ModLog.trace("Fetch queued for {}", uuid);

        CompletableFuture<ServerApiClient.SelectedSkin> selected = ServerApiClient.fetchSelectedAsync(uuid);
        selected.thenCompose(skin -> {
            if (skin == null || skin.url() == null || skin.url().isBlank()) {
                ModLog.trace("No remote skin available for {}, clearing cached texture", uuid);
                LAST_SKIN_URL.remove(uuid);
                LAST_MOUTH_OPEN_URL.remove(uuid);
                SLIM.remove(uuid);
                destroyTextures(MinecraftClient.getInstance(), uuid);
                return CompletableFuture.completedFuture(null);
            }

            SLIM.put(uuid, skin.slim());

            String normalizedMouthOpen = normalizeUrl(skin.mouthOpenUrl());
            String previousSkinUrl = LAST_SKIN_URL.get(uuid);
            String previousMouthOpenUrl = LAST_MOUTH_OPEN_URL.get(uuid);
            if (Objects.equals(skin.url(), previousSkinUrl)
                    && Objects.equals(normalizedMouthOpen, previousMouthOpenUrl)) {
                ModLog.trace("Skipping download for {} (URLs unchanged)", uuid);
                return CompletableFuture.completedFuture(null);
            }

            LAST_SKIN_URL.put(uuid, skin.url());
            LAST_MOUTH_OPEN_URL.put(uuid, normalizedMouthOpen);

            CompletableFuture<ServerApiClient.DownloadedImage> skinFuture =
                    ServerApiClient.downloadImageWithBytesAsync(skin.url());
            CompletableFuture<ServerApiClient.DownloadedImage> mouthOpenFuture = normalizedMouthOpen.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : ServerApiClient.downloadImageWithBytesAsync(normalizedMouthOpen);
            final boolean mouthOpenRequested = !normalizedMouthOpen.isEmpty();
            return skinFuture
                    .thenCombine(mouthOpenFuture, (skinResult, mouthOpenResult) -> new DownloadedImages(
                            skinResult == null ? null : skinResult.image(),
                            mouthOpenResult == null ? null : mouthOpenResult.image(),
                            mouthOpenRequested,
                            skinResult == null ? null : skinResult.bytes(),
                            mouthOpenResult == null ? null : mouthOpenResult.bytes()));
        }).whenCompleteAsync((images, throwable) -> {
            if (throwable != null) {
                ModLog.error("Skin apply failed for uuid=" + uuid, throwable);
                finishFetch(uuid);
                return;
            }
            if (images == null) {
                ModLog.trace("No texture update for {}", uuid);
                finishFetch(uuid);
                return;
            }
            LAST_CHECK.put(uuid, System.currentTimeMillis());

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                ModLog.trace("Client not ready; dropping texture update for {}", uuid);
                closeQuietly(images.skinImage);
                closeQuietly(images.mouthOpenImage);
                finishFetch(uuid);
                return;
            }
            RENDER_PREP_EXECUTOR.execute(() -> {
                try {
                if (images.skinImage == null) {
                    ModLog.warn("Skin image download returned null for {}", uuid);
                    LAST_SKIN_URL.remove(uuid);
                    closeQuietly(images.mouthOpenImage);
                    return;
                }

                // Enforce maxSkinResolution and the texture quality cap - downscale if needed
                ModConfig.TextureQuality quality = ModConfig.get().getTextureQuality();
                int maxRes = quality.effectiveMaxResolution(ModConfig.get().getMaxSkinResolution());
                NativeImage skinImage = downscaleToFit(uuid, images.skinImage, maxRes);
                NativeImage mouthOpenImage = images.mouthOpenImage == null
                        ? null : downscaleToFit(uuid, images.mouthOpenImage, maxRes);

                NativeImage talkingImage = createOverlayImage(uuid, skinImage, mouthOpenImage,
                        "mouth-open");
                if (images.mouthOpenRequested && talkingImage == null) {
                    ModLog.warn("Mouth-open texture missing after download for {}", uuid);
                }

                if (CompressedSkinCache.shouldCompress(quality, skinImage.getWidth(), skinImage.getHeight())) {
                    long started = System.nanoTime();
                    CompressedSkinCache.LosslessSource lossless = new CompressedSkinCache.LosslessSource(
                            images.skinBytes, talkingImage == null ? null : images.mouthOpenBytes,
                            ModConfig.get().getMaxSkinResolution());
                    CompressedSkinCache.Prepared prepared =
                            CompressedSkinCache.prepare(quality, skinImage, talkingImage, lossless);
                    ModLog.debug("Compressed skin for {} ({}x{}, {}) to {} KB in {} ms",
                            uuid, skinImage.getWidth(), skinImage.getHeight(), quality,
                            prepared.bytes / 1024, (System.nanoTime() - started) / 1_000_000L);
                    closeQuietly(skinImage);
                    closeQuietly(talkingImage);
                    // Drop any RGBA copies so only the compressed blocks stay resident.
                    closeQuietly(SKIN_IMAGES.remove(uuid));
                    closeQuietly(TALKING_IMAGES.remove(uuid));
                    INJECTED_IMAGE_HASH.remove(uuid);
                    MyopiaTextureCache.invalidate(uuid);
                    CompressedSkinCache.put(uuid, prepared);
                    client.execute(() -> releaseLegacyTexture(uuid));
                    if (SkinOverrideStore.isManaged(uuid)) {
                        SkinOverrideStore.clear(uuid);
                    }
                    ModLog.trace("Compressed texture applied for {} (talkingVariant={})", uuid, talkingImage != null);
                    return;
                }
                CompressedSkinCache.invalidate(uuid);

                NativeImage previousSkin = SKIN_IMAGES.put(uuid, skinImage);
                closeQuietly(previousSkin);
                if (talkingImage != null) {
                    NativeImage previousTalking = TALKING_IMAGES.put(uuid, talkingImage);
                    closeQuietly(previousTalking);
                } else {
                    NativeImage removed = TALKING_IMAGES.remove(uuid);
                    closeQuietly(removed);
                }

                if (SkinOverrideStore.isManaged(uuid)) {
                    SkinOverrideStore.clear(uuid);
                }

                ModLog.trace("Texture applied for {} (talkingVariant={})", uuid, talkingImage != null);
                } finally {
                    finishFetch(uuid);
                }
            });
        }, DOWNLOAD_EXECUTOR);
    }

    /** Frees the full-resolution RGBA texture once a player has switched to a compressed skin. */
    private static void releaseLegacyTexture(UUID uuid) {
        if (!CompressedSkinCache.has(uuid)) {
            return;
        }
        restorePixels(uuid);
        if (FALLBACK_TEXTURES.remove(uuid)) {
            Identifier fallbackId = VANILLA_TEXTURES.remove(uuid);
            MinecraftClient client = MinecraftClient.getInstance();
            if (fallbackId != null && client != null) {
                client.getTextureManager().destroyTexture(fallbackId);
            }
        }
    }

    /** Scales {@code image} down (keeping aspect ratio) so neither side exceeds {@code maxRes}. */
    static NativeImage downscaleToFit(UUID uuid, NativeImage image, int maxRes) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= maxRes && h <= maxRes) {
            return image;
        }
        double scale = (double) maxRes / Math.max(w, h);
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        ModLog.debug("Downscaling skin for {} from {}x{} to {}x{}", uuid, w, h, tw, th);
        NativeImage downscaled = new NativeImage(tw, th, true);
        blitPixels(downscaled, image);
        image.close();
        return downscaled;
    }

    private static void finishFetch(UUID uuid) {
        if (FETCH_COORDINATOR.finish(uuid)) {
            LAST_CHECK.remove(uuid);
            ServerApiClient.invalidateSelectedCache(uuid);
            fetchAndApplyForStarted(uuid);
        }
    }

    public static Boolean isSlimOrNull(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Boolean direct = SLIM.get(uuid);
        if (direct != null) {
            return direct;
        }
        return PREFERRED_SLIM.get(uuid);
    }

    public static void setSlim(UUID uuid, boolean slim) {
        if (uuid == null) {
            return;
        }
        SLIM.put(uuid, slim);
        PREFERRED_SLIM.put(uuid, slim);
    }

    public static void clearAll() {
        int cacheSize = SKIN_IMAGES.size();
        for (NativeImage image : SKIN_IMAGES.values()) {
            closeQuietly(image);
        }
        for (NativeImage image : TALKING_IMAGES.values()) {
            closeQuietly(image);
        }
        SKIN_IMAGES.clear();
        TALKING_IMAGES.clear();
        SLIM.clear();
        PREFERRED_SLIM.clear();
        LAST_CHECK.clear();
        LAST_SKIN_URL.clear();
        LAST_MOUTH_OPEN_URL.clear();
        FETCH_COORDINATOR.clear();
        FAST_RETRY_SCHEDULED.clear();
        INJECTED_IMAGE_HASH.clear();
        restoreAllPixels();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            for (UUID uuid : FALLBACK_TEXTURES) {
                Identifier fallbackId = VANILLA_TEXTURES.get(uuid);
                if (fallbackId != null) {
                    client.getTextureManager().destroyTexture(fallbackId);
                }
            }
        }
        FALLBACK_TEXTURES.clear();
        VANILLA_TEXTURES.clear();
        SHARED_DYNAMIC_TEXTURE.clear();
        MyopiaTextureCache.clearAll();
        CompressedSkinCache.clearAll();
        MyopiaLodTracker.get().reset();
        ModLog.debug("Skin caches cleared ({} entries)", cacheSize);
    }

    public static void injectPixels(UUID uuid, NativeImage source) {
        if (uuid == null || source == null) {
            return;
        }
        // Skip if content hash matches (avoids re-injecting unchanged skin every frame)
        String hash = computeImageHash(source);
        if (hash.equals(INJECTED_IMAGE_HASH.get(uuid))) {
            return;
        }
        Identifier vanillaId = VANILLA_TEXTURES.get(uuid);
        if (vanillaId == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        AbstractTexture tex = client.getTextureManager().getTexture(vanillaId);
        // Our own per-UUID texture is sized for the previous skin; if the new skin's size differs
        // (e.g. 64x64 -> HD), recreate it below instead of squashing the new skin into it.
        boolean ownedSizeMismatch = FALLBACK_TEXTURES.contains(uuid)
                && tex instanceof NativeImageBackedTexture owned
                && owned.getImage() != null
                && (owned.getImage().getWidth() != source.getWidth()
                        || owned.getImage().getHeight() != source.getHeight());
        if (tex instanceof NativeImageBackedTexture nibTex && !ownedSizeMismatch) {
            NativeImage target = nibTex.getImage();
            if (target == null) {
                return;
            }
            if (!FALLBACK_TEXTURES.contains(uuid) && !ORIGINAL_PIXELS.containsKey(uuid)) {
                ORIGINAL_PIXELS.put(uuid, copyImage(target));
            }
            blitPixels(target, source);
            nibTex.upload();
        } else {
            if (FALLBACK_TEXTURES.remove(uuid)) {
                Identifier stale = VANILLA_TEXTURES.get(uuid);
                if (stale != null) {
                    client.getTextureManager().destroyTexture(stale);
                }
            }
            int w = source.getWidth();
            int h = source.getHeight();
            NativeImage target = new NativeImage(w, h, true);
            blitPixels(target, source);
            NativeImageBackedTexture fallbackTex = new NativeImageBackedTexture(target);
            Identifier fallbackId = Identifiers.mod("skins/" + uuid);
            client.getTextureManager().registerTexture(fallbackId, fallbackTex);
            VANILLA_TEXTURES.put(uuid, fallbackId);
            FALLBACK_TEXTURES.add(uuid);
        }
        INJECTED_IMAGE_HASH.put(uuid, hash);
    }

    /**
     * Computes a fast content hash of a NativeImage for change detection.
     * Uses first/last rows + dimensions for speed (good enough for skin change detection).
     */
    private static String computeImageHash(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        // Identity is part of the key: the talking variant differs only around the mouth,
        // which the sampled rows below can miss.
        long hash = (w * 31L + h) * 31 + System.identityHashCode(image);
        // Sample first row, middle row, last row
        int[] rows = {0, h / 2, h - 1};
        for (int row : rows) {
            for (int x = 0; x < w; x += Math.max(1, w / 16)) { // Sample 16 points per row
                hash = hash * 31 + image.getColor(x, row);
            }
        }
        return Long.toHexString(hash);
    }

    private static void restorePixels(UUID uuid) {
        if (uuid == null) {
            return;
        }
        NativeImage original = ORIGINAL_PIXELS.remove(uuid);
        if (original == null) {
            return;
        }
        Identifier vanillaId = VANILLA_TEXTURES.get(uuid);
        if (vanillaId == null) {
            closeQuietly(original);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            closeQuietly(original);
            return;
        }
        AbstractTexture tex = client.getTextureManager().getTexture(vanillaId);
        if (!(tex instanceof NativeImageBackedTexture nibTex)) {
            closeQuietly(original);
            return;
        }
        NativeImage target = nibTex.getImage();
        if (target == null) {
            closeQuietly(original);
            return;
        }
        int w = Math.min(original.getWidth(), target.getWidth());
        int h = Math.min(original.getHeight(), target.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                target.setColor(x, y, original.getColor(x, y));
            }
        }
        nibTex.upload();
        closeQuietly(original);
    }

    private static void restoreAllPixels() {
        for (UUID uuid : ORIGINAL_PIXELS.keySet()) {
            restorePixels(uuid);
        }
    }

    static void blitPixels(NativeImage target, NativeImage source) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int tw = target.getWidth();
        int th = target.getHeight();
        if (sw == tw && sh == th) {
            // Same dimensions - direct copy
            for (int y = 0; y < th; y++) {
                for (int x = 0; x < tw; x++) {
                    target.setColor(x, y, source.getColor(x, y));
                }
            }
            return;
        }
        // Pre-compute scale ratios to avoid division in inner loop
        float xRatio = (float) sw / tw;
        float yRatio = (float) sh / th;
        int maxSx = sw - 1;
        int maxSy = sh - 1;
        if (sw < tw || sh < th) {
            // Source is smaller - need to scale up to target dimensions
            // Use nearest-neighbor sampling (pixel-perfect for power-of-2 upscale)
            for (int y = 0; y < th; y++) {
                int sy = Math.min(maxSy, (int) (y * yRatio));
                for (int x = 0; x < tw; x++) {
                    int sx = Math.min(maxSx, (int) (x * xRatio));
                    target.setColor(x, y, source.getColor(sx, sy));
                }
            }
            return;
        }
        // Source is larger - need to scale down to target dimensions
        // Use nearest-neighbor sampling (pixel-perfect for power-of-2 downscale)
        for (int y = 0; y < th; y++) {
            int sy = Math.min(maxSy, (int) (y * yRatio));
            for (int x = 0; x < tw; x++) {
                int sx = Math.min(maxSx, (int) (x * xRatio));
                target.setColor(x, y, source.getColor(sx, sy));
            }
        }
    }

    private static NativeImage copyImage(NativeImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        NativeImage copy = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy.setColor(x, y, source.getColor(x, y));
            }
        }
        return copy;
    }

    private static Identifier resolveRenderTexture(UUID uuid, MyopiaLod.Level level, double distanceSq) {
        if (CompressedSkinCache.has(uuid)) {
            // Talking just switches to another prebuilt texture; no pixel copy or re-upload.
            String variant = VoiceActivityTracker.isSpeaking(uuid)
                    && CompressedSkinCache.hasVariant(uuid, MyopiaTextureCache.VARIANT_TALKING)
                    ? MyopiaTextureCache.VARIANT_TALKING : MyopiaTextureCache.VARIANT_SKIN;
            return CompressedSkinCache.get(uuid, variant, level, distanceSq);
        }
        NativeImage image = SKIN_IMAGES.get(uuid);
        if (image == null) {
            return null;
        }
        String variant = MyopiaTextureCache.VARIANT_SKIN;
        if (VoiceActivityTracker.isSpeaking(uuid)) {
            NativeImage talking = TALKING_IMAGES.get(uuid);
            if (talking != null) {
                image = talking;
                variant = MyopiaTextureCache.VARIANT_TALKING;
            }
        }
        if (!level.isFull()) {
            Identifier lod = MyopiaTextureCache.get(uuid, variant, level, image);
            if (lod != null) {
                return lod;
            }
            // Fall through to full resolution if the LOD texture could not be built.
        }
        injectPixels(uuid, image);
        return VANILLA_TEXTURES.get(uuid);
    }

    private static boolean shouldPoll(UUID uuid) {
        long now = System.currentTimeMillis();
        long lastCheck = LAST_CHECK.getOrDefault(uuid, 0L);
        return now - lastCheck >= refreshIntervalMs;
    }

    private static String normalizeUrl(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    private static void destroyTextures(MinecraftClient client, UUID uuid) {
        if (uuid == null) {
            return;
        }
        NativeImage skin = SKIN_IMAGES.remove(uuid);
        closeQuietly(skin);
        NativeImage talking = TALKING_IMAGES.remove(uuid);
        closeQuietly(talking);
        MyopiaTextureCache.invalidate(uuid);
        CompressedSkinCache.invalidate(uuid);
        MyopiaLodTracker.get().forget(uuid);
        if (client != null && FALLBACK_TEXTURES.remove(uuid)) {
            Identifier fallbackId = VANILLA_TEXTURES.get(uuid);
            if (fallbackId != null) {
                client.getTextureManager().destroyTexture(fallbackId);
            }
        }
        VANILLA_TEXTURES.remove(uuid);
    }

    static NativeImage createOverlayImage(
            UUID uuid, NativeImage skinImage, NativeImage overlayImage, String variantName) {
        if (overlayImage == null) {
            return null;
        }
        try {
            int skinWidth = skinImage.getWidth();
            int skinHeight = skinImage.getHeight();
            int overlayWidth = overlayImage.getWidth();
            int overlayHeight = overlayImage.getHeight();
            if (skinWidth <= 0 || skinHeight <= 0 || overlayWidth <= 0 || overlayHeight <= 0) {
                return null;
            }

            int targetWidth = Math.max(skinWidth, overlayWidth);
            int targetHeight = Math.max(skinHeight, overlayHeight);

            if (targetWidth != skinWidth || targetHeight != skinHeight) {
                ModLog.debug("Scaling skin up to match high-res overlay ({} for {}): {}x{} -> {}x{}",
                        variantName, uuid, skinWidth, skinHeight, targetWidth, targetHeight);
            }

            NativeImage merged = new NativeImage(targetWidth, targetHeight, true);
            for (int y = 0; y < targetHeight; y++) {
                int sy = Math.min(skinHeight - 1, (y * skinHeight) / targetHeight);
                int oy = Math.min(overlayHeight - 1, (y * overlayHeight) / targetHeight);

                for (int x = 0; x < targetWidth; x++) {
                    int sx = Math.min(skinWidth - 1, (x * skinWidth) / targetWidth);
                    int ox = Math.min(overlayWidth - 1, (x * overlayWidth) / targetWidth);

                    int overlayColor = overlayImage.getColor(ox, oy);
                    int alpha = (overlayColor >>> 24) & 0xFF;
                    merged.setColor(x, y, alpha > 0 ? overlayColor : skinImage.getColor(sx, sy));
                }
            }
            return merged;
        } catch (Exception exception) {
            ModLog.warn("Failed to build {} texture for {}", variantName, uuid, exception);
            return null;
        } finally {
            closeQuietly(overlayImage);
        }
    }

    private static void closeQuietly(NativeImage image) {
        if (image == null) {
            return;
        }
        try {
            image.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * Resets all internal state for testing purposes.
     * This method is package-private and should only be used in tests.
     */
    static void resetForTesting() {
        SKIN_IMAGES.clear();
        TALKING_IMAGES.clear();
        SLIM.clear();
        PREFERRED_SLIM.clear();
        LAST_CHECK.clear();
        LAST_SKIN_URL.clear();
        LAST_MOUTH_OPEN_URL.clear();
        FETCH_COORDINATOR.clear();
        refreshIntervalMs = 5_000L;
        FAST_RETRY_SCHEDULED.clear();
        INJECTED_IMAGE_HASH.clear();
        for (NativeImage image : ORIGINAL_PIXELS.values()) {
            closeQuietly(image);
        }
        VANILLA_TEXTURES.clear();
        FALLBACK_TEXTURES.clear();
        ORIGINAL_PIXELS.clear();
        SHARED_DYNAMIC_TEXTURE.clear();
        MyopiaTextureCache.clearAll();
        CompressedSkinCache.clearAll();
        MyopiaLodTracker.get().reset();
    }

    private record DownloadedImages(
            NativeImage skinImage,
            NativeImage mouthOpenImage,
            boolean mouthOpenRequested,
            byte[] skinBytes,
            byte[] mouthOpenBytes) {
    }
}
