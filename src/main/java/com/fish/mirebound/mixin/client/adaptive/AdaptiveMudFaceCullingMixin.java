package com.fish.mirebound.mixin.client.adaptive;

import com.fish.mirebound.client.AdaptiveMudModels;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves source-block face culling across adaptive proxy boundaries. */
@Mixin(Block.class)
public abstract class AdaptiveMudFaceCullingMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void mirebound$preserveTransparentAdaptiveBoundary(
            BlockState state, BlockGetter level, BlockPos currentPos,
            Direction direction, BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> callback) {
        BlockState neighbor = level.getBlockState(neighborPos);
        if (AdaptiveMudModels.shouldPreserveSourceInterface(
                state, neighbor, level, currentPos, neighborPos)) {
            callback.setReturnValue(true);
        }
    }

    @WrapOperation(
            method = "shouldRenderFace",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "skipRendering(Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/Direction;)Z"))
    private static boolean mirebound$cullMatchingAdaptiveSourceFace(
            BlockState current, BlockState neighbor, Direction face,
            Operation<Boolean> original,
            BlockState state, BlockGetter level, BlockPos currentPos,
            Direction direction, BlockPos neighborPos) {
        return original.call(current, neighbor, face)
                || AdaptiveMudModels.shouldCullSourceInterface(
                        state, neighbor, level, currentPos, neighborPos, direction);
    }
}
