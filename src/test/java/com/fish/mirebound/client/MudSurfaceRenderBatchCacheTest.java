package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.Arrays;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

class MudSurfaceRenderBatchCacheTest {
    @Test
    void transparentAdaptiveSourcesUseTheTranslucentSurfaceBatch() {
        int[] pixels = new int[16 * 16];
        Arrays.fill(pixels, FastColor.ABGR32.color(128, 80, 120, 160));
        MudSurfaceAppearance.Appearance appearance = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null, pixels);

        assertTrue(MudSurfaceRenderBatchCache.usesTranslucentSurface(
                appearance, SinkingMedium.MUD));
    }

    @Test
    void opaqueMudKeepsCutoutWhileNativeLivingSlimeUsesTranslucency() {
        MudSurfaceAppearance.Appearance opaque = new MudSurfaceAppearance.Appearance(
                ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null);

        assertFalse(MudSurfaceRenderBatchCache.usesTranslucentSurface(
                opaque, SinkingMedium.MUD));
        assertTrue(MudSurfaceRenderBatchCache.usesTranslucentSurface(
                opaque, SinkingMedium.LIVING_SLIME));
    }

    @Test
    void planarTranslucentSurfaceBatchDoesNotSortThousandsOfIndependentQuads() {
        assertFalse(MudSurfaceDecalRenderTypes.sortsSurfaceTranslucentQuads());
    }

    @Test
    void nativeTextureAverageAlphaSelectsTranslucentCompression() {
        int[] pixels = new int[16 * 16];
        Arrays.fill(pixels, FastColor.ABGR32.color(170, 78, 110, 188));
        float opacity = MudSkinTextureCache.averageOpacity(pixels);
        MudSurfaceAppearance.Appearance bloodClot = new MudSurfaceAppearance.Appearance(
                ResourceLocation.fromNamespaceAndPath(
                        "mirebound", "textures/block/assimilation_slime.png"),
                0.0F, 0.0F, 1.0F, 1.0F,
                255, 255, 255, null, null, opacity);

        assertTrue(MudSurfaceRenderBatchCache.usesTranslucentSurface(
                bloodClot, SinkingMedium.ASSIMILATION_SLIME));
    }

}
