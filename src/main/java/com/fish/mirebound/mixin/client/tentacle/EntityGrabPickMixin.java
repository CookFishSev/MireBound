package com.fish.mirebound.mixin.client.tentacle;

import com.fish.mirebound.client.tentacle.TentacleGrabCamera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the ragdoll eye only while GameRenderer is executing its own pick call.
 * Other mods and normal entity logic continue to observe the player's real view.
 */
@Mixin(Entity.class)
public abstract class EntityGrabPickMixin {
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private void mirebound$useScopedGrabEye(float partialTick,
            CallbackInfoReturnable<Vec3> callback) {
        Vec3 origin = TentacleGrabCamera.scopedPickOrigin((Entity) (Object) this);
        if (origin != null) {
            callback.setReturnValue(origin);
        }
    }

    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void mirebound$useScopedGrabDirection(float partialTick,
            CallbackInfoReturnable<Vec3> callback) {
        Vec3 direction = TentacleGrabCamera.scopedPickDirection((Entity) (Object) this);
        if (direction != null) {
            callback.setReturnValue(direction);
        }
    }
}
