package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GeneratedMudDepthTest {
    @Test void passiveSinkingCrossesTwoFullLayersAndStopsInsideTheThird() {
        var original = SinkingPhysicsProfile.forMedium(SinkingMedium.MUD);
        double depth = .01, motionY = 0, settling = 0;
        // Include the ordinary solver's deliberately slow braking near the depth cap.
        for (int tick = 0; tick < 100_000 && depth < 2.499; tick++) {
            int layer = Math.max(0, (int) Math.floor(depth - .018));
            var profile = original.withGeneratedDepth(layer < 2 ? 16 : 8, layer >= 2);
            var input = new SinkingPhysicsSolver.Input(depth, 4, 1.8,
                    0, motionY, 0, settling, 0, 0, false, false, -1, false,
                    0, 0, 0, Math.clamp(depth / 1.8, 0, 1), 1, 1, 0,
                    layer, 1, true);
            var result = SinkingPhysicsSolver.solve(profile, input);
            assertTrue(result.motionY() <= 0, "Generated mud must not push the player upward");
            depth -= result.motionY();
            assertTrue(depth <= 2.5, "Backing layers must not extend the sinking limit");
            motionY = result.motionY();
            settling = result.settlingVelocity();
        }
        assertTrue(depth >= 2.499, "Both complete layers must remain passable without struggling; depth=" + depth);
    }

    @Test void extraBackingLayersDoNotIncreaseShallowOrIntegerDepthLimits() {
        var original = SinkingPhysicsProfile.forMedium(SinkingMedium.MUD);
        var shallow = original.withGeneratedDepth(4, true);
        var integerEnd = original.withGeneratedDepth(16, true);
        assertEquals(.25, SinkingPhysicsSolver.sinkLimit(shallow, 8, 0, 1, true, 1));
        assertEquals(1, SinkingPhysicsSolver.sinkLimit(integerEnd, 8, 0, 1, true, 1));
        assertEquals(3, SinkingPhysicsSolver.sinkLimit(integerEnd, 8, 2, 1, true, 1));
        assertEquals(2.5, SinkingPhysicsSolver.sinkLimit(original.withGeneratedDepth(8, true), 8, 2, 1, true, 1));
        assertTrue(SinkingPhysicsSolver.sinkLimit(original.withGeneratedDepth(16), 8, 0, 1, true, 1) > 1);
        assertSame(integerEnd, original.withGeneratedDepth(16, true));
        assertNotSame(integerEnd, original.withGeneratedDepth(16));
    }

    @Test void profileBlendingPreservesTheGeneratedTerminalLayer() {
        var original = SinkingPhysicsProfile.forMedium(SinkingMedium.MUD);
        var end = original.withGeneratedDepth(16, true);
        var blended = SinkingPhysicsProfile.blend(original.withGeneratedDepth(16), end, .5);
        assertEquals(1, SinkingPhysicsSolver.sinkLimit(blended, 8, 0, 1, true, 1));
        assertFalse(MudBlockProfileStore.shapeCountsAsModified(MudBlockVariant.NATURAL_DEPTH));
        assertFalse(MudBlockProfileStore.shapeCountsAsModified(MudBlockVariant.NATURAL_DEPTH_END));
    }

    @Test void generatedLayersReachTwoAndAHalfBlocksWithoutChangingOrdinaryMud() {
        var original = SinkingPhysicsProfile.forMedium(SinkingMedium.MUD);
        double previous = SinkingPhysicsSolver.configuredDepth(original);
        var fullLayer = original.withGeneratedDepth(16);
        var halfLayer = original.withGeneratedDepth(8);
        assertEquals(1, fullLayer.simpleMaximumDepth);
        assertEquals(1, fullLayer.simpleNaturalDepth);
        assertEquals(.5, halfLayer.simpleMaximumDepth);
        assertEquals(.5, halfLayer.simpleNaturalDepth);
        assertTrue(SinkingPhysicsSolver.sinkLimit(fullLayer, 3, 0, 1, true, 1) > 1);
        assertTrue(SinkingPhysicsSolver.sinkLimit(fullLayer, 3, 1, 1, true, 1) > 2);
        assertEquals(2.5, SinkingPhysicsSolver.sinkLimit(halfLayer, 3, 2, 1, false, 1));
        assertEquals(.25, SinkingPhysicsSolver.sinkLimit(original.withGeneratedDepth(4), 1, 0, 1, false, 1));
        assertEquals(previous, SinkingPhysicsSolver.configuredDepth(original));
        assertEquals(original.viscositySurface, fullLayer.viscositySurface);
        assertSame(fullLayer, original.withGeneratedDepth(16));
        assertSame(halfLayer, original.withGeneratedDepth(8));
    }
}
