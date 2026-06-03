package com.sammy.catskincRemake.mixin.client;

import com.sammy.catskincRemake.client.SkinManagerClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerEntityRenderer.class, priority = 2_000)
public abstract class PlayerRendererMixin {
    @Inject(
            method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideTexture(AbstractClientPlayerEntity player, CallbackInfoReturnable<Identifier> cir) {
        Identifier id = SkinManagerClient.getOrFetch(player);
        if (id != null) {
            cir.setReturnValue(id);
        }
    }
}

