package com.fish.mirebound.mud.behavior;

/** Adds a bounded downward pulse without changing a medium's depth limit. */
public final class DisturbanceSinkTemplate {
    private DisturbanceSinkTemplate() {
    }

    public static double apply(double baselineMotionY, double remainingDepth,
            double actionStrength, double sinkBoost) {
        double disturbed = Math.min(baselineMotionY, -Math.max(0.0D, sinkBoost) * actionStrength);
        return Math.max(disturbed, -Math.max(0.0D, remainingDepth));
    }
}
