package com.fish.mirebound.mud;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.GrowingPlantBodyBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;

/** Shared plant support rule for native and adaptive sinking blocks. */
public final class MudPlantSupport {
    private MudPlantSupport() {
    }

    public static boolean isSupportedPlant(BlockState plant) {
        if (plant == null || !plant.getFluidState().isEmpty()) {
            return false;
        }
        return plant.getBlock() instanceof BushBlock || isGrowingPlantSegment(plant);
    }

    public static boolean isGrowingPlantSegment(BlockState plant) {
        return plant != null && plant.getFluidState().isEmpty()
                && (plant.getBlock() instanceof GrowingPlantHeadBlock
                        || plant.getBlock() instanceof GrowingPlantBodyBlock);
    }

    public static boolean canSustain(BlockState support, Direction facing,
            BlockState plant) {
        if (support == null || facing != Direction.UP
                || !isSupportedPlant(plant)
                || !(support.getBlock() instanceof MudBlock mud)) {
            return false;
        }
        return canSustainSurface(
                MudBlock.surfaceDirection(support, mud.medium()), facing, true);
    }

    static boolean canSustainSurface(Direction surfaceDirection,
            Direction facing, boolean plant) {
        return plant && facing == Direction.UP
                && surfaceDirection == Direction.UP;
    }

    public static TriState result(BlockState support, Direction facing,
            BlockState plant) {
        return canSustain(support, facing, plant)
                ? TriState.TRUE : TriState.DEFAULT;
    }
}
