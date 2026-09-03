package com.fish.mirebound.mixin.client.compat.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Sable 1.2.2 assumes Create's rayTraceUntil never returns null. Create does
 * return null for a legitimate miss, so keep the miss nullable through Sable's
 * distance comparison instead of dereferencing it.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.neoforge.mixinhelper.compatibility.create.raycasts.SableRaycastHelper",
        remap = false)
public abstract class SableRaycastHelperMixin {
    @WrapOperation(
            method = "rayCastUntilWithSublevels(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Ljava/util/function/Predicate;Ljava/util/function/BiPredicate;)"
                    + "Lcom/simibubi/create/foundation/utility/RaycastHelper$PredicateTraceResult;",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/"
                            + "RaycastHelper$PredicateTraceResult;getPos()"
                            + "Lnet/minecraft/core/BlockPos;",
                    remap = false),
            require = 0)
    private static BlockPos mirebound$allowCreateRayMiss(
            @Coerce Object trace, Operation<BlockPos> original) {
        return trace == null ? null : original.call(trace);
    }
}
