package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SculkMireHudProgressTest {
    @Test
    void escapeProgressFillsFromZeroToOne() {
        assertEquals(0.0F, SculkMireHudProgress.escape(0, 100), 1.0E-6F);
        assertEquals(0.5F, SculkMireHudProgress.escape(50, 100), 1.0E-6F);
        assertEquals(1.0F, SculkMireHudProgress.escape(120, 100), 1.0E-6F);
    }

    @Test
    void restraintProgressCountsDownFromFull() {
        assertEquals(1.0F, SculkMireHudProgress.restraint(80, 80, 0.0F), 1.0E-6F);
        assertEquals(0.5F, SculkMireHudProgress.restraint(40, 80, 0.0F), 1.0E-6F);
        assertEquals(0.0F, SculkMireHudProgress.restraint(0, 80, 0.0F), 1.0E-6F);
    }
}
