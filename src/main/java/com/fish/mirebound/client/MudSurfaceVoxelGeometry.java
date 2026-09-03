package com.fish.mirebound.client;

import net.minecraft.world.phys.Vec3;

/** Pure height-field calculations used to omit buried voxel side walls. */
final class MudSurfaceVoxelGeometry {
    static final double NO_NEIGHBOR = Double.NEGATIVE_INFINITY;

    private MudSurfaceVoxelGeometry() {
    }

    static double visibleWallStart(
            double currentBase, double currentHeight, double neighborTop) {
        if (!Double.isFinite(neighborTop)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(currentHeight, neighborTop - currentBase));
    }

    static boolean rendersAsPile(
            double depression, double pileHeight, double visualHeightEpsilon) {
        return depression <= 0.003D && pileHeight > visualHeightEpsilon;
    }

    static boolean wallVisible(
            double startHeight, double topHeight, double visualHeightEpsilon) {
        return topHeight - startHeight > Math.max(1.0E-6D, visualHeightEpsilon);
    }

    static boolean reverseWinding(Vec3 axisX, Vec3 axisZ, Vec3 normal) {
        double crossX = axisX.y * axisZ.z - axisX.z * axisZ.y;
        double crossY = axisX.z * axisZ.x - axisX.x * axisZ.z;
        double crossZ = axisX.x * axisZ.y - axisX.y * axisZ.x;
        return crossX * normal.x + crossY * normal.y + crossZ * normal.z < 0.0D;
    }
}
