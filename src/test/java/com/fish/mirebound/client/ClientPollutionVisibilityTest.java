package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientPollutionVisibilityTest {
    @Test
    void suppressesSpectatorAndDetachedCameraIndependently() {
        assertFalse(ClientPollutionVisibility.suppressed(false, false));
        assertTrue(ClientPollutionVisibility.suppressed(true, false));
        assertTrue(ClientPollutionVisibility.suppressed(false, true));
        assertTrue(ClientPollutionVisibility.suppressed(true, true));
    }

    @Test
    void detachedCameraSuppressionIsReservedForSamplingAndViewEffects() {
        assertFalse(ClientPollutionVisibility.renderSuppressed(false));
        assertTrue(ClientPollutionVisibility.renderSuppressed(true));
    }
}
