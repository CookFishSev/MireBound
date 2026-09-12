package com.fish.mirebound.client.tooltip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WandLoreAnimationTest {
    @Test
    void charactersRevealInOrderWithoutFlashingBeforeTheirStart() {
        assertEquals(0, WandLoreAnimation.opacity(0, 0));
        assertTrue(WandLoreAnimation.opacity(.3, 0) > WandLoreAnimation.opacity(.3, 3));
        assertEquals(0, WandLoreAnimation.opacity(.3, 20));
        assertEquals(1, WandLoreAnimation.opacity(10, 40));
        for (int i = 0; i < 40; i++) {
            double previous = 0;
            for (double t = 0; t < 4; t += .01) {
                double value = WandLoreAnimation.opacity(t, i);
                assertTrue(value >= previous && value <= 1);
                previous = value;
            }
        }
    }

    @Test
    void continuousTooltipRebuildsDoNotRestartTheReveal() {
        var session = new WandLoreAnimation.Session();
        assertEquals(0, session.observe(false, 1000));
        assertEquals(0, session.observe(true, 1016));
        assertEquals(.016, session.observe(true, 1032), 1e-9);
        assertEquals(.216, session.observe(true, 1232), 1e-9);
        session.observe(false, 1250);
        assertEquals(0, session.observe(true, 1266));
        assertEquals(0, session.observe(true, 2000));
    }

    @Test
    void glyphsOnlyDimAndFloatWithinOnePixelWithContinuousMotion() {
        boolean dimmed = false;
        for (int i = 0; i < 40; i++) {
            for (double t = 0; t < 10; t += .02) {
                int gray = WandLoreAnimation.gray(t, i);
                assertTrue(gray >= 102 && gray <= 166);
                dimmed |= gray < 130;
                assertTrue(Math.abs(WandLoreAnimation.floatOffset(t, i)) <= .72F);
                assertTrue(Math.abs(WandLoreAnimation.gray(t, i)
                        - WandLoreAnimation.gray(t + .001, i)) <= 1);
                assertEquals(gray, WandLoreAnimation.gray(t, i));
            }
        }
        assertTrue(dimmed);
    }
}
