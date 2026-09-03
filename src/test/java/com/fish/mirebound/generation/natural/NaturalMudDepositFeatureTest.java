package com.fish.mirebound.generation.natural;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NaturalMudDepositFeatureTest {
    @Test
    void landDepositsAcceptFlatAndGentleTerrain() {
        assertTrue(NaturalMudDepositShape.acceptsLandHeights(
                64, 64, 64, 64, 64, 64));
        assertTrue(NaturalMudDepositShape.acceptsLandHeights(
                64, 65, 64, 65, 64, 65));
    }

    @Test
    void landDepositsRejectCliffsAndDistantTerraces() {
        assertFalse(NaturalMudDepositShape.acceptsLandHeights(
                64, 64, 62, 64, 64, 64));
        assertFalse(NaturalMudDepositShape.acceptsLandHeights(
                64, 66, 66, 66, 66, 66));
    }
}
