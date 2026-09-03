package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ArmorTextureMudDataTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "example", "textures/models/armor/custom.png");

    @Test
    void storesExactTexturePixelsAndMediums() {
        ArmorTextureMudData.Builder builder = ArmorTextureMudData.EMPTY.toBuilder();
        assertTrue(builder.mark(TEXTURE, 32, 16, 37, 0.75F, SinkingMedium.MIRE));
        assertTrue(builder.mark(TEXTURE, 32, 16, 91, 1.0F, SinkingMedium.TAR));

        ArmorTextureMudData data = builder.build();
        ArmorTextureMudData.Layer layer = data.layer(TEXTURE, 32, 16);
        assertNotNull(layer);
        Map<Integer, Pixel> pixels = collect(layer);
        assertEquals(2, pixels.size());
        assertEquals(SinkingMedium.MIRE, pixels.get(37).medium);
        assertEquals(0.75F, pixels.get(37).coverage, 0.005F);
        assertEquals(SinkingMedium.TAR, pixels.get(91).medium);
    }

    @Test
    void preservesVisualSourceThroughBuildAndWashing() {
        long source = 0x8123456789ABCDEFL;
        ArmorTextureMudData.Builder builder = ArmorTextureMudData.EMPTY.toBuilder();
        assertTrue(builder.mark(TEXTURE, 32, 16, 37, 0.75F,
                SinkingMedium.MIRE, source));

        ArmorTextureMudData data = builder.build();
        ArmorTextureMudData.Layer layer = data.layer(TEXTURE, 32, 16);
        assertNotNull(layer);
        assertEquals(source, layer.visualSourceAt(37));

        ArmorTextureMudData.Builder washing = data.toBuilder();
        assertTrue(washing.wash(TEXTURE, 32, 16, 37, 0.10F, 0.0F));
        ArmorTextureMudData.Layer washed = washing.build().layer(TEXTURE, 32, 16);
        assertNotNull(washed);
        assertEquals(source, washed.visualSourceAt(37));
    }

    @Test
    void keepsTexturesAndDimensionsIsolated() {
        ResourceLocation second = ResourceLocation.fromNamespaceAndPath(
                "example", "textures/models/armor/second.png");
        ArmorTextureMudData.Builder builder = ArmorTextureMudData.EMPTY.toBuilder();
        builder.mark(TEXTURE, 16, 16, 4, 1.0F, SinkingMedium.MUD);
        builder.mark(TEXTURE, 32, 32, 4, 1.0F, SinkingMedium.SILT);
        builder.mark(second, 16, 16, 4, 1.0F, SinkingMedium.TAR);

        ArmorTextureMudData data = builder.build();
        assertEquals(3, data.layers().size());
        assertEquals(SinkingMedium.MUD, collect(data.layer(TEXTURE, 16, 16)).get(4).medium);
        assertEquals(SinkingMedium.SILT, collect(data.layer(TEXTURE, 32, 32)).get(4).medium);
        assertEquals(SinkingMedium.TAR, collect(data.layer(second, 16, 16)).get(4).medium);
    }

    @Test
    void washingIsLocalAndEventuallyRemovesLayer() {
        ArmorTextureMudData.Builder initial = ArmorTextureMudData.EMPTY.toBuilder();
        initial.mark(TEXTURE, 16, 16, 7, 1.0F, SinkingMedium.MUD);
        initial.mark(TEXTURE, 16, 16, 8, 1.0F, SinkingMedium.MUD);
        ArmorTextureMudData current = initial.build();

        for (int step = 0; step < 20; step++) {
            ArmorTextureMudData.Builder washing = current.toBuilder();
            washing.wash(TEXTURE, 16, 16, 7, 0.08F, 0.00025F);
            current = washing.build();
        }

        ArmorTextureMudData.Layer layer = current.layer(TEXTURE, 16, 16);
        assertNotNull(layer);
        Map<Integer, Pixel> pixels = collect(layer);
        assertFalse(pixels.containsKey(7));
        assertTrue(pixels.containsKey(8));

        ArmorTextureMudData.Builder finalWash = current.toBuilder();
        for (int step = 0; step < 20; step++) {
            finalWash.wash(TEXTURE, 16, 16, 8, 0.08F, 0.00025F);
        }
        assertSame(ArmorTextureMudData.EMPTY, finalWash.build());
    }

    @Test
    void tinyWashAmountsCannotStallAtLowCoverage() {
        ArmorTextureMudData.Builder initial = ArmorTextureMudData.EMPTY.toBuilder();
        initial.mark(TEXTURE, 16, 16, 23, 3.0F / 255.0F, SinkingMedium.MUD);
        ArmorTextureMudData current = initial.build();

        for (int step = 0; step < 4 && !current.isEmpty(); step++) {
            ArmorTextureMudData.Builder washing = current.toBuilder();
            assertTrue(washing.wash(TEXTURE, 16, 16, 23, 0.00001F, 0.0F));
            current = washing.build();
        }

        assertSame(ArmorTextureMudData.EMPTY, current);
    }

    @Test
    void rejectsOutOfRangePixelsAndDimensions() {
        ArmorTextureMudData.Builder builder = ArmorTextureMudData.EMPTY.toBuilder();
        assertFalse(builder.mark(TEXTURE, 16, 16, -1, 1.0F, SinkingMedium.MUD));
        assertFalse(builder.mark(TEXTURE, 16, 16, 256, 1.0F, SinkingMedium.MUD));
        assertFalse(builder.mark(TEXTURE, ArmorTextureMudData.MAX_DIMENSION + 1, 16,
                0, 1.0F, SinkingMedium.MUD));
        assertSame(ArmorTextureMudData.EMPTY, builder.build());
    }

    @Test
    void coverageFractionUsesTextureAreaRatherThanOnlyDirtyPixels() {
        ArmorTextureMudData.Builder builder = ArmorTextureMudData.EMPTY.toBuilder();
        builder.mark(TEXTURE, 4, 4, 0, 1.0F, SinkingMedium.MUD);
        ArmorTextureMudData data = builder.build();
        assertEquals(1.0F / 16.0F, data.coverageFraction(), 1.0E-6F);
    }

    @Test
    void largeModelTextureCanRetainEveryOpaquePixel() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "large_model.png");
        ArmorTextureMudData.Builder builder = ArmorTextureMudData.EMPTY.toBuilder();
        int opaquePixels = 16067;
        for (int pixel = 0; pixel < opaquePixels; pixel++) {
            builder.mark(texture, 256, 256, pixel, 1.0F, SinkingMedium.MUD);
        }

        ArmorTextureMudData data = builder.build();
        assertEquals(opaquePixels, data.dirtyPixelCount());
    }

    @Test
    void textureCapacityScalesWithDimensionsUpToItemSafetyLimit() {
        assertEquals(256, ArmorTextureMudData.maximumPixelsForLayer(16, 16));
        assertEquals(4096, ArmorTextureMudData.maximumPixelsForLayer(64, 64));
        assertEquals(16384, ArmorTextureMudData.maximumPixelsForLayer(128, 128));
        assertEquals(65536, ArmorTextureMudData.maximumPixelsForLayer(256, 256));
        assertEquals(65536, ArmorTextureMudData.maximumPixelsForLayer(1024, 1024));
    }

    private static Map<Integer, Pixel> collect(ArmorTextureMudData.Layer layer) {
        Map<Integer, Pixel> result = new HashMap<>();
        layer.forEach((pixel, coverage, medium) -> result.put(pixel, new Pixel(coverage, medium)));
        return result;
    }

    private record Pixel(float coverage, SinkingMedium medium) {
    }
}
