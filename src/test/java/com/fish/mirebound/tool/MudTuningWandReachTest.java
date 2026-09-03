package com.fish.mirebound.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MudTuningWandReachTest {
    @Test
    void syncedModifierRecoversRangePastVanillaAttributeCap() {
        assertEquals(128.0D,
                MudTuningWandReach.configuredInteractionRange(
                        64.0D, 123.5D, true));
    }

    @Test
    void missingModifierUsesVisibleVanillaRange() {
        assertEquals(32.0D,
                MudTuningWandReach.configuredInteractionRange(
                        32.0D, 0.0D, false));
    }
}
