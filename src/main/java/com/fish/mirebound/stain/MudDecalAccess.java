package com.fish.mirebound.stain;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared ordinary-world and Sable access to hidden stain containers. */
public final class MudDecalAccess {
    static final int DECORATION_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private MudDecalAccess() {
    }

    public static BlockState state(ServerLevel level, Object subLevel, BlockPos pos) {
        return subLevel == null ? level.getBlockState(pos) : SableCompat.subLevelBlockState(level, subLevel, pos);
    }

    public static BlockEntity blockEntity(ServerLevel level, Object subLevel, BlockPos pos) {
        return subLevel == null ? level.getBlockEntity(pos) : SableCompat.subLevelBlockEntity(level, subLevel, pos);
    }

    public static boolean placeContainer(ServerLevel level, Object subLevel, BlockPos pos) {
        BlockState state = ModBlocks.MUD_FOOTPRINT.get().defaultBlockState();
        return subLevel == null
                ? level.setBlock(pos, state, DECORATION_UPDATE_FLAGS)
                : SableCompat.setSubLevelBlock(level, subLevel, pos, state);
    }

    public static void removeContainer(ServerLevel level, Object subLevel, BlockPos pos) {
        if (subLevel == null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), DECORATION_UPDATE_FLAGS);
        } else {
            SableCompat.removeSubLevelBlock(level, subLevel, pos);
        }
    }

    public static void removeContainer(ServerLevel level, BlockPos pos) {
        Object subLevel = level.getBlockEntity(pos) instanceof MudFootprintBlockEntity blockEntity
                ? SableCompat.containingSubLevel(blockEntity)
                : null;
        removeContainer(level, subLevel, pos);
    }
}
