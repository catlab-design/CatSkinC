package com.sammy.catskinc.client;

import com.sammy.catskinc.Catskinc;
import net.minecraft.resources.ResourceLocation;

public final class Identifiers {
    private Identifiers() {
    }

    public static ResourceLocation mod(String path) {
        return of(Catskinc.MOD_ID, path);
    }

    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation parse(String value) {
        return ResourceLocation.parse(value);
    }
}


