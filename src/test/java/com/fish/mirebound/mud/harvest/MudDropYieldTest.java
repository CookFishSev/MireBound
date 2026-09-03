package com.fish.mirebound.mud.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** Guards the volume-to-count curve shared by every medium's drop rule. */
class MudDropYieldTest {
    @Test
    void scaledUsesTheConfiguredMudBallBands() {
        RandomSource random = RandomSource.create(42L);
        assertBand(random, MudDropYield.SCALED, 1, 5, 0, 1);
        assertBand(random, MudDropYield.SCALED, 6, 10, 1, 2);
        assertBand(random, MudDropYield.SCALED, 11, 15, 2, 3);
        assertBand(random, MudDropYield.SCALED, 16, 16, 4, 4);
    }

    @Test
    void everyYieldStaysNonNegativeAndBoundedAcrossAllVolumes() {
        RandomSource random = RandomSource.create(7L);
        for (MudDropYield yield : MudDropYield.values()) {
            for (int pixels = -8; pixels <= 24; pixels++) {
                for (int sample = 0; sample < 64; sample++) {
                    int count = yield.count(pixels, random);
                    int maximum = yield == MudDropYield.NINE_PIECE_SCALED
                            ? 9 : 4;
                    assertTrue(count >= 0 && count <= maximum,
                            yield + " produced " + count + " for " + pixels + "px");
                }
            }
        }
    }

    @Test
    void outOfRangeVolumesClampInsteadOfEscapingTheBands() {
        RandomSource random = RandomSource.create(11L);
        // Zero volume cannot pay out; oversized values behave exactly like a full block.
        for (int sample = 0; sample < 64; sample++) {
            for (MudDropYield yield : MudDropYield.values()) {
                assertEquals(0, yield.count(0, random));
                assertEquals(0, yield.count(-5, random));
            }
            assertEquals(4, MudDropYield.SCALED.count(999, random));
        }
    }

    @Test
    void fullVolumeHasPredictableBulkMaterialValue() {
        RandomSource random = RandomSource.create(99L);
        for (int sample = 0; sample < 256; sample++) {
            assertEquals(4, MudDropYield.SCALED.count(16, random));
            assertEquals(4, MudDropYield.PIECE_SCALED.count(16, random));
            assertEquals(9, MudDropYield.NINE_PIECE_SCALED.count(16, random));
            assertEquals(1, MudDropYield.BLOCK_SCALED.count(16, random));
        }
    }

    @Test
    void fullBlockOnlyYieldRejectsPartialVolumes() {
        RandomSource random = RandomSource.create(101L);
        for (int pixels = 0; pixels < MudDropYield.MAX_PIXELS; pixels++) {
            assertEquals(0, MudDropYield.FULL_BLOCK_ONLY.count(pixels, random));
        }
        assertEquals(1, MudDropYield.FULL_BLOCK_ONLY.count(16, random));
    }

    @Test
    void singleYieldReturnsTheMediumForAnyNonEmptyVolume() {
        RandomSource random = RandomSource.create(102L);
        assertEquals(0, MudDropYield.SINGLE.count(0, random));
        assertEquals(0, MudDropYield.SINGLE.count(-1, random));
        for (int pixels = 1; pixels <= MudDropYield.MAX_PIXELS; pixels++) {
            assertEquals(1, MudDropYield.SINGLE.count(pixels, random));
        }
    }

    @Test
    void yieldOrderingHoldsAtFullVolume() {
        RandomSource random = RandomSource.create(99L);
        int scaled = 0;
        int pieces = 0;
        int blocks = 0;
        int sparse = 0;
        int rare = 0;
        int trials = 20_000;
        for (int trial = 0; trial < trials; trial++) {
            scaled += MudDropYield.SCALED.count(16, random);
            pieces += MudDropYield.PIECE_SCALED.count(16, random);
            blocks += MudDropYield.BLOCK_SCALED.count(16, random);
            sparse += MudDropYield.SPARSE.count(16, random);
            rare += MudDropYield.RARE.count(16, random);
        }
        assertEquals(pieces, scaled,
                "both full-block loose-material yields must return four");
        assertTrue(pieces > blocks, "PIECE_SCALED must out-yield BLOCK_SCALED");
        assertTrue(blocks > sparse, "BLOCK_SCALED must out-yield SPARSE");
        assertTrue(sparse > rare, "SPARSE must out-yield RARE");
        assertTrue(rare > 0, "RARE must still be reachable at full volume");
    }

    @Test
    void pieceAndBlockYieldsCanComeUpEmptyForPartialVolume() {
        RandomSource random = RandomSource.create(5L);
        boolean emptyPiece = false;
        boolean emptyBlock = false;
        for (int sample = 0; sample < 1_000; sample++) {
            emptyPiece |= MudDropYield.PIECE_SCALED.count(1, random) == 0;
            emptyBlock |= MudDropYield.BLOCK_SCALED.count(1, random) == 0;
        }
        assertTrue(emptyPiece, "a one-pixel layer must not guarantee a loose piece");
        assertTrue(emptyBlock, "a one-pixel layer must not guarantee a full block item");
    }

    @Test
    void everyRuleYieldIsReachableSoNoMediumIsSilentlyEmpty() {
        RandomSource random = RandomSource.create(3L);
        for (MudDropYield yield : MudDropYield.values()) {
            if (yield == MudDropYield.NONE) {
                continue;
            }
            boolean produced = false;
            for (int sample = 0; sample < 4_000 && !produced; sample++) {
                produced = yield.count(16, random) > 0;
            }
            assertTrue(produced, yield + " never produced a drop at full volume");
        }
    }

    private static void assertBand(RandomSource random, MudDropYield yield,
            int minimumPixels, int maximumPixels, int minimumDrop, int maximumDrop) {
        for (int pixels = minimumPixels; pixels <= maximumPixels; pixels++) {
            for (int sample = 0; sample < 64; sample++) {
                int count = yield.count(pixels, random);
                assertTrue(count >= minimumDrop && count <= maximumDrop,
                        pixels + "px produced " + count);
            }
        }
    }
}
