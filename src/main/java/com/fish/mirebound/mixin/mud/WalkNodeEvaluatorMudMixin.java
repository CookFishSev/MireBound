package com.fish.mirebound.mixin.mud;

import com.fish.mirebound.mud.MudMobPhysics;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the vanilla walk evaluator from treating a sinking block as an OPEN
 * floor when a block or a custom path type supplies an adjacent path type.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMudMixin {
    @Inject(
            method = "getPathTypeStatic(Lnet/minecraft/world/level/pathfinder/PathfindingContext;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private static void mirebound$blockMudSupport(
            net.minecraft.world.level.pathfinder.PathfindingContext context,
            BlockPos.MutableBlockPos pos,
            CallbackInfoReturnable<PathType> cir) {
        if (MudMobPhysics.isPathBlocked(context.level(), pos)) {
            cir.setReturnValue(PathType.BLOCKED);
        }
    }

    @Inject(method = "getPathTypeFromState", at = @At("HEAD"), cancellable = true, require = 1)
    private static void mirebound$markMudBlocked(
            BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> cir) {
        if (ModBlocks.mediumOf(level.getBlockState(pos).getBlock()) != null) {
            cir.setReturnValue(PathType.BLOCKED);
        }
    }
}
