package com.fish.mirebound.mud.harvest;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MudVolumeDropSystemTest {
    @Test
    void mudBallDropsStayInsideEachHeightBand() {
        RandomSource random = RandomSource.create(42L);
        assertBand(random, 1, 5, 0, 1);
        assertBand(random, 6, 10, 1, 2);
        assertBand(random, 11, 15, 2, 3);
        assertBand(random, 16, 16, 4, 4);
    }

    private static void assertBand(RandomSource random, int minimumHeight,
            int maximumHeight, int minimumDrop, int maximumDrop) {
        for (int height = minimumHeight; height <= maximumHeight; height++) {
            for (int sample = 0; sample < 64; sample++) {
                int count = MudVolumeDropSystem.mudBallCount(height, random);
                assertTrue(count >= minimumDrop && count <= maximumDrop,
                        height + "px produced " + count);
            }
        }
    }
}
