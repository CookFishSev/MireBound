package com.fish.mirebound.compat.sable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Small immutable OBB used for exact world-AABB contact in a Sable-local frame. */
final class SableOrientedBox {
    private static final double AXIS_EPSILON = 1.0E-12D;
    private static final double CONTACT_EPSILON = 1.0E-8D;
    private static final Vec3 LOCAL_X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 LOCAL_Y = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 LOCAL_Z = new Vec3(0.0D, 0.0D, 1.0D);

    private final Vec3 center;
    private final Vec3 axisX;
    private final Vec3 axisY;
    private final Vec3 axisZ;
    private final Vec3 halfExtents;
    private final AABB enclosingBounds;

    private SableOrientedBox(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ,
            Vec3 halfExtents) {
        this.center = center;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
        this.halfExtents = halfExtents;
        double extentX = Math.abs(axisX.x) * halfExtents.x
                + Math.abs(axisY.x) * halfExtents.y
                + Math.abs(axisZ.x) * halfExtents.z;
        double extentY = Math.abs(axisX.y) * halfExtents.x
                + Math.abs(axisY.y) * halfExtents.y
                + Math.abs(axisZ.y) * halfExtents.z;
        double extentZ = Math.abs(axisX.z) * halfExtents.x
                + Math.abs(axisY.z) * halfExtents.y
                + Math.abs(axisZ.z) * halfExtents.z;
        this.enclosingBounds = new AABB(
                center.x - extentX, center.y - extentY, center.z - extentZ,
                center.x + extentX, center.y + extentY, center.z + extentZ);
    }

    static SableOrientedBox fromWorldBounds(
            AABB worldBounds, SableCompat.RigidTransform transform) {
        if (worldBounds == null || transform == null) {
            return null;
        }
        Vec3 center = transform.toLocal(worldBounds.getCenter());
        Vec3 axisX = normalized(transform.toLocalDirection(LOCAL_X));
        Vec3 axisY = normalized(transform.toLocalDirection(LOCAL_Y));
        Vec3 axisZ = normalized(transform.toLocalDirection(LOCAL_Z));
        if (center == null || axisX == null || axisY == null || axisZ == null) {
            return null;
        }
        return new SableOrientedBox(
                center, axisX, axisY, axisZ,
                new Vec3(
                        worldBounds.getXsize() * 0.5D,
                        worldBounds.getYsize() * 0.5D,
                        worldBounds.getZsize() * 0.5D));
    }

    static SableOrientedBox createForTest(Vec3 center, Vec3 axisX, Vec3 axisY,
            Vec3 axisZ, Vec3 halfExtents) {
        return new SableOrientedBox(
                center, axisX.normalize(), axisY.normalize(), axisZ.normalize(), halfExtents);
    }

    Vec3 center() {
        return center;
    }

    AABB enclosingBounds() {
        return enclosingBounds;
    }

    SableOrientedBox moved(Vec3 movement) {
        return new SableOrientedBox(
                center.add(movement), axisX, axisY, axisZ, halfExtents);
    }

    boolean intersects(AABB box) {
        if (box == null || !enclosingBounds.intersects(box)) {
            return false;
        }
        Vec3 boxCenter = box.getCenter();
        Vec3 boxHalf = new Vec3(
                box.getXsize() * 0.5D,
                box.getYsize() * 0.5D,
                box.getZsize() * 0.5D);
        Vec3 delta = center.subtract(boxCenter);

        if (separated(delta, boxHalf, LOCAL_X)
                || separated(delta, boxHalf, LOCAL_Y)
                || separated(delta, boxHalf, LOCAL_Z)
                || separated(delta, boxHalf, axisX)
                || separated(delta, boxHalf, axisY)
                || separated(delta, boxHalf, axisZ)) {
            return false;
        }

        Vec3[] orientedAxes = {axisX, axisY, axisZ};
        Vec3[] localAxes = {LOCAL_X, LOCAL_Y, LOCAL_Z};
        for (Vec3 orientedAxis : orientedAxes) {
            for (Vec3 localAxis : localAxes) {
                if (separated(delta, boxHalf, orientedAxis.cross(localAxis))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean separated(Vec3 centerDelta, Vec3 boxHalf, Vec3 testAxis) {
        if (testAxis.lengthSqr() <= AXIS_EPSILON) {
            return false;
        }
        double centerDistance = Math.abs(centerDelta.dot(testAxis));
        double orientedRadius = halfExtents.x * Math.abs(axisX.dot(testAxis))
                + halfExtents.y * Math.abs(axisY.dot(testAxis))
                + halfExtents.z * Math.abs(axisZ.dot(testAxis));
        double boxRadius = boxHalf.x * Math.abs(testAxis.x)
                + boxHalf.y * Math.abs(testAxis.y)
                + boxHalf.z * Math.abs(testAxis.z);
        return centerDistance > orientedRadius + boxRadius + CONTACT_EPSILON;
    }

    private static Vec3 normalized(Vec3 vector) {
        return vector == null || vector.lengthSqr() <= AXIS_EPSILON
                ? null : vector.normalize();
    }
}
