package com.sammy.catskinc.neoforge;

import com.sammy.catskinc.client.CatskincClient;
import com.sammy.catskinc.client.ModSounds;

public final class CatskincNeoForgeClient {
    private CatskincNeoForgeClient() {
    }

    public static void registerClientInit() {
        ModSounds.register();
        CatskincClient.init();
    }
}

