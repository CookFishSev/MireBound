package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.AnimatedPlayerGeometryCapture;
import com.fish.mirebound.client.AssimilationCapeAnimation;
import com.fish.mirebound.client.tentacle.TentacleGrabCapeAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeLayerGeometryMixin {
    private static final String RENDER_METHOD =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V";

    @Inject(method = RENDER_METHOD, at = @At("HEAD"))
    private void mirebound$beginAssimilatedCape(PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo callback) {
        AssimilationCapeAnimation.begin(player, poseStack);
        // Captured before vanilla's own translate/rotate chain, because the grab animation replaces
        // that chain outright rather than adding to it.
        TentacleGrabCapeAnimation.begin(player, poseStack);
    }

    @Redirect(
            method = RENDER_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/PlayerModel;renderCloak(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void mirebound$captureAnimatedCape(PlayerModel<AbstractClientPlayer> model,
            PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay,
            PoseStack outerPoseStack, MultiBufferSource buffers, int outerPackedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // The grab hang runs first: it rebuilds the transform from the captured base, so anything
        // the assimilation freeze layered on would be discarded. When no grab is active it is a
        // no-op and the assimilation path behaves exactly as before.
        if (!TentacleGrabCapeAnimation.prepareCloak(player, poseStack, partialTick)) {
            AssimilationCapeAnimation.prepareCloak(player, poseStack);
        }
        VertexConsumer capture = AnimatedPlayerGeometryCapture.wrapCape(
                consumer, player, model, poseStack);
        try {
            model.renderCloak(poseStack, capture, packedLight, packedOverlay);
        } finally {
            AnimatedPlayerGeometryCapture.finishCape(
                    capture, player, model, poseStack);
        }
    }

    @Inject(method = RENDER_METHOD, at = @At("RETURN"))
    private void mirebound$endAssimilatedCape(PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo callback) {
        AssimilationCapeAnimation.end(player);
        TentacleGrabCapeAnimation.end(player);
    }
}
