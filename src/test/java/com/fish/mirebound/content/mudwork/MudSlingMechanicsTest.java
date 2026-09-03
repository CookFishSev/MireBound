package com.fish.mirebound.content.mudwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudSlingMechanicsTest {
    @Test
    void chargeCurveIsMonotonicAndClamped() {
        float previous = MudSlingMechanics.chargePower(-20);
        assertEquals(0.0F, previous);
        for (int ticks = 0; ticks <= 80; ticks++) {
            float current = MudSlingMechanics.chargePower(ticks);
            assertTrue(current >= previous);
            assertTrue(current >= 0.0F && current <= 1.0F);
            previous = current;
        }
        assertEquals(1.0F, MudSlingMechanics.chargePower(80));
    }

    @Test
    void slingHasAUsefulButBoundedPayloadEnvelope() {
        assertTrue(MudSlingMechanics.launchSpeed(1.0F, true)
                > MudSlingMechanics.launchSpeed(1.0F, false));
        assertEquals(3, MudSlingMechanics.fragmentCount(0.0F, true));
        assertEquals(8, MudSlingMechanics.fragmentCount(1.0F, true));
        assertEquals(3, MudSlingMechanics.fragmentCount(1.0F, false));
        assertEquals(7, MudSlingMechanics.cooldownTicks(0.0F, true));
        assertEquals(12, MudSlingMechanics.cooldownTicks(1.0F, true));
        assertEquals(10, MudSlingMechanics.cooldownTicks(1.0F, false));
    }

    @Test
    void outOfRangePowerCannotEscapeTheEnvelope() {
        assertEquals(MudSlingMechanics.launchSpeed(0.0F, true),
                MudSlingMechanics.launchSpeed(-5.0F, true));
        assertEquals(MudSlingMechanics.launchSpeed(1.0F, true),
                MudSlingMechanics.launchSpeed(5.0F, true));
        assertEquals(8, MudSlingMechanics.fragmentCount(5.0F, true));
    }
}
