package com.fish.mirebound.client.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ClientConfigLayoutTest {
    @Test
    void largeWindowsKeepTheSameCompactDesign() {
        for (int[] size : new int[][] {{854, 480}, {1280, 720}, {2560, 1080}}) {
            var layout = ClientConfigLayout.fit(size[0], size[1]);
            assertEquals(1.0, layout.scale());
            assertEquals(560, layout.panel().width());
            assertEquals(360, layout.panel().height());
            assertEquals(size[0] * .5, layout.panel().left() + layout.panel().width() * .5, .5);
            assertEquals(size[1] * .5, layout.panel().top() + layout.panel().height() * .5, .5);
        }
    }

    @Test
    void smallWideAndTallWindowsScaleBothAxesTogether() {
        for (int[] size : new int[][] {{320, 240}, {854, 240}, {260, 600}, {427, 240}, {100, 80}}) {
            var layout = ClientConfigLayout.fit(size[0], size[1]);
            var panel = layout.panel();
            assertTrue(layout.scale() > 0 && layout.scale() < 1);
            assertTrue(panel.left() * layout.scale() >= 11);
            assertTrue(panel.top() * layout.scale() >= 11);
            assertTrue(panel.right() * layout.scale() <= size[0] - 11);
            assertTrue(panel.bottom() * layout.scale() <= size[1] - 11);
            assertEquals(560.0 / 360, panel.width() / (double) panel.height(), 1e-9);
        }
    }

    @Test
    void pointerAndDragCoordinatesMatchTheScaledButtons() {
        var layout = ClientConfigLayout.fit(320, 240);
        double buttonX = layout.panel().right() - 42;
        double buttonY = layout.panel().bottom() - 17;
        assertEquals(buttonX, layout.pointer(buttonX * layout.scale()), 1e-9);
        assertEquals(buttonY, layout.pointer(buttonY * layout.scale()), 1e-9);
        assertEquals(20, layout.pointer(20 * layout.scale()), 1e-9);
    }
}
