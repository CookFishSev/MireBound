package com.fish.mirebound.mud;

import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Resolves ordinary-world vertical sinking columns without owning entity state. */
final class MudColumnResolver {
    private MudColumnResolver() {
    }

    static BlockPos findTop(Level level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        while (cursor.getY() < level.getMaxBuildHeight() - 1) {
            cursor.move(Direction.UP);
            if (!isSinkingAt(level, cursor)) {
                cursor.move(Direction.DOWN);
                break;
            }
        }
        return cursor.immutable();
    }

    static BlockPos findBottom(Level level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        while (cursor.getY() > level.getMinBuildHeight()) {
            cursor.move(Direction.DOWN);
            if (!isSinkingAt(level, cursor)) {
                cursor.move(Direction.UP);
                break;
            }
        }
        return cursor.immutable();
    }

    static double availableDepth(double surfaceY, BlockPos bottomPos) {
        return Math.max(0.12D, surfaceY - bottomPos.getY());
    }

    private static boolean isSinkingAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        return medium != null && MudBlock.supportsVerticalSinking(state, medium);
    }
}
