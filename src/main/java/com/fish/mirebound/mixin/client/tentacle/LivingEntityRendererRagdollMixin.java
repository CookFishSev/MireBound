package com.fish.mirebound.mixin.client.tentacle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.fish.mirebound.client.AssimilationPlayerAnimation;
import com.fish.mirebound.client.MudProbePlayerAnimation;
import com.fish.mirebound.client.rope.RopePlayerAnimation;
import com.fish.mirebound.client.tentacle.TentacleGrabPlayerRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRagdollMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mirebound$applyRagdollAfterAnimation(EntityModel model, Entity rawEntity,
            float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, Operation<Void> original) {
        if (rawEntity instanceof AbstractClientPlayer player && model instanceof PlayerModel<?> playerModel) {
            boolean grabbed = TentacleGrabPlayerRenderer.prepareBeforeSetup(
                    player, (PlayerModel<AbstractClientPlayer>) playerModel,
                    netHeadYaw, headPitch);
            float adjustedAge = AssimilationPlayerAnimation.animationAge(
                    player.getId(), ageInTicks);
            // Limb swing is suppressed because the ragdoll owns every limb, but the look angles are
            // forwarded: the grabbed player can still turn their head, and applyAfterSetup composes
            // that look direction onto the simulated neck tilt.
            original.call(model, rawEntity,
                    grabbed ? 0.0F : limbSwing,
                    grabbed ? 0.0F : limbSwingAmount,
                    adjustedAge,
                    netHeadYaw,
                    headPitch);
            TentacleGrabPlayerRenderer.applyAfterSetup(
                    player, (PlayerModel<AbstractClientPlayer>) playerModel);
            if (!grabbed) {
                MudProbePlayerAnimation.applyAfterSetup(
                        player, (PlayerModel<AbstractClientPlayer>) playerModel);
            }
            AssimilationPlayerAnimation.applyAfterSetup(
                    player, (PlayerModel<AbstractClientPlayer>) playerModel);
            if (!grabbed) {
                RopePlayerAnimation.applyAfterSetup(
                        player, (PlayerModel<AbstractClientPlayer>) playerModel);
            }
            return;
        }
        original.call(model, rawEntity, limbSwing, limbSwingAmount,
                ageInTicks, netHeadYaw, headPitch);
    }
}
