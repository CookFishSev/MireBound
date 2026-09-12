package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

class MudWallStainVariationTest {
    private static final int GREEN = FastColor.ABGR32.color(190, 104, 168, 115);

    @Test
    void aStraightBandGainsUnevenEdgeHeightsWithoutAddingPollutionOutsideContact() {
        Set<Integer> heights = new HashSet<>();
        int[] rows = band(0, 0);
        for (int x = 0; x < 16; x++) {
            int highest = -1;
            for (int y = 0; y < 8; y++) {
                int color = MudWallStainVariation.apply(GREEN, x, y, x, y, 71, rows);
                if (FastColor.ABGR32.alpha(color) > 30) highest = y;
            }
            heights.add(highest);
            assertEquals(0, MudWallStainVariation.apply(0, x, 8, x, 8, 71, rows));
        }
        assertTrue(heights.size() > 1, "the top must not remain a straight stripe");
        assertTrue(heights.stream().allMatch(y -> y >= 5 && y <= 7));
    }

    @Test
    void spatialPatternMatchesAcrossDifferentTilingAndNegativeBlockCoordinates() {
        for (int worldX = -32; worldX < 32; worldX++) {
            int start = Math.floorDiv(worldX, 16) * 16;
            int x = worldX - start;
            int shiftedStart = Math.floorDiv(worldX - 5, 16) * 16 + 5;
            int shiftedX = worldX - shiftedStart;
            for (int y = 0; y < 10; y++) {
                int input = y < 8 ? GREEN : 0;
                assertEquals(MudWallStainVariation.apply(input, x, y, worldX, y, 71, band(start, 0)),
                        MudWallStainVariation.apply(input, shiftedX, y, worldX, y, 71, band(shiftedStart, 0)));
            }
        }
        for (int worldY = -8; worldY < 10; worldY++) {
            int start = Math.floorDiv(worldY, 16) * 16;
            int shifted = Math.floorDiv(worldY - 5, 16) * 16 + 5;
            assertEquals(MudWallStainVariation.apply(GREEN, 7, worldY - start, 7, worldY, 71, band(0, start)),
                    MudWallStainVariation.apply(GREEN, 7, worldY - shifted, 7, worldY, 71, band(0, shifted)));
        }
    }

    @Test
    void onlyTheBorderIsCutAndOpaqueInteriorsStayOpaque() {
        int[] rows = band(0, 0);
        for (int x = 0; x < 16; x++) {
            int opaque = FastColor.ABGR32.color(255, 104, 168, 115);
            assertEquals(255, FastColor.ABGR32.alpha(MudWallStainVariation.apply(opaque, x, 2, x, 2, 71, rows)));
        }
    }

    @Test
    void fadingNeverMakesThePatternDarkerOrStronger() {
        int[] rows = band(0, 0);
        for (int x = 0; x < 16; x++) {
            int previous = 255;
            for (int alpha = 255; alpha >= 0; alpha--) {
                int color = FastColor.ABGR32.color(alpha, 104, 168, 115);
                int varied = FastColor.ABGR32.alpha(MudWallStainVariation.apply(color, x, 7, x, 7, 71, rows));
                assertTrue(varied <= previous && varied <= alpha);
                previous = varied;
            }
            assertEquals(0, previous);
        }
    }

    @Test
    void neighboringRowsOnlyFillTheirOwnHaloWithoutWrappingBits() {
        for (int[] offset : new int[][] {{0, 0}, {-16, 0}, {16, 0}, {0, -16}, {0, 16}}) {
            int[] actual = new int[20];
            int[] expected = new int[20];
            for (int y = 0; y < 16; y++) {
                int bits = y % 2 == 0 ? 0xA53F : 0xC09B;
                MudWallStainVariation.addRow(actual, bits, y, offset[0], offset[1]);
                for (int x = 0; x < 16; x++) {
                    int px = x + offset[0] + 2;
                    int py = y + offset[1] + 2;
                    if ((bits & 1 << x) != 0 && px >= 0 && px < 20 && py >= 0 && py < 20) expected[py] |= 1 << px;
                }
            }
            for (int row = 0; row < 20; row++) assertEquals(expected[row], actual[row]);
        }
    }

    @Test
    void identicalPixelsSkipUploadAndClearingALayerCountsAsAChange() {
        try (NativeImage image = new NativeImage(16, 16, true)) {
            assertTrue(MudWallTextureCache.setPixelIfChanged(image, 5, 7, GREEN));
            assertFalse(MudWallTextureCache.setPixelIfChanged(image, 5, 7, GREEN));
            assertTrue(MudWallTextureCache.setPixelIfChanged(image, 5, 7, 0));
            assertEquals(0, image.getPixelRGBA(5, 7));
            assertFalse(MudWallTextureCache.setPixelIfChanged(image, 5, 7, 0));
        }
    }

    private static int[] band(int startU, int startV) {
        int[] rows = new int[20];
        for (int y = -2; y < 18; y++) {
            if (startV + y < 8) rows[y + 2] = (1 << 20) - 1;
        }
        return rows;
    }
}
