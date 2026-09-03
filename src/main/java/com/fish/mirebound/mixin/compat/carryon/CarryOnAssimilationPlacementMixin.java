package com.fish.mirebound.mixin.compat.carryon;

import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.compat.sable.SableCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Projects Carry On's plot-local placement point before teleporting a frozen player. */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public abstract class CarryOnAssimilationPlacementMixin {
    @WrapOperation(
            method = "tryPlaceEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;teleportTo(DDD)V"),
            remap = false,
            require = 0)
    private static void mirebound$projectFrozenPlayerPlacement(
            Entity entity, double x, double y, double z, Operation<Void> original) {
        Vec3 target = new Vec3(x, y, z);
        if (AssimilationSystem.isFrozen(entity instanceof net.minecraft.world.entity.player.Player player
                ? player : null)) {
            SableCompat.clearEntityTracking(entity);
            target = SableCompat.projectOutOfSubLevel(entity.level(), target);
        }
        original.call(entity, target.x, target.y, target.z);
        if (AssimilationSystem.isFrozen(entity instanceof net.minecraft.world.entity.player.Player player
                ? player : null)) {
            SableCompat.clearEntityTracking(entity);
        }
    }
}
