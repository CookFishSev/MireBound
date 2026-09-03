package com.fish.mirebound.mud.container;

import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MudVolumeDataTest {
    @Test
    void carriedVolumeIsAlwaysOneFiniteBlockOrLess() {
        MudVolumeData low = new MudVolumeData(-10, -4);
        MudVolumeData high = new MudVolumeData(999, 80);

        assertEquals(SinkingMedium.MUD, low.medium());
        assertEquals(1, low.pixels());
        assertEquals(SinkingMedium.byId(SinkingMedium.COUNT - 1), high.medium());
        assertEquals(16, high.pixels());
    }

    @Test
    void ordinaryUseFillsAsMuchOfTheBucketAsPossible() {
        assertEquals(12, MudVolumeContainerSystem.collectionAmount(1, 12, false));
        assertEquals(1, MudVolumeContainerSystem.collectionAmount(15, 12, false));
    }

    @Test
    void sneakUseCollectsExactlyOnePixel() {
        assertEquals(1, MudVolumeContainerSystem.collectionAmount(5, 16, true));
        assertEquals(1, MudVolumeContainerSystem.collectionAmount(5, 3, true));
    }

    @Test
    void fullBucketAndEmptySourceCannotCollect() {
        assertEquals(0, MudVolumeContainerSystem.collectionAmount(16, 16, true));
        assertEquals(0, MudVolumeContainerSystem.collectionAmount(4, 0, false));
    }
}
