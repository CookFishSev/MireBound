package com.fish.mirebound.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MudVisionSamplingLayoutTest {
    @AfterEach
    void resetLayout() {
        MudVisionSamplingLayout.reset();
    }

    @Test
    void defaultsMatchTheCanonicalEightPixelFace() {
        MudVisionSamplingLayout.reset();

        assertEquals(8.0D, MudVisionSamplingLayout.widthPixels(), 1.0E-9D);
        assertEquals(8.0D, MudVisionSamplingLayout.heightPixels(), 1.0E-9D);
        assertEquals(0.0D, MudVisionSamplingLayout.bottomOffsetPixels(), 1.0E-9D);
        assertEquals(0.205D, MudVisionSamplingLayout.faceRadius(), 1.0E-9D);
        assertEquals(1.035D, MudVisionSamplingLayout.maxHeightFactor(), 1.0E-9D);
    }

    @Test
    void debugTuningIsClampedBeforeBothSidesReadIt() {
        MudVisionSamplingLayout.setWidthPixels(100.0D);
        MudVisionSamplingLayout.setHeightPixels(-5.0D);
        MudVisionSamplingLayout.setBottomOffsetPixels(20.0D);

        assertEquals(16.0D, MudVisionSamplingLayout.widthPixels(), 1.0E-9D);
        assertEquals(1.0D, MudVisionSamplingLayout.heightPixels(), 1.0E-9D);
        assertEquals(8.0D, MudVisionSamplingLayout.bottomOffsetPixels(), 1.0E-9D);
    }
}
