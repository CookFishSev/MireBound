package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

class AssimilationPlayerAnimationTest {
    @Test
    void freezingKeepsRenderedEyeScalesInsteadOfResetModelValues() {
        ModelPart eye = new ModelPart(List.of(), Map.of());
        eye.xScale = .75F;
        eye.yScale = .5F;
        eye.zScale = .3F;
        eye.y = 1.25F;
        var actual = AssimilationPlayerAnimation.PartTransform.capture(eye);
        eye.xScale = eye.yScale = eye.zScale = 1;
        eye.y = 0;
        var frozen = new IdentityHashMap<ModelPart, AssimilationPlayerAnimation.PartTransform>();
        AssimilationPlayerAnimation.applyFrozenPart(eye, Map.of(eye, actual), frozen);
        assertEquals(actual, AssimilationPlayerAnimation.PartTransform.capture(eye));
        eye.yScale = 1;
        AssimilationPlayerAnimation.applyFrozenPart(eye, Map.of(), frozen);
        assertEquals(.5F, eye.yScale);
    }

    @Test
    void hiddenAlternativesStayHiddenWhileFreezing() {
        ModelPart hidden = new ModelPart(List.of(), Map.of());
        hidden.visible = false;
        hidden.skipDraw = true;
        hidden.zScale = 0;
        var actual = AssimilationPlayerAnimation.PartTransform.capture(hidden);
        hidden.visible = true;
        hidden.skipDraw = false;
        hidden.zScale = 1;
        AssimilationPlayerAnimation.applyFrozenPart(hidden, Map.of(hidden, actual), new IdentityHashMap<>());
        assertFalse(hidden.visible);
        assertTrue(hidden.skipDraw);
        assertEquals(0, hidden.zScale);
    }

    @Test
    void unseenPartsFreezeTheirCurrentRenderedStateIndependently() {
        ModelPart eye = new ModelPart(List.of(), Map.of());
        eye.xScale = .6F;
        var firstPlayer = new IdentityHashMap<ModelPart, AssimilationPlayerAnimation.PartTransform>();
        AssimilationPlayerAnimation.applyFrozenPart(eye, null, firstPlayer);
        eye.xScale = .9F;
        var secondPlayer = new IdentityHashMap<ModelPart, AssimilationPlayerAnimation.PartTransform>();
        AssimilationPlayerAnimation.applyFrozenPart(eye, null, secondPlayer);
        assertEquals(.9F, eye.xScale);
        AssimilationPlayerAnimation.applyFrozenPart(eye, null, firstPlayer);
        assertEquals(.6F, eye.xScale);
    }
}
