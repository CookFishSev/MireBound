package com.fish.mirebound.water;

/** Immutable hot-path settings for continuous pressure-water simulation. */
public record WaterGunProfile(
        int capacity,
        int waterPerTick,
        int inputTimeoutTicks,
        int syncIntervalTicks,
        int washIntervalTicks,
        double pressure,
        double gravity,
        double maximumRange,
        double segmentLength,
        double streamWidth,
        double firingMovementScale,
        double recoilDegrees,
        float baseWashRadius,
        float distanceSpread,
        float maximumWashRadius,
        float washAmountPerTick,
        double renderDistance,
        int sableMaximumBlockSamples) {
    public static final WaterGunProfile DEFAULT = new WaterGunProfile(
            1000, 3, 16, 2, 2,
            2.20D, 0.06D, 18.0D, 0.62D, 0.045D, 0.82D, 3.5D,
            0.26F, 0.048F, 1.12F, 0.075F,
            64.0D, 256);

    public float washRadius(double distance) {
        return Math.min(maximumWashRadius, baseWashRadius + (float) distance * distanceSpread);
    }

    public WaterGunProfile withVisualSettings(
            int syncedCapacity,
            int syncedWaterPerTick,
            double syncedPressure,
            double syncedGravity,
            double syncedMaximumRange,
            double syncedSegmentLength,
            double syncedStreamWidth,
            double syncedFiringMovementScale,
            double syncedRecoilDegrees,
            float syncedBaseWashRadius,
            float syncedDistanceSpread,
            float syncedMaximumWashRadius,
            int syncedSableMaximumBlockSamples) {
        return new WaterGunProfile(
                syncedCapacity, syncedWaterPerTick,
                inputTimeoutTicks, syncIntervalTicks, washIntervalTicks,
                syncedPressure, syncedGravity, syncedMaximumRange, syncedSegmentLength, syncedStreamWidth,
                syncedFiringMovementScale, syncedRecoilDegrees,
                syncedBaseWashRadius, syncedDistanceSpread, syncedMaximumWashRadius, washAmountPerTick,
                renderDistance, syncedSableMaximumBlockSamples);
    }
}
