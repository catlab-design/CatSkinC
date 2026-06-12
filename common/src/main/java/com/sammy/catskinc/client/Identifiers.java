package com.sammy.catskinc.client;

import com.sammy.catskinc.Catskinc;
import net.minecraft.util.Identifier;

public final class Identifiers {
    private Identifiers() {
    }

    public static Identifier mod(String path) {
        return of(Catskinc.MOD_ID, path);
    }

    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier parse(String value) {
        return new Identifier(value);
    }
}


