package com.sammy.catskinc.mixin.client;

import com.sammy.catskinc.client.PlayerSkinOverrideResolver;
import com.sammy.catskinc.client.SkinManagerClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = PlayerEntityRenderer.class, priority = 2_000)
public abstract class PlayerRendererMixin {
    @Inject(
            method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void Catskinc$overrideTexture(AbstractClientPlayerEntity player, CallbackInfoReturnable<Identifier> cir) {
        if (player == null) {
            return;
        }
        Identifier id = PlayerSkinOverrideResolver.resolveTexture(player.getUuid());
        if (id != null) {
            cir.setReturnValue(id);
        }
    }
}