package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.ArmorAccessoryRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartArmorAccessoryMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"))
    private void mirebound$captureArmorAccessoryModel(PoseStack poseStack, VertexConsumer consumer,
            int packedLight, int packedOverlay, int color, CallbackInfo callback) {
        ArmorAccessoryRenderContext.captureModelPart((ModelPart) (Object) this);
    }
}
