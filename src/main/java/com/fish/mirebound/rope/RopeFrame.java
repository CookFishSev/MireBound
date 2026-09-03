package com.fish.mirebound.rope;

import net.minecraft.world.phys.Vec3;

/** Orthonormal rigid orientation used by a held or anchored rope segment. */
public record RopeFrame(Vec3 x, Vec3 y, Vec3 z) {
    private static final double EPSILON = 1.0E-10D;
    public static final RopeFrame IDENTITY = new RopeFrame(
            new Vec3(1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, 1.0D, 0.0D),
            new Vec3(0.0D, 0.0D, 1.0D));

    public static RopeFrame from(Vec3 x, Vec3 y, Vec3 z) {
        if (x == null || y == null || z == null
                || !finite(x) || !finite(y) || !finite(z)) {
            return null;
        }
        Vec3 normalizedY = y.lengthSqr() <= EPSILON ? new Vec3(0.0D, 1.0D, 0.0D)
                : y.normalize();
        Vec3 projectedX = x.subtract(normalizedY.scale(x.dot(normalizedY)));
        if (projectedX.lengthSqr() <= EPSILON) {
            projectedX = Math.abs(normalizedY.y) < 0.95D
                    ? new Vec3(0.0D, 1.0D, 0.0D).cross(normalizedY)
                    : new Vec3(1.0D, 0.0D, 0.0D).cross(normalizedY);
        }
        projectedX = projectedX.normalize();
        Vec3 normalizedZ = projectedX.cross(normalizedY);
        if (normalizedZ.lengthSqr() <= EPSILON) {
            return null;
        }
        normalizedZ = normalizedZ.normalize();
        if (normalizedZ.dot(z) < 0.0D) {
            projectedX = projectedX.scale(-1.0D);
            normalizedZ = normalizedZ.scale(-1.0D);
        }
        return new RopeFrame(projectedX, normalizedY, normalizedZ);
    }

    public static RopeFrame fromTangent(Vec3 tangent) {
        Vec3 y = tangent == null || tangent.lengthSqr() <= EPSILON
                ? new Vec3(0.0D, 1.0D, 0.0D) : tangent.normalize();
        Vec3 reference = Math.abs(y.y) < 0.95D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 x = reference.cross(y);
        if (x.lengthSqr() <= EPSILON) {
            x = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return from(x, y, x.cross(y));
    }

    public RopeFrame rotateAround(Vec3 axis, double radians) {
        if (axis == null || axis.lengthSqr() <= EPSILON
                || !Double.isFinite(radians) || Math.abs(radians) <= EPSILON) {
            return this;
        }
        Vec3 normalized = axis.normalize();
        return new RopeFrame(rotate(x, normalized, radians),
                rotate(y, normalized, radians), rotate(z, normalized, radians));
    }

    public RopeFrame rotateLocalY(double radians) {
        return rotateAround(y, radians);
    }

    public RopeFrame preMultiply(Vec3 worldAxis, double radians) {
        return rotateAround(worldAxis, radians);
    }

    /** Applies mouse rotation around the two fixed axes in the screen plane. */
    public RopeFrame applyScreenPlaneInput(double yawDegrees, double pitchDegrees,
            Vec3 screenUp, Vec3 screenRight, double sensitivity) {
        if (screenUp == null || screenUp.lengthSqr() <= EPSILON
                || screenRight == null || screenRight.lengthSqr() <= EPSILON
                || !Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)
                || !Double.isFinite(sensitivity)) {
            return this;
        }
        Vec3 up = screenUp.normalize();
        Vec3 right = screenRight.subtract(up.scale(screenRight.dot(up)));
        if (right.lengthSqr() <= EPSILON) {
            return this;
        }
        right = right.normalize();
        double yawRadians = Math.toRadians(yawDegrees) * sensitivity;
        double pitchRadians = Math.toRadians(-pitchDegrees) * sensitivity;
        RopeFrame rotated = rotateAround(up, yawRadians)
                .rotateAround(right, pitchRadians);
        RopeFrame normalized = from(rotated.x, rotated.y, rotated.z);
        return normalized == null ? this : normalized;
    }

    /** Compatibility overload for callers that only have a view direction. */
    public RopeFrame applyPhysicsStaffInput(double yawDegrees, double pitchDegrees,
            Vec3 worldViewDirection, double sensitivity) {
        if (worldViewDirection == null || worldViewDirection.lengthSqr() <= EPSILON) {
            return this;
        }
        Vec3 forward = worldViewDirection.normalize();
        Vec3 referenceUp = Math.abs(forward.y) < 0.999D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = forward.cross(referenceUp);
        if (right.lengthSqr() <= EPSILON) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 up = right.normalize().cross(forward);
        return applyScreenPlaneInput(yawDegrees, pitchDegrees, up, right, sensitivity);
    }

    private static Vec3 rotate(Vec3 vector, Vec3 axis, double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return vector.scale(cosine)
                .add(axis.cross(vector).scale(sine))
                .add(axis.scale(axis.dot(vector) * (1.0D - cosine)));
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }
}
