package com.fish.mirebound.splash;

import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Continuous droplet collision against a linearly translated entity box. */
public final class MudSplashCollision {
    private static final double EPSILON = 1.0E-10D;

    private MudSplashCollision() {
    }

    public static SweptHit sweepPlayer(AABB currentBounds, Vec3 playerMotion,
            Vec3 from, Vec3 to) {
        return sweepEntity(currentBounds, playerMotion, from, to);
    }

    public static SweptHit sweepEntity(AABB currentBounds, Vec3 entityMotion,
            Vec3 from, Vec3 to) {
        Vec3 motion = finite(entityMotion) ? entityMotion : Vec3.ZERO;
        AABB previousBounds = currentBounds.move(-motion.x, -motion.y, -motion.z);
        Vec3 relativeTo = to.subtract(motion);

        double time;
        if (previousBounds.contains(from)) {
            time = 0.0D;
        } else {
            Optional<Vec3> relativeHit = previousBounds.clip(from, relativeTo);
            if (relativeHit.isEmpty()) {
                return null;
            }
            Vec3 relativeDelta = relativeTo.subtract(from);
            double lengthSqr = relativeDelta.lengthSqr();
            time = lengthSqr <= EPSILON
                    ? 0.0D
                    : clamp(relativeHit.get().subtract(from).dot(relativeDelta) / lengthSqr, 0.0D, 1.0D);
        }

        Vec3 trajectoryPoint = from.lerp(to, time);
        AABB boundsAtImpact = previousBounds.move(
                motion.x * time, motion.y * time, motion.z * time);
        Vec3 surfacePoint = new Vec3(
                clamp(trajectoryPoint.x, boundsAtImpact.minX, boundsAtImpact.maxX),
                clamp(trajectoryPoint.y, boundsAtImpact.minY, boundsAtImpact.maxY),
                clamp(trajectoryPoint.z, boundsAtImpact.minZ, boundsAtImpact.maxZ));
        return new SweptHit(time, trajectoryPoint, surfacePoint);
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record SweptHit(double time, Vec3 trajectoryPoint, Vec3 surfacePoint) {
    }
}
