package com.fish.mirebound.mud;

import net.minecraft.util.Mth;

final class LivingSlimeSolver {
    private LivingSlimeSolver() {
    }

    static Result solve(LivingSlimePhysicsProfile profile, Input input) {
        double depth = Math.max(0.0D, input.depth());
        double depthLimitScale = Mth.clamp(input.depthLimitScale(), 0.10D, 1.0D);
        double columnLimit = Math.max(
                profile.minColumnDepth * depthLimitScale,
                (input.availableDepth() - profile.columnMargin) * depthLimitScale);
        double elasticDepth = Math.max(0.04D, Math.min(
                columnLimit * profile.elasticDepthColumnFactor,
                input.playerHeight() * profile.elasticDepthHeightFactor));
        double depthProgress = smooth(depth / Math.max(columnLimit, 0.04D));
        double elasticProgress = smooth(depth / elasticDepth);
        double remaining = Math.max(0.0D, columnLimit - depth);

        double walkScale = Mth.lerp(
                depthProgress, profile.walkShallow, profile.walkDeep);
        walkScale = MudEnchantmentEffects.restoreWalkScale(
                walkScale,
                input.walkRestoration());
        double horizontalTug = Mth.lerp(
                depthProgress,
                profile.anchorTugShallow,
                profile.anchorTugDeep);
        double anchorFollow = Mth.lerp(
                depthProgress,
                profile.anchorFollowShallow,
                profile.anchorFollowDeep);
        double deepResponse = Mth.lerp(elasticProgress, 1.0D, 0.62D);
        double verticalTug = profile.verticalTug * deepResponse;

        double inwardSpeed = Math.max(0.0D, -input.motionY());
        double impactProgress = smooth((inwardSpeed - profile.impactThreshold)
                / Math.max(0.02D, profile.maxDownSpeed - profile.impactThreshold));
        double verticalRetention = profile.verticalRetention
                * Mth.lerp(impactProgress, 1.0D, profile.impactRestitution);

        double motionX = input.motionX() * walkScale
                + input.anchorDeltaX() * horizontalTug;
        double motionZ = input.motionZ() * walkScale
                + input.anchorDeltaZ() * horizontalTug;
        double motionY;
        double impactEnergy = Math.max(0.0D, input.impactEnergy());
        boolean impactReleased = false;
        if (input.struggleCarry()) {
            motionY = Math.min(
                    profile.maxUpSpeed,
                    Math.max(0.0D, input.motionY()));
        } else {
            double sink = profile.baseSinkBias
                    + input.horizontalSpeed() * profile.movementSinkScale
                    + (input.crouching() ? profile.crouchSink : 0.0D);
            motionY = input.motionY() * verticalRetention
                    + input.anchorDeltaY() * verticalTug
                    - sink;
            motionY = Mth.clamp(
                    motionY,
                    -profile.maxDownSpeed,
                    profile.maxUpSpeed);
            if (remaining <= 1.0E-5D && motionY < 0.0D) {
                motionY = 0.0D;
            } else if (motionY < 0.0D) {
                motionY = -Math.min(-motionY, remaining);
            }

            if (impactEnergy > 0.0D
                    && motionY > 0.0D
                    && input.anchorDeltaY() > 0.0D) {
                double impactReturn = Math.min(profile.maxUpSpeed, impactEnergy);
                motionY = Math.max(motionY, impactReturn);
                impactEnergy = 0.0D;
                impactReleased = true;
            }
        }

        return new Result(
                motionX,
                motionY,
                motionZ,
                walkScale,
                verticalRetention,
                horizontalTug,
                verticalTug,
                anchorFollow,
                columnLimit,
                elasticDepth,
                remaining,
                depthProgress,
                impactEnergy,
                impactReleased);
    }

    static double captureImpact(LivingSlimePhysicsProfile profile, double motionY) {
        if (motionY >= -profile.impactThreshold) {
            return 0.0D;
        }
        return Math.min(profile.maxUpSpeed, -motionY * profile.impactRestitution);
    }

    private static double smooth(double value) {
        double clamped = Mth.clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    record Input(
            double depth,
            double availableDepth,
            double playerHeight,
            double motionX,
            double motionY,
            double motionZ,
            double horizontalSpeed,
            double anchorDeltaX,
            double anchorDeltaY,
            double anchorDeltaZ,
            double impactEnergy,
            boolean crouching,
            boolean struggleCarry,
            double depthLimitScale,
            double walkRestoration) {
        Input(
                double depth,
                double availableDepth,
                double playerHeight,
                double motionX,
                double motionY,
                double motionZ,
                double horizontalSpeed,
                double anchorDeltaX,
                double anchorDeltaY,
                double anchorDeltaZ,
                double impactEnergy,
                boolean crouching,
                boolean struggleCarry) {
            this(
                    depth,
                    availableDepth,
                    playerHeight,
                    motionX,
                    motionY,
                    motionZ,
                    horizontalSpeed,
                    anchorDeltaX,
                    anchorDeltaY,
                    anchorDeltaZ,
                    impactEnergy,
                    crouching,
                    struggleCarry,
                    1.0D,
                    0.0D);
        }
    }

    record Result(
            double motionX,
            double motionY,
            double motionZ,
            double walkScale,
            double verticalRetention,
            double horizontalTug,
            double verticalTug,
            double anchorFollow,
            double columnLimit,
            double elasticDepth,
            double remainingDepth,
            double depthProgress,
            double impactEnergy,
            boolean impactReleased) {
    }
}
