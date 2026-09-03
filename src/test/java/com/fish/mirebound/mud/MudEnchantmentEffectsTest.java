package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudEnchantmentEffectsTest {
    @Test
    void walkRestorationRecoversOnlyLostMovement() {
        assertEquals(0.60D, MudEnchantmentEffects.restoreWalkScale(0.50D, 0.20D), 1.0E-9D);
        assertEquals(1.0D, MudEnchantmentEffects.restoreWalkScale(1.0D, 0.75D), 1.0E-9D);
        assertTrue(MudEnchantmentEffects.restoreWalkScale(0.02D, 0.48D) < 0.50D);
    }
}
