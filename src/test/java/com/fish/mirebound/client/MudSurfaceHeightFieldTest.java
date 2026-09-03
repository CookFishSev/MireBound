package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSurfaceHeightFieldTest {
    @Test
    void rimProfileFallsOutwardWithoutScalingIndividualPixels() {
        double first = MudSurfaceHeightField.rimWeight(1.0D, 2.50D);
        double diagonal = MudSurfaceHeightField.rimWeight(Math.sqrt(2.0D), 2.50D);
        double second = MudSurfaceHeightField.rimWeight(2.0D, 2.50D);
        double third = MudSurfaceHeightField.rimWeight(3.0D, 2.50D);
        double outside = MudSurfaceHeightField.rimWeight(4.0D, 2.50D);

        assertTrue(first > diagonal);
        assertTrue(diagonal > second);
        assertTrue(second > third);
        assertTrue(third > 0.0D);
        assertEquals(0.0D, outside, 1.0E-9D);
    }

    @Test
    void pileDistributionPreservesVolumeUntilItsHeightCap() {
        double uncapped = MudSurfaceHeightField.normalizedPileHeight(
                8.0D, 16.0D, 2.0D, 2.0D);
        double capped = MudSurfaceHeightField.normalizedPileHeight(
                32.0D, 16.0D, 2.0D, 1.0D);

        assertEquals(0.5D, uncapped, 1.0E-9D);
        assertEquals(0.5D, capped, 1.0E-9D);
    }

    @Test
    void ordinaryDefaultsProduceAReadableThreeRingPile() {
        double[] values = MudPhysicsProfiles.defaultValues(SinkingMedium.MUD);

        assertEquals(1.10D,
                values[MudPhysicsParameter.SURFACE_HOLE_RADIUS_SCALE.ordinal()], 1.0E-9D);
        assertEquals(2.50D,
                values[MudPhysicsParameter.SURFACE_RIM_WIDTH_PIXELS.ordinal()], 1.0E-9D);
        assertEquals(1.25D,
                values[MudPhysicsParameter.SURFACE_RIM_HEIGHT_PIXELS.ordinal()], 1.0E-9D);
        assertEquals(1.05D,
                values[MudPhysicsParameter.SURFACE_DISPLACEMENT_PIXELS.ordinal()], 1.0E-9D);
        assertEquals(4.0D,
                values[MudPhysicsParameter.SURFACE_IMPACT_PILE_EXPANSION_PIXELS.ordinal()],
                1.0E-9D);
    }

    @Test
    void impactExpansionGrowsSmoothlyWithSpeedAndAvailableMud() {
        assertEquals(0.0D,
                MudSurfaceHeightField.impactExpansionPixels(3.0D, 0.0D, 1.0D),
                1.0E-9D);
        double moderate = MudSurfaceHeightField.impactExpansionPixels(3.0D, 0.5D, 0.5D);
        double fast = MudSurfaceHeightField.impactExpansionPixels(3.0D, 1.0D, 0.5D);
        double fullVolume = MudSurfaceHeightField.impactExpansionPixels(3.0D, 1.0D, 1.0D);

        assertTrue(moderate > 0.0D);
        assertTrue(fast > moderate);
        assertTrue(fullVolume > fast);
        assertEquals(3.0D, fullVolume, 1.0E-9D);
    }

    @Test
    void qualifiedImpactKeepsAReadableContactRadius() {
        double subthreshold = MudSurfaceHeightField.impactRadiusPixels(
                3.0D, 4.0D, 0.01D, 1.0D);
        double threshold = MudSurfaceHeightField.impactRadiusPixels(
                3.0D, 4.0D, 0.12D, 1.0D);
        double moderate = MudSurfaceHeightField.impactRadiusPixels(
                3.0D, 4.0D, 0.50D, 1.0D);
        double full = MudSurfaceHeightField.impactRadiusPixels(
                3.0D, 4.0D, 1.0D, 1.0D);

        assertEquals(0.0D, subthreshold, 1.0E-9D);
        assertEquals(3.48D, threshold, 1.0E-9D);
        assertTrue(moderate > threshold);
        assertTrue(full > moderate);
        assertEquals(7.0D, full, 1.0E-9D);
    }

    @Test
    void impactCraterUsesAnIndependentRetainedSurfaceKey() {
        int playerId = 42;
        int impactKey = MudSurfaceEffectManager.impactHoleKey(
                playerId, new Vec3(12.5D, 64.0D, -8.5D));

        assertTrue(impactKey < 0);
        assertNotEquals(playerId, impactKey);
    }

    @Test
    void impactCompressionIsARadialStampInTheSharedHeightField() {
        double cardinal = MudSurfaceHeightField.impactDepression(
                5.5D, 0.0D, 6.0D, 0.0D, 0.8D);
        double diagonal = MudSurfaceHeightField.impactDepression(
                5.5D / Math.sqrt(2.0D),
                5.5D / Math.sqrt(2.0D),
                6.0D, 0.0D, 0.8D);

        assertEquals(cardinal, diagonal, 1.0E-9D);
        assertEquals(1.0D, MudSurfaceHeightField.impactDepression(
                0.0D, 0.0D, 6.0D, 0.0D, 1.0D), 1.0E-9D);
        assertTrue(MudSurfaceHeightField.impactDepression(
                0.0D, 0.0D, 6.0D, 0.0D, 0.8D) > cardinal);
        assertEquals(0.0D,
                MudSurfaceHeightField.impactDepression(
                        7.0D, 0.0D, 6.0D, 0.0D, 0.8D),
                1.0E-9D);
    }

}
