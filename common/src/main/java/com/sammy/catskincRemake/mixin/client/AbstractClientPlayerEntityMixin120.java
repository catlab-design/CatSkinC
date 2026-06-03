package com.sammy.catskincRemake.mixin.client;

import com.sammy.catskincRemake.client.SkinManagerClient;
import com.sammy.catskincRemake.client.SkinOverrideStore;
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
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideTexture(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        UUID uuid = self.getUuid();

        SkinOverrideStore.Entry entry = SkinOverrideStore.get(uuid);
        if (entry != null) {
            cir.setReturnValue(entry.texture);
            return;
        }

        Identifier id = SkinManagerClient.getOrFetch(self);
        if (id != null) {
            cir.setReturnValue(id);
        }
    }

    @Inject(
            method = "getModel()Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideModel(CallbackInfoReturnable<String> cir) {
        UUID uuid = ((AbstractClientPlayerEntity) (Object) this).getUuid();
        SkinOverrideStore.Entry entry = SkinOverrideStore.get(uuid);
        if (entry != null) {
            cir.setReturnValue(entry.slim ? "slim" : "default");
            return;
        }
        Boolean slim = SkinManagerClient.isSlimOrNull(uuid);
        if (slim != null) {
            cir.setReturnValue(slim.booleanValue() ? "slim" : "default");
        }
    }
}

