package com.fish.mirebound.stain;

import com.fish.mirebound.mud.SinkingMedium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.PushReaction;
import org.junit.jupiter.api.Test;

class MudFootprintBlockEntityTest {
    @Test
    void decorativeContainerNeverBlocksPistonsOrNotifiesNeighbors() {
        assertEquals(PushReaction.DESTROY, MudFootprintBlock.DECORATION_PUSH_REACTION);
        assertEquals(0, MudDecalAccess.DECORATION_UPDATE_FLAGS & Block.UPDATE_NEIGHBORS);
        assertTrue((MudDecalAccess.DECORATION_UPDATE_FLAGS & Block.UPDATE_CLIENTS) != 0);
        assertTrue((MudDecalAccess.DECORATION_UPDATE_FLAGS & Block.UPDATE_KNOWN_SHAPE) != 0);
    }

    @Test
    void supportValidationRejectsAirAndCollisionlessBlocks() {
        assertFalse(MudFootprintBlock.isValidSupport(true, false, false, true));
        assertFalse(MudFootprintBlock.isValidSupport(false, true, false, true));
        assertFalse(MudFootprintBlock.isValidSupport(false, false, true, false));
        assertFalse(MudFootprintBlock.isValidSupport(false, false, false, true));
        assertTrue(MudFootprintBlock.isValidSupport(false, false, false, false));
    }

    @Test
    void preciseWallPixelPackingRoundTripsEveryCellAndMedium() {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                for (SinkingMedium medium : SinkingMedium.values()) {
                    float strength = 0.06F + ((x * 17 + y * 11 + medium.id()) & 127) / 160.0F;
                    long packed = MudFootprintBlockEntity.packWallPixel(x, y, strength, medium);

                    assertEquals(x, MudFootprintBlockEntity.wallPixelHorizontal(packed));
                    assertEquals(y, MudFootprintBlockEntity.wallPixelVertical(packed));
                    assertEquals(medium, MudFootprintBlockEntity.wallPixelMedium(packed));
                    assertEquals(strength, MudFootprintBlockEntity.wallPixelStrength(packed), 1.0F / 255.0F);
                }
            }
        }
    }

    @Test
    void timedWallPixelPreservesCreationTickWithoutChangingCoverageFields() {
        long createdAt = 0xABCDEF12L;
        long packed = MudFootprintBlockEntity.packWallPixel(13, 4, 0.63F, SinkingMedium.MIRE, createdAt);

        assertEquals(13, MudFootprintBlockEntity.wallPixelHorizontal(packed));
        assertEquals(4, MudFootprintBlockEntity.wallPixelVertical(packed));
        assertEquals(SinkingMedium.MIRE, MudFootprintBlockEntity.wallPixelMedium(packed));
        assertEquals(0.63F, MudFootprintBlockEntity.wallPixelStrength(packed), 1.0F / 255.0F);
        assertEquals(true, MudFootprintBlockEntity.wallPixelHasCreationTime(packed));
        assertEquals((int) (createdAt & 0xFFFFFFL), MudFootprintBlockEntity.wallPixelCreatedAt(packed));
    }

    @Test
    void wallPixelAgeTreatsSmallClientClockLagAsFresh() {
        long packed = MudFootprintBlockEntity.packWallPixel(
                3, 7, 0.75F, SinkingMedium.MUD, 102L);

        assertEquals(0, MudFootprintBlockEntity.wallPixelAge(packed, 100L));
    }

    @Test
    void wallPixelAgePreservesTimestampWraparound() {
        long packed = MudFootprintBlockEntity.packWallPixel(
                3, 7, 0.75F, SinkingMedium.MUD, 0xFFFFFEL);

        assertEquals(3, MudFootprintBlockEntity.wallPixelAge(packed, 1L));
    }

    @Test
    void newerOverlappingMediumBecomesForegroundWhileRetainingOldColor() {
        long original = MudFootprintBlockEntity.packWallPixel(7, 9, 0.72F, SinkingMedium.PEAT_BOG, 120L);
        long incoming = MudFootprintBlockEntity.packWallPixel(7, 9, 0.84F, SinkingMedium.SOFT_QUICKSAND, 128L);

        long[] merged = MudFootprintBlockEntity.mergeWallPixels(
                new long[] {original},
                new long[] {incoming});

        assertEquals(1, merged.length);
        assertEquals(SinkingMedium.SOFT_QUICKSAND, MudFootprintBlockEntity.wallPixelMedium(merged[0]));
        assertEquals(SinkingMedium.PEAT_BOG, MudFootprintBlockEntity.wallPixelSecondaryMedium(merged[0]));
        assertTrue(MudFootprintBlockEntity.wallPixelSecondaryWeight(merged[0]) < 0.46F);
        assertTrue(MudFootprintBlockEntity.wallPixelStrength(merged[0]) > 0.72F);
        assertEquals(128, MudFootprintBlockEntity.wallPixelCreatedAt(merged[0]));
    }

    @Test
    void newerShallowMediumDoesNotCoverAnExistingThickCoat() {
        long original = MudFootprintBlockEntity.packWallPixel(7, 9, 0.88F, SinkingMedium.PEAT_BOG, 120L);
        long incoming = MudFootprintBlockEntity.packWallPixel(7, 9, 0.32F, SinkingMedium.SOFT_QUICKSAND, 128L);

        long[] merged = MudFootprintBlockEntity.mergeWallPixels(
                new long[] {original}, new long[] {incoming});

        assertEquals(1, merged.length);
        assertEquals(SinkingMedium.PEAT_BOG, MudFootprintBlockEntity.wallPixelMedium(merged[0]));
        assertEquals(SinkingMedium.SOFT_QUICKSAND, MudFootprintBlockEntity.wallPixelSecondaryMedium(merged[0]));
        assertEquals(0.88F, MudFootprintBlockEntity.wallPixelStrength(merged[0]), 1.0F / 255.0F);
        assertEquals(120, MudFootprintBlockEntity.wallPixelCreatedAt(merged[0]));
    }

    @Test
    void newerSameMediumCoatRefreshesPixelLifetime() {
        long old = MudFootprintBlockEntity.packWallPixel(4, 8, 0.91F, SinkingMedium.MUD, 100L);
        long fresh = MudFootprintBlockEntity.packWallPixel(4, 8, 0.44F, SinkingMedium.MUD, 180L);

        long[] merged = MudFootprintBlockEntity.mergeWallPixels(
                new long[] {old}, new long[] {fresh});

        assertEquals(1, merged.length);
        assertEquals(180, MudFootprintBlockEntity.wallPixelCreatedAt(merged[0]));
        assertEquals(0.91F, MudFootprintBlockEntity.wallPixelStrength(merged[0]), 1.0F / 255.0F);
    }

    @Test
    void repeatedFlowMergeIsIdempotent() {
        long lower = MudFootprintBlockEntity.packWallPixel(5, 3, 0.68F, SinkingMedium.MUD, 100L);
        long flowing = MudFootprintBlockEntity.packWallPixel(5, 3, 0.74F, SinkingMedium.SOFT_QUICKSAND, 140L);

        long[] once = MudFootprintBlockEntity.mergeWallPixels(new long[] {lower}, new long[] {flowing});
        long[] twice = MudFootprintBlockEntity.mergeWallPixels(once, new long[] {flowing});

        assertEquals(once.length, twice.length);
        assertEquals(once[0], twice[0]);
    }

    @Test
    void addingOnePrecisePixelDoesNotRestartTheWholeFaceAge() {
        long originalPixel = MudFootprintBlockEntity.packWallPixel(2, 6, 0.48F, SinkingMedium.MUD, 100L);
        MudFootprintBlockEntity.Entry original = new MudFootprintBlockEntity.Entry(
                9L, 0.5F, 0.5F, 0.002F, 0.0F, net.minecraft.core.Direction.NORTH, true,
                1.0F, 1.0F, 0.48F, SinkingMedium.MUD, 0L,
                new long[] {originalPixel}, 100L, 1900L, 0.42F);
        long newPixel = MudFootprintBlockEntity.packWallPixel(3, 6, 0.82F, SinkingMedium.PEAT_BOG, 800L);

        MudFootprintBlockEntity.Entry updated = original.withPreciseWallPixels(
                new long[] {originalPixel, newPixel}, 0.82F, SinkingMedium.PEAT_BOG, 2600L);

        assertEquals(100L, updated.createdAt());
        assertEquals(2600L, updated.expiresAt());
        assertEquals(1.0F, updated.fade());
        assertEquals(2, updated.wallPixels().length);
    }

    @Test
    void capacityEvictionPreservesFusedWallFacesBeforeOrdinaryDecals() {
        MudFootprintBlockEntity.Entry precise = entry(
                1L,
                true,
                new long[] {MudFootprintBlockEntity.packWallPixel(4, 5, 0.8F, SinkingMedium.MUD, 20L)});
        MudFootprintBlockEntity.Entry ordinary = entry(2L, false, new long[0]);

        int selected = MudFootprintBlockEntity.oldestReplaceableEntryIndex(List.of(precise, ordinary));

        assertEquals(1, selected);
    }

    @Test
    void rainFadeKeepsAdaptiveFootprintOwnership() {
        long visualSource = 0x1234ABCD5678L;
        MudFootprintBlockEntity.Entry original = new MudFootprintBlockEntity.Entry(
                7L, 0.5F, 0.006F, 0.5F, 0.0F, Direction.UP, false,
                0.25F, 0.25F, 0.8F, SinkingMedium.MUD, visualSource,
                new long[0], 20L, 200L, 1.0F);

        MudFootprintBlockEntity.Entry washed = original.withFade(0.72F);

        assertEquals(visualSource, washed.visualSource());
        assertEquals(0.72F, washed.fade(), 1.0E-6F);
        assertEquals(original.medium(), washed.medium());
    }

    @Test
    void preciseWallLayerIdentityIncludesMediumAndVisualSource() {
        MudFootprintBlockEntity.Entry layer = new MudFootprintBlockEntity.Entry(
                21L, 0.5F, 0.5F, 0.002F, 0.0F, Direction.NORTH, true,
                1.0F, 1.0F, 0.8F, SinkingMedium.MUD, 0x1234L,
                new long[] {MudFootprintBlockEntity.packWallPixel(
                        4, 6, 0.8F, SinkingMedium.MUD, 20L)},
                20L, 200L, 1.0F);

        assertTrue(MudFootprintBlockEntity.samePreciseLayer(
                layer, Direction.NORTH, SinkingMedium.MUD, 0x1234L));
        assertFalse(MudFootprintBlockEntity.samePreciseLayer(
                layer, Direction.NORTH, SinkingMedium.SOFT_QUICKSAND, 0x1234L));
        assertFalse(MudFootprintBlockEntity.samePreciseLayer(
                layer, Direction.NORTH, SinkingMedium.MUD, 0x5678L));
        assertFalse(MudFootprintBlockEntity.samePreciseLayer(
                layer, Direction.SOUTH, SinkingMedium.MUD, 0x1234L));
    }

    private static MudFootprintBlockEntity.Entry entry(long id, boolean wall, long[] pixels) {
        return new MudFootprintBlockEntity.Entry(
                id,
                0.5F,
                0.5F,
                0.5F,
                0.0F,
                Direction.NORTH,
                wall,
                1.0F,
                1.0F,
                0.8F,
                SinkingMedium.MUD,
                0L,
                pixels,
                20L,
                200L,
                1.0F);
    }
}
