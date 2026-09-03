package com.fish.mirebound.adaptive;

import com.fish.mirebound.mud.MudBlock;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared source-model and source-shape deformation policy. */
public final class AdaptiveMudDeformation {
    private static final double EPSILON = 1.0E-7D;

    private AdaptiveMudDeformation() {
    }

    public static boolean enabledByDefault(BlockState source) {
        return source.getRenderShape() == RenderShape.MODEL
                && !(source.getBlock() instanceof BushBlock)
                && !(source.getBlock() instanceof BaseTorchBlock)
                && !(source.getBlock() instanceof PointedDripstoneBlock)
                && !(source.getBlock() instanceof WebBlock);
    }

    public static VoxelShape deform(
            BlockState source, VoxelShape shape, float factor, Direction facing) {
        if (!enabledByDefault(source)) {
            return shape;
        }
        return deformShape(shape, factor, facing);
    }

    static VoxelShape deformShape(
            VoxelShape shape, float factor, Direction facing) {
        if (shape.isEmpty()) {
            return shape;
        }
        double minimumY = shape.bounds().minY;
        double clampedFactor = Mth.clamp(factor, 0.0F, 1.0F);
        if (clampedFactor >= 0.9999F
                && Math.abs(minimumY) <= EPSILON
                && facing == Direction.UP) {
            return shape;
        }
        VoxelShape result = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double maximumY = compressedCoordinate(box.maxY, minimumY, clampedFactor);
            double boxMinimumY = compressedCoordinate(box.minY, minimumY, clampedFactor);
            if (box.maxX - box.minX <= EPSILON
                    || maximumY - boxMinimumY <= EPSILON
                    || box.maxZ - box.minZ <= EPSILON) {
                continue;
            }
            AABB compressed = new AABB(
                    box.minX, boxMinimumY, box.minZ,
                    box.maxX, maximumY, box.maxZ);
            result = Shapes.or(result, orient(compressed, facing));
        }
        return result;
    }

    static double compressedCoordinate(double coordinate, double minimum, double factor) {
        return (coordinate - minimum) * factor;
    }

    static double topSurfaceAt(VoxelShape shape, double localX, double localZ) {
        if (shape == null || shape.isEmpty()
                || !Double.isFinite(localX) || !Double.isFinite(localZ)) {
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

    private static VoxelShape orient(AABB box, Direction facing) {
        if (facing == Direction.UP) {
            return Shapes.create(box);
        }
        Vec3 minimum = new Vec3(
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY);
        Vec3 maximum = new Vec3(
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
        for (int corner = 0; corner < 8; corner++) {
            Vec3 transformed = MudBlock.orientLocalPoint(new Vec3(
                    (corner & 1) == 0 ? box.minX : box.maxX,
                    (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ),
                    facing);
            minimum = new Vec3(
                    Math.min(minimum.x, transformed.x),
                    Math.min(minimum.y, transformed.y),
                    Math.min(minimum.z, transformed.z));
            maximum = new Vec3(
                    Math.max(maximum.x, transformed.x),
                    Math.max(maximum.y, transformed.y),
                    Math.max(maximum.z, transformed.z));
        }
        return Shapes.box(
                Mth.clamp(minimum.x, 0.0D, 1.0D),
                Mth.clamp(minimum.y, 0.0D, 1.0D),
                Mth.clamp(minimum.z, 0.0D, 1.0D),
                Mth.clamp(maximum.x, 0.0D, 1.0D),
                Mth.clamp(maximum.y, 0.0D, 1.0D),
                Mth.clamp(maximum.z, 0.0D, 1.0D));
    }
}
