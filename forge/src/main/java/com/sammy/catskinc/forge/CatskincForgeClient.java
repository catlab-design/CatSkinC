package com.sammy.catskinc.forge;

import com.sammy.catskinc.client.CatskincClient;
import com.sammy.catskinc.client.ModSounds;

public final class CatskincForgeClient {
    private CatskincForgeClient() {
    }

    @SuppressWarnings("removal")
    public static void registerClientInit() {
        ModSounds.register();
        CatskincClient.init();
    }
}

