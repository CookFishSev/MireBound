package com.fish.mirebound.mixin.client.tentacle;

import com.fish.mirebound.client.tentacle.TentacleGrabPlayerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates a grabbed player's whole body without rotating their name tag.
 *
 * <p>The torso rotation used to be pushed onto the {@code PoseStack} in {@code RenderPlayerEvent.Pre}
 * and popped in {@code Post}. {@code LivingEntityRenderer.render} draws the model and its layers
 * inside a {@code pushPose}/{@code popPose} pair but calls {@code super.render} — which draws the
 * name tag, damage numbers and glow outline — only <em>after</em> that pop, and the Forge events
 * bracket the whole method. A transform installed in {@code Pre} therefore reached the name tag too,
 * so a player hanging upside down had upside-down text floating over them.
 *
 * <p>{@code setupRotations} runs inside the pair, so a rotation applied here cannot escape into the
 * name tag. Injecting at {@code HEAD} keeps the original composition order: the ragdoll rotation
 * stays outside vanilla's own {@code 180 - yBodyRot} yaw, exactly as it was when it lived in
 * {@code Pre}.
 *
 * <p>{@code PlayerRenderer} overrides {@code setupRotations} for swimming and elytra flight, but each
 * of its branches begins with a {@code super} call, so this injection still runs — and still runs
 * outermost. Targeting the base class rather than the override also means one injection covers the
 * fall-flying and swimming paths instead of needing to be repeated per branch.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRagdollRotationMixin {
    @Inject(
            method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"))
    private void mirebound$rotateRagdollBody(LivingEntity entity, PoseStack poseStack,
            float bob, float yBodyRot, float partialTick, float scale, CallbackInfo callback) {
        TentacleGrabPlayerRenderer.applyBodyRotation(entity, poseStack, scale);
    }
}
