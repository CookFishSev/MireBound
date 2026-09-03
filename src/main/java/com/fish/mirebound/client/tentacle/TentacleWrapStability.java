package com.fish.mirebound.client.tentacle;

import net.minecraft.world.phys.Vec3;

/** Pure geometry for the only supported wrap: a full-body helix. */
final class TentacleWrapStability {
    private static final double EPSILON = 1.0E-10D;

    private TentacleWrapStability() {
    }

    static AxialSpan fullBodySpan(double halfLength) {
        double half = Math.max(0.0D, halfLength);
        if (half <= EPSILON) {
            return new AxialSpan(0.0D, 0.0D);
        }
        // The body axis points from pelvis/feet toward chest/head. Always
        // traverse anatomical bottom -> top. When the ragdoll is upside down
        // this naturally becomes world top -> bottom without a state flip.
        return new AxialSpan(-half, half);
    }

    static Vec3 strandNormal(Vec3 fallback, Vec3 axis, double phase) {
        Vec3 normalizedAxis = axis.lengthSqr() <= EPSILON
                ? new Vec3(0.0D, 1.0D, 0.0D) : axis.normalize();
        Vec3 first = perpendicularFallback(fallback, normalizedAxis);
        Vec3 second = normalizedAxis.cross(first).normalize();
        return first.scale(Math.cos(phase)).add(second.scale(Math.sin(phase))).normalize();
    }

    private static Vec3 perpendicularFallback(Vec3 fallback, Vec3 axis) {
        Vec3 projected = fallback.subtract(axis.scale(fallback.dot(axis)));
        if (projected.lengthSqr() > EPSILON) {
            return projected.normalize();
        }
        Vec3 reference = Math.abs(axis.y) < 0.90D
                ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        return reference.subtract(axis.scale(reference.dot(axis))).normalize();
    }

    record AxialSpan(double start, double end) {
    }
}
