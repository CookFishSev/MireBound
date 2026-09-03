package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.AnimatedPlayerGeometryCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererGeometryMixin {
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mirebound$captureAnimatedBody(EntityModel model, PoseStack poseStack,
            VertexConsumer consumer, int packedLight, int packedOverlay, int color,
            LivingEntity entity, float yaw, float partialTick, PoseStack outerPoseStack,
            MultiBufferSource buffers, int outerPackedLight) {
        VertexConsumer capture = AnimatedPlayerGeometryCapture.wrapBody(
                consumer, entity, model, poseStack);
        try {
            model.renderToBuffer(poseStack, capture, packedLight, packedOverlay, color);
        } finally {
            AnimatedPlayerGeometryCapture.finishBody(
                    capture, entity, model, poseStack);
        }
    }
}
