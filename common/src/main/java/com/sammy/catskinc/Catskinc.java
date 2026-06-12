package com.sammy.catskinc;

import com.sammy.catskinc.voice.PlasmoVoiceServerBridgeBootstrap;

public final class Catskinc {
    public static final String MOD_ID = "catskinc";

    public static void init() {
        PlasmoVoiceServerBridgeBootstrap.init();
    }
}

