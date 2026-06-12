package com.sammy.catskinc.forge;

import com.sammy.catskinc.Catskinc;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CatskincForge.FORGE_MOD_ID)
@SuppressWarnings("removal")
public final class CatskincForge {
    public static final String FORGE_MOD_ID = "catskinc";

    public CatskincForge() {
        EventBuses.registerModEventBus(FORGE_MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        Catskinc.init();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> CatskincForgeClient::registerClientInit);
    }
}

