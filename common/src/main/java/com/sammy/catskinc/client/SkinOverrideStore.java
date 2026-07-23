package com.sammy.catskinc.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class SkinOverrideStore {
    public static final class Entry {
        public final ResourceLocation texture;
        public final boolean slim;
        private final boolean managed;

        private Entry(ResourceLocation texture, boolean slim, boolean managed) {
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

    public static void put(UUID uuid, ResourceLocation registeredTexture, boolean slim) {
        if (uuid == null || registeredTexture == null) {
            return;
        }
        clear(uuid);
        ENTRIES.put(uuid, new Entry(registeredTexture, slim, false));
    }

    public static void putManaged(UUID uuid, NativeImage image, boolean slim) {
        if (uuid == null || image == null) {
            return;
        }
        ResourceLocation vanillaId = SkinManagerClient.getVanillaIdentifier(uuid);
        if (vanillaId == null) {
            return;
        }
        SkinManagerClient.injectPixels(uuid, image);
        clear(uuid);
        ENTRIES.put(uuid, new Entry(vanillaId, slim, true));
    }

    public static void putManagedFromFile(UUID uuid, File png, boolean slim) throws Exception {
        try (FileInputStream in = new FileInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            putManaged(uuid, image, slim);
        }
    }

    public static void clear(UUID uuid) {
        if (uuid == null) {
            return;
        }
        Entry removed = ENTRIES.remove(uuid);
        if (removed == null) {
            return;
        }
        if (!removed.managed) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.getTextureManager().release(removed.texture);
            }
        }
    }

    public static void clearAll() {
        Minecraft client = Minecraft.getInstance();
        for (var entry : ENTRIES.entrySet()) {
            Entry value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!value.managed && client != null) {
                client.getTextureManager().release(value.texture);
            }
        }
        ENTRIES.clear();
    }
}
