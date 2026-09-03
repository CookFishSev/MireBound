package com.fish.mirebound.compat.sable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableOrientedBoxTest {
    private static final double DIAGONAL = Math.sqrt(0.5D);

    @Test
    void rotatedContactIsDetectedAtTheEdge() {
        SableOrientedBox item = rotatedItem(new Vec3(0.0D, 0.0D, 0.0D));

        assertTrue(item.intersects(new AABB(
                0.16D, -0.12D, -0.03D,
                0.28D, 0.12D, 0.03D)));
    }

    @Test
    void broadphaseOverlapDoesNotBecomeMudContact() {
        SableOrientedBox item = rotatedItem(new Vec3(0.0D, 0.0D, 0.0D));

        assertTrue(item.enclosingBounds().intersects(new AABB(
                0.16D, -0.12D, 0.16D,
                0.22D, 0.12D, 0.22D)));
        assertFalse(item.intersects(new AABB(
                0.16D, -0.12D, 0.16D,
                0.22D, 0.12D, 0.22D)));
    }

    @Test
    void separatedGeometryIsRejected() {
        SableOrientedBox item = rotatedItem(new Vec3(0.0D, 0.0D, 0.0D));

        assertFalse(item.intersects(new AABB(
                0.5D, -0.1D, 0.5D,
                0.7D, 0.1D, 0.7D)));
    }

    @Test
    void movingAnOrientedBoxMovesItsFiniteContactRegion() {
        Vec3 movement = new Vec3(2.0D, -1.0D, 3.0D);
        SableOrientedBox moved = rotatedItem(Vec3.ZERO).moved(movement);
        AABB translatedMud = new AABB(
                0.16D, -0.12D, -0.03D,
                0.28D, 0.12D, 0.03D).move(movement);

        assertTrue(moved.intersects(translatedMud));
        assertFalse(moved.intersects(new AABB(
                0.16D, -0.12D, -0.03D,
                0.28D, 0.12D, 0.03D)));
    }

    @Test
    void leavingAThinVolumeAlongTheSweepAxisEndsContact() {
        SableOrientedBox item = rotatedItem(Vec3.ZERO);
        AABB thinMud = new AABB(
                -0.3D, -0.04D, -0.3D,
                0.3D, 0.04D, 0.3D);

        assertTrue(item.intersects(thinMud));
        assertFalse(item.moved(new Vec3(0.0D, 0.3D, 0.0D)).intersects(thinMud));
        assertFalse(item.moved(new Vec3(0.0D, -0.3D, 0.0D)).intersects(thinMud));
    }

    private static SableOrientedBox rotatedItem(Vec3 center) {
        return SableOrientedBox.createForTest(
                center,
                new Vec3(DIAGONAL, 0.0D, DIAGONAL),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(-DIAGONAL, 0.0D, DIAGONAL),
                new Vec3(0.125D, 0.125D, 0.125D));
    }
}
