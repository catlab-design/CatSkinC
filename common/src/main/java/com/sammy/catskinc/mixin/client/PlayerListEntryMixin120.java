package com.sammy.catskinc.mixin.client;

import com.mojang.authlib.GameProfile;
import com.sammy.catskinc.client.SkinManagerClient;
import com.sammy.catskinc.client.SkinOverrideStore;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = PlayerListEntry.class, priority = 1_000)
public abstract class PlayerListEntryMixin120 {
    @Inject(
            method = "getSkinTexture()Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void Catskinc$overrideTexture(CallbackInfoReturnable<Identifier> cir) {
        UUID uuid = getUuid();
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

    @Inject(
            method = "getModel()Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void Catskinc$overrideModel(CallbackInfoReturnable<String> cir) {
        UUID uuid = getUuid();
        if (uuid == null) {
            return;
        }
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

    private UUID getUuid() {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        GameProfile profile = self.getProfile();
        return profile == null ? null : profile.getId();
    }
}


