package com.sammy.catskinc.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Development-only local player used to inspect Myopia LOD without a second account. */
final class MyopiaDebugPreview {
    private static final int[] ENTITY_IDS = {-9_172_001, -9_172_002, -9_172_003, -9_172_004};
    private static final double[] DISTANCES = {10.0, 25.0, 45.0, 85.0};
    private static final UUID[] UUIDS = {
            UUID.fromString("d3d9ecb8-7893-4b91-b3b8-c3f47d435f38"),
            UUID.fromString("17c24d8c-3a47-4b8b-947d-8105828653f2"),
            UUID.fromString("9d670d95-0273-4f5a-85e7-2b32d4cd396e"),
            UUID.fromString("f7fe307c-6e93-47bd-b999-0633e652a1c1")
    };

    private MyopiaDebugPreview() { }

    static void setEnabled(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;
        for (int i = 0; i < ENTITY_IDS.length; i++) {
            client.level.removeEntity(ENTITY_IDS[i], net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            SkinManagerClient.clearTexture(UUIDS[i]);
        }
        if (!enabled) return;
        for (int i = 0; i < ENTITY_IDS.length; i++) {
            RemotePlayer player = new RemotePlayer(client.level,
                    new GameProfile(UUIDS[i], "Myopia " + (int) DISTANCES[i] + "m"));
            player.setPos(client.player.getX() + DISTANCES[i], client.player.getY(), client.player.getZ());
            SkinManagerClient.installDebugSkin(UUIDS[i]);
            client.level.addEntity(player);
        }
    }
}