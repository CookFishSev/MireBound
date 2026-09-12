package com.fish.mirebound.client.coverage;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EquipmentSurfaceCaptureTimingTest {
    @Test
    void firstPersonSamplingCanStartOnEitherTickParity() {
        for (long first : new long[] {10, 11}) {
            assertTrue(EquipmentSurfaceRenderer.fullSurfaceCaptureDue(first, null));
            assertFalse(EquipmentSurfaceRenderer.fullSurfaceCaptureDue(first, first));
            assertFalse(EquipmentSurfaceRenderer.fullSurfaceCaptureDue(first + 1, first));
            assertTrue(EquipmentSurfaceRenderer.fullSurfaceCaptureDue(first + 2, first));
        }
    }

    @Test
    void clockResetDoesNotSuppressTheNextCapture() {
        assertTrue(EquipmentSurfaceRenderer.fullSurfaceCaptureDue(0, 100L));
    }
}
