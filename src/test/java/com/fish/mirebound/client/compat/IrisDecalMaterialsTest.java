package com.fish.mirebound.client.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

class IrisDecalMaterialsTest {
    @Test
    void neutralMapsCoverEveryAlbedoTexelIncludingAllFourCorners() {
        for (int[] size : new int[][] {{16, 16}, {64, 64}, {32, 16}}) {
            try (NativeImage normals = IrisDecalMaterials.materialImage(size[0], size[1], IrisDecalMaterials.FLAT_NORMAL_ABGR);
                    NativeImage specular = IrisDecalMaterials.materialImage(size[0], size[1], IrisDecalMaterials.MATTE_SPECULAR_ABGR)) {
                assertEquals(size[0], normals.getWidth());
                assertEquals(size[1], normals.getHeight());
                for (int y = 0; y < size[1]; y++) {
                    for (int x = 0; x < size[0]; x++) {
                        int normal = normals.getPixelRGBA(x, y);
                        assertEquals(255, FastColor.ABGR32.alpha(normal), "full height disables false parallax");
                        assertEquals(127, FastColor.ABGR32.red(normal));
                        assertEquals(127, FastColor.ABGR32.green(normal));
                        assertEquals(255, FastColor.ABGR32.blue(normal));
                        assertEquals(0, specular.getPixelRGBA(x, y));
                    }
                }
            }
        }
    }
}
