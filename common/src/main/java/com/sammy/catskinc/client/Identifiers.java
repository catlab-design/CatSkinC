package com.sammy.catskinc.client;

import com.sammy.catskinc.Catskinc;
import net.minecraft.resources.Identifier;

public final class Identifiers {
    private Identifiers() {
    }

    public static Identifier mod(String path) {
        return of(Catskinc.MOD_ID, path);
    }

    public static Identifier of(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static Identifier parse(String value) {
        return Identifier.parse(value);
    }
}


