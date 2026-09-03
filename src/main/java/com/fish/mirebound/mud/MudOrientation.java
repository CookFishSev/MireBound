package com.fish.mirebound.mud;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure local-coordinate transforms shared by block shapes and baked models. */
public final class MudOrientation {
    private MudOrientation() {
    }

    public static Vec3 orientPoint(Vec3 point, Direction facing) {
        return switch (facing) {
            case UP -> point;
            case DOWN -> new Vec3(point.x, 1.0D - point.y, 1.0D - point.z);
            case NORTH -> new Vec3(point.x, point.z, 1.0D - point.y);
            case SOUTH -> new Vec3(point.x, 1.0D - point.z, point.y);
            case EAST -> new Vec3(point.y, 1.0D - point.x, point.z);
            case WEST -> new Vec3(1.0D - point.y, point.x, point.z);
        };
    }

    public static Vec3 orientVector(Vec3 vector, Direction facing) {
        return switch (facing) {
            case UP -> vector;
            case DOWN -> new Vec3(vector.x, -vector.y, -vector.z);
            case NORTH -> new Vec3(vector.x, vector.z, -vector.y);
            case SOUTH -> new Vec3(vector.x, -vector.z, vector.y);
            case EAST -> new Vec3(vector.y, -vector.x, vector.z);
            case WEST -> new Vec3(-vector.y, vector.x, vector.z);
        };
    }

    public static Direction orientDirection(
            Direction direction, Direction facing) {
        Vec3 vector = orientVector(new Vec3(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ()),
                facing);
        return Direction.getNearest(vector.x, vector.y, vector.z);
    }

    public static Direction surfaceDirection(int configuredHeightPixels, Direction storedFacing) {
        return configuredHeightPixels >= 16 ? Direction.UP : storedFacing;
    }

    public static AABB layerBounds(Direction facing, double thickness) {
        double clamped = Math.max(0.0D, Math.min(1.0D, thickness));
        return switch (facing) {
            case UP -> new AABB(0.0D, 0.0D, 0.0D, 1.0D, clamped, 1.0D);
            case DOWN -> new AABB(
                    0.0D, 1.0D - clamped, 0.0D, 1.0D, 1.0D, 1.0D);
            case NORTH -> new AABB(
                    0.0D, 0.0D, 1.0D - clamped, 1.0D, 1.0D, 1.0D);
            case SOUTH -> new AABB(
                    0.0D, 0.0D, 0.0D, 1.0D, 1.0D, clamped);
            case EAST -> new AABB(
                    0.0D, 0.0D, 0.0D, clamped, 1.0D, 1.0D);
            case WEST -> new AABB(
                    1.0D - clamped, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        };
    }
}
