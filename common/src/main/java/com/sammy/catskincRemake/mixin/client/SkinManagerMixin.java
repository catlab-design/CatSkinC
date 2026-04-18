package com.sammy.catskincRemake.mixin.client;

import com.mojang.authlib.GameProfile;
import com.sammy.catskincRemake.client.SkinManagerClient;
import com.sammy.catskincRemake.client.SkinOverrideStore;
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
    private void catskincRemake$overrideGuiSkin(GameProfile profile, CallbackInfoReturnable<Identifier> cir) {
        UUID uuid = profile == null ? null : profile.getId();
        if (uuid == null) {
            return;
        }

        SkinOverrideStore.Entry entry = SkinOverrideStore.get(uuid);
        if (entry != null) {
            cir.setReturnValue(entry.texture);
            return;
        }

        Identifier cached = SkinManagerClient.getCached(uuid);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }

        SkinManagerClient.ensureFetch(uuid);
    }
}
