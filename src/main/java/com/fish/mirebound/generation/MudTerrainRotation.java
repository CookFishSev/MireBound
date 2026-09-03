package com.fish.mirebound.generation;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** One of the 24 axis-aligned orientations used by terrain previews and jobs. */
public record MudTerrainRotation(
        Direction localX,
        Direction localY,
        Direction localZ) {
    public static final MudTerrainRotation IDENTITY = new MudTerrainRotation(
            Direction.EAST, Direction.UP, Direction.SOUTH);

    public MudTerrainRotation {
        localX = Objects.requireNonNullElse(localX, Direction.EAST);
        localY = Objects.requireNonNullElse(localY, Direction.UP);
        localZ = Objects.requireNonNullElse(localZ, Direction.SOUTH);
    }

    public MudTerrainRotation rotate(Direction.Axis axis) {
        return new MudTerrainRotation(
                rotate(localX, axis), rotate(localY, axis), rotate(localZ, axis));
    }

    public BlockPos apply(BlockPos offset) {
        int x = localX.getStepX() * offset.getX()
                + localY.getStepX() * offset.getY()
                + localZ.getStepX() * offset.getZ();
        int y = localX.getStepY() * offset.getX()
                + localY.getStepY() * offset.getY()
                + localZ.getStepY() * offset.getZ();
        int z = localX.getStepZ() * offset.getX()
                + localY.getStepZ() * offset.getY()
                + localZ.getStepZ() * offset.getZ();
        return new BlockPos(x, y, z);
    }

    public boolean valid() {
        if (localX.getAxis() == localY.getAxis()
                || localX.getAxis() == localZ.getAxis()
                || localY.getAxis() == localZ.getAxis()) {
            return false;
        }
        int crossX = localX.getStepY() * localY.getStepZ()
                - localX.getStepZ() * localY.getStepY();
        int crossY = localX.getStepZ() * localY.getStepX()
                - localX.getStepX() * localY.getStepZ();
        int crossZ = localX.getStepX() * localY.getStepY()
                - localX.getStepY() * localY.getStepX();
        return crossX == localZ.getStepX()
                && crossY == localZ.getStepY()
                && crossZ == localZ.getStepZ();
    }

    private static Direction rotate(Direction direction, Direction.Axis axis) {
        int x = direction.getStepX();
        int y = direction.getStepY();
        int z = direction.getStepZ();
        return switch (axis) {
            case X -> Direction.getNearest(x, -z, y);
            case Y -> Direction.getNearest(z, y, -x);
            case Z -> Direction.getNearest(-y, x, z);
        };
    }
}
