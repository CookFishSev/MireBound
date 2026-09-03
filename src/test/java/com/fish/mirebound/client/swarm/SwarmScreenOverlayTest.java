package com.fish.mirebound.client.swarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.client.ScreenOverlayLayout;
import org.junit.jupiter.api.Test;

class SwarmScreenOverlayTest {
    @Test
    void coverLayoutPreservesTextureAspectWithoutLeavingEmptyEdges() {
        ScreenOverlayLayout.CoverRect wide =
                ScreenOverlayLayout.cover(1000, 400, 320, 180);
        ScreenOverlayLayout.CoverRect tall =
                ScreenOverlayLayout.cover(400, 1000, 320, 180);

        assertTrue(wide.width() >= 1000);
        assertTrue(wide.height() >= 400);
        assertTrue(tall.width() >= 400);
        assertTrue(tall.height() >= 1000);
        assertEquals(320.0D / 180.0D,
                wide.width() / (double) wide.height(), 0.01D);
        assertEquals(320.0D / 180.0D,
                tall.width() / (double) tall.height(), 0.01D);
    }

    @Test
    void nativeAspectUsesViewportWithoutCropping() {
        ScreenOverlayLayout.CoverRect nativeAspect =
                ScreenOverlayLayout.cover(960, 540, 320, 180);

        assertEquals(0, nativeAspect.x());
        assertEquals(0, nativeAspect.y());
        assertEquals(960, nativeAspect.width());
        assertEquals(540, nativeAspect.height());
    }
}
