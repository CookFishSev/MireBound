package com.fish.mirebound.content.mudwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WetAdobeDryingTest {
    @Test
    void shelteredAdobeDoesNotDry() {
        WetAdobeDrying.Result result = WetAdobeDrying.update(
                1, false, false, 0.0D);

        assertEquals(1, result.dryness());
        assertFalse(result.complete());
    }

    @Test
    void clearSkyAdvancesUntilTheBlockCompletes() {
        assertEquals(2, WetAdobeDrying.update(
                1, true, false, 0.2D).dryness());

        WetAdobeDrying.Result complete = WetAdobeDrying.update(
                WetAdobeDrying.MAXIMUM_DRYNESS,
                true, false, 0.2D);
        assertEquals(WetAdobeDrying.MAXIMUM_DRYNESS,
                complete.dryness());
        assertTrue(complete.complete());
    }

    @Test
    void rainRehydratesWithoutGoingBelowZero() {
        assertEquals(1, WetAdobeDrying.update(
                2, true, true, 0.2D).dryness());
        assertEquals(0, WetAdobeDrying.update(
                0, true, true, 0.2D).dryness());
        assertFalse(WetAdobeDrying.update(
                3, true, true, 0.2D).complete());
    }

    @Test
    void transitionRollsAndInputsAreBounded() {
        assertEquals(3, WetAdobeDrying.update(
                9, true, false, 0.9D).dryness());
        assertEquals(0, WetAdobeDrying.update(
                -4, true, true, -1.0D).dryness());
    }
}
