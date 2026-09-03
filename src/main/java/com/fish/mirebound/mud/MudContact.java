package com.fish.mirebound.mud;

import com.fish.mirebound.compat.sable.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Immutable contact frame shared by movement, coverage, and client prediction. */
record MudContact(
        BlockState state,
        SinkingMedium medium,
        SinkingMedium physicsMedium,
        BlockPos physicsProfilePos,
        BlockPos surfaceProfilePos,
        double surfaceY,
        double depth,
        double depthFactor,
        double horizontalCoverage,
        double availableDepth,
        double layerTopDepth,
        double layerDepth,
        boolean hasDeeperLayer,
        SableCoverageContext sableContext,
        Vec3 surfacePoint,
        Vec3 surfaceNormal,
        Vec3 surfaceAxisX,
        Vec3 surfaceAxisZ,
        double clipNegativeX,
        double clipPositiveX,
        double clipNegativeZ,
        double clipPositiveZ,
        boolean pollutionContact) {
}

record SableCoverageContext(
        Object subLevel,
        SinkingMedium contactMedium,
        double surfaceY,
        double availableDepth,
        Vec3 localUp,
        BlockPos columnPos,
        SableLayer[] layers) {
    private static final double SURFACE_SEARCH_RADIUS = 0.009D;

    SableLayerPoint layerPoint(Vec3 worldPoint) {
        Vec3 localPoint = SableCompat.toLocal(subLevel, worldPoint);
        if (localPoint == null) {
            return null;
        }

        double coordinate = localPoint.dot(localUp);
        for (SableLayer layer : layers) {
            double depth = layer.topCoordinate() - coordinate;
            if (depth < -SURFACE_SEARCH_RADIUS
                    || coordinate < layer.bottomCoordinate() - 0.004D
                    || !layer.contains(localPoint, SURFACE_SEARCH_RADIUS)) {
                continue;
            }
            return new SableLayerPoint(layer, localPoint, depth);
        }
        return null;
    }
}

record SableLayer(
        SinkingMedium medium,
        BlockPos pos,
        BlockState state,
        long visualSource,
        double surfaceHeight,
        double topCoordinate,
        double bottomCoordinate,
        VoxelShape localShape) {
    boolean contains(Vec3 point, double tolerance) {
        Vec3 local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB box : localShape.toAabbs()) {
            if (local.x >= box.minX - tolerance
                    && local.x <= box.maxX + tolerance
                    && local.y >= box.minY - tolerance
                    && local.y <= box.maxY + tolerance
                    && local.z >= box.minZ - tolerance
                    && local.z <= box.maxZ + tolerance) {
                return true;
            }
        }
        return false;
    }
}

record SableLayerPoint(SableLayer layer, Vec3 localPoint, double depth) {
}

record SableGravityColumn(
        Object subLevel,
        Direction localDown,
        Vec3 localUp,
        Vec3 localAxisX,
        Vec3 localAxisZ,
        BlockPos surfacePos,
        BlockState surfaceState,
        SinkingMedium surfaceMedium,
        double surfaceCoordinate,
        double availableDepth,
        SableLayer[] layers) {
    Vec3 surfacePoint(Vec3 localPoint) {
        Direction.Axis axis = localDown.getAxis();
        double signedCoordinate = surfaceCoordinate
                * (localUp.x + localUp.y + localUp.z);
        return replaceAxisCoordinate(localPoint, axis, signedCoordinate);
    }

    double depth(Vec3 localPoint) {
        return surfaceCoordinate - localPoint.dot(localUp);
    }

    SableLayer layerAt(Vec3 localPoint) {
        double coordinate = localPoint.dot(localUp);
        for (SableLayer layer : layers) {
            if (coordinate <= layer.topCoordinate() + 0.018D
                    && coordinate >= layer.bottomCoordinate() - 0.018D) {
                return layer;
            }
        }
        return null;
    }

    private static Vec3 replaceAxisCoordinate(
            Vec3 point, Direction.Axis axis, double coordinate) {
        return switch (axis) {
            case X -> new Vec3(coordinate, point.y, point.z);
            case Y -> new Vec3(point.x, coordinate, point.z);
            case Z -> new Vec3(point.x, point.y, coordinate);
        };
    }
}

record SurfaceClip(
        double negativeX,
        double positiveX,
        double negativeZ,
        double positiveZ) {
}

record PhysicsLayer(BlockState state, SinkingMedium medium, BlockPos pos) {
}

record LayerDepth(double topDepth, double depth, boolean hasDeeperLayer) {
}
