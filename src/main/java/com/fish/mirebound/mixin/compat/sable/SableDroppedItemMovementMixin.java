package com.fish.mirebound.mixin.compat.sable;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.itemphysics.DroppedItemPhysicsSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives an anchored item one movement owner instead of combining mud and Sable motion. */
@Mixin(value = Entity.class, priority = 900)
public abstract class SableDroppedItemMovementMixin {
    @Unique
    private static boolean mirebound$loggedMovementOwnership;

    @Inject(
            method = "sable$getTrackingSubLevel()Ldev/ryanhcode/sable/sublevel/SubLevel;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void mirebound$hideOwnedItemTracking(
            CallbackInfoReturnable<Object> callback) {
        Entity entity = (Entity) (Object) this;
        if (DroppedItemPhysicsSystem.ownsSableMovement(entity)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(
            method = "move(Lnet/minecraft/world/entity/MoverType;"
                    + "Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mirebound$ownAnchoredItemMovement(
            MoverType moverType, Vec3 movement, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (!DroppedItemPhysicsSystem.ownsSableMovement(entity)) {
            return;
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(false);
        callback.cancel();
        if (!mirebound$loggedMovementOwnership) {
            mirebound$loggedMovementOwnership = true;
            Mirebound.LOGGER.info(
                    "Mirebound is owning movement for Sable-anchored dropped items");
        }
    }

    @Inject(
            method = "moveTowardsClosestSpace(DDD)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mirebound$captureBeforeVanillaEscape(
            double x, double y, double z, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof ItemEntity item)
                || !DroppedItemPhysicsSystem.suppressSableEscape(item)) {
            return;
        }
        if (DroppedItemPhysicsSystem.ownsSableMovement(item)) {
            item.setDeltaMovement(Vec3.ZERO);
            item.setOnGround(false);
        }
        callback.cancel();
    }
}
