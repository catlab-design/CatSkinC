package com.sammy.catskincRemake;

import com.sammy.catskincRemake.voice.PlasmoVoiceServerBridgeBootstrap;

public final class CatskincRemake {
    public static final String MOD_ID = "catskinc-remake";

    public static void init() {
        PlasmoVoiceServerBridgeBootstrap.init();
    }
}
