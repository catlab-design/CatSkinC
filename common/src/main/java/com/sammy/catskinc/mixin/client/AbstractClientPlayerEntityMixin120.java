package com.sammy.catskinc.mixin.client;

import com.sammy.catskinc.client.PlayerSkinOverrideResolver;
import com.sammy.catskinc.client.SkinManagerClient;
import com.sammy.catskinc.client.SkinOverrideStore;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin120 {
    @Inject(
            method = "getSkinTexture()Lnet/minecraft/util/Identifier;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void Catskinc$overrideTexture(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        UUID uuid = self.getUuid();
        Identifier original = cir.getReturnValue();

        SkinManagerClient.onSkinLookup(uuid, original);
        Identifier override = PlayerSkinOverrideResolver.resolveTexture(uuid);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Inject(
            method = "getModel()Ljava/lang/String;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void Catskinc$overrideModel(CallbackInfoReturnable<String> cir) {
        UUID uuid = ((AbstractClientPlayerEntity) (Object) this).getUuid();
        Boolean slim = SkinManagerClient.isSlimOrNull(uuid);
        SkinOverrideStore.Entry entry = SkinOverrideStore.get(uuid);
        if (entry != null) {
            cir.setReturnValue(entry.slim ? "slim" : "default");
            return;
        }
        if (slim != null) {
            cir.setReturnValue(slim.booleanValue() ? "slim" : "default");
        }
    }
}