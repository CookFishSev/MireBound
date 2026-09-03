package com.fish.mirebound.mixin.client.itemphysics;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.client.itemphysics.DroppedItemPresentation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla client simulation from fighting server-owned mud anchors. */
@Mixin(Entity.class)
public abstract class DroppedItemMovementClientMixin {
    @Unique
    private static boolean mirebound$loggedClientMovementOwnership;

    @Inject(method = "tick", at = @At("HEAD"))
    private void mirebound$resetAnchoredItemMotion(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (!entity.level().isClientSide()
                || !(entity instanceof ItemEntity item)
                || !DroppedItemPresentation.isAnchored(item)) {
            return;
        }
        item.setDeltaMovement(Vec3.ZERO);
        item.setOnGround(false);
    }

    @Inject(
            method = "move(Lnet/minecraft/world/entity/MoverType;"
                    + "Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mirebound$ownAnchoredItemMovement(
            MoverType moverType, Vec3 movement, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (!entity.level().isClientSide()
                || !(entity instanceof ItemEntity item)
                || !DroppedItemPresentation.isAnchored(item)) {
            return;
        }
        item.setDeltaMovement(Vec3.ZERO);
        item.setOnGround(false);
        callback.cancel();
        if (!mirebound$loggedClientMovementOwnership) {
            mirebound$loggedClientMovementOwnership = true;
            Mirebound.LOGGER.info(
                    "Mirebound is owning client movement for mud-anchored dropped items");
        }
    }
}
