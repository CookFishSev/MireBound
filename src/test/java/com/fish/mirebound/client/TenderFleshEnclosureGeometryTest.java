package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TenderFleshEnclosureGeometryTest {
    @Test
    void closedPillarsAndAllMembraneSlicesShareOneRoofTip() {
        Vec3 center = new Vec3(20.0D, 64.0D, -10.0D);
        Vec3 normal = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 tip = point(center, new Vec3(1.0D, 0.0D, 0.0D), normal, 1.0D);
        for (Vec3 radial : new Vec3[] {
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(-1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 0.0D, -1.0D)}) {
            assertEquals(tip, point(center, radial, normal, 1.0D));
        }
        for (int slice = 0; slice <= 4; slice++) {
            assertEquals(tip, TenderFleshEnclosureGeometry.membranePoint(
                    tip, tip, center, normal, slice / 4.0D, 0.5D));
        }
    }

    @Test
    void membraneBendConvergesContinuouslyAndKeepsPillarSeamsWelded() {
        Vec3 center = Vec3.ZERO;
        Vec3 normal = new Vec3(0.0D, 1.0D, 0.0D);
        for (double span : new double[] {1.0D, 0.01D, 0.0001D, 0.000001D}) {
            Vec3 from = new Vec3(span, 2.0D, 0.0D);
            Vec3 to = new Vec3(0.0D, 2.0D, span);
            Vec3 middle = TenderFleshEnclosureGeometry.membranePoint(
                    from, to, center, normal, 0.5D, 0.5D);
            assertTrue(middle.distanceTo(new Vec3(0.0D, 2.0D, 0.0D)) <= span * 1.1D);
            assertEquals(from, TenderFleshEnclosureGeometry.membranePoint(
                    from, to, center, normal, 0.0D, 0.5D));
            assertEquals(to, TenderFleshEnclosureGeometry.membranePoint(
                    from, to, center, normal, 1.0D, 0.5D));
        }
    }

    @Test
    void tiltedEnclosureStillClosesAlongItsOwnNormal() {
        Vec3 center = new Vec3(5.0D, -2.0D, 8.0D);
        Vec3 normal = new Vec3(1.0D, 1.0D, 0.0D).normalize();
        Vec3 first = point(center, new Vec3(0.0D, 0.0D, 1.0D), normal, 1.0D);
        Vec3 second = point(center, new Vec3(0.0D, 0.0D, -1.0D), normal, 1.0D);
        assertEquals(first, second);
        assertEquals(0.0D, first.subtract(center).cross(normal).length(), 1.0E-9D);
    }

    private static Vec3 point(Vec3 center, Vec3 radial, Vec3 normal, double progress) {
        return TenderFleshEnclosureGeometry.pillarPoint(
                center, radial, normal, 1.5D, 0.6D, 2.0D,
                1.0D, 1.0D, 0.15D, 0.2D, progress);
    }
}
