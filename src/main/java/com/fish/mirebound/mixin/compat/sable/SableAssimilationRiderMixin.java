package com.fish.mirebound.mixin.compat.sable;

import com.fish.mirebound.assimilation.AssimilationSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps frozen Carry On passengers out of Sable's hidden plot coordinate frame. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.mixinhelpers.entity.entity_riding_sub_level_vehicle."
        + "EntityRidingSubLevelVehicleHelper", remap = false)
public abstract class SableAssimilationRiderMixin {
    @Inject(
            method = "kickRidingEntity(Lnet/minecraft/world/entity/Entity;"
                    + "Ldev/ryanhcode/sable/sublevel/SubLevel;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private static void mirebound$keepFrozenPassengerInWorldSpace(
            Entity rider, @Coerce Object subLevel, CallbackInfoReturnable<Vec3> callback) {
        if (AssimilationSystem.bypassSableVehicleTracking(rider)) {
            callback.setReturnValue(rider.position());
        }
    }
}
