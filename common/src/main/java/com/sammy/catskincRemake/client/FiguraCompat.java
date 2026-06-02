package com.sammy.catskincRemake.client;

import dev.architectury.platform.Platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Soft-dependency compatibility bridge for <a href="https://figura.moonlight-devs.org/">Figura</a>.
 *
 * <p>Figura lets a player replace their whole player rendering with a custom avatar.
 * On 1.21.1 every skin attribute (texture, model/slim, cape, elytra) is funnelled through the
 * single {@code AbstractClientPlayer#getSkin()} method, which Figura also hooks. When CatSkinC
 * unconditionally rewrote that result it fought Figura for the same chokepoint: depending on
 * mixin order one mod won the texture while the other won the model, so a player ended up with
 * the CatSkinC texture but the wrong (wide) arms <em>and</em> with Figura body parts overwritten.
 *
 * <p>The fix is to <strong>yield</strong>: whenever a player currently has an active Figura
 * avatar, CatSkinC performs no skin/model override for that player and lets Figura render
 * untouched. All detection happens through reflection so Figura stays an optional dependency;
 * if Figura is absent (or its internals change) this class fails safe to {@code false} and
 * CatSkinC behaves exactly as before.
 */
public final class FiguraCompat {
    private static final String AVATAR_MANAGER_CLASS = "org.figuramc.figura.avatar.AvatarManager";
    private static final String GET_AVATAR_METHOD = "getAvatarForPlayer";
    private static final String LOADED_FIELD = "loaded";

    /** Cached mod-presence result. {@code null} = not resolved yet. */
    private static volatile Boolean present;
    /** Set once reflection throws so we stop probing a hostile/changed API. */
    private static volatile boolean broken;
    private static volatile Method getAvatarForPlayer;
    private static volatile Field loadedField;
    private static volatile boolean loadedFieldResolved;

    /** Test seam: when set, replaces the real Figura probe entirely. */
    private static volatile Predicate<UUID> testProbe;

    private FiguraCompat() {
    }

    /**
     * @return {@code true} when Figura is installed and {@code uuid} currently has a loaded avatar,
     *         meaning CatSkinC should not override that player's skin or model.
     */
    public static boolean hasActiveAvatar(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Predicate<UUID> probe = testProbe;
        if (probe != null) {
            return probe.test(uuid);
        }
        if (broken || !isPresent()) {
            return false;
        }
        try {
            Method method = getAvatarForPlayer;
            if (method == null) {
                Class<?> avatarManager = Class.forName(AVATAR_MANAGER_CLASS);
                method = avatarManager.getMethod(GET_AVATAR_METHOD, UUID.class);
                getAvatarForPlayer = method;
            }
            Object avatar = method.invoke(null, uuid);
            if (avatar == null) {
                return false;
            }
            Field loaded = resolveLoadedField(avatar);
            if (loaded != null) {
                return loaded.getBoolean(avatar);
            }
            // Avatar present but we cannot read its loaded flag — treat presence as active so
            // we still yield rather than fight Figura.
            return true;
        } catch (Throwable throwable) {
            broken = true;
            ModLog.debug("Figura compatibility probe disabled: {}", throwable.getMessage());
            return false;
        }
    }

    private static Field resolveLoadedField(Object avatar) {
        Field cached = loadedField;
        if (cached != null && cached.getDeclaringClass().isInstance(avatar)) {
            return cached;
        }
        if (loadedFieldResolved && cached == null) {
            return null;
        }
        try {
            Field field = avatar.getClass().getField(LOADED_FIELD);
            if (field.getType() == boolean.class) {
                loadedField = field;
                loadedFieldResolved = true;
                return field;
            }
        } catch (NoSuchFieldException ignored) {
            // Older/newer Figura without a public boolean 'loaded' field.
        }
        loadedFieldResolved = true;
        return null;
    }

    private static boolean isPresent() {
        Boolean cached = present;
        if (cached != null) {
            return cached;
        }
        boolean detected;
        try {
            detected = Platform.isModLoaded("figura");
        } catch (Throwable throwable) {
            // Platform may be unavailable outside a running game (e.g. unit tests).
            detected = false;
        }
        present = detected;
        return detected;
    }

    /** Test hook: force the avatar-presence answer (or {@code null} to use the real probe). */
    static void setAvatarProbeForTesting(Predicate<UUID> probe) {
        testProbe = probe;
    }

    /** Reset all cached state for tests. */
    static void resetForTesting() {
        testProbe = null;
        present = null;
        broken = false;
        getAvatarForPlayer = null;
        loadedField = null;
        loadedFieldResolved = false;
    }
}
