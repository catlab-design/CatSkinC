package com.sammy.catskincRemake.mixin.client;

import com.mojang.authlib.GameProfile;
import com.sammy.catskincRemake.client.PlayerSkinOverrideResolver;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.function.Supplier;

@Mixin(value = SkinManager.class, priority = 1_000)
public abstract class SkinManagerMixin {
    @Inject(
            method = "lookupInsecure(Lcom/mojang/authlib/GameProfile;)Ljava/util/function/Supplier;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideGuiSkin(GameProfile profile, CallbackInfoReturnable<Supplier<PlayerSkin>> cir) {
        UUID uuid = profile == null ? null : profile.getId();
        if (uuid == null) {
            return;
        }

        Supplier<PlayerSkin> originalSupplier = cir.getReturnValue();
        cir.setReturnValue(() -> PlayerSkinOverrideResolver.resolvePlayerSkin(
                uuid,
                originalSupplier == null ? null : originalSupplier.get()
        ));
    }
}
