package com.sammy.catskinc.neoforge;

import com.sammy.catskinc.Catskinc;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.neoforged.fml.common.Mod;

@Mod(CatskincNeoForge.NEOFORGE_MOD_ID)
public final class CatskincNeoForge {
    public static final String NEOFORGE_MOD_ID = "catskinc";

    public CatskincNeoForge() {
        Catskinc.init();
        if (Platform.getEnvironment() == Env.CLIENT) {
            CatskincNeoForgeClient.registerClientInit();
        }
    }
}

