package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenMaskCameraPolicyTest {
    @Test
    void bodyOwnedMasksAreFirstPersonOnly() {
        assertTrue(ScreenMaskCameraPolicy.showsBodyOwnedMasks(true));
        assertFalse(ScreenMaskCameraPolicy.showsBodyOwnedMasks(false));
    }

    @Test
    void thirdPersonRequiresActualCameraContactForTheDynamicMask() {
        assertTrue(ScreenMaskCameraPolicy.showsDynamicMudMask(true, false));
        assertFalse(ScreenMaskCameraPolicy.showsDynamicMudMask(false, false));
        assertTrue(ScreenMaskCameraPolicy.showsDynamicMudMask(false, true));
    }
}
