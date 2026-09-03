package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

class ScreenMudSourceMixerTest {
    @Test
    void combinesRepeatedVisualSourcesBeforeTextureSampling() {
        ScreenMudSourceMixer mixer = new ScreenMudSourceMixer();
        mixer.add(SinkingMedium.MUD, 17L, 0.25F);
        mixer.add(SinkingMedium.MUD, 17L, 0.50F);
        mixer.add(SinkingMedium.TAR, 17L, 0.20F);

        assertEquals(2, mixer.size());
        assertEquals(SinkingMedium.MUD, mixer.medium(0));
        assertEquals(17L, mixer.visualSource(0));
        assertEquals(0.75F, mixer.weight(0), 1.0E-6F);
        assertEquals(SinkingMedium.TAR, mixer.medium(1));
    }

    @Test
    void resetReusesTheBoundedStorage() {
        ScreenMudSourceMixer mixer = new ScreenMudSourceMixer();
        mixer.add(SinkingMedium.MUD, 1L, 1.0F);
        mixer.reset();
        mixer.add(SinkingMedium.LIVING_SLIME, 2L, 0.4F);

        assertEquals(1, mixer.size());
        assertEquals(SinkingMedium.LIVING_SLIME, mixer.medium(0));
        assertEquals(0.4F, mixer.weight(0), 1.0E-6F);
    }
}
