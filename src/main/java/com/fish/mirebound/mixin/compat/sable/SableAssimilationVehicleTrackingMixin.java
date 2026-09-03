package com.fish.mirebound.mixin.compat.sable;

import com.fish.mirebound.assimilation.AssimilationSystem;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents a frozen player from inheriting its carrier's Sable plot tracker. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.ActiveSableCompanion", remap = false)
public abstract class SableAssimilationVehicleTrackingMixin {
    @Inject(
            method = "getVehicleSubLevel(Lnet/minecraft/world/entity/Entity;)"
                    + "Ldev/ryanhcode/sable/sublevel/SubLevel;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void mirebound$ignoreFrozenPassengerVehicle(
            Entity entity, CallbackInfoReturnable<Object> callback) {
        if (AssimilationSystem.bypassSableVehicleTracking(entity)) {
            callback.setReturnValue(null);
        }
    }
}
