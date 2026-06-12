package com.sammy.catskinc.client;

import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class SkinTextureFactory {
    private SkinTextureFactory() {
    }

    public static Object withTextureAndModel(Object baseSkinTextures, Identifier texture, Boolean slim) {
        if (baseSkinTextures == null || texture == null) {
            return baseSkinTextures;
        }
        try {
            Class<?> type = baseSkinTextures.getClass();
            Method textureUrlMethod = type.getMethod("textureUrl");
            Method capeMethod = type.getMethod("capeTexture");
            Method elytraMethod = type.getMethod("elytraTexture");
            Method modelMethod = type.getMethod("model");
            Method secureMethod = type.getMethod("secure");

            Object textureUrl = textureUrlMethod.invoke(baseSkinTextures);
            Object cape = capeMethod.invoke(baseSkinTextures);
            Object elytra = elytraMethod.invoke(baseSkinTextures);
            Object model = modelMethod.invoke(baseSkinTextures);
            Object secure = secureMethod.invoke(baseSkinTextures);

            if (slim != null && model != null) {
                Object mapped = mapModel(model.getClass(), slim.booleanValue());
                if (mapped != null) {
                    model = mapped;
                }
            }

            Constructor<?> constructor = findCtor(type, model == null ? null : model.getClass());
            if (constructor == null) {
                return baseSkinTextures;
            }
            return constructor.newInstance(texture, textureUrl, cape, elytra, model, secure);
        } catch (Exception ignored) {
            return baseSkinTextures;
        }
    }

    private static Constructor<?> findCtor(Class<?> skinTexturesType, Class<?> modelType) {
        for (Constructor<?> constructor : skinTexturesType.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 6) {
                continue;
            }
            if (!Identifier.class.isAssignableFrom(params[0])) {
                continue;
            }
            if (modelType != null && !params[4].isAssignableFrom(modelType) && !modelType.isAssignableFrom(params[4])) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor;
        }
        return null;
    }

    private static Object mapModel(Class<?> modelType, boolean slim) {
        Object[] constants = modelType.getEnumConstants();
        if (constants == null) {
            return null;
        }
        String preferred = slim ? "SLIM" : "WIDE";
        for (Object constant : constants) {
            if (constant != null && preferred.equalsIgnoreCase(constant.toString())) {
                return constant;
            }
        }
        for (Object constant : constants) {
            if (constant == null) {
                continue;
            }
            String value = constant.toString();
            if (slim && value.toLowerCase().contains("slim")) {
                return constant;
            }
            if (!slim && (value.toLowerCase().contains("wide") || value.toLowerCase().contains("default"))) {
                return constant;
            }
        }
        return constants.length > 0 ? constants[0] : null;
    }
}


