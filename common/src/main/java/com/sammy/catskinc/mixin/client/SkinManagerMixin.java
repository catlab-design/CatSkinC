package com.sammy.catskinc.mixin.client;

import com.mojang.authlib.GameProfile;
import com.sammy.catskinc.client.PlayerSkinOverrideResolver;
import com.sammy.catskinc.client.SkinManagerClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = PlayerSkinProvider.class, priority = 1_000)
public abstract class SkinManagerMixin {
    @Inject(
            method = "loadSkin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void Catskinc$overrideGuiSkin(GameProfile profile, CallbackInfoReturnable<Identifier> cir) {
        UUID uuid = profile == null ? null : profile.getId();
        if (uuid == null) {
            return;
        }

        Identifier override = PlayerSkinOverrideResolver.resolveTexture(uuid);
        if (override != null) {
            cir.setReturnValue(override);
            return;
        }

        SkinManagerClient.ensureFetch(uuid);
    }
}