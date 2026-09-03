package com.fish.mirebound.splash;

import net.minecraft.util.Mth;

/** Ballistic cone launch and client-only visual breakup rules for mud fountains. */
public final class MudFountainMotion {
    private MudFountainMotion() {
    }

    public static boolean shouldBreakUp(
            boolean pending, double verticalVelocity, double triggerVelocity) {
        return pending && verticalVelocity <= triggerVelocity;
    }

    public static double radialSpeed(double launchSpeed, double cohesion,
            double coneScale, boolean core, double variation) {
        double clampedCohesion = Mth.clamp(cohesion, 0.0D, 1.0D);
        double base = core
                ? Mth.lerp(clampedCohesion, 0.055D, 0.018D)
                : Mth.lerp(clampedCohesion, 0.220D, 0.100D);
        double variationScale = core
                ? Mth.lerp(Mth.clamp(variation, 0.0D, 1.0D), 0.45D, 1.20D)
                : Mth.lerp(Mth.clamp(variation, 0.0D, 1.0D), 0.65D, 1.40D);
        return Math.max(0.0D, launchSpeed)
                * Mth.clamp(coneScale, 0.10D, 2.0D)
                * base * variationScale;
    }

    public static double upwardSpeed(
            double launchSpeed, boolean core, double variation) {
        double factor = core
                ? Mth.lerp(Mth.clamp(variation, 0.0D, 1.0D), 1.02D, 1.12D)
                : Mth.lerp(Mth.clamp(variation, 0.0D, 1.0D), 0.94D, 1.12D);
        return Math.max(0.0D, launchSpeed) * factor;
    }
}
