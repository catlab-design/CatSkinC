package com.sammy.catskinc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

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
    private static final Map<UUID, PendingInjection> PENDING_INJECTIONS = new ConcurrentHashMap<>();

    private static final class PendingInjection {
        final NativeImage image;
        final boolean slim;

        PendingInjection(NativeImage image, boolean slim) {
            this.image = image;
            this.slim = slim;
        }
    }

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

    public static void putManaged(UUID uuid, NativeImage image, boolean slim) {
        if (uuid == null || image == null) {
            return;
        }
        Identifier vanillaId = SkinManagerClient.getVanillaIdentifier(uuid);
        if (vanillaId == null) {
            // Queue for later injection when vanilla texture becomes available
            PENDING_INJECTIONS.put(uuid, new PendingInjection(image, slim));
            ModLog.trace("Queued pending managed injection for {}", uuid);
            return;
        }
        // Managed entries write directly into the vanilla texture (via injectPixels)
        // and store the vanilla texture ID. They don't own a separate texture.
        SkinManagerClient.injectPixels(uuid, image);
        clear(uuid);
        ENTRIES.put(uuid, new Entry(vanillaId, slim, true));
    }

    // Called from SkinManagerClient.onSkinLookup when vanilla texture ID becomes available
    public static void processPendingInjection(UUID uuid, Identifier vanillaId) {
        if (uuid == null || vanillaId == null) {
            return;
        }
        PendingInjection pending = PENDING_INJECTIONS.remove(uuid);
        if (pending == null) {
            return;
        }
        ModLog.trace("Processing pending managed injection for {}", uuid);
        SkinManagerClient.injectPixels(uuid, pending.image);
        ENTRIES.put(uuid, new Entry(vanillaId, pending.slim, true));
    }

    public static void putManagedFromFile(UUID uuid, File png, boolean slim) throws IOException {
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
        // Non-managed entries own their texture and must be destroyed.
        // Managed entries use the vanilla texture ID (from VANILLA_TEXTURES) which
        // is shared and must NOT be destroyed here.
        if (!removed.managed) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.getTextureManager().destroyTexture(removed.texture);
            }
        }
    }

    public static void clearAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (var entry : ENTRIES.entrySet()) {
            Entry value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!value.managed && client != null) {
                client.getTextureManager().destroyTexture(value.texture);
            }
        }
        ENTRIES.clear();
    }
}