package com.fish.mirebound.rope;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared bounded geometry and motion rules for ladder-like rope climbing. */
public final class RopeClimbing {
    private static final double MINIMUM_VERTICAL_SHARE = 0.90D;
    private static final double CONTACT_PADDING = 0.035D;
    private static final double VERTICAL_PADDING = 0.08D;
    private static final double CLIMB_SPEED = 0.15D;
    private static final double SLIDE_SPEED = 0.15D;

    private RopeClimbing() {
    }

    public static Vec3 contactPoint(
            AABB body, Vec3 start, Vec3 end, double ropeRadius) {
        if (body == null || start == null || end == null
                || !isNearlyVertical(start, end)) {
            return null;
        }
        double segmentMinY = Math.min(start.y, end.y);
        double segmentMaxY = Math.max(start.y, end.y);
        if (segmentMaxY < body.minY - VERTICAL_PADDING
                || segmentMinY > body.maxY + VERTICAL_PADDING) {
            return null;
        }
        double sampleY = Mth.clamp(
                (body.minY + body.maxY) * 0.5D, segmentMinY, segmentMaxY);
        double verticalSpan = end.y - start.y;
        double amount = Math.abs(verticalSpan) <= 1.0E-10D
                ? 0.5D : Mth.clamp((sampleY - start.y) / verticalSpan, 0.0D, 1.0D);
        Vec3 point = start.lerp(end, amount);
        double nearestX = Mth.clamp(point.x, body.minX, body.maxX);
        double nearestZ = Mth.clamp(point.z, body.minZ, body.maxZ);
        double reach = Math.max(0.0D, ropeRadius) + CONTACT_PADDING;
        double offsetX = point.x - nearestX;
        double offsetZ = point.z - nearestZ;
        return offsetX * offsetX + offsetZ * offsetZ <= reach * reach
                ? point : null;
    }

    public static boolean isNearlyVertical(Vec3 start, Vec3 end) {
        if (start == null || end == null) {
            return false;
        }
        Vec3 span = end.subtract(start);
        double length = span.length();
        return length > 1.0E-6D
                && Math.abs(span.y) / length >= MINIMUM_VERTICAL_SHARE;
    }

    public static Vec3 motion(Vec3 current, boolean jumping, boolean crouching) {
        if (current == null) {
            return Vec3.ZERO;
        }
        if (crouching) {
            return new Vec3(current.x, 0.0D, current.z);
        }
        if (jumping) {
            return new Vec3(current.x, Math.max(current.y, CLIMB_SPEED), current.z);
        }
        return new Vec3(current.x, Math.max(current.y, -SLIDE_SPEED), current.z);
    }
}
