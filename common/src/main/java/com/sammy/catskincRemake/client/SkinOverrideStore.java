package com.sammy.catskincRemake.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinOverrideStore {
    public static final class Entry {
        public final Identifier texture;
        public final boolean slim;
        private final boolean managed;

        private Entry(Identifier texture, boolean slim, boolean managed) {
            this.texture = texture;
            this.slim = slim;
            this.managed = managed;
        }
    }

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private SkinOverrideStore() {
    }

    public static Entry get(UUID uuid) {
        return uuid == null ? null : ENTRIES.get(uuid);
    }

    public static boolean isManaged(UUID uuid) {
        Entry entry = get(uuid);
        return entry != null && entry.managed;
    }

    public static void put(UUID uuid, Identifier registeredTexture, boolean slim) {
        if (uuid == null || registeredTexture == null) {
            return;
        }
        clear(uuid);
        ENTRIES.put(uuid, new Entry(registeredTexture, slim, false));
    }

    public static void putManaged(UUID uuid, NativeImageBackedTexture texture, boolean slim) {
        if (uuid == null || texture == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        clear(uuid);
        Identifier id = Identifiers.mod("override/" + uuid + "/" + System.nanoTime());
        client.getTextureManager().registerTexture(id, texture);
        ENTRIES.put(uuid, new Entry(id, slim, true));
    }

    public static void putManagedFromFile(UUID uuid, File png, boolean slim) throws Exception {
        try (FileInputStream in = new FileInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            texture.setFilter(false, false);
            putManaged(uuid, texture, slim);
        }
    }

    public static void clear(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Entry removed = ENTRIES.remove(uuid);
        if (removed == null || !removed.managed) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.getTextureManager().destroyTexture(removed.texture);
        }
    }

    public static void clearAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (var entry : ENTRIES.entrySet()) {
            Entry value = entry.getValue();
            if (value != null && value.managed && client != null) {
                client.getTextureManager().destroyTexture(value.texture);
            }
        }
        ENTRIES.clear();
    }
}
