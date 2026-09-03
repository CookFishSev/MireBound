package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudAmbientOcclusionTest {
    @Test
    void onlyActuallyTranslucentBlockMediaSkipAmbientOcclusion() {
        for (SinkingMedium medium : SinkingMedium.values()) {
            if (medium == SinkingMedium.LIVING_SLIME
                    || medium == SinkingMedium.ASSIMILATION_SLIME) {
                assertFalse(medium.opaqueBlock(), medium.name());
            } else {
                assertTrue(medium.opaqueBlock(), medium.name());
            }
        }
    }
}
