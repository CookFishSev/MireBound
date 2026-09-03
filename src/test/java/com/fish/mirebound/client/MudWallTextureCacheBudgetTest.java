package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MudWallTextureCacheBudgetTest {
    @AfterEach
    void resetBudget() {
        MudWallTextureCache.resetRebuildBudget();
    }

    @Test
    void limitsRebuildBurstsWithinOneGameTick() {
        MudWallTextureCache.resetRebuildBudget();

        for (int rebuild = 0; rebuild < 24; rebuild++) {
            assertTrue(MudWallTextureCache.reserveTextureRebuild(80L));
        }
        assertFalse(MudWallTextureCache.reserveTextureRebuild(80L));
    }

    @Test
    void refreshesTheBudgetOnTheNextGameTick() {
        MudWallTextureCache.resetRebuildBudget();
        for (int rebuild = 0; rebuild < 24; rebuild++) {
            assertTrue(MudWallTextureCache.reserveTextureRebuild(80L));
        }

        assertTrue(MudWallTextureCache.reserveTextureRebuild(81L));
    }
}
