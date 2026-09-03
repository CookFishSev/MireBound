package com.fish.mirebound.rope;

import net.minecraft.world.phys.Vec3;

/** Shared ray hit test for a rope centerline and its selectable radius. */
public final class RopeHitGeometry {
    public static final double SELECTION_RADIUS = 2.125D / 16.0D + 0.035D;
    private static final double EPSILON = 1.0E-10D;

    private RopeHitGeometry() {
    }

    public static Vec3 closestPointOnSegment(Vec3 point, Vec3 start, Vec3 end) {
        if (!finite(point) || !finite(start) || !finite(end)) {
            return null;
        }
        Vec3 span = end.subtract(start);
        double lengthSquared = span.lengthSqr();
        if (lengthSquared <= EPSILON) {
            return start;
        }
        double amount = point.subtract(start).dot(span) / lengthSquared;
        return start.add(span.scale(Math.max(0.0D, Math.min(1.0D, amount))));
    }

    /** Returns the first ray distance that enters a finite line-segment capsule. */
    public static double rayCapsuleHitDistance(Vec3 origin, Vec3 direction,
            Vec3 start, Vec3 end, double radius, double maximumDistance) {
        if (!finite(origin) || !finite(direction) || !finite(start) || !finite(end)
                || direction.lengthSqr() <= EPSILON || radius < 0.0D
                || maximumDistance < 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3 ray = direction.normalize();
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= EPSILON) {
            return raySphereHitDistance(origin, ray, start, radius, maximumDistance);
        }

        double length = Math.sqrt(lengthSquared);
        Vec3 axis = segment.scale(1.0D / length);
        Vec3 relative = origin.subtract(start);
        double axisOrigin = relative.dot(axis);
        double axisDirection = ray.dot(axis);
        Vec3 perpendicularOrigin = relative.subtract(axis.scale(axisOrigin));
        Vec3 perpendicularDirection = ray.subtract(axis.scale(axisDirection));
        double best = Math.min(
                raySphereHitDistance(origin, ray, start, radius, maximumDistance),
                raySphereHitDistance(origin, ray, end, radius, maximumDistance));

        double quadraticA = perpendicularDirection.lengthSqr();
        double quadraticB = 2.0D * perpendicularOrigin.dot(perpendicularDirection);
        double quadraticC = perpendicularOrigin.lengthSqr() - radius * radius;
        if (quadraticC <= 0.0D
                && axisOrigin >= 0.0D && axisOrigin <= length) {
            return 0.0D;
        }
        if (quadraticA <= EPSILON) {
            if (quadraticC <= 0.0D) {
                if (axisOrigin >= 0.0D && axisOrigin <= length) {
                    return 0.0D;
                }
                if (Math.abs(axisDirection) > EPSILON) {
                    double first = (0.0D - axisOrigin) / axisDirection;
                    double second = (length - axisOrigin) / axisDirection;
                    double enter = Math.max(0.0D, Math.min(first, second));
                    double exit = Math.max(first, second);
                    if (enter <= exit && enter <= maximumDistance) {
                        best = Math.min(best, enter);
                    }
                }
            }
            return best;
        }

        double discriminant = quadraticB * quadraticB
                - 4.0D * quadraticA * quadraticC;
        if (discriminant >= 0.0D) {
            double root = Math.sqrt(discriminant);
            double first = (-quadraticB - root) / (2.0D * quadraticA);
            double second = (-quadraticB + root) / (2.0D * quadraticA);
            best = Math.min(best, cylinderRoot(
                    first, axisOrigin, axisDirection, length, maximumDistance));
            best = Math.min(best, cylinderRoot(
                    second, axisOrigin, axisDirection, length, maximumDistance));
        }
        return best;
    }

    private static double cylinderRoot(double rayDistance, double axisOrigin,
            double axisDirection, double length, double maximumDistance) {
        if (rayDistance < 0.0D || rayDistance > maximumDistance) {
            return Double.POSITIVE_INFINITY;
        }
        double along = axisOrigin + axisDirection * rayDistance;
        return along >= -EPSILON && along <= length + EPSILON
                ? Math.max(0.0D, rayDistance) : Double.POSITIVE_INFINITY;
    }

    private static double raySphereHitDistance(Vec3 origin, Vec3 direction,
            Vec3 center, double radius, double maximumDistance) {
        Vec3 relative = origin.subtract(center);
        double constant = relative.lengthSqr() - radius * radius;
        if (constant <= 0.0D) {
            return 0.0D;
        }
        double linear = relative.dot(direction);
        double discriminant = linear * linear - constant;
        if (discriminant < 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        double distance = -linear - Math.sqrt(discriminant);
        return distance >= 0.0D && distance <= maximumDistance
                ? distance : Double.POSITIVE_INFINITY;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
