package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds reduced-resolution copies of managed skins for the Myopia LOD system.
 *
 * <p>Each (player, variant, level) gets its own GPU texture registered as
 * {@code catskinc:skins/<uuid>/<variant>/lod<n>}. The full-resolution source image is
 * never modified; entries are rebuilt lazily whenever the source image instance changes.
 * All methods must be called on the render thread.
 */
public final class MyopiaTextureCache {
    public static final String VARIANT_SKIN = "skin";
    public static final String VARIANT_TALKING = "talking";

    private static final Map<UUID, Map<String, EnumMap<MyopiaLod.Level, Entry>>> ENTRIES = new ConcurrentHashMap<>();

    private MyopiaTextureCache() {
    }

    /**
     * Returns the identifier of a texture holding {@code source} scaled down to {@code level}.
     * Returns {@code null} if the texture manager is unavailable.
     */
    public static Identifier get(UUID uuid, String variant, MyopiaLod.Level level, NativeImage source) {
        if (uuid == null || level == null || source == null) {
            return null;
        }
        if (level.isFull()) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return null;
        }
        Map<String, EnumMap<MyopiaLod.Level, Entry>> byVariant = ENTRIES.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        EnumMap<MyopiaLod.Level, Entry> byLevel = byVariant.computeIfAbsent(variant, k -> new EnumMap<>(MyopiaLod.Level.class));

        Entry entry = byLevel.get(level);
        if (entry != null && entry.source == source) {
            return entry.id;
        }

        int w = level.targetSize(source.getWidth());
        int h = level.targetSize(source.getHeight());
        Identifier id = Identifiers.mod("skins/" + uuid + "/" + variant + "/lod" + level.ordinal());

        if (entry != null && entry.texture.getImage() != null
                && entry.texture.getImage().getWidth() == w
                && entry.texture.getImage().getHeight() == h) {
            // Same dimensions: rewrite pixels in place instead of re-registering.
            SkinManagerClient.blitPixels(entry.texture.getImage(), source);
            entry.texture.upload();
            byLevel.put(level, new Entry(id, entry.texture, source));
            return id;
        }

        if (entry != null) {
            client.getTextureManager().destroyTexture(entry.id);
        }
        NativeImage scaled = new NativeImage(w, h, true);
        SkinManagerClient.blitPixels(scaled, source);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(scaled);
        client.getTextureManager().registerTexture(id, texture);
        byLevel.put(level, new Entry(id, texture, source));
        ModLog.trace("Myopia LOD texture built for {} ({}, {}): {}x{}", uuid, variant, level, w, h);
        return id;
    }

    /** Drops all LOD textures for one player. */
    public static void invalidate(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Map<String, EnumMap<MyopiaLod.Level, Entry>> byVariant = ENTRIES.remove(uuid);
        if (byVariant == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        for (EnumMap<MyopiaLod.Level, Entry> byLevel : byVariant.values()) {
            for (Entry entry : byLevel.values()) {
                destroy(client, entry);
            }
        }
    }

    /** Drops all LOD textures for every player (e.g. when Myopia is disabled or on disconnect). */
    public static void clearAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Map<String, EnumMap<MyopiaLod.Level, Entry>> byVariant : ENTRIES.values()) {
            for (EnumMap<MyopiaLod.Level, Entry> byLevel : byVariant.values()) {
                for (Entry entry : byLevel.values()) {
                    destroy(client, entry);
                }
            }
        }
        ENTRIES.clear();
    }

    static int cachedTextureCount() {
        int count = 0;
        for (Map<String, EnumMap<MyopiaLod.Level, Entry>> byVariant : ENTRIES.values()) {
            for (EnumMap<MyopiaLod.Level, Entry> byLevel : byVariant.values()) {
                count += byLevel.size();
            }
        }
        return count;
    }

    private static void destroy(MinecraftClient client, Entry entry) {
        if (entry == null) {
            return;
        }
        try {
            if (client != null) {
                // TextureManager.destroyTexture closes the backing NativeImage.
                client.getTextureManager().destroyTexture(entry.id);
            } else {
                entry.texture.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static final class Entry {
        final Identifier id;
        final NativeImageBackedTexture texture;
        final NativeImage source;

        Entry(Identifier id, NativeImageBackedTexture texture, NativeImage source) {
            this.id = id;
            this.texture = texture;
            this.source = source;
        }
    }
}
