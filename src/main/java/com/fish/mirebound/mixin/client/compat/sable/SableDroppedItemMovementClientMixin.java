package com.fish.mirebound.mixin.client.compat.sable;

import com.fish.mirebound.client.itemphysics.DroppedItemPresentation;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.itemphysics.DroppedItemSableAnchorView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors server movement ownership and removes Sable's stale client plot projection. */
@Mixin(value = Entity.class, priority = 850)
public abstract class SableDroppedItemMovementClientMixin
        implements DroppedItemSableAnchorView {
    @Override
    public boolean mirebound$isSableMudAnchored() {
        Entity entity = (Entity) (Object) this;
        return entity instanceof ItemEntity item
                && DroppedItemPresentation.isSableAnchored(item);
    }

    @Inject(
            method = "sable$getTrackingSubLevel()Ldev/ryanhcode/sable/sublevel/SubLevel;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void mirebound$hideClientOwnedItemTracking(
            CallbackInfoReturnable<Object> callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()
                && entity instanceof ItemEntity item
                && DroppedItemPresentation.isSableAnchored(item)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(
            method = "sable$getPlotPosition()Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void mirebound$hideClientOwnedItemPlotPosition(
            CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()
                && entity instanceof ItemEntity item
                && DroppedItemPresentation.isSableAnchored(item)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mirebound$clearAnchoredItemPlotState(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()
                && entity instanceof ItemEntity item
                && DroppedItemPresentation.isSableAnchored(item)) {
            SableCompat.clearEntityTracking(item);
        }
    }
}
