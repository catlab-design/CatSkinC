package com.sammy.catskinc.fabric.client;

import com.sammy.catskinc.client.CatskincClient;
import com.sammy.catskinc.client.ModSounds;
import net.fabricmc.api.ClientModInitializer;

public final class CatskincFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModSounds.register();
        CatskincClient.init();
    }
}

