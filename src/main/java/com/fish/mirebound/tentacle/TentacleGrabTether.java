package com.fish.mirebound.tentacle;

import net.minecraft.world.phys.Vec3;

/**
 * Keeps the driven tentacle tip close enough to its actual ragdoll grip that
 * both can cross obstacle boundaries together instead of taking opposite sides
 * of a wall.
 */
final class TentacleGrabTether {
    private static final double EPSILON = 1.0E-10D;

    private TentacleGrabTether() {
    }

    static double maximumLead(double playerWidth, double tipRadius,
            double ragdollRadius, double maximumPlayerSpeed, double sizeScale) {
        double clampedScale = Math.max(0.25D, Math.min(8.0D, sizeScale));
        double forceScale = Math.pow(clampedScale, 0.55D);
        double followDistance = maximumPlayerSpeed * Math.sqrt(forceScale);
        double bodyAllowance = playerWidth * 0.35D
                + tipRadius + ragdollRadius;
        return Math.max(bodyAllowance, followDistance);
    }

    static Result constrain(Vec3 currentGrip, Vec3 desiredGoal,
            TentacleCollisionSpace collision, double maximumLead,
            double tipCollisionRadius) {
        Vec3 offset = desiredGoal.subtract(currentGrip);
        double requestedLead = offset.length();
        double lead = Math.max(0.0D, maximumLead);
        Vec3 bounded = requestedLead > lead && requestedLead > EPSILON
                ? currentGrip.add(offset.scale(lead / requestedLead))
                : desiredGoal;
        Vec3 safe = collision == null
                ? bounded
                : collision.move(
                        currentGrip, bounded, Math.max(0.0D, tipCollisionRadius));
        return new Result(
                safe, requestedLead,
                desiredGoal.distanceTo(bounded),
                bounded.distanceTo(safe));
    }

    static boolean contactClear(Vec3 tentaclePoint, Vec3 entityPoint,
            TentacleCollisionSpace collision, double clearance) {
        return collision != null && collision.clear(
                tentaclePoint, entityPoint, Math.max(0.0D, clearance));
    }

    record Result(Vec3 goal, double requestedLead,
            double leadCorrection, double collisionCorrection) {
        double correction() {
            return leadCorrection + collisionCorrection;
        }
    }
}
