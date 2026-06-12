package com.sammy.catskincRemake.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
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
    private static final Map<UUID, Identifier> BASE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Identifier> TALKING_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SLIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PREFERRED_SLIM = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_CHECK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_SKIN_URL = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_MOUTH_OPEN_URL = new ConcurrentHashMap<>();

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
            fetchAndApplyFor(uuid);
            return null;
        }
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> destroyTextures(client, uuid));
        } else {
            BASE_CACHE.remove(uuid);
            TALKING_CACHE.remove(uuid);
        }
        LAST_SKIN_URL.remove(uuid);
        LAST_MOUTH_OPEN_URL.remove(uuid);
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
                ModLog.trace("No remote skin available for {} (keeping current texture)", uuid);
                LAST_SKIN_URL.remove(uuid);
                LAST_MOUTH_OPEN_URL.remove(uuid);
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
            LAST_CHECK.put(uuid, System.currentTimeMillis());
            if (throwable != null) {
                ModLog.error("Skin apply failed for uuid=" + uuid, throwable);
                return;
            }
            if (images == null) {
                ModLog.trace("No texture update for {}", uuid);
                return;
            }

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

                boolean skinConsumed = false;
                boolean talkingConsumed = false;
                try {
                    TextureManager textureManager = client.getTextureManager();

                    Identifier baseId = idFor(uuid);
                    Identifier oldBaseId = BASE_CACHE.get(uuid);
                    NativeImageBackedTexture baseTexture = new NativeImageBackedTexture(images.skinImage);
                    skinConsumed = true;
                    baseTexture.setFilter(false, false);
                    textureManager.registerTexture(baseId, baseTexture);
                    BASE_CACHE.put(uuid, baseId);
                    if (oldBaseId != null && !oldBaseId.equals(baseId)) {
                        textureManager.destroyTexture(oldBaseId);
                    }

                    Identifier talkingId = talkingIdFor(uuid);
                    Identifier oldTalkingId = TALKING_CACHE.remove(uuid);
                    if (talkingImage != null) {
                        NativeImageBackedTexture talkingTexture = new NativeImageBackedTexture(talkingImage);
                        talkingConsumed = true;
                        talkingTexture.setFilter(false, false);
                        textureManager.registerTexture(talkingId, talkingTexture);
                        TALKING_CACHE.put(uuid, talkingId);
                    }
                    if (oldTalkingId != null && !oldTalkingId.equals(talkingId)) {
                        textureManager.destroyTexture(oldTalkingId);
                    }

                    if (SkinOverrideStore.isManaged(uuid)) {
                        SkinOverrideStore.clear(uuid);
                    }

                    ModLog.trace("Texture applied for {} (talkingVariant={})", uuid, talkingImage != null);
                } catch (Exception exception) {
                    ModLog.error("Texture update failed for uuid=" + uuid, exception);
                    if (!skinConsumed) {
                        closeQuietly(images.skinImage);
                    }
                    if (!talkingConsumed) {
                        closeQuietly(talkingImage);
                    }
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            TextureManager textureManager = client.getTextureManager();
            for (Identifier id : BASE_CACHE.values()) {
                textureManager.destroyTexture(id);
            }
            for (Identifier id : TALKING_CACHE.values()) {
                textureManager.destroyTexture(id);
            }
        }
        BASE_CACHE.clear();
        TALKING_CACHE.clear();
        SLIM.clear();
        PREFERRED_SLIM.clear();
        LAST_CHECK.clear();
        LAST_SKIN_URL.clear();
        LAST_MOUTH_OPEN_URL.clear();
        IN_FLIGHT.clear();
        ModLog.debug("Skin caches cleared ({} entries)", cacheSize);
    }

    private static Identifier idFor(UUID uuid) {
        return Identifiers.mod("remote/" + uuid.toString().replace("-", ""));
    }

    private static Identifier talkingIdFor(UUID uuid) {
        return Identifiers.mod("remote/" + uuid.toString().replace("-", "") + "/talking");
    }

    private static Identifier resolveRenderTexture(UUID uuid) {
        Identifier base = BASE_CACHE.get(uuid);
        if (base == null) {
            return null;
        }
        if (VoiceActivityTracker.isSpeaking(uuid)) {
            Identifier talking = TALKING_CACHE.get(uuid);
            if (talking != null) {
                return talking;
            }
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

    private static void destroyTextures(MinecraftClient client, UUID uuid) {
        TextureManager textureManager = client.getTextureManager();
        Identifier base = BASE_CACHE.remove(uuid);
        if (base != null) {
            textureManager.destroyTexture(base);
        }
        Identifier talking = TALKING_CACHE.remove(uuid);
        if (talking != null) {
            textureManager.destroyTexture(talking);
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
        BASE_CACHE.clear();
        TALKING_CACHE.clear();
        SLIM.clear();
        PREFERRED_SLIM.clear();
        LAST_CHECK.clear();
        LAST_SKIN_URL.clear();
        LAST_MOUTH_OPEN_URL.clear();
        IN_FLIGHT.clear();
        refreshIntervalMs = 15_000L;
    }

    private record DownloadedImages(
            NativeImage skinImage,
            NativeImage mouthOpenImage,
            boolean mouthOpenRequested) {
    }
}
