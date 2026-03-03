package com.sammy.catskincRemake.client;

import com.sammy.catskincRemake.CatskincRemake;
import net.minecraft.util.Identifier;
import java.lang.reflect.Method;

public final class Identifiers {
    private static final Method IDENTIFIER_OF = findMethod("of", String.class, String.class);
    private static final Method IDENTIFIER_TRY_PARSE = findMethod("tryParse", String.class);

    private Identifiers() {
    }

    public static Identifier mod(String path) {
        return of(CatskincRemake.MOD_ID, path);
    }

    public static Identifier of(String namespace, String path) {
        if (IDENTIFIER_OF != null) {
            try {
                return (Identifier) IDENTIFIER_OF.invoke(null, namespace, path);
            } catch (Exception ignored) {
            }
        }
        return parse(namespace + ":" + path);
    }

    public static Identifier parse(String value) {
        if (IDENTIFIER_TRY_PARSE != null) {
            try {
                Identifier identifier = (Identifier) IDENTIFIER_TRY_PARSE.invoke(null, value);
                if (identifier != null) {
                    return identifier;
                }
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("Invalid identifier: " + value);
    }

    private static Method findMethod(String name, Class<?>... args) {
        try {
            return Identifier.class.getMethod(name, args);
        } catch (Exception ignored) {
            return null;
        }
    }
}

