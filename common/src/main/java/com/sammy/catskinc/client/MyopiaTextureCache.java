package com.sammy.catskinc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;

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
    public static ResourceLocation get(UUID uuid, String variant, MyopiaLod.Level level, NativeImage source) {
        if (uuid == null || level == null || source == null) {
            return null;
        }
        if (level.isFull()) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
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
        ResourceLocation id = Identifiers.mod("skins/" + uuid + "/" + variant + "/lod" + level.ordinal());

        if (entry != null && entry.texture.getPixels() != null
                && entry.texture.getPixels().getWidth() == w
                && entry.texture.getPixels().getHeight() == h) {
            // Same dimensions: rewrite pixels in place instead of re-registering.
            SkinManagerClient.blitPixels(entry.texture.getPixels(), source);
            entry.texture.upload();
            byLevel.put(level, new Entry(id, entry.texture, source));
            return id;
        }

        if (entry != null) {
            client.getTextureManager().release(entry.id);
        }
        NativeImage scaled = new NativeImage(w, h, true);
        SkinManagerClient.blitPixels(scaled, source);
        DynamicTexture texture = new DynamicTexture(scaled);
        client.getTextureManager().register(id, texture);
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
        Minecraft client = Minecraft.getInstance();
        for (EnumMap<MyopiaLod.Level, Entry> byLevel : byVariant.values()) {
            for (Entry entry : byLevel.values()) {
                destroy(client, entry);
            }
        }
    }

    /** Drops all LOD textures for every player (e.g. when Myopia is disabled or on disconnect). */
    public static void clearAll() {
        Minecraft client = Minecraft.getInstance();
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

    private static void destroy(Minecraft client, Entry entry) {
        if (entry == null) {
            return;
        }
        try {
            if (client != null) {
                client.getTextureManager().release(entry.id);
            } else {
                entry.texture.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static final class Entry {
        final ResourceLocation id;
        final DynamicTexture texture;
        final NativeImage source;

        Entry(ResourceLocation id, DynamicTexture texture, NativeImage source) {
            this.id = id;
            this.texture = texture;
            this.source = source;
        }
    }
}