package com.fish.mirebound.tentacle;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collision-clips the next held-player movement against world and Sable geometry. */
final class TentacleHeldPlayerCollision {
    private static final double EPSILON = 1.0E-10D;
    private static final double SURFACE_SEPARATION = 0.003D;

    private TentacleHeldPlayerCollision() {
    }

    static double captureReach(double height, double collisionRadius,
            double maximumSpeed, double sizeScale, int cacheTicks) {
        double clampedScale = Math.max(0.25D, Math.min(8.0D, sizeScale));
        double forceScale = Math.pow(clampedScale, 0.55D);
        double maximumTravel = maximumSpeed * Math.sqrt(forceScale)
                * Math.max(1, cacheTicks);
        return height * 1.05D + collisionRadius + maximumTravel;
    }

    static Vec3 constrainVelocity(AABB bounds, Vec3 desiredVelocity,
            TentacleCollisionSpace collision, double minimumRadius) {
        if (collision == null || desiredVelocity == null
                || desiredVelocity.lengthSqr() <= EPSILON
                || !finite(desiredVelocity)) {
            return desiredVelocity == null ? Vec3.ZERO : desiredVelocity;
        }

        double halfWidth = Math.max(bounds.getXsize(), bounds.getZsize()) * 0.5D;
        // The diagonal radius encloses the complete vanilla rectangular footprint,
        // including its corners against a rotated Sable wall.
        double radius = Math.max(minimumRadius, Math.sqrt(2.0D) * halfWidth);
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        double middleY = (bounds.minY + bounds.maxY) * 0.5D;
        double bottomY = bounds.minY + radius + SURFACE_SEPARATION;
        double topY = bounds.maxY - radius - SURFACE_SEPARATION;

        Vec3 constrained = desiredVelocity;
        if (bottomY >= topY) {
            return constrainSample(
                    new Vec3(centerX, middleY, centerZ), constrained, collision, radius);
        }
        constrained = constrainSample(
                new Vec3(centerX, bottomY, centerZ), constrained, collision, radius);
        constrained = constrainSample(
                new Vec3(centerX, middleY, centerZ), constrained, collision, radius);
        return constrainSample(
                new Vec3(centerX, topY, centerZ), constrained, collision, radius);
    }

    private static Vec3 constrainSample(Vec3 sample, Vec3 desiredVelocity,
            TentacleCollisionSpace collision, double radius) {
        Vec3 resolved = collision.move(sample, sample.add(desiredVelocity), radius);
        Vec3 candidate = resolved.subtract(sample);
        return finite(candidate) ? candidate : Vec3.ZERO;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
