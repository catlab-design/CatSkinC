package com.sammy.catskinc.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;

final class PreviewRemotePlayer extends RemotePlayer {
    PreviewRemotePlayer(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }
}

