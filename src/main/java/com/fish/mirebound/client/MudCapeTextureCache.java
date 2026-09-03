package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudCoveragePatternSeed;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Bakes the 10x16 cape coverage grid directly into the player's real cape texture. */
public final class MudCapeTextureCache {
    private static final float REFERENCE_WIDTH = 64.0F;
    private static final float REFERENCE_HEIGHT = 32.0F;
    private static final float COVERAGE_EPSILON = 0.001F;
    private static final int ADAPTIVE_TEXTURE_PHASE = MudBodyPart.COUNT;
    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final Map<ResourceLocation, Entry> GENERATED = new HashMap<>();

    private MudCapeTextureCache() {
    }

    public static ResourceLocation bakedCapeFor(
            AbstractClientPlayer player, ResourceLocation sourceTexture) {
        if (sourceTexture == null
                || !MireboundClientSettings.clientOptionEnabled(
                        ClientOption.PLAYER_COVERAGE)) {
            return null;
        }
        Entry generatedSource = GENERATED.get(sourceTexture);
        if (generatedSource != null && generatedSource.source != null) {
            sourceTexture = generatedSource.source;
        }
        if (!SkinPixelCache.hasPixels(sourceTexture)) {
            return null;
        }
        ClientMudState.CoverageState display = ClientMudState.displaySnapshot(player.getId());
        long signature = ClientMudState.displayCapeSignature(player.getId());
        if (signature == 0L) {
            return null;
        }
        long animation = MudSkinTextureCache.skinCoverageAnimationSignature(
                ClientMudState.displayCapeMediumMask(player.getId()));
        if (animation != 0L) {
            signature = signature * 31L + animation;
        }

        int width = SkinPixelCache.width(sourceTexture);
        int height = SkinPixelCache.height(sourceTexture);
        Entry entry = ENTRIES.get(player.getId());
        if (entry == null || entry.width != width || entry.height != height) {
            remove(player.getId());
            DynamicTexture texture = new DynamicTexture(width, height, true);
            texture.setFilter(false, false);
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register(
                    "mirebound_baked_mud_cape", texture);
            entry = new Entry(location, texture, width, height);
            ENTRIES.put(player.getId(), entry);
            GENERATED.put(location, entry);
        }
        if (!sourceTexture.equals(entry.source) || signature != entry.signature) {
            rebuild(entry, player, display, sourceTexture, signature);
        }
        return entry.location;
    }

    public static boolean isGeneratedCape(ResourceLocation texture) {
        return texture != null && GENERATED.containsKey(texture);
    }

    static void clearEntity(int entityId) {
        remove(entityId);
    }

    static void reset() {
        for (Entry entry : ENTRIES.values()) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
        }
        ENTRIES.clear();
        GENERATED.clear();
    }

    private static void remove(int entityId) {
        Entry old = ENTRIES.remove(entityId);
        if (old != null) {
            GENERATED.remove(old.location);
            Minecraft.getInstance().getTextureManager().release(old.location);
        }
    }

    private static void rebuild(Entry entry, AbstractClientPlayer player,
            ClientMudState.CoverageState display,
            ResourceLocation sourceTexture, long signature) {
        NativeImage image = entry.texture.getPixels();
        if (image == null) {
            image = new NativeImage(entry.width, entry.height, true);
            entry.texture.setPixels(image);
        }
        for (int y = 0; y < entry.height; y++) {
            for (int x = 0; x < entry.width; x++) {
                image.setPixelRGBA(x, y, SkinPixelCache.pixel(sourceTexture, x, y));
            }
        }

        // These regions come directly from PlayerModel's 10x16x1 cloak Cube UVs.
        // The cloak's 180-degree model rotation makes NORTH the visible outer face.
        paintRegion(image, player, display, sourceTexture, new FaceRegion(
                1.0F, 1.0F, 11.0F, 17.0F,
                FaceKind.BROAD, broadSideForReferenceU(1.0F), false, false), 101);
        paintRegion(image, player, display, sourceTexture, new FaceRegion(
                12.0F, 1.0F, 22.0F, 17.0F,
                FaceKind.BROAD, broadSideForReferenceU(12.0F), true, false), 211);
        paintRegion(image, player, display, sourceTexture, new FaceRegion(
                0.0F, 1.0F, 1.0F, 17.0F,
                FaceKind.LEFT_EDGE, null, false, false), 307);
        paintRegion(image, player, display, sourceTexture, new FaceRegion(
                11.0F, 1.0F, 12.0F, 17.0F,
                FaceKind.RIGHT_EDGE, null, false, false), 401);
        paintRegion(image, player, display, sourceTexture, new FaceRegion(
                1.0F, 0.0F, 11.0F, 1.0F,
                FaceKind.TOP_EDGE, null, false, false), 503);
        paintRegion(image, player, display, sourceTexture, new FaceRegion(
                11.0F, 0.0F, 21.0F, 1.0F,
                FaceKind.BOTTOM_EDGE, null, false, false), 601);

        entry.texture.upload();
        entry.texture.setFilter(false, false);
        entry.source = sourceTexture;
        entry.signature = signature;
    }

    private static void paintRegion(NativeImage image, AbstractClientPlayer player,
            ClientMudState.CoverageState display,
            ResourceLocation sourceTexture, FaceRegion region, int salt) {
        int minX = Mth.clamp(Mth.floor(region.minU * image.getWidth() / REFERENCE_WIDTH),
                0, image.getWidth());
        int maxX = Mth.clamp(Mth.ceil(region.maxU * image.getWidth() / REFERENCE_WIDTH),
                0, image.getWidth());
        int minY = Mth.clamp(Mth.floor(region.minV * image.getHeight() / REFERENCE_HEIGHT),
                0, image.getHeight());
        int maxY = Mth.clamp(Mth.ceil(region.maxV * image.getHeight() / REFERENCE_HEIGHT),
                0, image.getHeight());
        for (int y = minY; y < maxY; y++) {
            float modelV = (y + 0.5F) * REFERENCE_HEIGHT / image.getHeight();
            float regionV = Mth.clamp((modelV - region.minV) / (region.maxV - region.minV),
                    0.0F, Math.nextDown(1.0F));
            float v = logicalV(regionV, region.mirrorV);
            for (int x = minX; x < maxX; x++) {
                float modelU = (x + 0.5F) * REFERENCE_WIDTH / image.getWidth();
                float u = Mth.clamp((modelU - region.minU) / (region.maxU - region.minU),
                        0.0F, Math.nextDown(1.0F));
                u = logicalU(u, region.mirrorU);
                CapeSample sample = sampleForRegion(display, region, u, v);
                if (sample.coverage <= COVERAGE_EPSILON) {
                    continue;
                }
                int original = SkinPixelCache.pixel(sourceTexture, x, y);
                if (FastColor.ABGR32.alpha(original) <= 0) {
                    continue;
                }
                int textureX = MudSkinTextureCache.adaptiveCoverageSampleX(
                        x, sample.visualSource, ADAPTIVE_TEXTURE_PHASE)
                        + MudCoveragePatternSeed.sampleOffsetX(
                                display.coveragePatternSeed());
                int textureY = MudSkinTextureCache.adaptiveCoverageSampleY(
                        y, sample.visualSource, ADAPTIVE_TEXTURE_PHASE)
                        + MudCoveragePatternSeed.sampleOffsetY(
                                display.coveragePatternSeed());
                image.setPixelRGBA(x, y, MudSkinTextureCache.bakedOverlayPixel(
                        sample.medium, sample.appearance, sample.visualSource,
                        original, textureX, textureY, sample.coverage,
                        MudCoveragePatternSeed.mix(
                                salt + player.getId() * 31,
                                display.coveragePatternSeed())));
            }
        }
    }

    private static CapeSample sampleForRegion(ClientMudState.CoverageState display,
            FaceRegion region, float u, float v) {
        int row = Mth.clamp(Mth.floor(v * MudCapeLayout.ROWS),
                0, MudCapeLayout.ROWS - 1);
        int column = textureColumnForLogicalU(u);
        return switch (region.kind) {
            case BROAD -> sample(display, region.side, row, column);
            case LEFT_EDGE -> mergedSample(display, row, MudCapeLayout.COLUMNS - 1);
            case RIGHT_EDGE -> mergedSample(display, row, 0);
            case TOP_EDGE -> mergedSample(display, 0, column);
            case BOTTOM_EDGE -> mergedSample(display, MudCapeLayout.ROWS - 1, column);
        };
    }

    static int textureColumnForLogicalU(float logicalU) {
        int uvColumn = Mth.clamp(Mth.floor(logicalU * MudCapeLayout.COLUMNS),
                0, MudCapeLayout.COLUMNS - 1);
        return MudCapeLayout.COLUMNS - 1 - uvColumn;
    }

    static float logicalU(float regionU, boolean mirrored) {
        float clamped = Mth.clamp(regionU, 0.0F, Math.nextDown(1.0F));
        return mirrored ? Math.nextDown(1.0F) - clamped : clamped;
    }

    static float logicalV(float regionV, boolean mirrored) {
        float clamped = Mth.clamp(regionV, 0.0F, Math.nextDown(1.0F));
        return mirrored ? Math.nextDown(1.0F) - clamped : clamped;
    }

    static MudCapeLayout.Side broadSideForReferenceU(float minU) {
        return minU >= 12.0F ? MudCapeLayout.Side.INNER : MudCapeLayout.Side.OUTER;
    }

    private static CapeSample sample(ClientMudState.CoverageState display, MudCapeLayout.Side side,
            int row, int column) {
        if (!MireboundClientSettings.preciseUvOwnership()) {
            return mergedSample(display, row, column);
        }
        return exactSample(display, side, row, column);
    }

    private static CapeSample exactSample(ClientMudState.CoverageState display, MudCapeLayout.Side side,
            int row, int column) {
        SinkingMedium medium = display.capePixelMedium(side, row, column);
        int appearance = display.capePixelAppearance(side, row, column);
        long visualSource = display.capePixelVisualSource(side, row, column);
        int cell = MudCapeLayout.index(side, row, column);
        if (!MudCoverageAppearance.allowsCoveragePixel(
                medium, appearance, MudCoverageRules.DOMAIN_CAPE,
                cell, MudCapeLayout.CELL_COUNT)) {
            return new CapeSample(0.0F, medium, appearance, visualSource);
        }
        return new CapeSample(
                display.capePixelCoverage(side, row, column),
                medium,
                appearance,
                visualSource);
    }

    private static CapeSample mergedSample(ClientMudState.CoverageState display, int row, int column) {
        CapeSample front = exactSample(display, MudCapeLayout.Side.OUTER, row, column);
        CapeSample back = exactSample(display, MudCapeLayout.Side.INNER, row, column);
        return back.coverage > front.coverage ? back : front;
    }

    private enum FaceKind {
        BROAD,
        LEFT_EDGE,
        RIGHT_EDGE,
        TOP_EDGE,
        BOTTOM_EDGE
    }

    private record FaceRegion(float minU, float minV, float maxU, float maxV,
            FaceKind kind, MudCapeLayout.Side side, boolean mirrorU, boolean mirrorV) {
    }

    private record CapeSample(float coverage, SinkingMedium medium,
            int appearance, long visualSource) {
    }

    private static final class Entry {
        final ResourceLocation location;
        final DynamicTexture texture;
        final int width;
        final int height;
        ResourceLocation source;
        long signature = Long.MIN_VALUE;

        Entry(ResourceLocation location, DynamicTexture texture, int width, int height) {
            this.location = location;
            this.texture = texture;
            this.width = width;
            this.height = height;
        }
    }
}
