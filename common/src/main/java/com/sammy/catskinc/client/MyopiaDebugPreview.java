package com.sammy.catskinc.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;

import java.util.UUID;

/** Development-only local player used to inspect Myopia LOD without a second account. */
final class MyopiaDebugPreview {
    private static final int[] ENTITY_IDS = {-9_172_001, -9_172_002, -9_172_003, -9_172_004};
    private static final double[] DISTANCES = {10.0, 25.0, 45.0, 85.0};
    private static final UUID[] UUIDS = {
            java.util.UUID.fromString("d3d9ecb8-7893-4b91-b3b8-c3f47d435f38"),
            java.util.UUID.fromString("17c24d8c-3a47-4b8b-947d-8105828653f2"),
            java.util.UUID.fromString("9d670d95-0273-4f5a-85e7-2b32d4cd396e"),
            java.util.UUID.fromString("f7fe307c-6e93-47bd-b999-0633e652a1c1")
    };

    private MyopiaDebugPreview() { }

    static void setEnabled(boolean enabled) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;
        for (int i = 0; i < ENTITY_IDS.length; i++) {
            client.world.removeEntity(ENTITY_IDS[i], Entity.RemovalReason.DISCARDED);
            SkinManagerClient.clearTexture(UUIDS[i]);
        }
        if (!enabled) return;
        for (int i = 0; i < ENTITY_IDS.length; i++) {
            OtherClientPlayerEntity player = new OtherClientPlayerEntity(client.world,
                    new GameProfile(UUIDS[i], "Myopia " + (int) DISTANCES[i] + "m"));
            player.setPosition(client.player.getX() + DISTANCES[i], client.player.getY(), client.player.getZ());
            SkinManagerClient.installDebugSkin(UUIDS[i]);
            client.world.addEntity(ENTITY_IDS[i], player);
        }
    }
}
