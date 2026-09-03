package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudContactRulesTest {
    @Test
    void aRealEdgeOverlapCanEnterAfterVerticalPenetration() {
        assertTrue(MudContactRules.qualifiesWorldContact(0.055D));
    }

    @Test
    void ordinaryFootprintOverlapRemainsContinuous() {
        assertTrue(MudContactRules.qualifiesWorldContact(0.19D));
        assertTrue(MudContactRules.qualifiesWorldContact(0.045D));
    }

    @Test
    void numericalSliversAreRejectedEvenAtABlockBoundary() {
        assertFalse(MudContactRules.qualifiesWorldContact(0.001D));
    }

    @Test
    void feetMustActuallyPenetrateTheMudSurfaceBeforeContactStarts() {
        assertFalse(MudContactRules.qualifiesWorldVerticalContact(1.0D, 1.0D));
        assertFalse(MudContactRules.qualifiesWorldVerticalContact(0.995D, 1.0D));
        assertTrue(MudContactRules.qualifiesWorldVerticalContact(0.98D, 1.0D));
    }

    @Test
    void sculkSurfacePassDoesNotQualifyAsPollutionContact() {
        double surfaceY = 1.0D;
        double feetDepth = 0.010D;

        assertTrue(MudContactRules.qualifiesSculkSurfaceContact(feetDepth, true));
        assertFalse(MudContactRules.qualifiesWorldVerticalContact(
                surfaceY - feetDepth, surfaceY));
        assertFalse(MudContactRules.qualifiesSableFeetContact(feetDepth));
    }

    @Test
    void sableUsesTheRealFeetPlaneInsteadOfTheDownwardWitnessProbe() {
        assertFalse(MudContactRules.qualifiesSableFeetContact(-0.02D));
        assertFalse(MudContactRules.qualifiesSableFeetContact(0.005D));
        assertTrue(MudContactRules.qualifiesSableFeetContact(0.020D));
    }

    @Test
    void sableThinMudUsesItsRealLocalHeightInsteadOfAFullBlock() {
        BlockPos pos = new BlockPos(10, 20, 30);
        double onePixel = 1.0D / 16.0D;

        assertTrue(MudContactRules.insideSableLayerBounds(
                new Vec3(10.5D, 20.04D, 30.5D), pos, onePixel, 0.009D));
        assertFalse(MudContactRules.insideSableLayerBounds(
                new Vec3(10.5D, 20.25D, 30.5D), pos, onePixel, 0.009D));
        assertFalse(MudContactRules.insideSableLayerBounds(
                new Vec3(9.95D, 20.04D, 30.5D), pos, onePixel, 0.009D));
    }

    @Test
    void sideVolumeContactHasASmallStableEngagementFloor() {
        assertEquals(0.0D, MudContactRules.effectiveVolumeImmersion(0.0D), 1.0E-12D);
        assertEquals(0.060D, MudContactRules.effectiveVolumeImmersion(0.015D), 1.0E-12D);
        assertEquals(0.35D, MudContactRules.effectiveVolumeImmersion(0.35D), 1.0E-12D);
    }

    @Test
    void deeperVolumeContactIncreasesResistanceWithoutAddingLift() {
        SinkingPhysicsProfile profile =
                SinkingPhysicsProfile.forMedium(SinkingMedium.SOFT_QUICKSAND);
        MudContactRules.VolumeResistance shallow =
                MudContactRules.volumeResistance(profile, 0.06D);
        MudContactRules.VolumeResistance deep =
                MudContactRules.volumeResistance(profile, 0.52D);

        assertTrue(shallow.walkScale() > deep.walkScale());
        assertTrue(shallow.verticalScale() > deep.verticalScale());
        assertTrue(shallow.verticalScale() <= 1.0D);
        assertTrue(deep.verticalScale() > 0.0D);
    }
}
