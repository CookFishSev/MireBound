package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.coverage.MudFeetContact;
import com.fish.mirebound.compat.sable.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class MudSoleEntryTest {
    @Test
    void onlyLegBottomFacesRequireEntryProbe() {
        assertTrue(MudContactRules.requiresSoleEntry(
                MudBodyPart.LEFT_LEG, MudSurface.BOTTOM));
        assertTrue(MudContactRules.requiresSoleEntry(
                MudBodyPart.RIGHT_LEG, MudSurface.BOTTOM));
        assertFalse(MudContactRules.requiresSoleEntry(
                MudBodyPart.LEFT_LEG, MudSurface.FRONT));
        assertFalse(MudContactRules.requiresSoleEntry(
                MudBodyPart.BODY, MudSurface.BOTTOM));
    }

    @Test
    void lowestLegSidePixelsShareTheSoleEntryGate() {
        assertTrue(MudContactRules.requiresSoleEntry(
                MudBodyPart.LEFT_LEG, MudSurface.FRONT, 0));
        assertTrue(MudContactRules.requiresSoleEntry(
                MudBodyPart.RIGHT_LEG, MudSurface.LEFT, 0));
        assertFalse(MudContactRules.requiresSoleEntry(
                MudBodyPart.LEFT_LEG, MudSurface.FRONT, 1));
        assertFalse(MudContactRules.requiresSoleEntry(
                MudBodyPart.LEFT_ARM, MudSurface.FRONT, 0));
    }

    @Test
    void entryProbeMovesInsideTheSoleInsteadOfBelowIt() {
        Vec3 surfacePoint = new Vec3(2.0D, 10.0D, 4.0D);
        Vec3 probe = MudContactRules.soleEntryProbePoint(
                surfacePoint, new Vec3(0.0D, -1.0D, 0.0D));

        assertEquals(2.0D, probe.x, 1.0E-9D);
        assertEquals(10.020D, probe.y, 1.0E-9D);
        assertEquals(4.0D, probe.z, 1.0E-9D);
    }

    @Test
    void animatedSoleCannotProbeBelowTheAuthoritativeFeetPlane() {
        Vec3 probe = MudContactRules.soleEntryProbePoint(
                10.0D,
                new Vec3(2.0D, 9.94D, 4.0D),
                new Vec3(0.0D, -1.0D, 0.0D));

        assertEquals(2.0D, probe.x, 1.0E-9D);
        assertEquals(10.020D, probe.y, 1.0E-9D);
        assertEquals(4.0D, probe.z, 1.0E-9D);
    }

    @Test
    void raisedAnimatedSoleKeepsItsOwnHigherProbe() {
        Vec3 probe = MudContactRules.soleEntryProbePoint(
                10.0D,
                new Vec3(2.0D, 10.08D, 4.0D),
                new Vec3(0.0D, -1.0D, 0.0D));

        assertEquals(10.10D, probe.y, 1.0E-9D);
    }

    @Test
    void tiltedAnimatedSoleAlsoRespectsTheAuthoritativeFeetPlane() {
        Vec3 probe = MudContactRules.soleEntryProbePoint(10.0D,
                new Vec3(2.0D, 9.94D, 4.0D), new Vec3(0.0D, -0.4D, 0.9165D));
        assertEquals(10.020D, probe.y, 1.0E-9D);
    }

    @Test
    void horizontalSableEdgeRejectsLoweredAnimatedFootAndBootPoints() {
        // The solid half supports feet at world Y=10; mud occupies the adjacent half.
        SableCompat.AffineTransform toLocal = new SableCompat.AffineTransform(
                new Vec3(1000.0D, 31.0D, -700.0D),
                new Vec3(0.0D, 0.0D, -1.0D), new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D));
        SableLayer mud = new SableLayer(SinkingMedium.MUD, new BlockPos(1000, 40, -700),
                null, 0L, 1.0D, 41.0D, 40.0D, Shapes.block());
        for (double y : new double[] {9.90D, 9.97D, 10.0D, 10.003D}) {
            Vec3 point = new Vec3(-0.3D, y, 0.5D);
            assertTrue(mud.contains(toLocal.toWorld(point), 0.004D));
            assertFalse(mud.contains(toLocal.toWorld(
                    MudFeetContact.entryPoint(10.0D, point)), 0.004D));
        }
        Vec3 immersedFoot = new Vec3(-0.3D, 9.94D, 0.5D);
        assertTrue(mud.contains(toLocal.toWorld(
                MudFeetContact.entryPoint(9.94D, immersedFoot)), 0.004D));
    }

    @Test
    void raisedContactPointsRemainUnchanged() {
        Vec3 point = new Vec3(2.0D, 10.40D, 4.0D);
        assertEquals(point, MudFeetContact.entryPoint(10.0D, point));
    }
}
