package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StruggleHudLayoutTest {
    @Test
    void followsTheVisibleVanillaBottomMeter() {
        int survivalY = StruggleHudLayout.barY(240, true);
        int creativeY = StruggleHudLayout.barY(240, false);

        assertEquals(201, survivalY);
        assertEquals(208, creativeY);
        assertEquals(7, creativeY - survivalY);
    }

    @Test
    void followsAChangedVanillaStatusCursor() {
        assertEquals(190, StruggleHudLayout.barY(240, true, 50));
        assertEquals(197, StruggleHudLayout.barY(240, false, 50));
    }

    @Test
    void reservesOneVanillaStatusSlotPerCustomBar() {
        assertEquals(0, StruggleHudLayout.customBarLift(0));
        assertEquals(11, StruggleHudLayout.customBarLift(1));
        assertEquals(22, StruggleHudLayout.customBarLift(2));
    }

    @Test
    void tenderFleshReleaseWindowUsesTheRealThresholdWithHysteresis() {
        assertFalse(TenderFleshHudRenderer.updateReleaseWindow(false, 0.41F, 0.42F));
        assertTrue(TenderFleshHudRenderer.updateReleaseWindow(false, 0.42F, 0.42F));
        assertTrue(TenderFleshHudRenderer.updateReleaseWindow(true, 0.35F, 0.42F));
        assertFalse(TenderFleshHudRenderer.updateReleaseWindow(true, 0.34F, 0.42F));
    }
}
