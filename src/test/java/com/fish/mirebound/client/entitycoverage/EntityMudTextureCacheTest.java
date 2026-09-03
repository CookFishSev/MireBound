package com.fish.mirebound.client.entitycoverage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.entitycoverage.EntityMudCoverageSpot;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.List;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;

class EntityMudTextureCacheTest {
    @Test
    void localSpotHasOpaqueCoreAndSmoothBoundedEdge() {
        float center = EntityMudGeometryProjector.spotAlphaFactor(
                0.0F, 1.0F, 0.65F);
        float edge = EntityMudGeometryProjector.spotAlphaFactor(
                0.85F, 1.0F, 0.65F);

        assertTrue(center > edge);
        assertTrue(edge > 0.0F);
        assertEquals(0.0F, EntityMudGeometryProjector.spotAlphaFactor(
                1.0F, 1.0F, 0.65F));
    }

    @Test
    void overlayTextureKeepsSourceAspectRatio() {
        assertEquals(new EntityMudTextureCache.TextureSize(64, 32),
                EntityMudTextureCache.boundedSize(64, 32));
        assertEquals(new EntityMudTextureCache.TextureSize(128, 64),
                EntityMudTextureCache.boundedSize(512, 256));
    }

    @Test
    void overlayInheritsCutoutShapeFromEntityTexture() {
        assertEquals(0, EntityMudTextureCache.maskedAlpha(255, 0));
        assertEquals(128, EntityMudTextureCache.maskedAlpha(255, 128));
        assertEquals(64, EntityMudTextureCache.maskedAlpha(128, 128));
        assertEquals(255, EntityMudTextureCache.maskedAlpha(255, 255));
    }

    @Test
    void interpolationDoesNotRebuildLocalizedTexture() {
        ClientEntityMudCoverage.View faint = view(0.10F);
        ClientEntityMudCoverage.View strong = view(0.80F);

        assertEquals(faint.visualSignature(), strong.visualSignature());
    }

    @Test
    void lowerVolumeCoversEverySurfaceBelowMudLine() {
        assertEquals(1.0F, EntityMudGeometryProjector.volumeEdgeFactor(
                0.20F, 0.45F,
                EntityMudCoverageSpot.Shape.LOWER_VOLUME), 0.0001F);
        assertEquals(0.0F, EntityMudGeometryProjector.volumeEdgeFactor(
                0.50F, 0.45F,
                EntityMudCoverageSpot.Shape.LOWER_VOLUME), 0.0001F);
    }

    @Test
    void collisionDepthMapsToRenderedModelHeight() {
        assertEquals(0.35F, EntityMudGeometryProjector.modelVolumeBoundary(
                0.50F, 1.40F, 2.0F,
                EntityMudCoverageSpot.Shape.LOWER_VOLUME), 0.0001F);
        assertEquals(0.65F, EntityMudGeometryProjector.modelVolumeBoundary(
                0.50F, 1.40F, 2.0F,
                EntityMudCoverageSpot.Shape.UPPER_VOLUME), 0.0001F);
    }

    @Test
    void localizedVolumeKeepsItsOwnSideAndFadesBeforeOppositeSide() {
        float center = EntityMudGeometryProjector.localizedVolumeEdgeFactor(
                1.0F, 0.0F, 0.40F);
        float transition = EntityMudGeometryProjector.localizedVolumeEdgeFactor(
                1.0F, 0.44F, 0.40F);
        float opposite = EntityMudGeometryProjector.localizedVolumeEdgeFactor(
                1.0F, 0.80F, 0.40F);

        assertEquals(1.0F, center, 0.0001F);
        assertTrue(transition > 0.0F && transition < center);
        assertEquals(0.0F, opposite, 0.0001F);
    }

    @Test
    void expandingVolumeKeepsPreviouslyProjectedPixels() {
        var first = new EntityMudGeometryProjector.SpotProjection(
                new char[] {2, 7}, new byte[] {80, 120});
        var second = new EntityMudGeometryProjector.SpotProjection(
                new char[] {4, 7}, new byte[] {90, (byte) 200});

        var combined = EntityMudGeometryProjector.union(first, second);

        assertArrayEquals(new char[] {2, 4, 7}, combined.pixels());
        assertEquals(200, Byte.toUnsignedInt(combined.edges()[2]));
    }

    @Test
    void newerMediumCompositesOverExistingCoverageInsteadOfBeingDiscarded() {
        int brown = FastColor.ABGR32.color(255, 20, 60, 120);
        int cyan = FastColor.ABGR32.color(128, 220, 180, 20);

        int mixed = EntityMudPixelCompositor.compositeAbgr(brown, cyan);

        assertEquals(255, FastColor.ABGR32.alpha(mixed));
        assertTrue(FastColor.ABGR32.blue(mixed) > FastColor.ABGR32.blue(brown));
        assertTrue(FastColor.ABGR32.red(mixed) < FastColor.ABGR32.red(brown));
    }

    @Test
    void opaqueOverlappingMediaRemainVisiblyMixed() {
        int brown = FastColor.ABGR32.color(255, 20, 60, 120);
        int cyan = FastColor.ABGR32.color(255, 220, 180, 20);

        int mixed = EntityMudPixelCompositor.compositeMudAbgr(brown, cyan);

        assertEquals(255, FastColor.ABGR32.alpha(mixed));
        assertTrue(FastColor.ABGR32.blue(mixed) > FastColor.ABGR32.blue(brown));
        assertTrue(FastColor.ABGR32.blue(mixed) < FastColor.ABGR32.blue(cyan));
        assertTrue(FastColor.ABGR32.red(mixed) < FastColor.ABGR32.red(brown));
        assertTrue(FastColor.ABGR32.red(mixed) > FastColor.ABGR32.red(cyan));
    }

    @Test
    void completelyOverlappingMediaKeepBroadSmoothColorVariation() {
        int minimum = 255;
        int maximum = 0;
        int previous = EntityMudPixelCompositor.overlapSourceAlphaLimit(
                0, 9, 0x13572468);
        for (int x = 0; x < 96; x++) {
            int current = EntityMudPixelCompositor.overlapSourceAlphaLimit(
                    x, 9, 0x13572468);
            minimum = Math.min(minimum, current);
            maximum = Math.max(maximum, current);
            assertTrue(Math.abs(current - previous) <= 32);
            previous = current;
        }

        assertTrue(maximum - minimum >= 60);
        assertTrue(minimum < 100);
        assertTrue(maximum > 180);
    }

    @Test
    void spotOnlyViewRemainsRenderableWithoutLegacyAggregateChannels() {
        ClientEntityMudCoverage.View view = new ClientEntityMudCoverage.View(
                null, 0L, 0.0F, null, 0L, 0.0F,
                1.0F, 17, 5, 3, List.of(new ClientEntityMudCoverage.SpotView(
                        1, EntityMudCoverageSpot.Shape.RADIAL,
                        0.0F, 0.2F, -1.0F, 0.1F, 0.6F,
                        SinkingMedium.MUD, 0L)));

        assertTrue(view.totalCoverage() > 0.001F);
    }

    @Test
    void incrementalSpotUpdatePreservesUntouchedCoverageAndRemovesById() {
        ClientEntityMudCoverage.SpotView first = spot(
                1, 0.20F, SinkingMedium.MUD);
        ClientEntityMudCoverage.SpotView second = spot(
                2, 0.40F, SinkingMedium.TAR);
        ClientEntityMudCoverage.SpotView changedSecond = spot(
                2, 0.85F, SinkingMedium.SOFT_QUICKSAND);

        List<ClientEntityMudCoverage.SpotView> result =
                ClientEntityMudCoverage.applySpotUpdate(
                        List.of(first, second), List.of(changedSecond),
                        List.of(1), false);

        assertEquals(List.of(changedSecond), result);
    }

    @Test
    void aggregateRevisionDoesNotInvalidateUnchangedSpotTexture() {
        ClientEntityMudCoverage.View first = new ClientEntityMudCoverage.View(
                SinkingMedium.MUD, 0L, 0.2F,
                null, 0L, 0.0F, 1.0F, 17, 4, 9, List.of());
        ClientEntityMudCoverage.View second = new ClientEntityMudCoverage.View(
                SinkingMedium.MUD, 0L, 0.8F,
                null, 0L, 0.0F, 1.0F, 17, 5, 9, List.of());

        assertEquals(first.visualSignature(), second.visualSignature());
    }

    @Test
    void fadeScaleChangesOpacityWithoutInvalidatingSpotTexture() {
        ClientEntityMudCoverage.View visible = new ClientEntityMudCoverage.View(
                SinkingMedium.MUD, 0L, 0.8F,
                null, 0L, 0.0F, 1.0F, 17, 4, 9, List.of());
        ClientEntityMudCoverage.View faded = new ClientEntityMudCoverage.View(
                SinkingMedium.MUD, 0L, 0.8F,
                null, 0L, 0.0F, 0.25F, 17, 5, 9, List.of());

        assertEquals(visible.visualSignature(), faded.visualSignature());
        assertEquals(visible.totalCoverage() * 0.25F,
                faded.totalCoverage(), 0.0001F);
    }

    private static ClientEntityMudCoverage.View view(float strength) {
        return new ClientEntityMudCoverage.View(
                SinkingMedium.MUD, 0L, strength,
                null, 0L, 0.0F, 1.0F, 17, 4, 7, List.of());
    }

    private static ClientEntityMudCoverage.SpotView spot(
            int id, float strength, SinkingMedium medium) {
        return new ClientEntityMudCoverage.SpotView(
                id, EntityMudCoverageSpot.Shape.RADIAL,
                0.0F, 0.5F, -1.0F, 0.1F, strength, medium, id);
    }
}
