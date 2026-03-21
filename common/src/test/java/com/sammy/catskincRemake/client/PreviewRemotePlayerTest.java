package com.sammy.catskincRemake.client;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PreviewRemotePlayerTest {
    @Test
    void overridesNameRenderingForPreviewPlayers() throws Exception {
        assertEquals(
                PreviewRemotePlayer.class,
                PreviewRemotePlayer.class.getDeclaredMethod("shouldShowName").getDeclaringClass()
        );
    }

    @Test
    void choosesPreviewProfileIdsThatMatchTheRequestedArmModel() throws Exception {
        Method method = SkinUploadScreen.class.getDeclaredMethod("previewProfileUuid", boolean.class);
        method.setAccessible(true);

        UUID slimUuid = (UUID) method.invoke(null, true);
        UUID wideUuid = (UUID) method.invoke(null, false);

        assertNotNull(slimUuid);
        assertNotNull(wideUuid);
        assertEquals(PlayerSkin.Model.SLIM, DefaultPlayerSkin.get(slimUuid).model());
        assertEquals(PlayerSkin.Model.WIDE, DefaultPlayerSkin.get(wideUuid).model());
    }
}
