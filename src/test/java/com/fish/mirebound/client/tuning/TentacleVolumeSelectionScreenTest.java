package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TentacleVolumeSelectionScreenTest {
    @Test
    void sliderMapsItsFullWidthToVolumesOneThroughFifty() {
        assertEquals(1, TentacleVolumeSelectionScreen.volumeAt(20.0D, 20, 300));
        assertEquals(26, TentacleVolumeSelectionScreen.volumeAt(160.0D, 20, 300));
        assertEquals(50, TentacleVolumeSelectionScreen.volumeAt(300.0D, 20, 300));
        assertEquals(1, TentacleVolumeSelectionScreen.volumeAt(-100.0D, 20, 300));
        assertEquals(50, TentacleVolumeSelectionScreen.volumeAt(900.0D, 20, 300));
    }

    @Test
    void thumbMappingKeepsBothEndpointsExact() {
        assertEquals(20, TentacleVolumeSelectionScreen.volumeX(1, 20, 300));
        assertEquals(300, TentacleVolumeSelectionScreen.volumeX(50, 20, 300));
    }

    @Test
    void cancelHitAreaUsesHalfOpenPixelBounds() {
        assertTrue(TentacleVolumeSelectionScreen.cancelAt(310.0D, 90.0D,
                310, 90, 356, 111));
        assertTrue(TentacleVolumeSelectionScreen.cancelAt(355.9D, 110.9D,
                310, 90, 356, 111));
        assertFalse(TentacleVolumeSelectionScreen.cancelAt(356.0D, 100.0D,
                310, 90, 356, 111));
        assertFalse(TentacleVolumeSelectionScreen.cancelAt(330.0D, 111.0D,
                310, 90, 356, 111));
    }
}
