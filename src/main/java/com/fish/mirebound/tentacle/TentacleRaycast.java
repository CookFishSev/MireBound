package com.fish.mirebound.tentacle;

import java.util.List;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Bounded tentacle geometry tests shared by wand targeting and server validation. */
public final class TentacleRaycast {
    private static final double EPSILON = 1.0E-10D;

    private TentacleRaycast() {
    }

    public static SphereHit raycastSphere(Vec3 origin, Vec3 direction,
            double maximumDistance, Vec3 center, double radius) {
        if (origin == null || direction == null || center == null
                || !Double.isFinite(maximumDistance) || maximumDistance <= 0.0D
                || !Double.isFinite(radius) || radius < 0.0D
                || direction.lengthSqr() <= EPSILON) {
            return null;
        }
        Vec3 ray = direction.normalize();
        Vec3 toCenter = center.subtract(origin);
        double projection = toCenter.dot(ray);
        double perpendicularSquared = toCenter.lengthSqr() - projection * projection;
        double radiusSquared = radius * radius;
        if (projection < -radius || perpendicularSquared > radiusSquared) {
            return null;
        }
        double entry = projection - Math.sqrt(Math.max(0.0D,
                radiusSquared - perpendicularSquared));
        double distance = Math.max(0.0D, entry);
        return distance <= maximumDistance
                ? new SphereHit(distance, origin.add(ray.scale(distance))) : null;
    }

    public static boolean containsPoint(List<Vec3> points, Vec3 point,
            DoubleUnaryOperator radiusAt, double padding) {
        if (points == null || points.isEmpty() || point == null || radiusAt == null) {
            return false;
        }
        if (points.size() == 1) {
            double radius = Math.max(0.0D, radiusAt.applyAsDouble(0.0D))
                    + Math.max(0.0D, padding);
            return points.getFirst().distanceToSqr(point) <= radius * radius;
        }
        for (int segment = 0; segment < points.size() - 1; segment++) {
            Vec3 from = points.get(segment);
            Vec3 offset = points.get(segment + 1).subtract(from);
            double lengthSquared = offset.lengthSqr();
            double amount = lengthSquared <= EPSILON ? 0.0D
                    : Mth.clamp(point.subtract(from).dot(offset) / lengthSquared, 0.0D, 1.0D);
            double fraction = (segment + amount) / (points.size() - 1.0D);
            double radius = Math.max(0.0D, radiusAt.applyAsDouble(fraction))
                    + Math.max(0.0D, padding);
            if (from.add(offset.scale(amount)).distanceToSqr(point) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    public record SphereHit(double rayDistance, Vec3 surfacePosition) {
    }
}
