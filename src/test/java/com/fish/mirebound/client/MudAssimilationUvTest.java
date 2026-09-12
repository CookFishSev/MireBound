package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class MudAssimilationUvTest {
    @Test
    void animationPixelsUseTheSameBodyMappingAtStandardAndHdResolutions() {
        for (boolean slim : new boolean[] {false, true}) {
            for (int size : new int[] {64, 128, 256}) {
                Map<Integer, Integer> cells = new HashMap<>();
                MudSkinTextureCache.visitAssimilationPixels(size, size, slim, null,
                        (x, y) -> 0xFFFFFFFF, (x, y, cell, salt) -> cells.put(y * size + x, cell));
                int scale = size / 64;
                int pupil = cells.get(7 * scale * size + 5 * scale);
                int eyebrow = cells.get(6 * scale * size + 24 * scale);
                assertEquals(MudSurfaceLayout.cellIndex(MudBodyPart.HEAD, MudSurface.FRONT, 4, 1), pupil);
                assertEquals(MudSurfaceLayout.cellIndex(MudBodyPart.HEAD, MudSurface.FRONT, 4, 6), eyebrow);
                assertFalse(cells.containsKey(6 * scale * size + 56 * scale));
            }
        }
    }

    @Test
    void transparentPixelsStayAbsentIncludingTheOuterLayerAndExtensionHoles() {
        int[] pixels = new int[64 * 64];
        pixels[7 * 64 + 5] = 0xFFFFFFFF;
        pixels[6 * 64 + 24] = 0x80404040;
        pixels[12 * 64 + 44] = 0xFF808080;
        IntBinaryOperator source = (x, y) -> pixels[y * 64 + x];
        Map<Integer, Integer> written = new HashMap<>();
        MudSkinTextureCache.visitAssimilationPixels(64, 64, false, null, source,
                (x, y, cell, salt) -> written.put(y * 64 + x, source.applyAsInt(x, y)));
        assertEquals(3, written.size());
        assertEquals(0x80404040, written.get(6 * 64 + 24));
        assertFalse(written.containsKey(7 * 64 + 6));
        assertFalse(written.containsKey(12 * 64 + 43));
        pixels[7 * 64 + 6] = 0xFFFFFFFF;
        assertTrue(MudSkinTextureCache.assimilationPixels(64, 64, false, source, cell -> 1)[7 * 64 + 6]);
    }

    @Test
    void markedAnimationPixelsRemainAssimilatedUntilTheirOwningCellIsCleared() {
        int owner = MudSurfaceLayout.cellIndex(MudBodyPart.HEAD, MudSurface.FRONT, 4, 1);
        IntBinaryOperator source = (x, y) -> 0xFFFFFFFF;
        boolean[] maskExempt = MudSkinTextureCache.assimilationPixels(64, 64, false, source,
                cell -> cell == owner ? 1.0 : 0.0);
        assertTrue(maskExempt[7 * 64 + 5]);
        assertFalse(maskExempt[6 * 64 + 24]);
        assertFalse(MudSkinTextureCache.assimilationPixels(64, 64, false, source, cell -> 0)[7 * 64 + 5]);
    }

    @Test
    void isolatedPartTexturesKeepExtensionsWithTheirOwner() {
        Map<Integer, Integer> pixels = new HashMap<>();
        MudSkinTextureCache.visitAssimilationPixels(64, 64, true, MudBodyPart.LEFT_ARM,
                (x, y) -> 0xFFFFFFFF, (x, y, cell, salt) -> pixels.put(y * 64 + x, cell));
        assertFalse(pixels.containsKey(7 * 64 + 5));
        assertTrue(pixels.containsKey(49 * 64 + 33));
        assertTrue(pixels.values().stream().allMatch(cell -> MudSurfaceLayout.part(cell) == MudBodyPart.LEFT_ARM));
    }
}
