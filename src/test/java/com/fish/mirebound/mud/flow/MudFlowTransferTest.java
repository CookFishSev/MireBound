package com.fish.mirebound.mud.flow;

import com.fish.mirebound.mud.MudPhysicsProfiles;
import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MudFlowTransferTest {
    private static final MudFlowProfile PROFILE = new MudFlowProfile(
            true, 4, 3, 3, 2, 12, 128);

    @Test
    void allMediaKeepFiniteFlowDisabledByDefault() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            assertFalse(MudFlowProfile.fromValues(
                    MudPhysicsProfiles.defaultValues(medium)).enabled(), medium.toString());
        }
    }

    @Test
    void downwardTransferHonorsSourceTargetAndRateLimits() {
        assertEquals(3, MudFlowTransfer.downward(16, 0, 3));
        assertEquals(1, MudFlowTransfer.downward(16, 15, 3));
        assertEquals(2, MudFlowTransfer.downward(2, 0, 3));
        assertEquals(0, MudFlowTransfer.downward(16, 16, 3));
    }

    @Test
    void horizontalTransferLevelsWithoutCreatingVolume() {
        int source = 12;
        int target = 2;
        int moved = MudFlowTransfer.horizontal(source, target, PROFILE);

        assertEquals(3, moved);
        assertEquals(source + target, source - moved + target + moved);
    }

    @Test
    void horizontalThresholdPreventsThinOrAlreadyLevelCellsFromSpreading() {
        assertEquals(0, MudFlowTransfer.horizontal(2, 0, PROFILE));
        assertEquals(0, MudFlowTransfer.horizontal(8, 7, PROFILE));
        assertEquals(1, MudFlowTransfer.horizontal(8, 6, PROFILE));
    }

    @Test
    void thinTailStopsInsteadOfCrawlingAcrossTheWorld() {
        assertEquals(0, MudFlowTransfer.horizontal(2, 0, PROFILE));
        assertEquals(0, MudFlowTransfer.horizontal(1, 0, PROFILE));
    }

    @Test
    void horizontalTransferNeverOverfillsItsTarget() {
        assertEquals(0, MudFlowTransfer.horizontal(16, 16, PROFILE));
        assertEquals(0, MudFlowTransfer.horizontal(16, 15, PROFILE));
    }
}
