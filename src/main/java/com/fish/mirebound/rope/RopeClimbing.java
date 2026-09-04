package com.fish.mirebound.rope;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared bounded geometry and motion rules for ladder-like rope climbing. */
public final class RopeClimbing {
    private static final double MINIMUM_VERTICAL_SHARE = 0.90D;
    private static final double CONTACT_PADDING = 0.035D;
    private static final double VERTICAL_PADDING = 0.08D;
    private static final double CLIMB_SPEED = 0.15D;
    private static final double SLIDE_SPEED = 0.15D;
    private static final Map<Integer, Boolean> CLIENT_CONTACTS = new HashMap<>();
    private static final Map<Integer, Boolean> CLIENT_RESCUE_CONTACTS = new HashMap<>();

    private RopeClimbing() {
    }

    /** Stores the current geometric result for the local player only. */
    public static void setClientContact(Player player, boolean active) {
        if (player != null) {
            CLIENT_CONTACTS.put(player.getId(), active);
        }
    }

    public static boolean clientContact(Player player) {
        return player != null && CLIENT_CONTACTS.getOrDefault(player.getId(), false);
    }

    public static void setClientRescueContact(Player player, boolean active) {
        if (player != null) {
            CLIENT_RESCUE_CONTACTS.put(player.getId(), active);
        }
    }

    public static boolean clientMovementContact(Player player) {
        return clientContact(player)
                || player != null
                && CLIENT_RESCUE_CONTACTS.getOrDefault(player.getId(), false);
    }

    public static void clearClientContacts() {
        CLIENT_CONTACTS.clear();
        CLIENT_RESCUE_CONTACTS.clear();
    }

    public static Vec3 contactPoint(
            AABB body, Vec3 start, Vec3 end, double ropeRadius) {
        if (body == null || start == null || end == null
                || !isNearlyVertical(start, end)) {
            return null;
        }
        Vec3 span = end.subtract(start);
        double length = span.length();
        double reach = Math.max(0.0D, ropeRadius) + CONTACT_PADDING;
        double lowY = body.minY - reach - VERTICAL_PADDING;
        double highY = body.maxY + reach + VERTICAL_PADDING;
        double[] verticalInterval = clipInterval(
                start.y, span.y, lowY, highY, 0.0D, 1.0D);
        if (verticalInterval == null) {
            return null;
        }
        double amount = closestHorizontalParameter(
                start, span, verticalInterval[0], verticalInterval[1], body);
        Vec3 point = start.add(span.scale(amount));
        double nearestX = Mth.clamp(point.x, body.minX, body.maxX);
        double nearestZ = Mth.clamp(point.z, body.minZ, body.maxZ);
        double offsetX = point.x - nearestX;
        double offsetZ = point.z - nearestZ;
        return offsetX * offsetX + offsetZ * offsetZ <= reach * reach
                ? point : null;
    }

    private static double[] clipInterval(double origin, double direction,
            double minimum, double maximum, double lower, double upper) {
        if (Math.abs(direction) <= 1.0E-10D) {
            return origin >= minimum && origin <= maximum
                    ? new double[] {lower, upper} : null;
        }
        double first = (minimum - origin) / direction;
        double second = (maximum - origin) / direction;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        lower = Math.max(lower, first);
        upper = Math.min(upper, second);
        return lower <= upper ? new double[] {lower, upper} : null;
    }

    private static double closestHorizontalParameter(Vec3 start, Vec3 span,
            double lower, double upper, AABB body) {
        Vec3 first = start.add(span.scale(lower));
        Vec3 second = start.add(span.scale(upper));
        double localIntersection = rectangleIntersectionParameter(first, second, body);
        if (Double.isFinite(localIntersection)) {
            return Mth.lerp(localIntersection, lower, upper);
        }

        double bestLocal = 0.0D;
        double bestDistance = horizontalDistanceSquared(first, body);
        double secondDistance = horizontalDistanceSquared(second, body);
        if (secondDistance < bestDistance) {
            bestDistance = secondDistance;
            bestLocal = 1.0D;
        }
        double[][] corners = {
                {body.minX, body.minZ},
                {body.minX, body.maxZ},
                {body.maxX, body.minZ},
                {body.maxX, body.maxZ}
        };
        double dx = second.x - first.x;
        double dz = second.z - first.z;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared > 1.0E-12D) {
            for (double[] corner : corners) {
                double local = Mth.clamp(((corner[0] - first.x) * dx
                        + (corner[1] - first.z) * dz) / lengthSquared, 0.0D, 1.0D);
                double x = first.x + dx * local;
                double z = first.z + dz * local;
                double distance = square(x - corner[0]) + square(z - corner[1]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestLocal = local;
                }
            }
        }
        return Mth.lerp(bestLocal, lower, upper);
    }

    private static double rectangleIntersectionParameter(
            Vec3 start, Vec3 end, AABB body) {
        double[] interval = {0.0D, 1.0D};
        if (!clipHorizontalAxis(start.x, end.x - start.x,
                body.minX, body.maxX, interval)
                || !clipHorizontalAxis(start.z, end.z - start.z,
                        body.minZ, body.maxZ, interval)) {
            return Double.NaN;
        }
        return interval[0];
    }

    private static boolean clipHorizontalAxis(double origin, double direction,
            double minimum, double maximum, double[] interval) {
        if (Math.abs(direction) <= 1.0E-12D) {
            return origin >= minimum && origin <= maximum;
        }
        double first = (minimum - origin) / direction;
        double second = (maximum - origin) / direction;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1];
    }

    private static double horizontalDistanceSquared(Vec3 point, AABB body) {
        double dx = point.x - Mth.clamp(point.x, body.minX, body.maxX);
        double dz = point.z - Mth.clamp(point.z, body.minZ, body.maxZ);
        return dx * dx + dz * dz;
    }

    private static double square(double value) {
        return value * value;
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
        return new Vec3(current.x, -SLIDE_SPEED, current.z);
    }

    /**
     * A ladder contact owns vertical rope motion, but it must not make an
     * ordinary sinking medium apply its horizontal resistance a second time.
     * Special medium solvers stay outside this bridge and keep their own rules.
     */
    public static Vec3 restoreMudHorizontalMotion(
            Vec3 originalMotion, Vec3 solvedMotion, boolean ropeContact,
            boolean specialMedium) {
        if (solvedMotion == null || !ropeContact || specialMedium) {
            return solvedMotion;
        }
        if (originalMotion == null) {
            return solvedMotion;
        }
        return new Vec3(originalMotion.x, solvedMotion.y, originalMotion.z);
    }
}
