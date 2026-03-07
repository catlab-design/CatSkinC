package com.sammy.catskincRemake.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

final class PreviewPlayerEntity extends OtherClientPlayerEntity {
    PreviewPlayerEntity(ClientWorld world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    @Override
    public boolean shouldRenderName() {
        return false;
    }

    @Override
    public float getNameLabelHeight() {
        return 0.0F;
    }
}
