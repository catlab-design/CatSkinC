package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SkinManagerClient {
    private static final Map<UUID, NativeImage> SKIN_IMAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, NativeImage> TALKING_IMAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SLIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PREFERRED_SLIM = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_CHECK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_SKIN_URL = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_MOUTH_OPEN_URL = new ConcurrentHashMap<>();

    private static final Map<UUID, Identifier> VANILLA_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, NativeImage> ORIGINAL_PIXELS = new ConcurrentHashMap<>();
    private static final Set<UUID> FALLBACK_TEXTURES = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, NativeImageBackedTexture> SHARED_DYNAMIC_TEXTURE = new ConcurrentHashMap<>();

    private static volatile long refreshIntervalMs = 5_000L;
    private static final long FAST_RETRY_MS = 2_000L;
    private static final Map<UUID, Long> FAST_RETRY_SCHEDULED = new ConcurrentHashMap<>();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CatSkinC-SkinManager");
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
        Identifier rendered = resolveRenderTexture(uuid);
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
        return uuid == null ? null : resolveRenderTexture(uuid);
    }

    public static void ensureFetch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (!SKIN_IMAGES.containsKey(uuid) || shouldPoll(uuid)) {
            if (!SKIN_IMAGES.containsKey(uuid)) {
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
        if (!SKIN_IMAGES.containsKey(uuid)) {
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
        if (!SKIN_IMAGES.containsKey(uuid)) {
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
        if (uuid == null || !IN_FLIGHT.add(uuid)) {
            if (uuid != null) {
                ModLog.trace("Fetch skipped (already in flight): {}", uuid);
            }
            return;
        }
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

            CompletableFuture<NativeImage> skinFuture = ServerApiClient.downloadImageAsync(skin.url());
            CompletableFuture<NativeImage> mouthOpenFuture = normalizedMouthOpen.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : ServerApiClient.downloadImageAsync(normalizedMouthOpen);
            final boolean mouthOpenRequested = !normalizedMouthOpen.isEmpty();
            return skinFuture
                    .thenCombine(mouthOpenFuture, (skinImage, mouthOpenImage) -> new DownloadedImages(
                            skinImage,
                            mouthOpenImage,
                            mouthOpenRequested));
        }).whenCompleteAsync((images, throwable) -> {
            IN_FLIGHT.remove(uuid);
            if (throwable != null) {
                ModLog.error("Skin apply failed for uuid=" + uuid, throwable);
                return;
            }
            if (images == null) {
                ModLog.trace("No texture update for {}", uuid);
                return;
            }
            LAST_CHECK.put(uuid, System.currentTimeMillis());

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                ModLog.trace("Client not ready; dropping texture update for {}", uuid);
                closeQuietly(images.skinImage);
                closeQuietly(images.mouthOpenImage);
                return;
            }
            client.execute(() -> {
                if (images.skinImage == null) {
                    ModLog.warn("Skin image download returned null for {}", uuid);
                    LAST_SKIN_URL.remove(uuid);
                    closeQuietly(images.mouthOpenImage);
                    return;
                }

                NativeImage talkingImage = createOverlayImage(uuid, images.skinImage, images.mouthOpenImage,
                        "mouth-open");
                if (images.mouthOpenRequested && talkingImage == null) {
                    ModLog.warn("Mouth-open texture missing after download for {}", uuid);
                }

                NativeImage previousSkin = SKIN_IMAGES.put(uuid, images.skinImage);
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
            });
        }, EXECUTOR);
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
        IN_FLIGHT.clear();
        FAST_RETRY_SCHEDULED.clear();
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
        ModLog.debug("Skin caches cleared ({} entries)", cacheSize);
    }

    public static void injectPixels(UUID uuid, NativeImage source) {
        if (uuid == null || source == null) {
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
        if (tex instanceof NativeImageBackedTexture nibTex) {
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

    private static void blitPixels(NativeImage target, NativeImage source) {
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
        if (sw < tw || sh < th) {
            // Source is smaller - need to scale up to target dimensions
            // Use nearest-neighbor sampling (pixel-perfect for power-of-2 upscale)
            for (int y = 0; y < th; y++) {
                int sy = Math.min(sh - 1, (y * sh) / th);
                for (int x = 0; x < tw; x++) {
                    int sx = Math.min(sw - 1, (x * sw) / tw);
                    target.setColor(x, y, source.getColor(sx, sy));
                }
            }
            return;
        }
        // Source is larger - need to scale down to target dimensions
        // Use nearest-neighbor sampling (pixel-perfect for power-of-2 downscale)
        for (int y = 0; y < th; y++) {
            int sy = Math.min(sh - 1, (y * sh) / th);
            for (int x = 0; x < tw; x++) {
                int sx = Math.min(sw - 1, (x * sw) / tw);
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

    private static Identifier resolveRenderTexture(UUID uuid) {
        NativeImage image = SKIN_IMAGES.get(uuid);
        if (image == null) {
            return null;
        }
        if (VoiceActivityTracker.isSpeaking(uuid)) {
            NativeImage talking = TALKING_IMAGES.get(uuid);
            if (talking != null) {
                image = talking;
            }
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
        if (client != null && FALLBACK_TEXTURES.remove(uuid)) {
            Identifier fallbackId = VANILLA_TEXTURES.get(uuid);
            if (fallbackId != null) {
                client.getTextureManager().destroyTexture(fallbackId);
            }
        }
        VANILLA_TEXTURES.remove(uuid);
    }

    private static NativeImage createOverlayImage(
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
        IN_FLIGHT.clear();
        refreshIntervalMs = 5_000L;
        FAST_RETRY_SCHEDULED.clear();
        for (NativeImage image : ORIGINAL_PIXELS.values()) {
            closeQuietly(image);
        }
        VANILLA_TEXTURES.clear();
        FALLBACK_TEXTURES.clear();
        ORIGINAL_PIXELS.clear();
        SHARED_DYNAMIC_TEXTURE.clear();
    }

    private record DownloadedImages(
            NativeImage skinImage,
            NativeImage mouthOpenImage,
            boolean mouthOpenRequested) {
    }
}