package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudCoverageRulesTest {
    @Test
    void mudClodCanAccumulateSeveralSplashGainsInOnePaintPass() {
        float ordinary = MudCoverageRules.accumulateSplash(0.0F, 0.45F);
        float clod = MudCoverageRules.accumulateSplash(0.0F, 0.45F, 8);

        assertEquals(ordinary * 8.0F, clod, 1.0E-6F);
        assertTrue(clod >= 0.05F);
        assertTrue(clod <= 0.12F);
    }

    @Test
    void contactStrengthUsesThePollutionMultiplierWithoutTreatingPixelRatioAsOpacity() {
        assertEquals(0.72F, MudCoverageRules.target(0.60F, 1.20F), 1.0E-6F);
        assertEquals(1.00F, MudCoverageRules.target(1.00F, 1.20F), 1.0E-6F);
    }

    @Test
    void invalidAndNegativeInputsCannotCreateCoverage() {
        assertEquals(0.0F, MudCoverageRules.target(Float.NaN, 1.0F), 0.0F);
        assertEquals(0.0F, MudCoverageRules.target(1.0F, -1.0F), 0.0F);
    }

    @Test
    void repeatedSplashesAccumulateTowardEightyFivePercent() {
        float coverage = 0.0F;
        for (int hit = 0; hit < 128; hit++) {
            float next = MudCoverageRules.accumulateSplash(coverage, 0.42F);
            assertTrue(next >= coverage);
            coverage = next;
        }

        assertEquals(MudCoverageRules.SPLASH_COVERAGE_MAXIMUM,
                coverage, 1.0E-5F);
    }

    @Test
    void oneSplashAddsOnlyASmallAmountAndNeverReducesDeeperCoverage() {
        float ordinaryHit = MudCoverageRules.accumulateSplash(0.0F, 0.48F);
        float strongestHit = MudCoverageRules.accumulateSplash(0.0F, 1.0F);

        assertTrue(ordinaryHit > 0.005F && ordinaryHit < 0.015F);
        assertEquals(0.015F, strongestHit, 1.0E-6F);
        assertEquals(0.94F,
                MudCoverageRules.accumulateSplash(0.94F, 0.60F), 1.0E-6F);
    }

    @Test
    void anotherMediumOrAdaptiveVisualCanCoverAnAlreadySaturatedCell() {
        assertTrue(MudCoverageRules.splashChangesCell(
                0.85F, 0.85F,
                SinkingMedium.MUD, SinkingMedium.SOFT_QUICKSAND,
                0L, 0L));
        assertTrue(MudCoverageRules.splashChangesCell(
                0.85F, 0.85F,
                SinkingMedium.MUD, SinkingMedium.MUD,
                11L, 22L));
    }

    @Test
    void maximumRatioSelectsAStableDistributedSubsetOfPixels() {
        int count = MudSurfaceLayout.CELL_COUNT;
        int selected = 0;
        for (int pixel = 0; pixel < count; pixel++) {
            boolean first = MudCoverageRules.allowsPixel(
                    18, MudCoverageRules.DOMAIN_SKIN, pixel, count, 0.50F);
            boolean repeated = MudCoverageRules.allowsPixel(
                    18, MudCoverageRules.DOMAIN_SKIN, pixel, count, 0.50F);
            assertEquals(first, repeated);
            if (first) {
                selected++;
            }
        }

        assertEquals(count * 0.50D, selected, 2.0D);
    }

    @Test
    void zeroAndFullRatiosRejectAndAcceptEveryPixel() {
        for (int pixel = 0; pixel < 64; pixel++) {
            assertEquals(false, MudCoverageRules.allowsPixel(3, 17, pixel, 64, 0.0F));
            assertEquals(true, MudCoverageRules.allowsPixel(3, 17, pixel, 64, 1.0F));
        }
    }

    @Test
    void sameMediumCanUseDifferentPerContactMaximumRatios() {
        int sparseAppearance = MudCoverageAppearanceSnapshot.pack(0.25F, 0.70F, 0.0F);
        int fullAppearance = MudCoverageAppearanceSnapshot.pack(1.0F, 1.0F, 0.0F);
        int sparse = 0;
        int full = 0;
        for (int pixel = 0; pixel < MudSurfaceLayout.CELL_COUNT; pixel++) {
            if (MudCoverageRules.allowsPixel(SinkingMedium.RED_QUICKSAND, sparseAppearance,
                    MudCoverageRules.DOMAIN_SKIN, pixel, MudSurfaceLayout.CELL_COUNT)) {
                sparse++;
            }
            if (MudCoverageRules.allowsPixel(SinkingMedium.RED_QUICKSAND, fullAppearance,
                    MudCoverageRules.DOMAIN_SKIN, pixel, MudSurfaceLayout.CELL_COUNT)) {
                full++;
            }
        }
        assertEquals(MudSurfaceLayout.CELL_COUNT * 0.25D, sparse, 2.0D);
        assertEquals(MudSurfaceLayout.CELL_COUNT, full);
    }

    @Test
    void appearanceSnapshotKeepsTrueOpacityAndVariationValues() {
        int appearance = MudCoverageAppearanceSnapshot.pack(0.91F, 0.73F, 0.17F, 0.08F);
        assertEquals(0.91F, MudCoverageAppearanceSnapshot.maximum(appearance, SinkingMedium.MUD), 1.0F / 255.0F);
        assertEquals(0.73F, MudCoverageAppearanceSnapshot.opacity(appearance, SinkingMedium.MUD), 1.0F / 255.0F);
        assertEquals(0.17F, MudCoverageAppearanceSnapshot.variation(appearance, SinkingMedium.MUD), 1.0F / 63.0F);
        assertEquals(0.08F,
                MudCoverageAppearanceSnapshot.brightnessVariation(appearance, SinkingMedium.MUD),
                1.0F / 63.0F);
    }

    @Test
    void legacyAppearanceSnapshotStillDecodesItsThreeOriginalChannels() {
        int legacy = 0x5A000000 | 232 << 16 | 43 << 8 | 186;
        assertEquals(232 / 255.0F,
                MudCoverageAppearanceSnapshot.maximum(legacy, SinkingMedium.MUD), 0.0F);
        assertEquals(186 / 255.0F,
                MudCoverageAppearanceSnapshot.opacity(legacy, SinkingMedium.MUD), 0.0F);
        assertEquals(43 / 255.0F,
                MudCoverageAppearanceSnapshot.variation(legacy, SinkingMedium.MUD), 0.0F);
    }
}
