package com.fish.mirebound.generation;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

/** Shared loaded-chunk surface lookup used by preview and authoritative generation. */
public final class MudTerrainDepositPlanner {
    private MudTerrainDepositPlanner() {
    }

    public static int findSurfaceY(
            Level level, BlockPos center, int x, int z,
            MudTerrainGenerationSettings settings) {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            return Integer.MIN_VALUE;
        }
        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int minimumY = Math.max(level.getMinBuildHeight(),
                center.getY() - settings.heightTolerance());
        int maximumY = Math.min(level.getMaxBuildHeight() - 1,
                center.getY() + settings.heightTolerance());
        if (surfaceY < minimumY || surfaceY > maximumY) {
            return Integer.MIN_VALUE;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, surfaceY, z);
        int searchFloor = Math.max(minimumY, surfaceY - 6);
        for (int y = surfaceY; y >= searchFloor; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof AdaptiveMudBlock
                    || AdaptiveMudEligibility.check(level, cursor, state).supported()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
