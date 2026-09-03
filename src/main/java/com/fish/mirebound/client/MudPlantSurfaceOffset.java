package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudPlantSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Adjusts plant models to the actual upward surface of a sinking block. */
public final class MudPlantSurfaceOffset {
    private static final double EPSILON = 1.0E-7D;

    private MudPlantSurfaceOffset() {
    }

    public static Vec3 adjust(BlockGetter level, BlockState plant,
            BlockPos plantPos, Vec3 original) {
        if (level == null || plant == null || plantPos == null || original == null
                || !MudPlantSupport.isSupportedPlant(plant)) {
            return original;
        }

        BlockPos rootPos = plantPos;
        BlockPos supportPos = plantPos.below();
        BlockState support = level.getBlockState(supportPos);
        if (!(support.getBlock() instanceof MudBlock)) {
            if (isUpperDoublePlant(plant, support)) {
                rootPos = supportPos;
                supportPos = supportPos.below();
                support = level.getBlockState(supportPos);
            }
        }
        if (!(support.getBlock() instanceof MudBlock)) {
            if (!MudPlantSupport.isGrowingPlantSegment(plant)) {
                return original;
            }
            for (int depth = 0; depth < 64; depth++) {
                BlockPos candidateSupportPos = supportPos.below();
                BlockState candidate = level.getBlockState(candidateSupportPos);
                if (candidate.getBlock() instanceof MudBlock) {
                    rootPos = supportPos;
                    supportPos = candidateSupportPos;
                    support = candidate;
                    break;
                }
                if (!MudPlantSupport.isGrowingPlantSegment(candidate)) {
                    return original;
                }
                rootPos = supportPos;
                supportPos = candidateSupportPos;
                support = candidate;
            }
            if (!(support.getBlock() instanceof MudBlock)) {
                return original;
            }
        }
        if (!(support.getBlock() instanceof MudBlock mud)
                || MudBlock.surfaceDirection(support, mud.medium()) != Direction.UP) {
            return original;
        }

        VoxelShape shape = MudBlock.localShape(level, supportPos, support, mud.medium());
        double localX = 0.5D + original.x;
        double localZ = 0.5D + original.z;
        double surface = topAt(shape, localX, localZ);
        if (!Double.isFinite(surface)) {
            return original;
        }

        double surfaceDelta = supportPos.getY() + surface - rootPos.getY();
        // X/Z retain the plant's vanilla random offset. Y is absolute relative
        // to the plant block and must not retain a second random displacement.
        return new Vec3(original.x, surfaceDelta, original.z);
    }

    private static boolean isUpperDoublePlant(BlockState plant,
            BlockState lower) {
        return plant.getBlock() instanceof DoublePlantBlock
                && plant.hasProperty(DoublePlantBlock.HALF)
                && plant.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
                && lower.is(plant.getBlock())
                && lower.hasProperty(DoublePlantBlock.HALF)
                && lower.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    public static double topAt(VoxelShape shape, double localX, double localZ) {
        if (shape == null || shape.isEmpty()) {
            return Double.NaN;
        }
        double top = Double.NEGATIVE_INFINITY;
        for (AABB box : shape.toAabbs()) {
            if (localX >= box.minX - EPSILON && localX <= box.maxX + EPSILON
                    && localZ >= box.minZ - EPSILON && localZ <= box.maxZ + EPSILON) {
                top = Math.max(top, box.maxY);
            }
        }
        return Double.isFinite(top) ? top : Double.NaN;
    }
}
