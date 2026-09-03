package com.fish.mirebound.mixin.client.compat.sable;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.client.AdaptiveMudClientCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Supplies source states to Sable's fast mesh path, which omits NeoForge model data. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.fancy.SubLevelMeshBuilder",
        remap = false)
public abstract class SableAdaptiveMudModelMixin {
    @WrapOperation(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
                            + "getBlockState(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;",
                    remap = false),
            remap = false,
            require = 0)
    private BlockState mirebound$useAdaptiveSourceState(
            RenderChunkRegion region, BlockPos pos, Operation<BlockState> original) {
        BlockState state = original.call(region, pos);
        if (!(state.getBlock() instanceof AdaptiveMudBlock)) {
            return state;
        }
        BlockState source = AdaptiveMudClientCache.sourceState(
                Minecraft.getInstance().level, pos);
        return source == null || source.getBlock() instanceof AdaptiveMudBlock
                ? state : source;
    }
}
