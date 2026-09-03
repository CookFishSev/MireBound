package com.fish.mirebound.mud.flow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudFlowStepRulesTest {
    @Test
    void unreadableOrOpenVerticalPathsBlockHorizontalSpread() {
        assertTrue(MudFlowStepRules.needsVerticalResolution(false, false, 16));
        assertTrue(MudFlowStepRules.needsVerticalResolution(true, true, 0));
        assertTrue(MudFlowStepRules.needsVerticalResolution(true, true, 15));
    }

    @Test
    void horizontalSpreadStartsOnlyAfterVerticalPathIsFullOrBlocked() {
        assertFalse(MudFlowStepRules.needsVerticalResolution(true, true, 16));
        assertFalse(MudFlowStepRules.needsVerticalResolution(true, false, 0));
    }

    @Test
    void fragileDecorationsCanBeDisplacedButRedstoneCannot() {
        assertTrue(MudFlowStepRules.canDisplaceDecoration(
                false, false, false, true, false));
        assertTrue(MudFlowStepRules.canDisplaceDecoration(
                false, false, false, false, true));
        assertFalse(MudFlowStepRules.canDisplaceDecoration(
                true, false, false, true, true));
        assertFalse(MudFlowStepRules.canDisplaceDecoration(
                false, true, false, true, true));
        assertFalse(MudFlowStepRules.canDisplaceDecoration(
                false, false, true, true, true));
    }
}
