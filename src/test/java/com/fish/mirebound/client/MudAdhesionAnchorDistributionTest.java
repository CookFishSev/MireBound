package com.fish.mirebound.client;

import com.fish.mirebound.mud.AdhesionStrandProfile;
import com.fish.mirebound.mud.SinkingMedium;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudAdhesionAnchorDistributionTest {
    @Test
    void fullBodyCandidatesDistributeAboveTheFootZone() {
        double minimum = 0.0D;
        double maximum = 1.45D;
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 8; index++) {
            double target = MudSurfaceEffectManager.adhesionAnchorTargetHeight(
                    index, 8, minimum, maximum, 1234L + index);
            lowest = Math.min(lowest, target);
            highest = Math.max(highest, target);
        }

        assertTrue(lowest >= 0.70D, "no body-ring anchor should collapse back to the feet");
        assertTrue(highest >= 1.20D, "the ring should reach upper dirty body pixels");
        assertTrue(highest - lowest >= 0.35D, "anchors should cover more than one height band");
    }

    @Test
    void shallowDirtyRegionKeepsTargetsInsideItsActualCoverage() {
        for (int index = 0; index < 6; index++) {
            double target = MudSurfaceEffectManager.adhesionAnchorTargetHeight(
                    index, 6, 0.05D, 0.42D, 9876L + index);
            assertTrue(target >= 0.05D && target <= 0.42D);
        }
    }

    @Test
    void stableContactStartsWithAVisibleBatchThenStaggersTheRemainder() {
        assertEquals(0, MudSurfaceEffectManager.adhesionSpawnCapacity(8, 8, 2, 4, 10));
        assertEquals(4, MudSurfaceEffectManager.adhesionSpawnCapacity(9, 8, 2, 4, 10));
        assertEquals(4, MudSurfaceEffectManager.adhesionSpawnCapacity(10, 8, 2, 4, 10));
        assertEquals(5, MudSurfaceEffectManager.adhesionSpawnCapacity(11, 8, 2, 4, 10));
        assertEquals(10, MudSurfaceEffectManager.adhesionSpawnCapacity(100, 8, 2, 4, 10));
    }

    @Test
    void earlyRingSlotsMaximizeAngularSeparation() {
        assertEquals(0, MudSurfaceEffectManager.adhesionRingSlot(0));
        assertEquals(8, MudSurfaceEffectManager.adhesionRingSlot(1));
        assertEquals(4, MudSurfaceEffectManager.adhesionRingSlot(2));
        assertEquals(12, MudSurfaceEffectManager.adhesionRingSlot(3));
        boolean[] occupied = new boolean[16];
        for (int index = 0; index < 16; index++) {
            int slot = MudSurfaceEffectManager.adhesionRingSlot(index);
            assertFalse(occupied[slot]);
            occupied[slot] = true;
        }
    }

    @Test
    void sheetRendererAcceptsTheEntireRetainedStrandPool() {
        assertEquals(MudSurfaceEffectManager.MAX_ADHESION_STRANDS_PER_PLAYER,
                MudSurfaceEffectRenderer.sheetRibCapacity());
        assertEquals(16, MudSurfaceEffectRenderer.sheetRibCapacity());
    }

    @Test
    void historicalPollutionCannotBecomeANewSessionAnchorByItself() {
        assertFalse(MudSurfaceEffectManager.adhesionSessionCandidate(
                false, 1.0F, 0.15D, true));
        assertFalse(MudSurfaceEffectManager.adhesionSessionCandidate(
                true, 1.0F, 0.15D, false));
        assertTrue(MudSurfaceEffectManager.adhesionSessionCandidate(
                true, 0.15F, 0.15D, true));
    }

    @Test
    void transientContactMissDoesNotResetTheAdhesionSession() {
        assertFalse(MudSurfaceEffectManager.adhesionSessionShouldReset(
                false, 1, 24, false));
        assertFalse(MudSurfaceEffectManager.adhesionSessionShouldReset(
                false, 24, 24, false));
        assertTrue(MudSurfaceEffectManager.adhesionSessionShouldReset(
                false, 25, 24, false));
        assertTrue(MudSurfaceEffectManager.adhesionSessionShouldReset(
                true, 0, 24, true));
    }

    @Test
    void retainedBridgeKeepsTheProfileThatCreatedItAfterLeavingLocalMud() {
        AdhesionStrandProfile captured = AdhesionStrandProfile.defaultsFor(SinkingMedium.TAR);
        AdhesionStrandProfile disabledWorldProfile =
                AdhesionStrandProfile.defaultsFor(SinkingMedium.MUD);

        assertSame(captured, MudSurfaceEffectManager.adhesionLifecycleProfile(
                captured, disabledWorldProfile, true));
        assertSame(disabledWorldProfile, MudSurfaceEffectManager.adhesionLifecycleProfile(
                captured, disabledWorldProfile, false));
    }
}
