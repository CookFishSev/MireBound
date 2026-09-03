package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.assimilation.AssimilationProfile;
import org.junit.jupiter.api.Test;

class AssimilationScreenOverlayTest {
    @Test
    void screenCoordinatesMapToTheCanonicalFrontFaceOrientation() {
        assertEquals(7.0F, AssimilationScreenOverlay.mappedFaceRow(0.0F, 8), 1.0E-7F);
        assertEquals(0.0F, AssimilationScreenOverlay.mappedFaceRow(1.0F, 8), 1.0E-7F);
        assertEquals(7.0F, AssimilationScreenOverlay.mappedFaceColumn(0.0F, 8), 1.0E-7F);
        assertEquals(0.0F, AssimilationScreenOverlay.mappedFaceColumn(1.0F, 8), 1.0E-7F);
        assertEquals(3.5F, AssimilationScreenOverlay.mappedFaceRow(0.5F, 8), 1.0E-7F);
        assertEquals(3.5F, AssimilationScreenOverlay.mappedFaceColumn(0.5F, 8), 1.0E-7F);
    }

    @Test
    void mixedMediumBlendUsesACompactSmoothKernel() {
        float center = MudSkinTextureCache.assimilationBlendAxisWeight(0.0F);
        float neighbor = MudSkinTextureCache.assimilationBlendAxisWeight(1.0F);
        float outside = MudSkinTextureCache.assimilationBlendAxisWeight(1.66F);

        assertEquals(1.0F, center, 1.0E-7F);
        assertTrue(neighbor > 0.20F && neighbor < 0.50F,
                "adjacent skin pixels should form a visible but bounded transition");
        assertEquals(0.0F, outside, 1.0E-7F,
                "mixing must not blur across unrelated distant pixels");
    }

    @Test
    void screenCracksAppearGraduallyAndExpireWithoutLeavingHoles() {
        AssimilationScreenCracks.reset();
        boolean appeared = false;
        boolean stillGrowing = false;
        float previousOpening = 0.0F;
        for (int tick = 0; tick <= 130; tick++) {
            AssimilationScreenCracks.tick(7, 0x41A55A17, 1.0F, tick,
                    AssimilationProfile.DEFAULT);
            float opening = maximumOpening();
            appeared |= opening > 0.08F;
            stillGrowing |= previousOpening > 0.0F && opening > previousOpening + 0.02F;
            previousOpening = opening;
        }
        assertTrue(appeared, "a high assimilation mask should develop a visible crack");
        assertTrue(stillGrowing, "a crack should progressively open instead of popping in whole");

        for (int tick = 131; tick <= 260; tick++) {
            AssimilationScreenCracks.tick(7, 0x41A55A17, 0.0F, tick,
                    AssimilationProfile.DEFAULT);
        }
        assertEquals(0.0F, maximumOpening(), 1.0E-7F,
                "inactive assimilation must not retain a stale opening");
        AssimilationScreenCracks.reset();
    }

    @Test
    void crackCrossSectionIsWideInTheMiddleAndFineAtBothEnds() {
        float start = AssimilationScreenCracks.pathTaper(0.0F, 1.0F);
        float middle = AssimilationScreenCracks.pathTaper(0.5F, 1.0F);
        float end = AssimilationScreenCracks.pathTaper(1.0F, 1.0F);
        assertTrue(middle > start * 4.0F);
        assertTrue(middle > end * 4.0F);
        assertTrue(AssimilationScreenCracks.pathTaper(0.48F, 0.50F)
                < AssimilationScreenCracks.pathTaper(0.35F, 0.50F),
                "the actively splitting tip should remain finer than the opened trail");
    }

    private static float maximumOpening() {
        float maximum = 0.0F;
        for (int y = 0; y < 72; y++) {
            for (int x = 0; x < 128; x++) {
                maximum = Math.max(maximum, AssimilationScreenCracks.openness(
                        (x + 0.5F) / 128.0F, (y + 0.5F) / 72.0F));
            }
        }
        return maximum;
    }
}
