package com.fish.mirebound.mixin.mud;

import com.fish.mirebound.mud.MudPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true, require = 1)
    private void mirebound$blockJumpInMud(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (MudPhysics.shouldBlockJump(self)) {
            ci.cancel();
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true, require = 1)
    private void mirebound$lockSculkClampMovement(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && MudPhysics.isSculkClampMovementLocked(player)) {
            player.setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }
}
