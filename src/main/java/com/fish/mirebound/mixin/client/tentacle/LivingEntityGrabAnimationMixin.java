package com.fish.mirebound.mixin.client.tentacle;

import com.fish.mirebound.client.tentacle.TentacleGrabPlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityGrabAnimationMixin {
    @Inject(method = {"isVisuallySwimming", "isFallFlying", "isUsingItem"},
            at = @At("HEAD"), cancellable = true)
    private void mirebound$suppressLivingActionAnimation(CallbackInfoReturnable<Boolean> callback) {
        if (TentacleGrabPlayerRenderer.suppressesAnimation((LivingEntity) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getSwimAmount", at = @At("HEAD"), cancellable = true)
    private void mirebound$suppressSwimAnimation(float partialTick,
            CallbackInfoReturnable<Float> callback) {
        if (TentacleGrabPlayerRenderer.suppressesAnimation((LivingEntity) (Object) this)) {
            callback.setReturnValue(0.0F);
        }
    }

    @Inject(method = "getFallFlyingTicks", at = @At("HEAD"), cancellable = true)
    private void mirebound$suppressFlightAnimation(CallbackInfoReturnable<Integer> callback) {
        if (TentacleGrabPlayerRenderer.suppressesAnimation((LivingEntity) (Object) this)) {
            callback.setReturnValue(0);
        }
    }

    @Inject(method = "getAttackAnim", at = @At("HEAD"), cancellable = true)
    private void mirebound$suppressAttackAnimation(float partialTick,
            CallbackInfoReturnable<Float> callback) {
        if (TentacleGrabPlayerRenderer.suppressesAnimation((LivingEntity) (Object) this)) {
            callback.setReturnValue(0.0F);
        }
    }
}
