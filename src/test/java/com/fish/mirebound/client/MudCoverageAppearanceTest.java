package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudCoverageAppearanceSnapshot;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

class MudCoverageAppearanceTest {
    @Test
    void visibleOpacityTracksCoverageWithoutAFullOpacityPlateau() {
        assertEquals(0.0F, MudSkinTextureCache.coverageStrengthOpacity(-0.1F), 1.0E-6F);
        assertEquals(0.18F, MudSkinTextureCache.coverageStrengthOpacity(0.18F), 1.0E-6F);
        assertEquals(0.50F, MudSkinTextureCache.coverageStrengthOpacity(0.50F), 1.0E-6F);
        assertEquals(1.0F, MudSkinTextureCache.coverageStrengthOpacity(1.1F), 1.0E-6F);
    }

    @Test
    void opacityVariationIsStableAndBounded() {
        float first = MudCoverageAppearance.opacityScale(18, 12, 7, 91, 0.75F, 0.40F);
        float repeated = MudCoverageAppearance.opacityScale(18, 12, 7, 91, 0.75F, 0.40F);

        assertEquals(first, repeated, 0.0F);
        assertTrue(first >= 0.45F && first <= 0.75F);
    }

    @Test
    void opacityVariationDoesNotChangeWhenDisabledAndVariesAcrossPixels() {
        assertEquals(0.65F,
                MudCoverageAppearance.opacityScale(18, 3, 4, 5, 0.65F, 0.0F), 0.0F);
        assertNotEquals(
                MudCoverageAppearance.opacityScale(18, 3, 4, 5, 1.0F, 0.40F),
                MudCoverageAppearance.opacityScale(18, 4, 4, 5, 1.0F, 0.40F));
    }

    @Test
    void brightnessVariationIsStableBoundedAndSpatiallySmooth() {
        float amount = 0.20F;
        float first = MudCoverageAppearance.brightnessScale(18, 7, 11, amount);
        assertEquals(first,
                MudCoverageAppearance.brightnessScale(18, 7, 11, amount), 0.0F);
        assertTrue(first >= 1.0F - amount && first <= 1.0F + amount);
        assertEquals(1.0F,
                MudCoverageAppearance.brightnessScale(18, 7, 11, 0.0F), 0.0F);

        float previous = MudCoverageAppearance.brightnessScale(18, 0, 9, amount);
        boolean varied = false;
        for (int x = 1; x < 24; x++) {
            float current = MudCoverageAppearance.brightnessScale(18, x, 9, amount);
            assertTrue(Math.abs(current - previous) < 0.16F,
                    "neighboring brightness samples must not form isolated light or dark pixels");
            varied |= Math.abs(current - previous) > 1.0E-5F;
            previous = current;
        }
        assertTrue(varied);
    }

    @Test
    void sandDefaultReflectsItsActualFinalOpacity() {
        assertEquals(0.70D,
                SinkingMedium.SOFT_QUICKSAND.defaultCoverageOpacity(),
                1.0E-9D);
    }

    @Test
    void configuredOpacityMapsDirectlyToFinalOverlayAlpha() {
        int original = FastColor.ABGR32.color(255, 180, 150, 120);
        int opaqueAppearance = MudCoverageAppearanceSnapshot.pack(1.0F, 1.0F, 0.0F);
        int clearAppearance = MudCoverageAppearanceSnapshot.pack(1.0F, 0.0F, 0.0F);

        int opaque = MudSkinTextureCache.overlayPixel(
                SinkingMedium.RED_QUICKSAND, opaqueAppearance, original, 3, 5, 1.0F, 17);
        int clear = MudSkinTextureCache.overlayPixel(
                SinkingMedium.RED_QUICKSAND, clearAppearance, original, 3, 5, 1.0F, 17);

        assertEquals(255, FastColor.ABGR32.alpha(opaque));
        assertEquals(0, FastColor.ABGR32.alpha(clear));
    }

    @Test
    void partialCoverageDirectlyReducesRenderedOpacity() {
        int original = FastColor.ABGR32.color(255, 180, 150, 120);
        int opaqueAppearance = MudCoverageAppearanceSnapshot.pack(1.0F, 1.0F, 0.0F);

        int full = MudSkinTextureCache.overlayPixel(
                SinkingMedium.MUD, opaqueAppearance, original, 3, 5, 1.0F, 17);
        int half = MudSkinTextureCache.overlayPixel(
                SinkingMedium.MUD, opaqueAppearance, original, 3, 5, 0.5F, 17);

        assertEquals(255, FastColor.ABGR32.alpha(full));
        assertEquals(128, FastColor.ABGR32.alpha(half), 1);
    }
}
