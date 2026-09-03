package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SummonSelectionScreenTest {
    @Test
    void oneSummonSlotSitsAboveTheCrosshair() {
        SummonSelectionScreen.SlotBounds bounds = SummonSelectionScreen.slotBounds(
                0, 1, 160, 120, 72, 46);

        assertEquals(137, bounds.left());
        assertEquals(25, bounds.top());
        assertEquals(183, bounds.right());
        assertEquals(71, bounds.bottom());
    }

    @Test
    void radialHitTestingSelectsOnlyTheVisibleSlots() {
        assertEquals(0, SummonSelectionScreen.slotAt(
                160, 48, 160, 120, 72, 46, 4));
        assertEquals(1, SummonSelectionScreen.slotAt(
                232, 120, 160, 120, 72, 46, 4));
        assertEquals(-1, SummonSelectionScreen.slotAt(
                160, 120, 160, 120, 72, 46, 4));
    }
}
