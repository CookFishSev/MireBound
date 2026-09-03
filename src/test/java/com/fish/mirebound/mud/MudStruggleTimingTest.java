package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MudStruggleTimingTest {
    @Test
    void serverChargeIsBoundedByAuthoritativeAccumulation() {
        assertEquals(0, MudStruggleTiming.serverChargeTicks(-1));
        assertEquals(0, MudStruggleTiming.serverChargeTicks(0));
        assertEquals(7, MudStruggleTiming.serverChargeTicks(7));
        assertEquals(20, MudStruggleTiming.serverChargeTicks(200));
    }

    @Test
    void cooldownScalesWithChargeAndCapsAtConfiguredMaximum() {
        assertEquals(0, MudStruggleTiming.cooldownTicks(0, 30));
        assertEquals(2, MudStruggleTiming.cooldownTicks(1, 30));
        assertEquals(15, MudStruggleTiming.cooldownTicks(10, 30));
        assertEquals(30, MudStruggleTiming.cooldownTicks(20, 30));
        assertEquals(30, MudStruggleTiming.cooldownTicks(200, 30));
    }

    @Test
    void zeroConfiguredMaximumDisablesCooldown() {
        assertEquals(0, MudStruggleTiming.cooldownTicks(20, 0));
    }
}
