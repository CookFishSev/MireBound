package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudSurfaceCellBudgetTest {
    @Test
    void preservesTheOriginalThresholdNearThePlayer() {
        double renderDistanceSquared = 32.0D * 32.0D;

        assertEquals(MudSurfaceCellBudget.NEAR_VISUAL_HEIGHT_EPSILON,
                MudSurfaceCellBudget.visualHeightEpsilon(0.0D, renderDistanceSquared),
                1.0E-12D);
        assertEquals(MudSurfaceCellBudget.NEAR_VISUAL_HEIGHT_EPSILON,
                MudSurfaceCellBudget.visualHeightEpsilon(8.0D * 8.0D,
                        renderDistanceSquared),
                1.0E-12D);
    }

    @Test
    void settledPixelsDisappearBeforeBecomingEffectivelyCoplanar() {
        assertEquals(0.16D / 16.0D,
                MudSurfaceCellBudget.NEAR_VISUAL_HEIGHT_EPSILON,
                1.0E-12D);
    }

    @Test
    void raisesTheThresholdSmoothlyWithDistance() {
        double renderDistanceSquared = 32.0D * 32.0D;
        double near = MudSurfaceCellBudget.visualHeightEpsilon(
                8.0D * 8.0D, renderDistanceSquared);
        double middle = MudSurfaceCellBudget.visualHeightEpsilon(
                20.0D * 20.0D, renderDistanceSquared);
        double far = MudSurfaceCellBudget.visualHeightEpsilon(
                renderDistanceSquared, renderDistanceSquared);

        assertTrue(middle > near);
        assertTrue(far > middle);
    }

    @Test
    void allocationStopsRemoteGrowthWithoutEvictingRetainedState() {
        int soft = MudSurfaceCellBudget.globalSoftLimit(12_288, 32);
        int hard = MudSurfaceCellBudget.globalHardLimit(soft, 12_288);

        assertFalse(MudSurfaceCellBudget.canAllocateSurfaceCell(
                false, 100, 12_288, soft, soft, hard));
        assertTrue(MudSurfaceCellBudget.canAllocateSurfaceCell(
                true, 100, 12_288, soft, soft, hard));
        assertFalse(MudSurfaceCellBudget.canAllocateSurfaceCell(
                true, 100, 12_288, hard, soft, hard));
        assertFalse(MudSurfaceCellBudget.canAllocateSurfaceCell(
                true, 12_288, 12_288, 0, soft, hard));
    }

    @Test
    void globalBudgetRetainsTwoFullImprintsAndALocalBurst() {
        int soft = MudSurfaceCellBudget.globalSoftLimit(12_288, 32);
        int hard = MudSurfaceCellBudget.globalHardLimit(soft, 12_288);

        assertEquals(24576, soft);
        assertEquals(30720, hard);
    }

    @Test
    void renderingKeepsTwoFullImprintsWithoutScalingPerPlayerDetail() {
        assertEquals(12288, MudSurfaceCellBudget.globalRenderLimit(12_288, 32));
        assertEquals(6144, MudSurfaceCellBudget.globalRenderLimit(12_288, 1));
        assertEquals(6144, MudSurfaceCellBudget.renderCellLimitPerHole(12_288));
    }

    @Test
    void remoteUpdatesAreStaggeredWithoutThrottlingNearbyOrLocalPlayers() {
        assertEquals(1, MudSurfaceCellBudget.updateIntervalTicks(false, 12.0D * 12.0D));
        assertEquals(2, MudSurfaceCellBudget.updateIntervalTicks(false, 20.0D * 20.0D));
        assertEquals(4, MudSurfaceCellBudget.updateIntervalTicks(false, 30.0D * 30.0D));
        assertEquals(1, MudSurfaceCellBudget.updateIntervalTicks(true, 60.0D * 60.0D));

        int updates = 0;
        for (long tick = 0L; tick < 8L; tick++) {
            if (MudSurfaceCellBudget.scheduledUpdate(tick, 3, 4)) {
                updates++;
            }
        }
        assertEquals(2, updates);
    }
}
