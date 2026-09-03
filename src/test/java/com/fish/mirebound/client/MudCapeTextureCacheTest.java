package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fish.mirebound.mud.MudCapeLayout;
import org.junit.jupiter.api.Test;

class MudCapeTextureCacheTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void innerFaceKeepsLogicalHorizontalDirection() {
        assertEquals(0.1F, MudCapeTextureCache.logicalU(0.1F, false), EPSILON);
        assertEquals(0.9F, MudCapeTextureCache.logicalU(0.9F, false), EPSILON);
    }

    @Test
    void mirroredOuterFaceMapsBackToTheSameCapeColumns() {
        assertEquals(0.9F, MudCapeTextureCache.logicalU(0.1F, true), EPSILON);
        assertEquals(0.1F, MudCapeTextureCache.logicalU(0.9F, true), EPSILON);
    }

    @Test
    void verticalMirroringIsStableAcrossEveryPixelInOneRow() {
        float mapped = MudCapeTextureCache.logicalV(0.2F, true);
        assertEquals(0.8F, mapped, EPSILON);
        assertEquals(mapped, MudCapeTextureCache.logicalV(0.2F, true), EPSILON);
        assertEquals(0.2F, MudCapeTextureCache.logicalV(0.2F, false), EPSILON);
    }

    @Test
    void playerModelBroadUvRegionsMapToPhysicalOuterAndInnerFaces() {
        assertEquals(MudCapeLayout.Side.OUTER, MudCapeTextureCache.broadSideForReferenceU(1.0F));
        assertEquals(MudCapeLayout.Side.INNER, MudCapeTextureCache.broadSideForReferenceU(12.0F));
    }

    @Test
    void renderedCapeColumnsReverseTheGeometryGridAxis() {
        assertEquals(9, MudCapeTextureCache.textureColumnForLogicalU(0.05F));
        assertEquals(0, MudCapeTextureCache.textureColumnForLogicalU(0.95F));
    }
}
