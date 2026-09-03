package com.fish.mirebound.mixin.client.compat.sable;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.client.AdaptiveMudClientCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Supplies the real source state to Sable's optimized single-block renderer. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData",
        remap = false)
public abstract class SableAdaptiveMudSingleBlockMixin {
    private static final AtomicBoolean MIREBOUND$LOGGED_SOURCE = new AtomicBoolean();

    @WrapOperation(
            method = "rebuild",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;"
                            + "getBlockState(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"),
            remap = false,
            require = 0)
    private BlockState mirebound$useAdaptiveSourceState(
            ClientLevel level, BlockPos pos, Operation<BlockState> original) {
        BlockState state = original.call(level, pos);
        if (!(state.getBlock() instanceof AdaptiveMudBlock)) {
            return state;
        }
        BlockState source = AdaptiveMudClientCache.sourceState(level, pos);
        if (source == null || source.getBlock() instanceof AdaptiveMudBlock) {
            return state;
        }
        if (MIREBOUND$LOGGED_SOURCE.compareAndSet(false, true)) {
            Mirebound.LOGGER.info("Sable adaptive single-block source rendering is active");
        }
        return source;
    }
}
