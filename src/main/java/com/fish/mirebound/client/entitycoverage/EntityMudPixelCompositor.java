package com.fish.mirebound.client.entitycoverage;

import com.fish.mirebound.client.MudCoverageAppearance;
import com.fish.mirebound.client.MudSkinTextureCache;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Composes projected media colors without owning model geometry capture. */
final class EntityMudPixelCompositor {
    private static final int OVERLAPPING_SOURCE_ALPHA_LIMIT = 166;
    private static final int OVERLAP_BLEND_CELL_SIZE = 6;
    private static final int OVERLAP_ALPHA_MINIMUM = 56;
    private static final int OVERLAP_ALPHA_RANGE = 144;

    private EntityMudPixelCompositor() {
    }

    static void paint(
            NativeImage image, ClientEntityMudCoverage.View view,
            Map<Integer, EntityMudGeometryProjector.SpotProjection> projections) {
        image.fillRect(0, 0, image.getWidth(), image.getHeight(), 0);
        if (view.spots().isEmpty() || projections.isEmpty()) {
            return;
        }
        int width = image.getWidth();
        int pixelCount = width * image.getHeight();
        for (ClientEntityMudCoverage.SpotView spot : view.spots()) {
            EntityMudGeometryProjector.SpotProjection projection =
                    projections.get(spot.id());
            if (projection == null) {
                continue;
            }
            int salt = view.patternSeed() ^ spot.id() * 97;
            for (int index = 0; index < projection.pixels().length; index++) {
                int pixel = projection.pixels()[index];
                if (pixel < 0 || pixel >= pixelCount) {
                    continue;
                }
                float edge = Byte.toUnsignedInt(
                        projection.edges()[index]) / 255.0F;
                float alphaFactor = edge * (0.18F
                        + Mth.clamp(spot.strength(), 0.0F, 1.0F) * 0.82F);
                int x = pixel % width;
                int y = pixel / width;
                paintCandidate(image, x, y,
                        spot.medium(), spot.visualSource(), alphaFactor, salt);
            }
        }
    }

    private static void paintCandidate(
            NativeImage image, int x, int y,
            SinkingMedium medium, long visualSource,
            float alphaFactor, int salt) {
        if (alphaFactor <= 0.0F) {
            return;
        }
        int alpha = Mth.clamp(Math.round(255.0F
                * MudCoverageAppearance.opacityScale(medium, x, y, salt)
                * alphaFactor), 1, 255);
        int source = MudSkinTextureCache.skinCoverageTextureAbgr(
                medium, visualSource, x, y, salt, alpha);
        image.setPixelRGBA(x, y,
                compositeMudAbgr(
                        image.getPixelRGBA(x, y), source, x, y, salt));
    }

    static int compositeMudAbgr(int below, int above) {
        return compositeMudAbgr(below, above,
                OVERLAPPING_SOURCE_ALPHA_LIMIT);
    }

    private static int compositeMudAbgr(
            int below, int above, int x, int y, int salt) {
        return compositeMudAbgr(
                below, above, overlapSourceAlphaLimit(x, y, salt));
    }

    private static int compositeMudAbgr(int below, int above, int alphaLimit) {
        int destinationAlpha = FastColor.ABGR32.alpha(below);
        int sourceAlpha = FastColor.ABGR32.alpha(above);
        if (destinationAlpha > 0 && sourceAlpha > alphaLimit) {
            above = above & 0x00FFFFFF | alphaLimit << 24;
        }
        return compositeAbgr(below, above);
    }

    static int overlapSourceAlphaLimit(int x, int y, int salt) {
        int gridX = Math.floorDiv(x, OVERLAP_BLEND_CELL_SIZE);
        int gridY = Math.floorDiv(y, OVERLAP_BLEND_CELL_SIZE);
        float localX = Math.floorMod(x, OVERLAP_BLEND_CELL_SIZE)
                / (float) OVERLAP_BLEND_CELL_SIZE;
        float localY = Math.floorMod(y, OVERLAP_BLEND_CELL_SIZE)
                / (float) OVERLAP_BLEND_CELL_SIZE;
        float smoothX = smooth(localX);
        float smoothY = smooth(localY);
        float top = Mth.lerp(smoothX,
                overlapNode(gridX, gridY, salt),
                overlapNode(gridX + 1, gridY, salt));
        float bottom = Mth.lerp(smoothX,
                overlapNode(gridX, gridY + 1, salt),
                overlapNode(gridX + 1, gridY + 1, salt));
        float noise = Mth.lerp(smoothY, top, bottom);
        float contrast = smooth(Mth.clamp((noise - 0.18F) / 0.64F, 0.0F, 1.0F));
        return Mth.clamp(Math.round(OVERLAP_ALPHA_MINIMUM
                + contrast * OVERLAP_ALPHA_RANGE),
                OVERLAP_ALPHA_MINIMUM,
                OVERLAP_ALPHA_MINIMUM + OVERLAP_ALPHA_RANGE);
    }

    private static float overlapNode(int gridX, int gridY, int salt) {
        int hash = salt * 0x9E3779B9;
        hash ^= gridX * 0x632BE5AB;
        hash = Integer.rotateLeft(hash, 13);
        hash ^= gridY * 0x85157AF5;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return (hash & 0xFFFF) / 65535.0F;
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    static int compositeAbgr(int below, int above) {
        int sourceAlpha = FastColor.ABGR32.alpha(above);
        if (sourceAlpha <= 0) {
            return below;
        }
        if (sourceAlpha >= 255) {
            return above;
        }
        int destinationAlpha = FastColor.ABGR32.alpha(below);
        int inverseSource = 255 - sourceAlpha;
        int outputAlpha = sourceAlpha
                + (destinationAlpha * inverseSource + 127) / 255;
        if (outputAlpha <= 0) {
            return 0;
        }
        int red = compositeChannel(
                FastColor.ABGR32.red(below), destinationAlpha,
                FastColor.ABGR32.red(above), sourceAlpha,
                inverseSource, outputAlpha);
        int green = compositeChannel(
                FastColor.ABGR32.green(below), destinationAlpha,
                FastColor.ABGR32.green(above), sourceAlpha,
                inverseSource, outputAlpha);
        int blue = compositeChannel(
                FastColor.ABGR32.blue(below), destinationAlpha,
                FastColor.ABGR32.blue(above), sourceAlpha,
                inverseSource, outputAlpha);
        return FastColor.ABGR32.color(outputAlpha, blue, green, red);
    }

    private static int compositeChannel(
            int destination, int destinationAlpha,
            int source, int sourceAlpha,
            int inverseSource, int outputAlpha) {
        int weighted = source * sourceAlpha
                + (destination * destinationAlpha * inverseSource + 127) / 255;
        return Mth.clamp((weighted + outputAlpha / 2) / outputAlpha, 0, 255);
    }
}
