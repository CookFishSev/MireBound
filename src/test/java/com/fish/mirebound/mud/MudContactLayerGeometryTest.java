package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MudContactLayerGeometryTest {
    @Test
    void ordinaryAndSableCoordinatesResolveTheSameLayerGeometry() {
        LayerDepth ordinary = MudContactResolver.layerDepth(
                10.0D, 3.0D, 9.0D, 8.25D);
        LayerDepth sable = MudContactResolver.layerDepth(
                107.0D, 3.0D, 106.0D, 105.25D);

        assertEquals(ordinary.topDepth(), sable.topDepth(), 1.0E-9D);
        assertEquals(ordinary.depth(), sable.depth(), 1.0E-9D);
        assertEquals(ordinary.hasDeeperLayer(), sable.hasDeeperLayer());
    }
}
