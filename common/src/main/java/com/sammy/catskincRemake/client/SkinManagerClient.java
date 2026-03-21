package com.sammy.catskincRemake.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

public final class SkinManagerClient {
    private static final Map<UUID, ResourceLocation> BASE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceLocation> IDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceLocation> TALKING_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SLIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PREFERRED_SLIM = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_CHECK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_SKIN_URL = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_MOUTH_OPEN_URL = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_MOUTH_CLOSE_URL = new ConcurrentHashMap<>();

    private static volatile long refreshIntervalMs = 15_000L;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CatSkinC-SkinManager");
        thread.setDaemon(true);
        return thread;
    });

    private SkinManagerClient() {
    }

    public static void setRefreshIntervalMs(long intervalMs) {
        refreshIntervalMs = Math.max(500L, intervalMs);
        ModLog.debug("Skin refresh interval set to {} ms", refreshIntervalMs);
    }

    public static ResourceLocation getOrFetch(AbstractClientPlayer player) {
        if (player == null) {
            return null;
        }
        return getOrFetch(player.getUUID());
    }

    public static ResourceLocation getOrFetch(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ResourceLocation rendered = resolveRenderTexture(uuid);
        if (rendered == null) {
            fetchAndApplyFor(uuid);
            return null;
        }
        if (shouldPoll(uuid)) {
            fetchAndApplyFor(uuid);
        }
        return rendered;
    }

    public static ResourceLocation getCached(UUID uuid) {
        return uuid == null ? null : resolveRenderTexture(uuid);
    }

    public static void ensureFetch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (!BASE_CACHE.containsKey(uuid) || shouldPoll(uuid)) {
            fetchAndApplyFor(uuid);
        }
    }

    public static void forceFetch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        LAST_CHECK.remove(uuid);
        fetchAndApplyFor(uuid);
    }

    public static void refresh(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> destroyTextures(client, uuid));
        } else {
            BASE_CACHE.remove(uuid);
            TALKING_CACHE.remove(uuid);
        }
        LAST_SKIN_URL.remove(uuid);
        LAST_MOUTH_OPEN_URL.remove(uuid);
        LAST_MOUTH_CLOSE_URL.remove(uuid);
        fetchAndApplyFor(uuid);
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
                ModLog.trace("No remote skin available for {}", uuid);
                clearRemoteState(uuid);
                return CompletableFuture.completedFuture(null);
            }

            SLIM.put(uuid, skin.slim());

            String normalizedMouthOpen = normalizeUrl(skin.mouthOpenUrl());
            String normalizedMouthClose = normalizeUrl(skin.mouthCloseUrl());
            String previousSkinUrl = LAST_SKIN_URL.get(uuid);
            String previousMouthOpenUrl = LAST_MOUTH_OPEN_URL.get(uuid);
            String previousMouthCloseUrl = LAST_MOUTH_CLOSE_URL.get(uuid);
            if (Objects.equals(skin.url(), previousSkinUrl)
                    && Objects.equals(normalizedMouthOpen, previousMouthOpenUrl)
                    && Objects.equals(normalizedMouthClose, previousMouthCloseUrl)) {
                ModLog.trace("Skipping download for {} (URLs unchanged)", uuid);
                return CompletableFuture.completedFuture(null);
            }

            LAST_SKIN_URL.put(uuid, skin.url());
            LAST_MOUTH_OPEN_URL.put(uuid, normalizedMouthOpen);
            LAST_MOUTH_CLOSE_URL.put(uuid, normalizedMouthClose);

            CompletableFuture<NativeImage> skinFuture = ServerApiClient.downloadImageAsync(skin.url());
            CompletableFuture<NativeImage> mouthOpenFuture = normalizedMouthOpen.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : ServerApiClient.downloadImageAsync(normalizedMouthOpen);
            CompletableFuture<NativeImage> mouthCloseFuture = normalizedMouthClose.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : ServerApiClient.downloadImageAsync(normalizedMouthClose);
            final boolean mouthOpenRequested = !normalizedMouthOpen.isEmpty();
            final boolean mouthCloseRequested = !normalizedMouthClose.isEmpty();
            return skinFuture
                    .thenCombine(mouthOpenFuture, PartialDownloadedImages::new)
                    .thenCombine(mouthCloseFuture,
                            (partial, mouthCloseImage) -> new DownloadedImages(
                                    partial.skinImage(),
                                    partial.mouthOpenImage(),
                                    mouthCloseImage,
                                    mouthOpenRequested,
                                    mouthCloseRequested));
        }).whenCompleteAsync((images, throwable) -> {
            IN_FLIGHT.remove(uuid);
            LAST_CHECK.put(uuid, System.currentTimeMillis());
            if (throwable != null) {
                ModLog.error("Skin apply failed for uuid=" + uuid, throwable);
                return;
            }
            if (images == null) {
                ModLog.trace("No texture update for {}", uuid);
                return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                ModLog.trace("Client not ready; dropping texture update for {}", uuid);
                closeQuietly(images.skinImage);
                closeQuietly(images.mouthOpenImage);
                closeQuietly(images.mouthCloseImage);
                return;
            }
            client.execute(() -> {
                try {
                    if (images.skinImage == null) {
                        ModLog.warn("Skin image download returned null for {}", uuid);
                        closeQuietly(images.mouthOpenImage);
                        closeQuietly(images.mouthCloseImage);
                        return;
                    }

                    NativeImage talkingImage = createOverlayImage(uuid, images.skinImage, images.mouthOpenImage,
                            "mouth-open");
                    NativeImage idleImage = createOverlayImage(uuid, images.skinImage, images.mouthCloseImage,
                            "mouth-close");
                    if (images.mouthOpenRequested && talkingImage == null) {
                        ModLog.warn("Mouth-open texture missing after download for {}", uuid);
                    }
                    if (images.mouthCloseRequested && idleImage == null) {
                        ModLog.warn("Mouth-close texture missing after download for {}", uuid);
                    }
                    TextureManager textureManager = client.getTextureManager();

                    // Register new textures BEFORE destroying old ones to prevent
                    // race condition where render thread accesses a freed texture.
                    ResourceLocation baseId = idFor(uuid);
                    ResourceLocation oldBaseId = BASE_CACHE.get(uuid);
                    DynamicTexture baseTexture = new DynamicTexture(images.skinImage);
                    baseTexture.setFilter(false, false);
                    textureManager.register(baseId, baseTexture);
                    BASE_CACHE.put(uuid, baseId);
                    if (oldBaseId != null && !oldBaseId.equals(baseId)) {
                        textureManager.release(oldBaseId);
                    }

                    ResourceLocation idleId = idleIdFor(uuid);
                    ResourceLocation oldIdleId = IDLE_CACHE.remove(uuid);
                    if (idleImage != null) {
                        DynamicTexture idleTexture = new DynamicTexture(idleImage);
                        idleTexture.setFilter(false, false);
                        textureManager.register(idleId, idleTexture);
                        IDLE_CACHE.put(uuid, idleId);
                    }
                    if (oldIdleId != null && !oldIdleId.equals(idleId)) {
                        textureManager.release(oldIdleId);
                    }

                    ResourceLocation talkingId = talkingIdFor(uuid);
                    ResourceLocation oldTalkingId = TALKING_CACHE.remove(uuid);
                    if (talkingImage != null) {
                        DynamicTexture talkingTexture = new DynamicTexture(talkingImage);
                        talkingTexture.setFilter(false, false);
                        textureManager.register(talkingId, talkingTexture);
                        TALKING_CACHE.put(uuid, talkingId);
                    }
                    if (oldTalkingId != null && !oldTalkingId.equals(talkingId)) {
                        textureManager.release(oldTalkingId);
                    }

                    if (SkinOverrideStore.isManaged(uuid)) {
                        SkinOverrideStore.clear(uuid);
                    }

                    ModLog.trace("Texture applied for {} (idleVariant={}, talkingVariant={})",
                            uuid, idleImage != null, talkingImage != null);
                } catch (Exception exception) {
                    ModLog.error("Texture update failed for uuid=" + uuid, exception);
                    closeQuietly(images.skinImage);
                    closeQuietly(images.mouthOpenImage);
                    closeQuietly(images.mouthCloseImage);
                }
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
        int cacheSize = BASE_CACHE.size();
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            TextureManager textureManager = client.getTextureManager();
            for (ResourceLocation id : BASE_CACHE.values()) {
                textureManager.release(id);
            }
            for (ResourceLocation id : IDLE_CACHE.values()) {
                textureManager.release(id);
            }
            for (ResourceLocation id : TALKING_CACHE.values()) {
                textureManager.release(id);
            }
        }
        BASE_CACHE.clear();
        IDLE_CACHE.clear();
        TALKING_CACHE.clear();
        SLIM.clear();
        PREFERRED_SLIM.clear();
        LAST_CHECK.clear();
        LAST_SKIN_URL.clear();
        LAST_MOUTH_OPEN_URL.clear();
        LAST_MOUTH_CLOSE_URL.clear();
        IN_FLIGHT.clear();
        ModLog.debug("Skin caches cleared ({} entries)", cacheSize);
    }

    private static ResourceLocation idFor(UUID uuid) {
        return Identifiers.mod("remote/" + uuid.toString().replace("-", ""));
    }

    private static ResourceLocation talkingIdFor(UUID uuid) {
        return Identifiers.mod("remote/" + uuid.toString().replace("-", "") + "/talking");
    }

    private static ResourceLocation idleIdFor(UUID uuid) {
        return Identifiers.mod("remote/" + uuid.toString().replace("-", "") + "/idle");
    }

    private static ResourceLocation resolveRenderTexture(UUID uuid) {
        ResourceLocation base = BASE_CACHE.get(uuid);
        if (base == null) {
            return null;
        }
        if (VoiceActivityTracker.isSpeaking(uuid)) {
            ResourceLocation talking = TALKING_CACHE.get(uuid);
            if (talking != null) {
                return talking;
            }
            return base;
        }
        ResourceLocation idle = IDLE_CACHE.get(uuid);
        if (idle != null) {
            return idle;
        }
        return base;
    }

    private static boolean shouldPoll(UUID uuid) {
        long now = System.currentTimeMillis();
        long lastCheck = LAST_CHECK.getOrDefault(uuid, 0L);
        return now - lastCheck >= refreshIntervalMs;
    }

    private static String normalizeUrl(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    private static void clearRemoteState(UUID uuid) {
        LAST_SKIN_URL.remove(uuid);
        LAST_MOUTH_OPEN_URL.remove(uuid);
        LAST_MOUTH_CLOSE_URL.remove(uuid);
        SLIM.remove(uuid);
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> destroyTextures(client, uuid));
        } else {
            BASE_CACHE.remove(uuid);
            IDLE_CACHE.remove(uuid);
            TALKING_CACHE.remove(uuid);
        }
    }

    private static void destroyTextures(Minecraft client, UUID uuid) {
        TextureManager textureManager = client.getTextureManager();
        ResourceLocation base = BASE_CACHE.remove(uuid);
        if (base != null) {
            textureManager.release(base);
        }
        ResourceLocation idle = IDLE_CACHE.remove(uuid);
        if (idle != null) {
            textureManager.release(idle);
        }
        ResourceLocation talking = TALKING_CACHE.remove(uuid);
        if (talking != null) {
            textureManager.release(talking);
        }
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

                    int overlayColor = overlayImage.getPixelRGBA(ox, oy);
                    int alpha = (overlayColor >>> 24) & 0xFF;
                    merged.setPixelRGBA(x, y, alpha > 0 ? overlayColor : skinImage.getPixelRGBA(sx, sy));
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
        BASE_CACHE.clear();
        IDLE_CACHE.clear();
        TALKING_CACHE.clear();
        SLIM.clear();
        PREFERRED_SLIM.clear();
        LAST_CHECK.clear();
        LAST_SKIN_URL.clear();
        LAST_MOUTH_OPEN_URL.clear();
        LAST_MOUTH_CLOSE_URL.clear();
        IN_FLIGHT.clear();
        refreshIntervalMs = 15_000L;
    }

    private record PartialDownloadedImages(NativeImage skinImage, NativeImage mouthOpenImage) {
    }

    private record DownloadedImages(
            NativeImage skinImage,
            NativeImage mouthOpenImage,
            NativeImage mouthCloseImage,
            boolean mouthOpenRequested,
            boolean mouthCloseRequested) {
    }
}
