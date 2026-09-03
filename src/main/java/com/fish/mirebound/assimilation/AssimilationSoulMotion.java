package com.fish.mirebound.assimilation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure movement integration for the collisionless owner-only soul camera. */
public final class AssimilationSoulMotion {
    private static final double STOP_EPSILON_SQUARED = 1.0E-8D;

    private AssimilationSoulMotion() {
    }

    /** Space ascends and Shift descends without changing the anchored body's pose. */
    public static double verticalInput(boolean jumpDown, boolean shiftDown) {
        return (jumpDown ? 1.0D : 0.0D) - (shiftDown ? 1.0D : 0.0D);
    }

    /** Builds spectator-style input: looking up or down never changes forward altitude. */
    public static Vec3 inputDirection(float yawDegrees, double forward,
            double strafe, double vertical) {
        if (Math.abs(forward) + Math.abs(strafe) + Math.abs(vertical) <= 1.0E-6D) {
            return Vec3.ZERO;
        }
        double yaw = Math.toRadians(yawDegrees);
        Vec3 horizontalForward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 horizontalRight = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        return horizontalForward.scale(forward)
                .add(horizontalRight.scale(strafe))
                .add(0.0D, vertical, 0.0D);
    }

    public static Vec3 emergenceTarget(Vec3 bodyEye, float yawDegrees,
            double backOffset, double upOffset) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3 backward = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        return bodyEye.add(backward.scale(Math.max(0.0D, backOffset)))
                .add(0.0D, upOffset, 0.0D);
    }

    public static double smoothTransition(double progress) {
        double bounded = Mth.clamp(progress, 0.0D, 1.0D);
        return bounded * bounded * (3.0D - bounded * 2.0D);
    }

    public static Vec3 updateVelocity(Vec3 velocity, Vec3 inputDirection,
            double speed, double acceleration, double drag) {
        if (inputDirection.lengthSqr() > STOP_EPSILON_SQUARED) {
            Vec3 target = inputDirection.normalize().scale(Math.max(0.0D, speed));
            return velocity.lerp(target, Mth.clamp(acceleration, 0.0D, 1.0D));
        }
        Vec3 slowed = velocity.scale(Mth.clamp(drag, 0.0D, 0.999D));
        return slowed.lengthSqr() < STOP_EPSILON_SQUARED ? Vec3.ZERO : slowed;
    }

    public static Step advance(Vec3 position, Vec3 velocity, Vec3 center, double radius) {
        return advance(position, velocity, center, radius, 0.0D);
    }

    public static Step advance(Vec3 position, Vec3 velocity, Vec3 center,
            double radius, double boundarySoftness) {
        double boundedRadius = Math.max(0.0D, radius);
        double softDistance = boundedRadius * Mth.clamp(boundarySoftness, 0.0D, 0.75D);
        Vec3 resistedVelocity = new Vec3(
                resistAxis(position.x, velocity.x, center.x, boundedRadius, softDistance),
                resistAxis(position.y, velocity.y, center.y, boundedRadius, softDistance),
                resistAxis(position.z, velocity.z, center.z, boundedRadius, softDistance));
        Vec3 requested = position.add(resistedVelocity);
        double x = Mth.clamp(requested.x, center.x - boundedRadius, center.x + boundedRadius);
        double y = Mth.clamp(requested.y, center.y - boundedRadius, center.y + boundedRadius);
        double z = Mth.clamp(requested.z, center.z - boundedRadius, center.z + boundedRadius);
        Vec3 boundedVelocity = new Vec3(
                x == requested.x ? resistedVelocity.x : 0.0D,
                y == requested.y ? resistedVelocity.y : 0.0D,
                z == requested.z ? resistedVelocity.z : 0.0D);
        return new Step(new Vec3(x, y, z), boundedVelocity);
    }

    public static double distanceFraction(Vec3 position, Vec3 center, double radius) {
        if (radius <= 1.0E-6D) {
            return 1.0D;
        }
        Vec3 offset = position.subtract(center);
        return Mth.clamp(Math.max(Math.max(Math.abs(offset.x), Math.abs(offset.y)),
                Math.abs(offset.z)) / radius, 0.0D, 1.0D);
    }

    public static double distanceEffect(double distanceFraction, double start) {
        double boundedStart = Mth.clamp(start, 0.0D, 0.95D);
        return smoothTransition((distanceFraction - boundedStart) / (1.0D - boundedStart));
    }

    /** Fades in at restoration start and stays opaque through the camera handoff. */
    public static float restoreBlackout(int remainingTicks, int totalTicks, int fadeTicks) {
        int total = Math.max(1, totalTicks);
        int remaining = Mth.clamp(remainingTicks, 0, total);
        int fade = Mth.clamp(fadeTicks, 1, Math.max(1, total / 2));
        double fadeIn = (total - remaining) / (double) fade;
        return (float) smoothTransition(Mth.clamp(fadeIn, 0.0D, 1.0D));
    }

    /** Keeps the return camera settled before revealing first person again. */
    public static float restoreReleaseBlackout(int remainingTicks, int fadeTicks) {
        int fade = Math.max(1, fadeTicks);
        return (float) smoothTransition(Mth.clamp(remainingTicks / (double) fade, 0.0D, 1.0D));
    }

    public static double restoreReturnProgress(double elapsedFraction) {
        return smoothTransition(Mth.clamp((elapsedFraction - 0.18D) / 0.38D, 0.0D, 1.0D));
    }

    private static double resistAxis(double position, double velocity, double center,
            double radius, double softDistance) {
        double offset = position - center;
        if (softDistance <= 1.0E-6D || velocity * offset <= 0.0D) {
            return velocity;
        }
        double edgeAmount = (Math.abs(offset) - (radius - softDistance)) / softDistance;
        if (edgeAmount <= 0.0D) {
            return velocity;
        }
        double retained = 1.0D - smoothTransition(edgeAmount);
        return velocity * Mth.clamp(retained, 0.035D, 1.0D);
    }

    public record Step(Vec3 position, Vec3 velocity) {
    }
}
