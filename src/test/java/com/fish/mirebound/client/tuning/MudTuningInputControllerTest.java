package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudTuningInputControllerTest {
    @Test
    void modeModifierWinsWhenBothScrollModifiersAreHeld() {
        assertEquals(MudTuningInputController.ScrollIntent.SWITCH_MODE,
                MudTuningInputController.scrollIntent(
                        MudTuningWandMode.RANGE, true, true));
        assertEquals(MudTuningInputController.ScrollIntent.SWITCH_MODE,
                MudTuningInputController.scrollIntent(
                        MudTuningWandMode.SUMMON, true, true));
    }

    @Test
    void nudgeModifierOnlyOwnsModesWithScrollActions() {
        assertEquals(MudTuningInputController.ScrollIntent.NUDGE_RANGE,
                MudTuningInputController.scrollIntent(
                        MudTuningWandMode.RANGE, false, true));
        assertEquals(MudTuningInputController.ScrollIntent.ADJUST_PLACEMENT,
                MudTuningInputController.scrollIntent(
                        MudTuningWandMode.SUMMON, false, true));
        assertEquals(MudTuningInputController.ScrollIntent.ADJUST_PLACEMENT,
                MudTuningInputController.scrollIntent(
                        MudTuningWandMode.GENERATION, false, true));
        assertEquals(MudTuningInputController.ScrollIntent.NONE,
                MudTuningInputController.scrollIntent(
                        MudTuningWandMode.SINGLE, false, true));
    }

    @Test
    void conversionUnlockRequiresBothButtonsForConsecutiveActiveTicks() {
        assertEquals(1, MudTuningInputController.advanceConversionUnlock(
                0, true, true));
        assertEquals(0, MudTuningInputController.advanceConversionUnlock(
                12, false, true));
        assertEquals(0, MudTuningInputController.advanceConversionUnlock(
                12, true, false));
    }

    @Test
    void conversionUnlockProgressStopsAtRequiredDuration() {
        int progress = 0;
        for (int tick = 0;
                tick < MudTuningInputController.CONVERSION_UNLOCK_TICKS + 8; tick++) {
            progress = MudTuningInputController.advanceConversionUnlock(
                    progress, true, true);
        }
        assertEquals(MudTuningInputController.CONVERSION_UNLOCK_TICKS, progress);
    }

    @Test
    void unrestrictedUnlockFeedbackPulsesMoreOften() {
        int standardPulses = 0;
        int unrestrictedPulses = 0;
        for (int tick = 1; tick < MudTuningInputController.CONVERSION_UNLOCK_TICKS; tick++) {
            if (MudTuningInputController.shouldPlayConversionUnlockStep(
                    tick - 1, tick, false)) {
                standardPulses++;
            }
            if (MudTuningInputController.shouldPlayConversionUnlockStep(
                    tick - 1, tick, true)) {
                unrestrictedPulses++;
            }
        }

        assertNotEquals(0, standardPulses);
        assertTrue(unrestrictedPulses > standardPulses);
    }

    @Test
    void unrestrictedProgressShakeIsIdleAtZeroAndIntensifiesNearCompletion() {
        assertEquals(0, MudTuningInputController.unrestrictedUnlockShake(0, false));
        int earlyMaximum = 0;
        int lateMaximum = 0;
        for (int tick = 1; tick < MudTuningInputController.CONVERSION_UNLOCK_TICKS; tick++) {
            int amount = Math.abs(MudTuningInputController
                    .unrestrictedUnlockShake(tick, false));
            if (tick < MudTuningInputController.CONVERSION_UNLOCK_TICKS * 3 / 4) {
                earlyMaximum = Math.max(earlyMaximum, amount);
            } else {
                lateMaximum = Math.max(lateMaximum, amount);
            }
        }
        assertEquals(1, earlyMaximum);
        assertEquals(2, lateMaximum);
    }
}
