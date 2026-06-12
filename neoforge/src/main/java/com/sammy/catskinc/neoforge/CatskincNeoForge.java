package com.sammy.catskinc.neoforge;

import com.sammy.catskinc.Catskinc;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(CatskincNeoForge.NEOFORGE_MOD_ID)
public final class CatskincNeoForge {
    public static final String NEOFORGE_MOD_ID = "catskinc";

    public CatskincNeoForge() {
        Catskinc.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CatskincNeoForgeClient.registerClientInit();
        }
    }
}

