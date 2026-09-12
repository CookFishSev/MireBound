package com.fish.mirebound.stain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class LegacyWallStainTest {
    @Test
    void savedRectangleBecomesPixelsWithoutLosingItsMaterialAgeOrFade() {
        var old = new MudFootprintBlockEntity.Entry(9, 0.5F, 0.5F, 0.006F, 0,
                Direction.NORTH, true, 0.25F, 0.5F, 0.8F, SinkingMedium.MUD,
                55, new long[0], 123, 500, 0.5F);
        var converted = MudFootprintBlockEntity.normalizeWallEntry(old);
        assertEquals(32, converted.wallPixels().length);
        assertEquals(9, converted.id());
        assertEquals(55, converted.visualSource());
        assertEquals(500, converted.expiresAt());
        assertEquals(1, converted.fade());
        for (long pixel : converted.wallPixels()) {
            assertEquals(0.4F, MudFootprintBlockEntity.wallPixelStrength(pixel), 1.0F / 255);
            assertEquals(123, MudFootprintBlockEntity.wallPixelCreatedAt(pixel));
            assertEquals(SinkingMedium.MUD, MudFootprintBlockEntity.wallPixelMedium(pixel));
            assertTrue(MudFootprintBlockEntity.wallPixelHorizontal(pixel) >= 6);
            assertTrue(MudFootprintBlockEntity.wallPixelHorizontal(pixel) <= 9);
        }
        assertSame(converted, MudFootprintBlockEntity.normalizeWallEntry(converted));
    }
}
