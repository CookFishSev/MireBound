package com.fish.mirebound.client.rope;

import net.minecraft.world.phys.Vec3;

/** Exact dimensions and UV rectangles authored in {@code art/blockbench/rope.bbmodel}. */
public final class RopeSegmentSpec {
    public static final Cuboid INNER = new Cuboid(
            2.0D / 16.0D, 8.0D / 16.0D,
            new Uv(0.0F, 0.0F, 4.0F / 32.0F, 16.0F / 32.0F),
            new Uv(4.0F / 32.0F, 16.0F / 32.0F, 0.0F, 20.0F / 32.0F));
    public static final Cuboid OUTER = new Cuboid(
            2.125D / 16.0D, 8.125D / 16.0D,
            new Uv(0.0F, 0.0F, 4.0F / 32.0F, 16.0F / 32.0F),
            new Uv(8.0F / 32.0F, 0.0F, 4.0F / 32.0F, 4.0F / 32.0F));

    private RopeSegmentSpec() {
    }

    static Vec3 faceNormal(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 normal = c.subtract(a).cross(b.subtract(a));
        return normal.lengthSqr() <= 1.0E-10D ? Vec3.ZERO : normal.normalize();
    }

    static boolean facePointsAwayFrom(
            Vec3 expectedOutward, Vec3 a, Vec3 b, Vec3 c) {
        Vec3 normal = faceNormal(a, b, c);
        return normal.lengthSqr() > 1.0E-10D
                && expectedOutward.lengthSqr() > 1.0E-10D
                && normal.dot(expectedOutward) < 0.0D;
    }

    static boolean shouldRenderJoint(double directionDot) {
        return directionDot > -0.999999D && directionDot < 0.999999D;
    }

    static boolean shouldRenderJoint(RopeSegmentPose.Frame first,
            RopeSegmentPose.Frame second) {
        return shouldRenderJoint(first.y().dot(second.y()));
    }

    public record Cuboid(double halfWidth, double halfLength, Uv side, Uv cap) {
    }

    public record Uv(float u0, float v0, float u1, float v1) {
    }
}
