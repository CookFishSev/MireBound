package com.fish.mirebound.generation.natural;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NaturalMudColumnPlanTest {
    @Test void treeProximityChangesSinkingButLeavesTheSameFourTerrainLayers() {
        var rule = new NaturalMudCoverageRule(true, 1, 0, .25, true, 2.5, 4, 0,
                NaturalMudCoverageRule.MixMode.PATCHES, 24, 0, Map.of(SinkingMedium.MUD, 100), 1, 4);
        var near = NaturalMudColumnPlan.create(rule.replacementLayers(), 4,
                NaturalMudCoveragePattern.depth(rule, 17, 0, 0, 0));
        var far = NaturalMudColumnPlan.create(rule.replacementLayers(), 4,
                NaturalMudCoveragePattern.depth(rule, 17, 0, 0, 8));
        assertEquals(4, near.layerCount());
        assertEquals(4, far.layerCount());
        assertEquals(16, near.depthPixels(0));
        assertEquals(16, near.depthPixels(1));
        assertEquals(8, near.depthPixels(2));
        assertFalse(near.terminalLayer(0));
        assertFalse(near.terminalLayer(1));
        assertTrue(near.terminalLayer(2));
        assertEquals(4, far.depthPixels(0));
        assertTrue(far.terminalLayer(0));
    }

    @Test void deepTreeSettingDoesNotGenerateMoreThanTheConfiguredTwoLayers() {
        var rule = new NaturalMudCoverageRule(true, 1, 0, .25, true, 6, 4, 0,
                NaturalMudCoverageRule.MixMode.PATCHES, 24, 0, Map.of(SinkingMedium.MUD, 100), 1, 2);
        double effectiveDepth = NaturalMudCoveragePattern.depth(rule, 17, 0, 0, 0);
        assertEquals(2, effectiveDepth);
        var plan = NaturalMudColumnPlan.create(rule.replacementLayers(), 12, effectiveDepth);
        assertEquals(2, plan.layerCount());
        assertEquals(32, plan.sinkingPixels());
        assertFalse(plan.terminalLayer(0));
        assertTrue(plan.terminalLayer(1));
    }

    @Test void aCaveOrProtectedBlockStopsMaterialAndDepthTogether() {
        var plan = NaturalMudColumnPlan.create(4, 1, 2.5);
        assertEquals(1, plan.layerCount());
        assertEquals(16, plan.depthPixels(0));
        assertTrue(plan.terminalLayer(0));
        assertEquals(0, NaturalMudColumnPlan.create(4, 0, 2.5).layerCount());
    }

    @Test void integerDepthStopsOnThatLayerEvenWithExtraMudBelow() {
        var plan = NaturalMudColumnPlan.create(4, 4, 2);
        assertFalse(plan.terminalLayer(0));
        assertTrue(plan.terminalLayer(1));
        assertEquals(16, plan.depthPixels(1));
        assertEquals(4, plan.layerCount());
    }
}
