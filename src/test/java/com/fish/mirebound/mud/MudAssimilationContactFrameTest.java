package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MudAssimilationContactFrameTest {
    @Test
    void contactWeightsAreVisibleOnlyInTheirSamplingTick() {
        MudPlayerData data = new MudPlayerData();
        data.beginAssimilationContactFrame(40);
        data.markAssimilationContact(SinkingMedium.ASSIMILATION_SLIME, 0.75F);

        assertEquals(0.75F,
                data.assimilationContactWeight(SinkingMedium.ASSIMILATION_SLIME, 40), 1.0E-7F);
        assertEquals(0.0F,
                data.assimilationContactWeight(SinkingMedium.ASSIMILATION_SLIME, 41), 1.0E-7F);

        data.beginAssimilationContactFrame(41);
        assertEquals(0.0F,
                data.assimilationContactWeight(SinkingMedium.ASSIMILATION_SLIME, 41), 1.0E-7F);
    }

    @Test
    void oneFrameRetainsIndependentMediaWeightsAndProfilePositions() {
        MudPlayerData data = new MudPlayerData();
        BlockPos slime = new BlockPos(4, 62, 7);
        BlockPos sand = new BlockPos(4, 61, 7);
        data.beginAssimilationContactFrame(90);
        data.markAssimilationContact(SinkingMedium.ASSIMILATION_SLIME, slime, 0.75F);
        data.markAssimilationContact(SinkingMedium.RED_QUICKSAND, sand, 0.25F);

        assertEquals(0.75F,
                data.assimilationContactWeight(SinkingMedium.ASSIMILATION_SLIME, 90), 1.0E-7F);
        assertEquals(0.25F,
                data.assimilationContactWeight(SinkingMedium.RED_QUICKSAND, 90), 1.0E-7F);
        assertEquals(slime,
                data.assimilationContactPosition(SinkingMedium.ASSIMILATION_SLIME, 90));
        assertEquals(sand,
                data.assimilationContactPosition(SinkingMedium.RED_QUICKSAND, 90));
    }
}
