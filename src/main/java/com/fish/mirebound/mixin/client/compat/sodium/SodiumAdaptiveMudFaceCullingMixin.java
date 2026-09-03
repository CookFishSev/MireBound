package com.fish.mirebound.mixin.client.compat.sodium;

import com.fish.mirebound.client.AdaptiveMudModels;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies source-block face culling in Sodium's independent occlusion path. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache",
        remap = false)
public abstract class SodiumAdaptiveMudFaceCullingMixin {
    @Inject(
            method = "shouldDrawSide",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void mirebound$preserveTransparentAdaptiveBoundary(
            BlockState state, BlockGetter level, BlockPos currentPos,
            Direction direction, CallbackInfoReturnable<Boolean> callback) {
        BlockPos neighborPos = currentPos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        if (AdaptiveMudModels.shouldPreserveSourceInterface(
                state, neighbor, level, currentPos, neighborPos)) {
            callback.setReturnValue(true);
        }
    }

    @WrapOperation(
            method = "shouldDrawSide",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "skipRendering(Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/Direction;)Z",
                    remap = false),
            remap = false,
            require = 0)
    private boolean mirebound$cullMatchingAdaptiveSourceFace(
            BlockState current, BlockState neighbor, Direction face,
            Operation<Boolean> original,
            BlockState state, BlockGetter level, BlockPos currentPos,
            Direction direction) {
        return original.call(current, neighbor, face)
                || AdaptiveMudModels.shouldCullSourceInterface(
                        state, neighbor, level, currentPos,
                        currentPos.relative(direction), direction);
    }
}
