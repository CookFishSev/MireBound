package com.fish.mirebound.mud.container;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import org.junit.jupiter.api.Test;

class MudContainerRulesTest {
    @Test
    void ecologyMediaAreNotBucketable() {
        assertFalse(MudContainerRules.isBucketable(SinkingMedium.INSECT_MOUND));
        assertFalse(MudContainerRules.isBucketable(SinkingMedium.TENDER_FLESH));
    }

    @Test
    void ordinaryAndSpecialMudRemainBucketable() {
        assertTrue(MudContainerRules.isBucketable(SinkingMedium.MUD));
        assertTrue(MudContainerRules.isBucketable(SinkingMedium.MIRE));
        assertTrue(MudContainerRules.isBucketable(SinkingMedium.TAR));
        assertTrue(MudContainerRules.isBucketable(SinkingMedium.ASSIMILATION_SLIME));
    }
}
