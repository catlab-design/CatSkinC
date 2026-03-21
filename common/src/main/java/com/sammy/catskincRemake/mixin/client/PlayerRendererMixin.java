package com.sammy.catskincRemake.mixin.client;

import com.sammy.catskincRemake.client.PlayerSkinOverrideResolver;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerRenderer.class, priority = 2_000)
public abstract class PlayerRendererMixin {
    @Invoker("setModelProperties")
    protected abstract void catskincRemake$invokeSetModelProperties(AbstractClientPlayer player);

    @Inject(
            method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$overrideTexture(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        if (player == null) {
            return;
        }

        ResourceLocation id = PlayerSkinOverrideResolver.resolveTexture(player.getUUID());
        if (id != null) {
            cir.setReturnValue(id);
        }
    }

    @Inject(
            method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$renderOverriddenRightHand(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer player,
            CallbackInfo ci
    ) {
        if (this.catskincRemake$renderFirstPersonHand(poseStack, buffers, packedLight, player, true)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void catskincRemake$renderOverriddenLeftHand(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer player,
            CallbackInfo ci
    ) {
        if (this.catskincRemake$renderFirstPersonHand(poseStack, buffers, packedLight, player, false)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkin()Lnet/minecraft/client/resources/PlayerSkin;"
            ),
            require = 0
    )
    private PlayerSkin catskincRemake$overrideFirstPersonHandSkin(
            AbstractClientPlayer player,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer renderedPlayer,
            ModelPart arm,
            ModelPart sleeve
    ) {
        if (player == null) {
            return null;
        }
        return PlayerSkinOverrideResolver.resolvePlayerSkin(player.getUUID(), player.getSkin());
    }

    @Redirect(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/PlayerSkin;texture()Lnet/minecraft/resources/ResourceLocation;"
            ),
            require = 0
    )
    private ResourceLocation catskincRemake$overrideFirstPersonHandTexture(
            PlayerSkin skin,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer player,
            ModelPart arm,
            ModelPart sleeve
    ) {
        if (player == null) {
            return skin == null ? null : skin.texture();
        }
        ResourceLocation override = PlayerSkinOverrideResolver.resolveTexture(player.getUUID());
        return override != null ? override : (skin == null ? null : skin.texture());
    }

    private boolean catskincRemake$renderFirstPersonHand(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            AbstractClientPlayer player,
            boolean rightHand
    ) {
        if (player == null) {
            return false;
        }

        ResourceLocation texture = PlayerSkinOverrideResolver.resolveTexture(player.getUUID());
        if (texture == null) {
            return false;
        }

        PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer)(Object)this).getModel();
        this.catskincRemake$invokeSetModelProperties(player);
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        ModelPart arm = rightHand ? model.rightArm : model.leftArm;
        ModelPart sleeve = rightHand ? model.rightSleeve : model.leftSleeve;
        arm.xRot = 0.0F;
        arm.render(poseStack, buffers.getBuffer(RenderType.entitySolid(texture)), packedLight, OverlayTexture.NO_OVERLAY);
        sleeve.xRot = 0.0F;
        sleeve.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(texture)), packedLight, OverlayTexture.NO_OVERLAY);
        return true;
    }
}

