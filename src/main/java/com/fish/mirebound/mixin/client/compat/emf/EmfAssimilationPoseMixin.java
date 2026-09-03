package com.fish.mirebound.mixin.client.compat.emf;

import com.fish.mirebound.client.AssimilationPlayerAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freezes EMF parts after its render-time animation pass without a hard EMF dependency. */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPart",
        priority = 2000, remap = false)
public abstract class EmfAssimilationPoseMixin {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"),
            order = 900,
            remap = false)
    private void mirebound$restoreAssimilatedPose(PoseStack poseStack, VertexConsumer consumer,
            int packedLight, int packedOverlay, int color, CallbackInfo callback) {
        AssimilationPlayerAnimation.applyFrozenTransform((ModelPart) (Object) this);
    }
}
