package com.sammy.catskincRemake.mixin.client;

import com.mojang.authlib.GameProfile;
import com.sammy.catskincRemake.client.PlayerSkinOverrideResolver;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = PlayerInfo.class, priority = 1_000)
public abstract class PlayerListEntryMixin120 {
    @Inject(
            method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        UUID uuid = getUuid();
        if (uuid == null) {
            return;
        }
        cir.setReturnValue(PlayerSkinOverrideResolver.resolvePlayerSkin(uuid, cir.getReturnValue()));
    }

    private UUID getUuid() {
        PlayerInfo self = (PlayerInfo) (Object) this;
        GameProfile profile = self.getProfile();
        return profile == null ? null : profile.getId();
    }
}

