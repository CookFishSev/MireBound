package com.fish.mirebound.generation;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import com.fish.mirebound.mud.MudBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Shared registry-state validation for lake shell and inner block choices. */
public final class MudTerrainBlockRules {
    private MudTerrainBlockRules() {
    }

    public static boolean validInner(BlockState state) {
        if (state.getBlock() instanceof AdaptiveMudBlock) {
            return false;
        }
        if (state.getBlock() instanceof MudBlock) {
            return true;
        }
        return validFullSource(state);
    }

    public static boolean validFullSource(BlockState state) {
        return AdaptiveMudEligibility.check(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, state).supported();
    }
}
