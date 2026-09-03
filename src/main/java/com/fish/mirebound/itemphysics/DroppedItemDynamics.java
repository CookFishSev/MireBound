package com.fish.mirebound.itemphysics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure, bounded math for dropped-item entry, settling, and drag. */
final class DroppedItemDynamics {
    private static final double FLOOR_CLEARANCE = 1.0D / 64.0D;

    private DroppedItemDynamics() {
    }

    static double maximumDepth(
            DroppedItemPhysicsProfile profile, double availableColumnDepth) {
        double available = availableDepthLimit(availableColumnDepth);
        return Math.min(profile.maximumSinkDepth(), available);
    }

    static double entryDepth(double currentDepth, double inwardSpeed, double maximumDepth,
            DroppedItemPhysicsProfile profile) {
        double impactDepth = Math.max(0.0D, inwardSpeed) * profile.impactPenetrationScale();
        double cappedImpact = Math.min(profile.maximumImpactPenetration(), impactDepth);
        return Math.max(Math.max(0.0D, currentDepth), Math.min(maximumDepth, cappedImpact));
    }

    /** Preserves deep side/underside entry so buoyancy can return it gradually. */
    static double anchoredEntryDepth(double currentDepth, double inwardSpeed,
            double maximumDepth, double availableColumnDepth,
            DroppedItemPhysicsProfile profile) {
        double available = Math.max(
                maximumDepth, availableDepthLimit(availableColumnDepth));
        return Mth.clamp(entryDepth(currentDepth, inwardSpeed, maximumDepth, profile),
                0.0D, available);
    }

    static double entryDepthSpeed(double inwardSpeed,
            DroppedItemPhysicsProfile profile) {
        return Math.max(0.0D, inwardSpeed) * profile.impactVelocityRetention();
    }

    static double settlingDepthSpeed(double currentSpeed, double remainingDepth,
            DroppedItemPhysicsProfile profile) {
        if (remainingDepth <= 1.0E-6D) {
            return 0.0D;
        }
        double terminal = Math.min(profile.settlingSpeed(), remainingDepth);
        double next = Mth.lerp(profile.settlingResponse(), Math.max(0.0D, currentSpeed), terminal);
        return Mth.clamp(next, 0.0D, remainingDepth);
    }

    static double buoyantReturnDepthSpeed(double currentSpeed, double excessDepth,
            DroppedItemPhysicsProfile profile) {
        if (excessDepth <= 1.0E-6D) {
            return 0.0D;
        }
        double easing = Mth.clamp(
                excessDepth / Math.max(1.0E-4D, profile.buoyantReturnEasingDistance()),
                0.0D, 1.0D);
        double target = -profile.buoyantReturnSpeed() * easing;
        double next = Mth.lerp(profile.buoyantReturnResponse(), Math.min(0.0D, currentSpeed), target);
        double maximumStep = excessDepth * profile.buoyantReturnMaximumStepFraction();
        return Mth.clamp(next, -maximumStep, 0.0D);
    }

    static double depth(Vec3 position, Vec3 surfaceNormal, double surfaceCoordinate) {
        return surfaceCoordinate - position.dot(surfaceNormal);
    }

    static double depthFromSurfacePlane(Vec3 position, Vec3 surfaceNormal,
            double surfaceCoordinate, Vec3 depthAxis) {
        double alignment = Math.max(1.0E-6D, surfaceNormal.dot(depthAxis));
        return depth(position, surfaceNormal, surfaceCoordinate) / alignment;
    }

    static double availableDepthFromSurfacePlane(double surfaceCoordinate,
            double bottomCoordinate, Vec3 surfaceNormal, Vec3 depthAxis) {
        double alignment = Math.max(1.0E-6D, surfaceNormal.dot(depthAxis));
        return Math.max(0.0D, surfaceCoordinate - bottomCoordinate) / alignment;
    }

    static Vec3 positionAtDepth(Vec3 position, Vec3 surfaceNormal,
            double surfaceCoordinate, double depth) {
        double desiredCoordinate = surfaceCoordinate - depth;
        return position.add(surfaceNormal.scale(desiredCoordinate - position.dot(surfaceNormal)));
    }

    static Vec3 positionAtDepthAlongAxis(Vec3 position, Vec3 surfaceNormal,
            double surfaceCoordinate, Vec3 depthAxis, double depth) {
        return position.add(depthAxis.scale(
                depthFromSurfacePlane(position, surfaceNormal, surfaceCoordinate, depthAxis)
                        - depth));
    }

    static double depthSpeed(Vec3 motion, Vec3 surfaceNormal) {
        return -motion.dot(surfaceNormal);
    }

    static double horizontalRetention(double depth, double itemHeight,
            DroppedItemPhysicsProfile profile) {
        double immersion = Mth.clamp(depth / Math.max(0.05D, itemHeight), 0.0D, 1.0D);
        double smooth = immersion * immersion * (3.0D - 2.0D * immersion);
        return Mth.lerp(
                smooth,
                profile.surfaceHorizontalRetention(),
                profile.submergedHorizontalRetention());
    }

    private static double availableDepthLimit(double availableColumnDepth) {
        return Math.max(0.0D, availableColumnDepth - FLOOR_CLEARANCE);
    }
}
