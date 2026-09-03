package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudTuningWandUiSoundsTest {
    @Test
    void volumePitchRisesSmoothlyAcrossTheFullSlider() {
        float previous = MudTuningWandUiSounds.volumePitch(1);
        assertEquals(0.68F, previous, 1.0E-6F);
        for (int volume = 2; volume <= 50; volume++) {
            float current = MudTuningWandUiSounds.volumePitch(volume);
            assertTrue(current > previous);
            previous = current;
        }
        assertTrue(previous > 1.65F);
        assertTrue(previous < 1.70F);
    }

    @Test
    void volumePitchClampsOutsideTheSliderRange() {
        assertEquals(MudTuningWandUiSounds.volumePitch(1),
                MudTuningWandUiSounds.volumePitch(-20), 1.0E-6F);
        assertEquals(MudTuningWandUiSounds.volumePitch(50),
                MudTuningWandUiSounds.volumePitch(80), 1.0E-6F);
    }
}
