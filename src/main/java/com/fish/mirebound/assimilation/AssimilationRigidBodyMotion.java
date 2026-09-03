package com.fish.mirebound.assimilation;

import net.minecraft.world.phys.Vec3;

/** Pure integration helpers for the sealed-player single rigid body. */
public final class AssimilationRigidBodyMotion {
    private static final double COLLISION_EPSILON = 1.0E-5D;
    private static final double MINIMUM_BOUNCE = 0.025D;

    private AssimilationRigidBodyMotion() {
    }

    public static Vec3 integrate(Vec3 velocity, float gravity, float airDrag,
            float maximumSpeed) {
        Vec3 source = finiteOrZero(velocity);
        Vec3 next = new Vec3(
                source.x * airDrag,
                (source.y - gravity) * airDrag,
                source.z * airDrag);
        double maximum = Math.max(0.01D, maximumSpeed);
        double lengthSqr = next.lengthSqr();
        return lengthSqr > maximum * maximum
                ? next.scale(maximum / Math.sqrt(lengthSqr)) : next;
    }

    public static CollisionResult resolveCollision(Vec3 requested, Vec3 actual,
            float restitution, float groundFriction) {
        Vec3 wanted = finiteOrZero(requested);
        Vec3 moved = finiteOrZero(actual);
        boolean blockedX = blocked(wanted.x, moved.x);
        boolean blockedY = blocked(wanted.y, moved.y);
        boolean blockedZ = blocked(wanted.z, moved.z);
        double x = blockedX ? -wanted.x * restitution : wanted.x;
        double y = blockedY ? -wanted.y * restitution : wanted.y;
        double z = blockedZ ? -wanted.z * restitution : wanted.z;
        if (blockedY && wanted.y < 0.0D) {
            if (Math.abs(y) < MINIMUM_BOUNCE) {
                y = 0.0D;
            }
            x *= groundFriction;
            z *= groundFriction;
        }
        if (Math.abs(x) < 1.0E-4D) {
            x = 0.0D;
        }
        if (Math.abs(z) < 1.0E-4D) {
            z = 0.0D;
        }
        return new CollisionResult(new Vec3(x, y, z), blockedX, blockedY, blockedZ);
    }

    public static Tilt updateTilt(float pitch, float roll, Vec3 velocity, float frozenYaw,
            float maximumSpeed, float maximumTilt, float response, boolean grounded) {
        Vec3 motion = finiteOrZero(velocity);
        double yaw = Math.toRadians(frozenYaw);
        double forward = -motion.x * Math.sin(yaw) + motion.z * Math.cos(yaw);
        double sideways = motion.x * Math.cos(yaw) + motion.z * Math.sin(yaw);
        double normalization = Math.max(0.05D, maximumSpeed * 0.45D);
        float targetPitch = clamp((float) (-forward / normalization * maximumTilt),
                -maximumTilt, maximumTilt);
        float targetRoll = clamp((float) (sideways / normalization * maximumTilt),
                -maximumTilt, maximumTilt);
        if (grounded && motion.horizontalDistanceSqr() < 1.0E-4D) {
            targetPitch = 0.0F;
            targetRoll = 0.0F;
        }
        return new Tilt(
                approach(pitch, targetPitch, response),
                approach(roll, targetRoll, response));
    }

    private static boolean blocked(double requested, double actual) {
        return Math.abs(requested) > COLLISION_EPSILON
                && Math.abs(requested - actual) > COLLISION_EPSILON;
    }

    private static Vec3 finiteOrZero(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z) ? value : Vec3.ZERO;
    }

    private static float approach(float current, float target, float response) {
        return current + (target - current) * clamp(response, 0.0F, 1.0F);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record CollisionResult(
            Vec3 velocity, boolean blockedX, boolean blockedY, boolean blockedZ) {
    }

    public record Tilt(float pitch, float roll) {
    }

}
