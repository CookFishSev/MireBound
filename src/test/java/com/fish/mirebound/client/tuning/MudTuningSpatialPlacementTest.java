package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MudTuningSpatialPlacementTest {
    @Test
    void scrollAdjustsAndClampsPlacementDistance() {
        assertEquals(8.5D, MudTuningSpatialPlacement.adjustedDistance(8.0D, 1.0D));
        assertEquals(7.5D, MudTuningSpatialPlacement.adjustedDistance(8.0D, -1.0D));
        assertEquals(MudTuningSpatialPlacement.MAXIMUM_DISTANCE,
                MudTuningSpatialPlacement.adjustedDistance(64.0D, 1.0D));
        assertEquals(MudTuningSpatialPlacement.MINIMUM_DISTANCE,
                MudTuningSpatialPlacement.adjustedDistance(1.0D, -1.0D));
    }

    @Test
    void onlyAttachedFirstPersonViewUsesCameraRay() {
        assertTrue(MudTuningSpatialPlacement.useCameraRay(true, true, false));
        assertFalse(MudTuningSpatialPlacement.useCameraRay(false, true, false));
        assertFalse(MudTuningSpatialPlacement.useCameraRay(true, false, false));
        assertFalse(MudTuningSpatialPlacement.useCameraRay(true, true, true));
    }
}
