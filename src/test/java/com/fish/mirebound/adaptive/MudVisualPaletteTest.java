package com.fish.mirebound.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class MudVisualPaletteTest {
    @Test
    void mergesOnlyIdenticalMediumAndSourceKeys() {
        MudVisualPalette palette = new MudVisualPalette();

        palette.add(SinkingMedium.MUD, 11L, 0.2F);
        palette.add(SinkingMedium.MUD, 11L, 0.3F);
        palette.add(SinkingMedium.MUD, 12L, 0.1F);

        assertEquals(2, palette.size());
        assertEquals(0.5F, palette.weightAt(0), 0.0001F);
        assertEquals(0.1F, palette.weightAt(1), 0.0001F);
    }

    @Test
    void remainsBoundedAndPreservesTotalWeight() {
        MudVisualPalette palette = new MudVisualPalette();
        for (int index = 0; index < 20; index++) {
            palette.add(SinkingMedium.byId(index % SinkingMedium.COUNT),
                    100L + index, index + 1.0F);
        }

        assertEquals(MudVisualPalette.MAX_ENTRIES, palette.size());
        assertEquals(210.0F, palette.totalWeight(), 0.0001F);
    }

    @Test
    void proportionalRemovalPreservesMixture() {
        MudVisualPalette palette = new MudVisualPalette();
        palette.add(SinkingMedium.MUD, 1L, 0.25F);
        palette.add(SinkingMedium.SOFT_QUICKSAND, 2L, 0.75F);

        assertEquals(0.4F, palette.removeProportional(0.4F), 0.0001F);
        assertEquals(0.15F, palette.weightAt(0), 0.0001F);
        assertEquals(0.45F, palette.weightAt(1), 0.0001F);
    }

    @Test
    void networkAndPersistentFormsRetainSources() {
        MudVisualPalette source = new MudVisualPalette();
        source.add(SinkingMedium.MUD, 1234L, 0.35F);
        source.add(SinkingMedium.MUD, 5678L, 0.65F);

        MudVisualPalette persistent = new MudVisualPalette();
        persistent.unpackPersistent(source.packPersistentEntries(), source.packVisualSources());
        assertEquals(2, persistent.size());
        assertEquals(1234L, persistent.visualSourceAt(0));
        assertEquals(1.0F, persistent.totalWeight(), 0.0002F);

        MudVisualPalette network = new MudVisualPalette();
        network.unpackNetwork(source.packNetworkEntries(), source.packVisualSources(), 0.8F);
        assertEquals(2, network.size());
        assertEquals(5678L, network.visualSourceAt(1));
        assertEquals(0.8F, network.totalWeight(), 0.0002F);
    }

    @Test
    void weightedSelectionIsStablePerCell() {
        MudVisualPalette palette = new MudVisualPalette();
        palette.add(SinkingMedium.MUD, 41L, 0.5F);
        palette.add(SinkingMedium.SOFT_QUICKSAND, 42L, 0.5F);

        for (int cell = 0; cell < 128; cell++) {
            assertEquals(palette.select(91L, cell, SinkingMedium.MUD),
                    palette.select(91L, cell, SinkingMedium.MUD));
        }
        assertTrue(palette.select(91L, 5, SinkingMedium.MUD).weight() > 0.0F);
    }

    @Test
    void adaptiveParticlesUseTheCapturedSourceColor() {
        long source = (long) 0x4A7FC2 << 35 | 1L;

        Vector3f color = MudVisualSource.particleColor(
                source, new Vector3f(1.0F, 0.0F, 0.0F));

        assertEquals(0x4A / 255.0F, color.x, 1.0E-6F);
        assertEquals(0x7F / 255.0F, color.y, 1.0E-6F);
        assertEquals(0xC2 / 255.0F, color.z, 1.0E-6F);
    }

    @Test
    void modernVisualSourcesCarryCoverageTextureSettings() {
        int red = 37;
        int green = 91;
        int blue = 118;
        int detail = 24;
        long source = Long.MIN_VALUE
                | (long) red << 35
                | (long) green << 42
                | (long) blue << 49
                | 2L << 56
                | (long) detail << 58
                | 1L;

        assertEquals(2, MudVisualSource.smoothingRadius(source));
        assertEquals(detail / 31.0F, MudVisualSource.textureDetail(source), 1.0E-6F);
        int color = MudVisualSource.color(source);
        assertEquals(Math.round(red * 255.0F / 127.0F), color >> 16 & 0xFF);
        assertEquals(Math.round(green * 255.0F / 127.0F), color >> 8 & 0xFF);
        assertEquals(Math.round(blue * 255.0F / 127.0F), color & 0xFF);
    }

    @Test
    void positionBackedVisualSourcesRoundTripDynamicOrigins() {
        BlockPos origin = new BlockPos(-83_451, 1_200, 117_002);
        long source = MudVisualSource.position(origin, Direction.WEST, 0x4A7FC2);

        assertTrue(MudVisualSource.positionBacked(source));
        assertEquals(origin, MudVisualSource.position(source));
        assertEquals(Direction.WEST, MudVisualSource.face(source));
        assertNull(MudVisualSource.state(source));
        assertEquals(0x4477BB, MudVisualSource.color(source));
    }

    @Test
    void positionFormatDoesNotCollideWithLegacyVisualSources() {
        long legacy = (long) 0x4A7FC2 << 35 | 1L;
        long modern = Long.MIN_VALUE | 24L << 58 | 1L;

        assertFalse(MudVisualSource.positionBacked(legacy));
        assertFalse(MudVisualSource.positionBacked(modern));
        assertEquals(MudVisualSource.NONE, MudVisualSource.position(
                new BlockPos(131_072, 64, 0), Direction.UP, 0xFFFFFF));
        assertEquals(MudVisualSource.NONE, MudVisualSource.position(
                new BlockPos(0, 1_984, 0), Direction.UP, 0xFFFFFF));
    }
}
