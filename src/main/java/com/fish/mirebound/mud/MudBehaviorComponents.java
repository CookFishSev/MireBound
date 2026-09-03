package com.fish.mirebound.mud;

import net.minecraft.util.Mth;

/**
 * Continuous behavior weights layered over the common viscoplastic solver.
 * Components intentionally modify existing forces and resistance instead of
 * introducing a second sinking controller.
 */
record MudBehaviorComponents(
        double granularCollapse,
        double cohesiveSuction,
        double adhesiveGrip) {
    static final MudBehaviorComponents NONE = new MudBehaviorComponents(0.0D, 0.0D, 0.0D);

    MudBehaviorComponents {
        granularCollapse = sanitize(granularCollapse);
        cohesiveSuction = sanitize(cohesiveSuction);
        adhesiveGrip = sanitize(adhesiveGrip);
    }

    double additionalSinkDrive(
            double horizontalSpeed,
            double agitation,
            boolean holdingStruggle,
            double depthProgress,
            double maximumSinkSpeed) {
        return additionalSinkDrive(
                horizontalSpeed,
                agitation,
                holdingStruggle,
                depthProgress,
                maximumSinkSpeed,
                1.0D);
    }

    double additionalSinkDrive(
            double horizontalSpeed,
            double agitation,
            boolean holdingStruggle,
            double depthProgress,
            double maximumSinkSpeed,
            double struggleSinkFactor) {
        if (granularCollapse <= 0.0D) {
            return 0.0D;
        }
        double disturbance = Mth.clamp(
                horizontalSpeed * 4.0D
                        + agitation * 0.85D
                        + (holdingStruggle ? 0.22D * Mth.clamp(struggleSinkFactor, 0.0D, 1.0D) : 0.0D),
                0.0D,
                1.0D);
        double shallowBias = 1.0D - smooth(depthProgress) * 0.35D;
        return maximumSinkSpeed
                * granularCollapse
                * (0.018D + disturbance * 0.22D)
                * shallowBias;
    }

    double yieldMultiplier(double depthProgress, double disturbance) {
        double depth = smooth(depthProgress);
        double activity = Mth.clamp(disturbance, 0.0D, 1.0D);
        double staticBinding = 1.0D
                + cohesiveSuction * (0.16D + depth * 0.42D)
                + adhesiveGrip * (0.08D + depth * 0.26D);
        double granularRelease = 1.0D
                - Mth.clamp(granularCollapse * activity * 0.32D, 0.0D, 0.62D);
        return Math.max(0.25D, staticBinding * granularRelease);
    }

    double viscosityMultiplier(double depthProgress, double disturbance) {
        double depth = smooth(depthProgress);
        double activity = Mth.clamp(disturbance, 0.0D, 1.0D);
        double binding = 1.0D
                + cohesiveSuction * (0.10D + depth * 0.44D)
                + adhesiveGrip * (0.32D + depth * 0.82D);
        double granularFlow = 1.0D
                - Mth.clamp(granularCollapse * (0.03D + activity * 0.10D), 0.0D, 0.28D);
        return Math.max(0.35D, binding * granularFlow);
    }

    double walkMultiplier(double immersionFraction) {
        double immersion = smooth(immersionFraction);
        double resistance = granularCollapse * 0.08D
                + cohesiveSuction * 0.14D
                + adhesiveGrip * 0.34D;
        return Mth.clamp(1.0D - immersion * resistance, 0.20D, 1.0D);
    }

    double verticalMultiplier(double depthProgress) {
        double depth = smooth(depthProgress);
        double resistance = cohesiveSuction * 0.12D
                + adhesiveGrip * 0.38D;
        return Mth.clamp(1.0D - depth * resistance, 0.18D, 1.0D);
    }

    double struggleMultiplier(double depthProgress) {
        double depth = smooth(depthProgress);
        double multiplier = 1.0D
                + granularCollapse * (1.0D - depth) * 0.06D
                - cohesiveSuction * depth * 0.16D
                - adhesiveGrip * (0.12D + depth * 0.28D);
        return Mth.clamp(multiplier, 0.28D, 1.15D);
    }

    static MudBehaviorComponents blend(
            MudBehaviorComponents from,
            MudBehaviorComponents to,
            double amount) {
        double t = smooth(amount);
        return new MudBehaviorComponents(
                Mth.lerp(t, from.granularCollapse, to.granularCollapse),
                Mth.lerp(t, from.cohesiveSuction, to.cohesiveSuction),
                Mth.lerp(t, from.adhesiveGrip, to.adhesiveGrip));
    }

    private static double sanitize(double value) {
        return Double.isFinite(value) ? Mth.clamp(value, 0.0D, 2.0D) : 0.0D;
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }
}
