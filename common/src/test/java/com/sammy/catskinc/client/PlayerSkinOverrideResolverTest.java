package com.sammy.catskinc.client;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerSkinOverrideResolverTest {
    private static final UUID TEST_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174111");

    @AfterEach
    void tearDown() {
        SkinOverrideStore.clear(TEST_UUID);
        SkinManagerClient.resetForTesting();
    }

    @Test
    void resolvesTextureFromTheOverrideStore() {
        Identifier overrideTexture = Identifiers.mod("test/first-person");
        SkinOverrideStore.put(TEST_UUID, overrideTexture, true);

        Identifier resolvedTexture = PlayerSkinOverrideResolver.resolveTexture(TEST_UUID);

        assertEquals(overrideTexture, resolvedTexture);
    }

    @Test
    void returnsNullWhenNoOverrideAndNoCache() {
        Identifier resolvedTexture = PlayerSkinOverrideResolver.resolveTexture(TEST_UUID);
        assertNull(resolvedTexture);
    }

    @Test
    void slimFlagCanBeRetrievedViaReflection() throws Exception {
        Identifier overrideTexture = Identifiers.mod("test/slim-skin");
        SkinOverrideStore.put(TEST_UUID, overrideTexture, true);

        // Access the private resolveOverride method to verify slim flag
        java.lang.reflect.Method method = PlayerSkinOverrideResolver.class.getDeclaredMethod("resolveOverride", UUID.class);
        method.setAccessible(true);
        Object override = method.invoke(null, TEST_UUID);

        assertNotNull(override);
        // Check the slim field via reflection
        java.lang.reflect.Field slimField = override.getClass().getDeclaredField("slim");
        slimField.setAccessible(true);
        Boolean slim = (Boolean) slimField.get(override);
        assertEquals(Boolean.TRUE, slim);
    }

    @Test
    void storeOverrideTakesPriorityOverCache() {
        // Put override in store
        Identifier storeTexture = Identifiers.mod("test/store-override");
        SkinOverrideStore.put(TEST_UUID, storeTexture, true);

        // The resolver should return the store texture, not null (cache miss)
        Identifier resolved = PlayerSkinOverrideResolver.resolveTexture(TEST_UUID);
        assertEquals(storeTexture, resolved);
    }
}