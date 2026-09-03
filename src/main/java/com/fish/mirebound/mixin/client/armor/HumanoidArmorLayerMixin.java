package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.ArmorMudRenderBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Unique
    private static final ThreadLocal<LivingEntity> MIREBOUND_ARMOR_ENTITY = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<EquipmentSlot> MIREBOUND_ARMOR_SLOT = new ThreadLocal<>();

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"))
    private void mirebound$captureArmorContext(PoseStack poseStack, MultiBufferSource buffers,
            LivingEntity entity, EquipmentSlot slot, int packedLight, HumanoidModel<?> model,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo callback) {
        MIREBOUND_ARMOR_ENTITY.set(entity);
        MIREBOUND_ARMOR_SLOT.set(slot);
    }

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("RETURN"))
    private void mirebound$clearArmorContext(PoseStack poseStack, MultiBufferSource buffers,
            LivingEntity entity, EquipmentSlot slot, int packedLight, HumanoidModel<?> model,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo callback) {
        MIREBOUND_ARMOR_ENTITY.remove();
        MIREBOUND_ARMOR_SLOT.remove();
    }

    @Redirect(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V"))
    private void mirebound$renderCompositedArmor(HumanoidArmorLayer<?, ?, ?> layer,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, Model model,
            int tint, ResourceLocation texture) {
        LivingEntity entity = MIREBOUND_ARMOR_ENTITY.get();
        EquipmentSlot slot = MIREBOUND_ARMOR_SLOT.get();
        if (entity == null || slot == null) {
            model.renderToBuffer(
                    poseStack,
                    buffers.getBuffer(net.minecraft.client.renderer.RenderType.armorCutoutNoCull(texture)),
                    packedLight,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    tint);
            return;
        }
        ArmorMudRenderBridge.renderArmorLayer(
                poseStack, buffers, packedLight, model, tint, texture, entity, slot);
    }
}
