package com.fish.mirebound.client;

import net.minecraft.util.Mth;

/** Distance-aware retention and rendering thresholds for 1/16 mud-surface cells. */
final class MudSurfaceCellBudget {
    private static final double PIXEL = 1.0D / 16.0D;
    static final double NEAR_VISUAL_HEIGHT_EPSILON = PIXEL * 0.16D;
    private static final double FAR_VISUAL_HEIGHT_EPSILON = PIXEL * 0.55D;
    private static final double NEAR_PROTECTION_DISTANCE = 8.0D;
    private static final double NEAR_PROTECTION_DISTANCE_SQUARED =
            NEAR_PROTECTION_DISTANCE * NEAR_PROTECTION_DISTANCE;
    private static final double FULL_RATE_DISTANCE_SQUARED = 12.0D * 12.0D;
    private static final double HALF_RATE_DISTANCE_SQUARED = 24.0D * 24.0D;
    private static final int GLOBAL_HOLE_BUDGET = 2;
    private static final int GLOBAL_RENDER_HOLE_BUDGET = 2;
    private static final int MAX_RENDER_CELLS_PER_HOLE = 6144;

    private MudSurfaceCellBudget() {
    }

    static double visualHeightEpsilon(
            double distanceSquared, double renderDistanceSquared) {
        if (!Double.isFinite(distanceSquared)
                || distanceSquared <= NEAR_PROTECTION_DISTANCE_SQUARED
                || renderDistanceSquared <= NEAR_PROTECTION_DISTANCE_SQUARED) {
            return NEAR_VISUAL_HEIGHT_EPSILON;
        }
        double amount = Mth.clamp(
                (distanceSquared - NEAR_PROTECTION_DISTANCE_SQUARED)
                        / (renderDistanceSquared - NEAR_PROTECTION_DISTANCE_SQUARED),
                0.0D, 1.0D);
        amount = amount * amount * (3.0D - 2.0D * amount);
        return Mth.lerp(amount,
                NEAR_VISUAL_HEIGHT_EPSILON, FAR_VISUAL_HEIGHT_EPSILON);
    }

    static int globalSoftLimit(int cellsPerHole, int maximumHoles) {
        int holes = Mth.clamp(maximumHoles, 1, GLOBAL_HOLE_BUDGET);
        long limit = (long) Math.max(1, cellsPerHole) * holes;
        return (int) Math.min(Integer.MAX_VALUE, limit);
    }

    static int globalHardLimit(int softLimit, int cellsPerHole) {
        long reserve = Math.max(256L, Math.max(1, cellsPerHole) / 2L);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, softLimit) + reserve);
    }

    static int globalRenderLimit(int cellsPerHole, int maximumHoles) {
        int holes = Mth.clamp(maximumHoles, 1, GLOBAL_RENDER_HOLE_BUDGET);
        long limit = (long) renderCellLimitPerHole(cellsPerHole) * holes;
        return (int) Math.min(Integer.MAX_VALUE, limit);
    }

    static int renderCellLimitPerHole(int cellsPerHole) {
        return Math.min(Math.max(1, cellsPerHole), MAX_RENDER_CELLS_PER_HOLE);
    }

    static boolean prioritizeNear(double distanceSquared) {
        return Double.isFinite(distanceSquared)
                && distanceSquared <= NEAR_PROTECTION_DISTANCE_SQUARED;
    }

    static boolean canAllocateSurfaceCell(
            boolean localPlayer, int holeCells, int cellsPerHole,
            int retainedCells, int softLimit, int hardLimit) {
        return holeCells < Math.max(1, cellsPerHole)
                && retainedCells < Math.max(1, hardLimit)
                && (localPlayer || retainedCells < Math.max(1, softLimit));
    }

    static int updateIntervalTicks(boolean localPlayer, double distanceSquared) {
        if (localPlayer || !Double.isFinite(distanceSquared)
                || distanceSquared <= FULL_RATE_DISTANCE_SQUARED) {
            return 1;
        }
        return distanceSquared <= HALF_RATE_DISTANCE_SQUARED ? 2 : 4;
    }

    static boolean scheduledUpdate(long gameTime, int identity, int intervalTicks) {
        int interval = Mth.clamp(intervalTicks, 1, 4);
        return interval == 1 || Math.floorMod(gameTime + identity, interval) == 0L;
    }
}
