package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import org.junit.jupiter.api.Test;

class ScreenMudOverlayTest {
    @Test
    void persistentScreenRingReadsOnlyTheFrontOfTheHead() {
        assertTrue(ScreenMudOverlay.contributesToPersistentScreenMask(
                MudBodyPart.HEAD, MudSurface.FRONT));

        for (MudSurface surface : MudSurface.values()) {
            if (surface != MudSurface.FRONT) {
                assertFalse(ScreenMudOverlay.contributesToPersistentScreenMask(
                        MudBodyPart.HEAD, surface));
            }
        }
        assertFalse(ScreenMudOverlay.contributesToPersistentScreenMask(
                MudBodyPart.BODY, MudSurface.FRONT));
    }

    @Test
    void visionAlphaInterpolatesOnlyAcrossTheTickThatChangedIt() {
        assertEquals(0.75F, ScreenMudOverlay.interpolatedVisionAlpha(
                1.0F, 0.5F, 0.5F, true), 1.0E-6F);
        assertEquals(0.5F, ScreenMudOverlay.interpolatedVisionAlpha(
                1.0F, 0.5F, 0.0F, false), 1.0E-6F);
    }

    @Test
    void visionTextureStatesCrossfadeAtRenderFramePrecision() {
        assertEquals(0.0F,
                ScreenMudOverlay.visionTextureTransitionProgress(
                        100.0D, 100.0D, 2.0F), 1.0E-6F);
        assertEquals(0.5F,
                ScreenMudOverlay.visionTextureTransitionProgress(
                        101.0D, 100.0D, 2.0F), 1.0E-6F);
        assertEquals(1.0F,
                ScreenMudOverlay.visionTextureTransitionProgress(
                        102.0D, 100.0D, 2.0F), 1.0E-6F);

        assertEquals(0x10203040,
                ScreenMudOverlay.interpolateNativeColor(
                        0x10203040, 0x90A0B0C0, 0.0F));
        assertEquals(0x90A0B0C0,
                ScreenMudOverlay.interpolateNativeColor(
                        0x10203040, 0x90A0B0C0, 1.0F));
        assertEquals(0x50607080,
                ScreenMudOverlay.interpolateNativeColor(
                        0x10203040, 0x90A0B0C0, 0.5F));
    }
}
