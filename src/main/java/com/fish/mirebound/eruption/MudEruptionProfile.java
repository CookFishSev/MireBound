package com.fish.mirebound.eruption;

import com.fish.mirebound.mud.MudPhysicsParameter;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** Immutable, page-aligned vent settings captured before entering the server hot path. */
public record MudEruptionProfile(
        SpawnSettings spawning,
        ContinuousSettings continuous,
        SurgeSettings surges) {
    public static MudEruptionProfile fromValues(double[] values) {
        double minimumRadius = value(values, MudPhysicsParameter.ERUPTION_MIN_RADIUS_PIXELS);
        double maximumRadius = Math.max(
                minimumRadius, value(values, MudPhysicsParameter.ERUPTION_MAX_RADIUS_PIXELS));
        int minimumLifetime = rounded(values, MudPhysicsParameter.ERUPTION_MIN_LIFETIME_TICKS);
        int maximumLifetime = Math.max(
                minimumLifetime, rounded(values, MudPhysicsParameter.ERUPTION_MAX_LIFETIME_TICKS));
        int minimumBurstInterval = rounded(
                values, MudPhysicsParameter.ERUPTION_MIN_BURST_INTERVAL_TICKS);
        int maximumBurstInterval = Math.max(minimumBurstInterval,
                rounded(values, MudPhysicsParameter.ERUPTION_MAX_BURST_INTERVAL_TICKS));
        double minimumHeight = value(values, MudPhysicsParameter.ERUPTION_MIN_HEIGHT);
        double maximumHeight = Math.max(
                minimumHeight, value(values, MudPhysicsParameter.ERUPTION_MAX_HEIGHT));
        int minimumDroplets = rounded(values, MudPhysicsParameter.ERUPTION_MIN_DROPLETS);
        int maximumDroplets = Math.max(
                minimumDroplets, rounded(values, MudPhysicsParameter.ERUPTION_MAX_DROPLETS));
        int minimumFlowDroplets = rounded(
                values, MudPhysicsParameter.ERUPTION_FLOW_MIN_DROPLETS);
        int maximumFlowDroplets = Math.max(minimumFlowDroplets,
                rounded(values, MudPhysicsParameter.ERUPTION_FLOW_MAX_DROPLETS));
        double minimumFlowHeight = value(values, MudPhysicsParameter.ERUPTION_FLOW_MIN_HEIGHT);
        double maximumFlowHeight = Math.max(minimumFlowHeight,
                value(values, MudPhysicsParameter.ERUPTION_FLOW_MAX_HEIGHT));

        SpawnSettings spawning = new SpawnSettings(
                value(values, MudPhysicsParameter.ERUPTION_ENABLED) >= 0.5D,
                value(values, MudPhysicsParameter.ERUPTION_SPAWN_CHANCE),
                rounded(values, MudPhysicsParameter.ERUPTION_SPAWN_INTERVAL_TICKS),
                rounded(values, MudPhysicsParameter.ERUPTION_SPAWN_ATTEMPTS),
                value(values, MudPhysicsParameter.ERUPTION_SEARCH_RADIUS),
                minimumRadius,
                maximumRadius,
                minimumLifetime,
                maximumLifetime,
                value(values, MudPhysicsParameter.ERUPTION_MIN_SPACING),
                faceMask(values));
        ContinuousSettings continuous = new ContinuousSettings(
                value(values, MudPhysicsParameter.ERUPTION_CONTINUOUS_ENABLED) >= 0.5D,
                rounded(values, MudPhysicsParameter.ERUPTION_FLOW_INTERVAL_TICKS),
                minimumFlowDroplets,
                maximumFlowDroplets,
                value(values, MudPhysicsParameter.ERUPTION_FLOW_HEIGHT_SCALE),
                minimumFlowHeight,
                maximumFlowHeight,
                value(values, MudPhysicsParameter.ERUPTION_FLOW_VOLUME_SCALE),
                value(values, MudPhysicsParameter.ERUPTION_FLOW_JET_COHESION),
                value(values, MudPhysicsParameter.ERUPTION_FLOW_TOP_SPREAD_SCALE),
                value(values, MudPhysicsParameter.ERUPTION_FLOW_SPREAD_TRIGGER_RATIO),
                rounded(values, MudPhysicsParameter.ERUPTION_FLOW_VARIATION_INTERVAL_TICKS),
                rounded(values, MudPhysicsParameter.ERUPTION_FLOW_SPREAD_DURATION_TICKS),
                rounded(values, MudPhysicsParameter.ERUPTION_FLOW_PARTICLE_LIFETIME_TICKS));
        SurgeSettings surges = new SurgeSettings(
                value(values, MudPhysicsParameter.ERUPTION_SURGES_ENABLED) >= 0.5D,
                minimumBurstInterval,
                maximumBurstInterval,
                minimumHeight,
                maximumHeight,
                minimumDroplets,
                maximumDroplets,
                value(values, MudPhysicsParameter.ERUPTION_POWER_SCALE),
                value(values, MudPhysicsParameter.ERUPTION_VOLUME_SCALE),
                value(values, MudPhysicsParameter.ERUPTION_JET_COHESION),
                value(values, MudPhysicsParameter.ERUPTION_TOP_SPREAD_SCALE),
                value(values, MudPhysicsParameter.ERUPTION_SPREAD_TRIGGER_RATIO),
                rounded(values, MudPhysicsParameter.ERUPTION_SURGE_DURATION_TICKS),
                rounded(values, MudPhysicsParameter.ERUPTION_SPREAD_DURATION_TICKS));
        return new MudEruptionProfile(spawning, continuous, surges);
    }

    private static int rounded(double[] values, MudPhysicsParameter parameter) {
        return Mth.floor(value(values, parameter) + 0.5D);
    }

    private static double value(double[] values, MudPhysicsParameter parameter) {
        int index = parameter.ordinal();
        return parameter.sanitize(values != null && index < values.length
                ? values[index] : parameter.minimum());
    }

    private static int faceMask(double[] values) {
        int mask = 0;
        mask = enabledFace(values, MudPhysicsParameter.ERUPTION_FACE_DOWN_ENABLED,
                Direction.DOWN, mask);
        mask = enabledFace(values, MudPhysicsParameter.ERUPTION_FACE_UP_ENABLED,
                Direction.UP, mask);
        mask = enabledFace(values, MudPhysicsParameter.ERUPTION_FACE_NORTH_ENABLED,
                Direction.NORTH, mask);
        mask = enabledFace(values, MudPhysicsParameter.ERUPTION_FACE_SOUTH_ENABLED,
                Direction.SOUTH, mask);
        mask = enabledFace(values, MudPhysicsParameter.ERUPTION_FACE_WEST_ENABLED,
                Direction.WEST, mask);
        return enabledFace(values, MudPhysicsParameter.ERUPTION_FACE_EAST_ENABLED,
                Direction.EAST, mask);
    }

    private static int enabledFace(double[] values, MudPhysicsParameter parameter,
            Direction face, int mask) {
        return value(values, parameter) >= 0.5D
                ? mask | 1 << face.get3DDataValue() : mask;
    }

    public record SpawnSettings(
            boolean enabled,
            double spawnChance,
            int spawnIntervalTicks,
            int spawnAttempts,
            double searchRadius,
            double minimumRadiusPixels,
            double maximumRadiusPixels,
            int minimumLifetimeTicks,
            int maximumLifetimeTicks,
            double minimumSpacing,
            int faceMask) {
        public boolean allows(Direction face) {
            return face != null && (faceMask & 1 << face.get3DDataValue()) != 0;
        }
    }

    public record ContinuousSettings(
            boolean enabled,
            int intervalTicks,
            int minimumDroplets,
            int maximumDroplets,
            double heightScale,
            double minimumHeight,
            double maximumHeight,
            double volumeScale,
            double jetCohesion,
            double coneScale,
            double breakupTriggerRatio,
            int variationIntervalTicks,
            int breakupDurationTicks,
            int particleLifetimeTicks) {
    }

    public record SurgeSettings(
            boolean enabled,
            int minimumIntervalTicks,
            int maximumIntervalTicks,
            double minimumHeight,
            double maximumHeight,
            int minimumDroplets,
            int maximumDroplets,
            double powerScale,
            double volumeScale,
            double jetCohesion,
            double coneScale,
            double breakupTriggerRatio,
            int durationTicks,
            int breakupDurationTicks) {
    }
}
