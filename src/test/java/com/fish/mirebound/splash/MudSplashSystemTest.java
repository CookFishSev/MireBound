package com.fish.mirebound.splash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudSplashSystemTest {
    @Test
    void defaultDropletsRemainVisibleForFiveSeconds() {
        assertEquals(100, MudSplashProfile.DEFAULT.lifetimeTicks());
    }

    @Test
    void runtimeProfileKeepsMaximumImpactAboveMinimum() {
        MudSplashProfile profile = new MudSplashProfile(
                true, 2.0D, 1.0D, 10, 64, 512,
                0.44D, 0.04D, 0.965D, 34, 8,
                0.13F, 0.84F, 0.12F, 0.72F, 48.0D);

        assertEquals(2.01D, profile.maximumImpactSpeed(), 1.0E-9D);
        assertEquals(MudSplashProfile.DEFAULT.maximumActiveDroplets(),
                profile.maximumActiveDroplets());
    }

    @Test
    void impactStrengthIsClampedBeforeExtremeVelocityCanScaleTheBurst() {
        assertEquals(0.0D, MudSplashSystem.normalizedImpact(0.20D, 0.30D, 2.40D), 1.0E-9D);
        assertEquals(1.0D, MudSplashSystem.normalizedImpact(200.0D, 0.30D, 2.40D), 1.0E-9D);
    }

    @Test
    void qualifiedNormalImpactHasVisibleStrengthAtTheThreshold() {
        assertEquals(0.0D,
                MudSplashSystem.qualifiedImpactStrength(0.29D, 0.30D, 2.40D),
                1.0E-9D);
        assertEquals(0.12D,
                MudSplashSystem.qualifiedImpactStrength(0.30D, 0.30D, 2.40D),
                1.0E-9D);
        assertTrue(MudSplashSystem.qualifiedImpactStrength(
                0.80D, 0.30D, 2.40D) > 0.12D);
    }

    @Test
    void firstImpactIsNotPermanentlyBlockedByTickOverflow() {
        assertTrue(MudSplashSystem.impactCooldownElapsed(
                120, Integer.MIN_VALUE, 8));
        assertTrue(MudSplashSystem.impactCooldownElapsed(120, 112, 8));
        assertEquals(false, MudSplashSystem.impactCooldownElapsed(120, 113, 8));
    }

    @Test
    void thinnerMudProducesFewerDropletsWithoutDisablingTheEffect() {
        int thin = MudSplashSystem.dropletCount(5, 24, 0.75D, 0.20D);
        int full = MudSplashSystem.dropletCount(5, 24, 0.75D, 1.0D);
        assertTrue(thin >= 1);
        assertTrue(thin < full);
    }

    @Test
    void perImpactLimitIsAlwaysRespected() {
        assertEquals(12, MudSplashSystem.dropletCount(32, 12, 1.0D, 1.0D));
    }

    @Test
    void highSpeedImpactCanProduceAVisiblyLargeBurst() {
        assertEquals(50, MudSplashSystem.dropletCount(10, 64, 1.0D, 1.0D));
    }

    @Test
    void surfaceCompressionSurvivesAnExhaustedDropletPool() {
        assertTrue(MudSplashSystem.impactHasFeedback(0, true));
        assertEquals(false, MudSplashSystem.impactHasFeedback(0, false));
    }

    @Test
    void mixedMediaEachReceiveDropletsBeforeWeightedRemainder() {
        int[] counts = MudSplashSystem.allocateMediumCounts(
                12, new int[] {6, 2, 1});

        assertEquals(12, counts[0] + counts[1] + counts[2]);
        assertTrue(counts[0] > counts[1]);
        assertTrue(counts[1] >= counts[2]);
        assertTrue(counts[2] >= 1);
    }

    @Test
    void adaptiveFountainPaletteStillUsesOneDropletBudget() {
        MudVisualPalette palette = new MudVisualPalette();
        palette.add(SinkingMedium.MUD, 11L, 0.55F);
        palette.add(SinkingMedium.MUD, 12L, 0.25F);
        palette.add(SinkingMedium.SOFT_QUICKSAND, 13L, 0.20F);

        int[] counts = MudSplashSystem.allocateVisualCounts(19, palette);

        assertEquals(19, java.util.Arrays.stream(counts).sum());
        assertTrue(counts[0] > counts[1]);
        assertTrue(counts[1] >= counts[2]);
    }

    @Test
    void tinyBurstKeepsOnlyTheMostRepresentedMedia() {
        int[] counts = MudSplashSystem.allocateMediumCounts(
                2, new int[] {2, 7, 4});

        assertEquals(0, counts[0]);
        assertEquals(1, counts[1]);
        assertEquals(1, counts[2]);
    }

    @Test
    void purgeBurstUsesEveryAssimilationMediumWithinOneBudget() {
        float[] weights = new float[com.fish.mirebound.mud.SinkingMedium.COUNT];
        weights[com.fish.mirebound.mud.SinkingMedium.ASSIMILATION_SLIME.id()] = 0.54F;
        weights[com.fish.mirebound.mud.SinkingMedium.RED_QUICKSAND.id()] = 0.30F;
        weights[com.fish.mirebound.mud.SinkingMedium.TAR.id()] = 0.06F;

        int[] counts = MudSplashSystem.allocatePurgeMediumCounts(
                8, weights, com.fish.mirebound.mud.SinkingMedium.MUD);

        assertEquals(8, java.util.Arrays.stream(counts).sum());
        assertTrue(counts[com.fish.mirebound.mud.SinkingMedium.ASSIMILATION_SLIME.id()]
                > counts[com.fish.mirebound.mud.SinkingMedium.RED_QUICKSAND.id()]);
        assertTrue(counts[com.fish.mirebound.mud.SinkingMedium.RED_QUICKSAND.id()] >= 1);
        assertTrue(counts[com.fish.mirebound.mud.SinkingMedium.TAR.id()] >= 1);
    }

    @Test
    void emptyPurgePaletteUsesTheCapturedFallbackMedium() {
        int[] counts = MudSplashSystem.allocatePurgeMediumCounts(
                5, new float[0], com.fish.mirebound.mud.SinkingMedium.PEAT_BOG);

        assertEquals(5, java.util.Arrays.stream(counts).sum());
        assertEquals(5, counts[com.fish.mirebound.mud.SinkingMedium.PEAT_BOG.id()]);
    }

    @Test
    void exactDropletHitUsesThePlayerBoundingBoxSurface() {
        AABB player = new AABB(0.0D, 0.0D, 0.0D, 0.6D, 1.8D, 0.6D);

        MudSplashCollision.SweptHit hit = MudSplashCollision.sweepPlayer(
                player, Vec3.ZERO,
                new Vec3(-1.0D, 0.9D, 0.3D),
                new Vec3(1.0D, 0.9D, 0.3D));

        assertNotNull(hit);
        assertEquals(0.0D, hit.trajectoryPoint().x, 1.0E-9D);
        assertEquals(0.0D, hit.surfacePoint().x, 1.0E-9D);
    }

    @Test
    void nearMissOutsideTheRealPlayerBoxDoesNotStain() {
        AABB player = new AABB(0.0D, 0.0D, 0.0D, 0.6D, 1.8D, 0.6D);

        MudSplashCollision.SweptHit hit = MudSplashCollision.sweepPlayer(
                player, Vec3.ZERO,
                new Vec3(-1.0D, 1.81D, 0.3D),
                new Vec3(1.0D, 1.81D, 0.3D));

        assertEquals(null, hit);
    }

    @Test
    void relativeSweepCatchesAPlayerMovingAcrossAStationaryDroplet() {
        AABB currentPlayer = new AABB(1.0D, 0.0D, 0.0D, 1.6D, 1.8D, 0.6D);
        Vec3 droplet = new Vec3(0.90D, 0.9D, 0.3D);

        MudSplashCollision.SweptHit hit = MudSplashCollision.sweepPlayer(
                currentPlayer, new Vec3(1.0D, 0.0D, 0.0D),
                droplet, droplet);

        assertNotNull(hit);
        assertTrue(hit.time() > 0.0D && hit.time() < 1.0D);
        assertEquals(0.90D, hit.surfacePoint().x, 1.0E-9D);
    }
}
