package com.fish.mirebound.stain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

class MudWallStainShapeTest {
    @Test
    void bridgesOnePixelHoleInsideContinuousBodyContact() {
        long[] pixels = new long[256];
        boolean[] direct = new boolean[256];
        put(pixels, direct, 7, 8, 0.82F);
        put(pixels, direct, 9, 8, 0.74F);

        MudWallStainSystem.bridgeSinglePixelWallGaps(pixels, direct, 40L);

        long bridge = pixels[8 | 8 << 4];
        assertTrue(bridge != 0L);
        assertEquals(SinkingMedium.MUD,
                MudFootprintBlockEntity.wallPixelMedium(bridge));
    }

    @Test
    void doesNotExpandAnOrdinaryTwoPixelCorner() {
        long[] pixels = new long[256];
        boolean[] direct = new boolean[256];
        put(pixels, direct, 7, 8, 0.82F);
        put(pixels, direct, 8, 7, 0.74F);

        MudWallStainSystem.bridgeSinglePixelWallGaps(pixels, direct, 40L);

        assertEquals(0L, pixels[8 | 8 << 4]);
    }

    private static void put(long[] pixels, boolean[] direct,
            int x, int y, float strength) {
        int cell = x | y << 4;
        pixels[cell] = MudFootprintBlockEntity.packWallPixel(
                x, y, strength, SinkingMedium.MUD, 40L);
        direct[cell] = true;
    }
}
