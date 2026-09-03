package com.fish.mirebound.mixin.client.tentacle;

import com.fish.mirebound.client.tentacle.TentacleGrabPlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGrabAnimationMixin {
    @Inject(method = {"isShiftKeyDown", "isCrouching", "isSprinting", "isSwimming"},
            at = @At("HEAD"), cancellable = true)
    private void mirebound$suppressBooleanActionAnimation(CallbackInfoReturnable<Boolean> callback) {
        if (TentacleGrabPlayerRenderer.suppressesAnimation((Entity) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
    private void mirebound$suppressPoseAnimation(CallbackInfoReturnable<Pose> callback) {
        if (TentacleGrabPlayerRenderer.suppressesAnimation((Entity) (Object) this)) {
            callback.setReturnValue(Pose.STANDING);
        }
    }
}
