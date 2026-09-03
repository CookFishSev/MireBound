package com.fish.mirebound.mixin.client.compat.sable;

import com.fish.mirebound.client.itemphysics.DroppedItemPresentation;
import com.fish.mirebound.compat.sable.SableCompat;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps anchored item packets in the world-space frame owned by the server anchor. */
@Mixin(value = ClientPacketListener.class, priority = 850)
public abstract class SableDroppedItemNetworkClientMixin {
    @Inject(
            method = "sable$lerp(Lnet/minecraft/world/entity/Entity;DDDFFIZZ)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void mirebound$lerpOwnedItemInWorldSpace(
            Entity entity,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            int lerpSteps,
            boolean teleport,
            boolean actuallyInSubLevel,
            CallbackInfo callback) {
        if (!(entity instanceof ItemEntity item)
                || !DroppedItemPresentation.isSableAnchored(item)) {
            return;
        }
        SableCompat.clearEntityTracking(item);
        item.lerpTo(x, y, z, yRot, xRot, lerpSteps);
        callback.cancel();
    }
}
