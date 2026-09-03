package com.fish.mirebound.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class AdaptiveMudConversionSchedulerTest {
    @Test
    void keepsSmallSelectionsInOneImmediateRegion() {
        AdaptiveMudConversionScheduler.RegionCursor regions =
                AdaptiveMudConversionScheduler.partition(
                        BlockPos.ZERO, new BlockPos(15, 15, 15));

        assertEquals(4_096, regions.totalVolume());
        assertEquals(4_096, regions.peek().volume());
        regions.advance();
        assertEquals(null, regions.peek());
    }

    @Test
    void partitionsLargeSelectionsWithoutGapsOrOverlap() {
        BlockPos minimum = new BlockPos(-13, -2, 7);
        BlockPos maximum = new BlockPos(50, 29, 38);
        AdaptiveMudConversionScheduler.RegionCursor regions =
                AdaptiveMudConversionScheduler.partition(minimum, maximum);
        Set<BlockPos> visited = new HashSet<>();

        for (AdaptiveMudConversionScheduler.Region region = regions.peek();
                region != null; region = regions.peek()) {
            assertTrue(region.volume() <= 4_096);
            for (BlockPos pos : BlockPos.betweenClosed(region.minimum(), region.maximum())) {
                assertTrue(visited.add(pos.immutable()), "overlapping position " + pos);
            }
            regions.advance();
        }

        assertEquals(64 * 32 * 32, visited.size());
        assertTrue(visited.contains(minimum));
        assertTrue(visited.contains(maximum));
    }

    @Test
    void representsNearWorldScaleSelectionsWithConstantCursorState() {
        AdaptiveMudConversionScheduler.RegionCursor regions =
                AdaptiveMudConversionScheduler.partition(
                        new BlockPos(-30_000_000, -64, -30_000_000),
                        new BlockPos(29_999_999, 319, 29_999_999));

        assertEquals(1_382_400_000_000_000_000L, regions.totalVolume());
        assertTrue(regions.peek().volume() <= 4_096);
    }

    @Test
    void refusesToProcessARegionContainingAnUnloadedChunk() {
        AdaptiveMudConversionScheduler.Region region =
                new AdaptiveMudConversionScheduler.Region(
                        new BlockPos(0, 0, 0), new BlockPos(31, 15, 15));

        assertFalse(AdaptiveMudConversionScheduler.allChunksLoaded(
                region, (chunkX, chunkZ) -> chunkX == 0));
        assertTrue(AdaptiveMudConversionScheduler.allChunksLoaded(
                region, (chunkX, chunkZ) -> true));
    }
}
