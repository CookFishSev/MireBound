package com.fish.mirebound.compat.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.coverage.MudFeetContact;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SableFeetSupportTest {
    private static final SableCompat.AffineTransform IDENTITY = new SableCompat.AffineTransform(
            Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
    private static final AABB SOLID_HALF = new AABB(0, 0, 0, 1, 1, 1);

    @Test
    void transientCollisionPenetrationAtTheSeamDoesNotStainTheOverhangingFoot() {
        AABB player = new AABB(.7, .977, .2, 1.3, 2.777, .8);
        Vec3 overhangingFoot = new Vec3(1.15, .971, .5);
        assertTrue(MudFeetContact.entryPoint(player.minY, overhangingFoot).y < 1.0);

        double supported = SableFeetSupport.supportedHeight(player, IDENTITY, IDENTITY, List.of(SOLID_HALF));
        assertEquals(1.0, supported, 1e-9);
        assertFalse(MudFeetContact.entryPoint(supported, overhangingFoot).y <= 1.004);
        assertTrue(1.0 - supported < .012);
    }

    @Test
    void leavingTheSolidHalfAllowsRealFootPenetrationImmediately() {
        AABB player = new AABB(1.01, .977, .2, 1.61, 2.777, .8);
        double supported = SableFeetSupport.supportedHeight(player, IDENTITY, IDENTITY, List.of(SOLID_HALF));
        assertEquals(player.minY, supported);
        assertTrue(MudFeetContact.entryPoint(supported, new Vec3(1.3, .971, .5)).y < 1.0);
    }

    @Test
    void vanillaGravityStepUsesThePreMoveFeetHeightForTheSupportSweep() {
        AABB player = new AABB(.7, .921, .2, 1.3, 2.721, .8);
        double support = SableFeetSupport.supportedHeight(player, 1.059, IDENTITY, IDENTITY, List.of(SOLID_HALF));
        assertEquals(1.0, support, 1e-9);
    }

    @Test
    void lowerFloorDoesNotHideActualImmersion() {
        AABB player = new AABB(.7, .7, .2, 1.3, 2.5, .8);
        AABB lowerFloor = new AABB(0, 0, 0, 1, .5, 1);
        assertEquals(player.minY, SableFeetSupport.supportedHeight(player, IDENTITY, IDENTITY, List.of(lowerFloor)));
    }

    @Test
    void wallBesideTheFeetIsNotAStandingSurface() {
        AABB player = new AABB(.7, .977, .2, 1.3, 2.777, .8);
        AABB tallWall = new AABB(0, 0, 0, 1, 3, 1);
        assertEquals(player.minY, SableFeetSupport.supportedHeight(player, IDENTITY, IDENTITY, List.of(tallWall)));
    }

    @Test
    void supportUsesTheSamePoseAsTheMudEvenAtLargePlotCoordinates() {
        Vec3 plotOrigin = new Vec3(20481032, 128, 20493319);
        SableCompat.AffineTransform forward = new SableCompat.AffineTransform(
                new Vec3(-plotOrigin.z, -plotOrigin.y, plotOrigin.x),
                new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 0, 0));
        SableCompat.AffineTransform inverse = new SableCompat.AffineTransform(plotOrigin,
                new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(-1, 0, 0));
        AABB player = new AABB(.2, .977, -1.3, .8, 2.777, -.7);
        assertEquals(1.0, SableFeetSupport.supportedHeight(player, inverse, forward,
                List.of(SOLID_HALF.move(plotOrigin))), 1e-7);
    }
}
