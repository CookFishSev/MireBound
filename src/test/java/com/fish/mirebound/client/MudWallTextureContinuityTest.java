package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

class MudWallTextureContinuityTest {
    @Test
    void ordinaryWorldKeepsLogicalWallRowsInNativeImageOrder() {
        assertEquals(0, MudWallTextureCache.textureRow(0));
        assertEquals(15, MudWallTextureCache.textureRow(15));
    }

    @Test
    void physicalizedSableRetainsItsLocalFrameRowFlip() {
        assertEquals(15, MudWallTextureCache.textureRow(0, true));
        assertEquals(0, MudWallTextureCache.textureRow(15, true));
        assertEquals(0, MudWallTextureCache.textureRow(0, false));
        assertEquals(15, MudWallTextureCache.textureRow(15, false));
    }

    @Test
    void verticalTextureCoordinatesRemainAdjacentAcrossBlockBoundaries() {
        int lowerTop = MudWallTextureContinuity.textureSampleY(0, 15);
        int upperBottom = MudWallTextureContinuity.textureSampleY(16, 0);

        assertEquals(-1, upperBottom - lowerTop);
    }

    @Test
    void texturePhaseDependsOnMaterialIdentityRatherThanBlockOrStainIdentity() {
        int first = MudWallTextureContinuity.textureSalt(
                SinkingMedium.MUD, 0x1234ABCDL, Direction.NORTH);
        int repeated = MudWallTextureContinuity.textureSalt(
                SinkingMedium.MUD, 0x1234ABCDL, Direction.NORTH);
        int otherMedium = MudWallTextureContinuity.textureSalt(
                SinkingMedium.TAR, 0x1234ABCDL, Direction.NORTH);

        assertEquals(first, repeated);
        assertNotEquals(first, otherMedium);
    }

    @Test
    void samplingPhasesDoNotRepeatOnTheSixteenPixelTexturePeriod() {
        int seed = 0x56AC91F2;
        int first = MudWallTextureContinuity.samplePhase(0, 0, seed);
        int oneTextureWidthAway = MudWallTextureContinuity.samplePhase(1, 0, seed);

        assertEquals(first, MudWallTextureContinuity.samplePhase(0, 0, seed));
        assertNotEquals(first & 0xF, oneTextureWidthAway & 0xF);
        assertNotEquals(first >>> 8 & 0xF, oneTextureWidthAway >>> 8 & 0xF);
    }

    @Test
    void colorMixKeepsCoverageAlpha() {
        int first = FastColor.ABGR32.color(173, 30, 70, 110);
        int second = FastColor.ABGR32.color(255, 130, 160, 190);
        int mixed = MudWallTextureContinuity.blendRgbPreserveAlpha(first, second, 0.30F);

        assertEquals(173, FastColor.ABGR32.alpha(mixed));
        assertNotEquals(first, mixed);
    }

    @Test
    void lowOpacityInteriorGapsReceiveBoundedNeighborSupport() {
        int[] source = filledAlphaGrid(3, 110);
        source[4] = colorWithAlpha(20);
        int[] target = new int[9];
        boolean[] occupied = filledBooleans(9, true);
        boolean[] stable = new boolean[9];

        MudWallTextureContinuity.stabilizeLowOpacity(source, target, occupied, stable, 3);

        int alpha = FastColor.ABGR32.alpha(target[4]);
        assertTrue(alpha > 45, "a faint interior pixel should not become a visible slit");
        assertTrue(alpha < 110, "neighbor support must not flatten the stain into one opacity");
    }

    @Test
    void opacityContinuityNeverExpandsBeyondOccupiedPixels() {
        int[] source = filledAlphaGrid(3, 120);
        source[4] = 0;
        int[] target = new int[9];
        boolean[] occupied = filledBooleans(9, true);
        occupied[4] = false;
        boolean[] stable = new boolean[9];

        MudWallTextureContinuity.stabilizeLowOpacity(source, target, occupied, stable, 3);

        assertEquals(0, target[4]);
    }

    @Test
    void wallPixelAlphaUsesTransferredBodyOpacityOnlyOnce() {
        assertEquals(128,
                MudWallTextureCache.effectiveWallAlpha(0.50F, 1.0F), 1);
        assertEquals(64,
                MudWallTextureCache.effectiveWallAlpha(0.50F, 0.50F), 1);
        assertEquals(0,
                MudWallTextureCache.effectiveWallAlpha(0.0F, 1.0F), 0);
        assertEquals(255,
                MudWallTextureCache.effectiveWallAlpha(1.4F, 1.0F), 0);
    }

    @Test
    void isolatedContourPixelsKeepTheirOriginalOpacity() {
        int[] source = new int[9];
        source[4] = colorWithAlpha(37);
        source[3] = colorWithAlpha(120);
        int[] target = new int[9];
        boolean[] occupied = new boolean[9];
        occupied[3] = true;
        occupied[4] = true;
        boolean[] stable = new boolean[9];

        MudWallTextureContinuity.stabilizeLowOpacity(source, target, occupied, stable, 3);

        assertEquals(37, FastColor.ABGR32.alpha(target[4]));
    }

    @Test
    void opaquePixelsRemainUntouchedInTranslucentFallback() {
        int[] source = filledAlphaGrid(3, 30);
        source[4] = colorWithAlpha(240);
        int[] target = new int[9];
        boolean[] occupied = filledBooleans(9, true);
        boolean[] stable = new boolean[9];

        MudWallTextureContinuity.stabilizeLowOpacity(source, target, occupied, stable, 3);

        assertEquals(240, FastColor.ABGR32.alpha(target[4]));
    }

    @Test
    void continuitySupportDoesNotDarkenStrongPixels() {
        int[] source = filledAlphaGrid(3, 24);
        source[4] = colorWithAlpha(140);
        int[] target = new int[9];
        boolean[] occupied = filledBooleans(9, true);
        boolean[] stable = new boolean[9];

        MudWallTextureContinuity.stabilizeLowOpacity(source, target, occupied, stable, 3);

        assertEquals(140, FastColor.ABGR32.alpha(target[4]));
    }

    @Test
    void uniformlyFadingNeighborhoodRemainsMonotonic() {
        int[] strong = filledAlphaGrid(3, 120);
        strong[4] = colorWithAlpha(70);
        int[] faint = filledAlphaGrid(3, 60);
        faint[4] = colorWithAlpha(35);
        int[] strongTarget = new int[9];
        int[] faintTarget = new int[9];
        boolean[] occupied = filledBooleans(9, true);
        boolean[] stable = new boolean[9];

        MudWallTextureContinuity.stabilizeLowOpacity(strong, strongTarget, occupied, stable, 3);
        MudWallTextureContinuity.stabilizeLowOpacity(faint, faintTarget, occupied, stable, 3);

        assertTrue(FastColor.ABGR32.alpha(faintTarget[4])
                < FastColor.ABGR32.alpha(strongTarget[4]));
    }

    private static int[] filledAlphaGrid(int size, int alpha) {
        int[] colors = new int[size * size];
        java.util.Arrays.fill(colors, colorWithAlpha(alpha));
        return colors;
    }

    private static boolean[] filledBooleans(int size, boolean value) {
        boolean[] values = new boolean[size];
        java.util.Arrays.fill(values, value);
        return values;
    }

    private static int colorWithAlpha(int alpha) {
        return FastColor.ABGR32.color(alpha, 45, 85, 125);
    }
}
