package com.sammy.catskincRemake.mixin.client;

import com.sammy.catskincRemake.client.PlayerSkinOverrideResolver;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin120 {
    @Inject(
            method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        UUID uuid = self.getUUID();
        cir.setReturnValue(PlayerSkinOverrideResolver.resolvePlayerSkin(uuid, cir.getReturnValue()));
    }
}

