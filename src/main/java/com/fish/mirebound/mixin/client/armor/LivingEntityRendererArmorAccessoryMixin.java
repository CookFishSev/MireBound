package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.ArmorAccessoryRenderContext;
import com.fish.mirebound.client.ArmorVertexContactCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererArmorAccessoryMixin {
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mirebound$captureArmorAccessoryLayer(RenderLayer layer, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, Entity rawEntity, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(rawEntity instanceof LivingEntity entity)) {
            layer.render(poseStack, buffers, packedLight, rawEntity, limbSwing, limbSwingAmount,
                    partialTick, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        ArmorAccessoryRenderContext.begin(entity, layer);
        MultiBufferSource captureBuffers = ArmorVertexContactCapture.wrapBuffers(buffers, entity);
        try {
            layer.render(poseStack, captureBuffers, packedLight, entity, limbSwing, limbSwingAmount,
                    partialTick, ageInTicks, netHeadYaw, headPitch);
        } finally {
            ArmorVertexContactCapture.finishLayer(captureBuffers);
            ArmorAccessoryRenderContext.end();
        }
    }
}
