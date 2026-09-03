package com.fish.mirebound.mixin.compat.sable;

import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState", remap = false)
public abstract class SableVoxelNeighborhoodStateMixin {
    @Inject(
            method = "isSolid(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void mirebound$treatSinkingBlocksAsPhysicalSolids(BlockGetter level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof MudBlock) {
            callback.setReturnValue(true);
        }
    }

    @Inject(
            method = "isFullBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void mirebound$treatSinkingBlocksAsPhysicalFullBlocks(BlockGetter level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof MudBlock mudBlock) {
            net.minecraft.world.level.Level world = level instanceof net.minecraft.world.level.Level value ? value : null;
            callback.setReturnValue((world == null
                    ? MudMediumRuntime.surfaceHeight(world, state, mudBlock.medium())
                    : MudMediumRuntime.surfaceHeight(world, pos, state, mudBlock.medium()))
                    >= 0.999D);
        }
    }
}
