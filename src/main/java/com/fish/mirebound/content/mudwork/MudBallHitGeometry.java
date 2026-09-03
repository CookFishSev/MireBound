package com.fish.mirebound.content.mudwork;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Separates face hits from ordinary body impacts without another entity query. */
final class MudBallHitGeometry {
    private static final double HEAD_CENTER_BELOW_EYES = 0.12D;
    private static final double HEAD_HIT_RADIUS = 0.62D;
    private static final double MINIMUM_FRONT_PROJECTION = 0.04D;
    private static final double MAXIMUM_FACE_PLANE_DISTANCE = 0.46D;
    private static final double MAXIMUM_APPROACH_PLANE_DISTANCE = 0.52D;
    private static final double MAXIMUM_FRONT_APPROACH_DOT = -0.30D;

    private MudBallHitGeometry() {
    }

    static Vec3 resolveEntityImpact(
            AABB collisionBox, Vec3 tickStart, Vec3 tickEnd,
            Vec3 fallback) {
        var clipped = collisionBox.clip(tickStart, tickEnd);
        if (clipped.isPresent()) {
            return clipped.get();
        }
        return new Vec3(
                Mth.clamp(fallback.x, collisionBox.minX, collisionBox.maxX),
                Mth.clamp(fallback.y, collisionBox.minY, collisionBox.maxY),
                Mth.clamp(fallback.z, collisionBox.minZ, collisionBox.maxZ));
    }

    static boolean strikesFrontHead(
            Vec3 impact, Vec3 incoming, Vec3 playerPosition, double eyeY,
            float headYawDegrees, float headPitchDegrees) {
        Vec3 headCenter = new Vec3(
                playerPosition.x, eyeY - HEAD_CENTER_BELOW_EYES,
                playerPosition.z);
        Vec3 relative = impact.subtract(headCenter);
        double distanceSquared = relative.lengthSqr();
        if (distanceSquared > HEAD_HIT_RADIUS * HEAD_HIT_RADIUS) {
            return false;
        }

        double yaw = Math.toRadians(headYawDegrees);
        double pitch = Math.toRadians(headPitchDegrees);
        double cosPitch = Math.cos(pitch);
        Vec3 forward = new Vec3(
                -Math.sin(yaw) * cosPitch,
                -Math.sin(pitch),
                Math.cos(yaw) * cosPitch);
        double front = relative.dot(forward);
        double planeDistance = Math.sqrt(Math.max(
                0.0D, distanceSquared - front * front));
        if (front >= MINIMUM_FRONT_PROJECTION
                && planeDistance <= MAXIMUM_FACE_PLANE_DISTANCE) {
            return true;
        }

        return incoming.lengthSqr() > 1.0E-8D
                && incoming.normalize().dot(forward)
                        <= MAXIMUM_FRONT_APPROACH_DOT
                && planeDistance <= MAXIMUM_APPROACH_PLANE_DISTANCE;
    }
}
