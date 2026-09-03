package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
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
}
