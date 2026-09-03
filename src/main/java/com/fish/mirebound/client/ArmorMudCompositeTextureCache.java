package com.fish.mirebound.client;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.ArmorTextureMudData;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudCoveragePatternSeed;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

final class ArmorMudCompositeTextureCache {
    private static final int CANONICAL_WIDTH = 64;
    private static final int CANONICAL_HEIGHT = 32;
    private static final int MAX_ENTRIES = 512;
    private static final long UNUSED_TICKS = 400L;
    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static long lastPruneTick = Long.MIN_VALUE;

    private ArmorMudCompositeTextureCache() {
    }

    static ResourceLocation textureFor(int entityId, ResourceLocation baseTexture, int tint, EquipmentSlot slot,
            MudBodyPart targetPart, ModelPart modelPart, ArmorMudData data, long gameTime) {
        if ((!hasCoverage(data, targetPart) && !hasAssimilation(entityId, targetPart))
                || !SkinPixelCache.hasPixels(baseTexture)) {
            return null;
        }
        int width = SkinPixelCache.width(baseTexture);
        int height = SkinPixelCache.height(baseTexture);
        if (width < CANONICAL_WIDTH || height < CANONICAL_HEIGHT) {
            return null;
        }

        Key key = new Key(entityId, baseTexture, tint, "armor:" + slot.getName(), targetPart, modelPart, false);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            evictOldestIfFull();
            entry = createEntry(width, height);
            CACHE.put(key, entry);
        }
        int signature = 31 * (31 * data.hashCode()
                + Long.hashCode(ClientAssimilationState.signature(entityId))) + Long.hashCode(
                animationSignature(data, null, targetPart, gameTime));
        if (entry.signature != signature) {
            if (!rebuild(entry, entityId, baseTexture, tint, slot, targetPart, modelPart, data, false,
                    ArmorTextureMudData.EMPTY)) {
                return null;
            }
            entry.signature = signature;
        }
        entry.lastSeenTick = gameTime;
        prune(gameTime);
        return entry.location;
    }

    static ResourceLocation accessoryTextureFor(int entityId, ResourceLocation baseTexture, String targetKey,
            MudBodyPart targetPart, ModelPart modelPart, ArmorMudData data,
            ArmorTextureMudData textureData, long gameTime) {
        if (!SkinPixelCache.hasPixels(baseTexture)) {
            return null;
        }
        int width = SkinPixelCache.width(baseTexture);
        int height = SkinPixelCache.height(baseTexture);
        ArmorTextureMudData.Layer directLayer = textureData.layer(baseTexture, width, height);
        boolean directCoverage = directLayer != null;
        if (width <= 0 || height <= 0 || !hasCoverage(data, targetPart)
                && !directCoverage && !hasAssimilation(entityId, targetPart)) {
            return null;
        }
        Key key = new Key(entityId, baseTexture, 0xFFFFFFFF, targetKey, targetPart, modelPart, true);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            evictOldestIfFull();
            entry = createEntry(width, height);
            CACHE.put(key, entry);
        }
        int signature = 31 * (31 * (31 * data.hashCode() + textureData.hashCode())
                + Long.hashCode(ClientAssimilationState.signature(entityId)))
                + Long.hashCode(animationSignature(data, directLayer, targetPart, gameTime));
        if (entry.signature != signature) {
            if (!rebuild(entry, entityId, baseTexture, 0xFFFFFFFF, null,
                    targetPart, modelPart, data, true, textureData)) {
                return null;
            }
            entry.signature = signature;
        }
        entry.lastSeenTick = gameTime;
        prune(gameTime);
        return entry.location;
    }

    static void reset() {
        for (Entry entry : CACHE.values()) {
            entry.close();
        }
        CACHE.clear();
        lastPruneTick = Long.MIN_VALUE;
        ArmorModelUvProjector.reset();
    }

    private static long animationSignature(ArmorMudData data, ArmorTextureMudData.Layer directLayer,
            MudBodyPart targetPart, long gameTime) {
        final long[] mediumMask = {0L};
        final long[] appearance = {0L};
        data.forEachVisual((cell, coverage, medium, visualSource) -> {
            if (coverage > 0.001F && MudSurfaceLayout.part(cell) == targetPart) {
                mediumMask[0] |= MudSkinTextureCache.mediumBit(medium);
                int revision = AdaptiveMudClientCache.appearanceRevision(
                        Minecraft.getInstance().level, visualSource);
                if (revision != 0) {
                    appearance[0] = (appearance[0] * 31L
                            + Long.hashCode(visualSource)) * 31L
                            + Integer.toUnsignedLong(revision);
                }
            }
        });
        if (directLayer != null) {
            directLayer.forEachVisual((pixel, coverage, medium, visualSource) -> {
                if (coverage > 0.001F) {
                    mediumMask[0] |= MudSkinTextureCache.mediumBit(medium);
                    int revision = AdaptiveMudClientCache.appearanceRevision(
                            Minecraft.getInstance().level, visualSource);
                    if (revision != 0) {
                        appearance[0] = (appearance[0] * 31L
                                + Long.hashCode(visualSource)) * 31L
                                + Integer.toUnsignedLong(revision);
                    }
                }
            });
        }
        long animation = MudSkinTextureCache.skinCoverageAnimationSignature(
                mediumMask[0], gameTime);
        return appearance[0] == 0L ? animation : appearance[0] * 31L + animation;
    }

    private static Entry createEntry(int width, int height) {
        DynamicTexture texture = new DynamicTexture(width, height, true);
        texture.setFilter(false, false);
        ResourceLocation location = Minecraft.getInstance().getTextureManager()
                .register("mirebound_composite_armor_mud", texture);
        return new Entry(texture, location);
    }

    private static boolean rebuild(Entry entry, int entityId, ResourceLocation baseTexture, int tint,
            EquipmentSlot slot, MudBodyPart targetPart, ModelPart modelPart,
            ArmorMudData data, boolean projectedOnly,
            ArmorTextureMudData textureData) {
        NativeImage image = entry.texture.getPixels();
        if (image == null) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, tint(SkinPixelCache.pixel(baseTexture, x, y), tint));
            }
        }

        ArmorMudPaintPlan mudPlan = ArmorMudPaintPlan.build(data, targetPart);
        int patternSeed = data.coveragePatternSeed() != 0
                ? data.coveragePatternSeed()
                : ClientMudState.coveragePatternSeed(entityId);
        float[] coverageByCell = new float[MudSurfaceLayout.CELL_COUNT];
        SinkingMedium[] mediumByCell = new SinkingMedium[MudSurfaceLayout.CELL_COUNT];
        long[] visualSourceByCell = new long[MudSurfaceLayout.CELL_COUNT];
        boolean[] assimilationByCell = new boolean[MudSurfaceLayout.CELL_COUNT];
        ClientAssimilationState.View assimilation = ClientAssimilationState.view(entityId);
        if (assimilation != null && assimilation.profile().armorEnabled()) {
            for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
                if (MudSurfaceLayout.part(cell) != targetPart) {
                    continue;
                }
                float coverage = ClientAssimilationState.coverage(entityId, cell);
                if (coverage <= 0.001F) {
                    continue;
                }
                SinkingMedium assimilationMedium =
                        ClientAssimilationState.medium(entityId, cell);
                coverageByCell[cell] = coverage;
                mediumByCell[cell] = assimilationMedium;
                assimilationByCell[cell] = true;
                if (!projectedOnly) {
                    TexturePixel uv = texturePixel(targetPart, MudSurfaceLayout.surface(cell),
                            MudSurfaceLayout.row(cell), MudSurfaceLayout.column(cell));
                    paintAssimilationCanonicalPixel(
                            image, uv, entityId, cell, coverage);
                    if (ClientAssimilationState.rescueFractureEdge(entityId, cell)) {
                        darkenCanonicalPixel(image, uv,
                                assimilation.profile().rescueCrackDarkness(), cell);
                    }
                }
            }
        }
        for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
            MudBodyPart part = MudSurfaceLayout.part(cell);
            float coverage = mudPlan.coverage(cell);
            if (part != targetPart || coverage <= 0.001F) {
                continue;
            }
            SinkingMedium medium = mudPlan.medium(cell);
            long visualSource = mudPlan.visualSource(cell);
            int coverageIndex = slot == null ? cell : ArmorMudManager.ownedCellIndex(slot, cell);
            int coverageCount = slot == null
                    ? MudSurfaceLayout.CELL_COUNT : ArmorMudManager.ownedCellCount(slot);
            int coverageDomain = slot == null
                    ? MudCoverageRules.armorDomain(4 + targetPart.ordinal())
                    : MudCoverageRules.armorDomain(ArmorMudManager.armorSlotIndex(slot));
            if (!MudCoverageAppearance.allowsCoveragePixel(
                    medium, coverageDomain, coverageIndex, coverageCount)) {
                continue;
            }
            coverageByCell[cell] = coverage;
            mediumByCell[cell] = medium;
            visualSourceByCell[cell] = visualSource;
            assimilationByCell[cell] = false;
            if (projectedOnly) {
                continue;
            }
            TexturePixel uv = texturePixel(
                    part,
                    MudSurfaceLayout.surface(cell),
                    MudSurfaceLayout.row(cell),
                    MudSurfaceLayout.column(cell));
            paintCanonicalPixel(image, uv, cell, coverage, medium, visualSource,
                    patternSeed);
        }
        boolean hasDirectTextureCoverage = textureData.layer(baseTexture, width, height) != null;
        if (!hasDirectTextureCoverage) {
            paintProjectedModelPixels(
                    image, targetPart, modelPart, coverageByCell, mediumByCell,
                    visualSourceByCell,
                    assimilationByCell,
                    !projectedOnly, entityId, patternSeed);
        }
        paintDirectTexturePixels(image, baseTexture, textureData, patternSeed);
        entry.texture.upload();
        entry.texture.setFilter(false, false);
        return true;
    }

    private static void paintCanonicalPixel(NativeImage image, TexturePixel uv, int cell,
            float coverage, SinkingMedium medium, long visualSource,
            int patternSeed) {
        float scale = image.getWidth() / (float) CANONICAL_WIDTH;
        int x0 = Mth.clamp(Mth.floor(uv.x * scale), 0, image.getWidth());
        int x1 = Mth.clamp(Mth.ceil((uv.x + 1) * scale), 0, image.getWidth());
        int y0 = Mth.clamp(Mth.floor(uv.y * scale), 0, image.getHeight());
        int y1 = Mth.clamp(Mth.ceil((uv.y + 1) * scale), 0, image.getHeight());
        int alpha = Math.round(255.0F * Mth.clamp(coverage, 0.0F, 1.0F));
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                paintPixel(image, x, y, cell, medium, visualSource, alpha,
                        patternSeed);
            }
        }
    }

    private static void paintAssimilationCanonicalPixel(NativeImage image, TexturePixel uv,
            int entityId, int cell, float coverage) {
        float scale = image.getWidth() / (float) CANONICAL_WIDTH;
        int x0 = Mth.clamp(Mth.floor(uv.x * scale), 0, image.getWidth());
        int x1 = Mth.clamp(Mth.ceil((uv.x + 1) * scale), 0, image.getWidth());
        int y0 = Mth.clamp(Mth.floor(uv.y * scale), 0, image.getHeight());
        int y1 = Mth.clamp(Mth.ceil((uv.y + 1) * scale), 0, image.getHeight());
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int base = image.getPixelRGBA(x, y);
                if (FastColor.ABGR32.alpha(base) == 0) {
                    continue;
                }
                image.setPixelRGBA(x, y,
                        MudSkinTextureCache.blendedAssimilationOverlayPixel(
                                entityId, cell, base, x, y, coverage,
                                cell * 31 + 0x41A55A17, true));
            }
        }
    }

    private static void paintProjectedModelPixels(NativeImage image, MudBodyPart targetPart, ModelPart modelPart,
            float[] coverageByCell, SinkingMedium[] mediumByCell,
            long[] visualSourceByCell,
            boolean[] assimilationByCell, boolean skipCanonicalUv,
            int entityId, int patternSeed) {
        if (modelPart == null) {
            return;
        }
        ArmorModelUvProjector.Plan plan = ArmorModelUvProjector.plan(
                modelPart, targetPart, image.getWidth(), image.getHeight());
        if (plan.candidates().isEmpty()) {
            return;
        }
        boolean[] canonicalUv = skipCanonicalUv
                ? canonicalUvMask(targetPart, image.getWidth(), image.getHeight())
                : null;
        for (Map.Entry<Integer, int[]> mapping : plan.candidates().entrySet()) {
            int pixel = mapping.getKey();
            if (pixel < 0 || pixel >= image.getWidth() * image.getHeight()
                    || canonicalUv != null && canonicalUv[pixel]) {
                continue;
            }
            int bestCell = -1;
            float bestCoverage = 0.0F;
            int[] candidates = mapping.getValue();
            for (int cell : candidates) {
                if (cell >= 0 && cell < coverageByCell.length && coverageByCell[cell] > bestCoverage) {
                    bestCell = cell;
                    bestCoverage = coverageByCell[cell];
                }
            }
            if (bestCell < 0 || mediumByCell[bestCell] == null) {
                continue;
            }
            int x = pixel % image.getWidth();
            int y = pixel / image.getWidth();
            int alpha = Math.round(255.0F * Mth.clamp(bestCoverage, 0.0F, 1.0F));
            if (assimilationByCell[bestCell]) {
                int base = image.getPixelRGBA(x, y);
                if (FastColor.ABGR32.alpha(base) > 0) {
                    image.setPixelRGBA(x, y,
                            MudSkinTextureCache.blendedAssimilationOverlayPixel(
                                    entityId, bestCell, base, x, y, bestCoverage,
                                    bestCell * 31 + 0x41A55A17, true));
                }
            } else {
                paintPixel(image, x, y, bestCell, mediumByCell[bestCell],
                        visualSourceByCell[bestCell], alpha, patternSeed);
            }
            ClientAssimilationState.View assimilation = ClientAssimilationState.view(entityId);
            if (assimilation != null
                    && ClientAssimilationState.rescueFractureEdge(entityId, bestCell)) {
                image.setPixelRGBA(x, y, MudSkinTextureCache.assimilationFracturePixel(
                        image.getPixelRGBA(x, y), assimilation.profile().rescueCrackDarkness(),
                        bestCell));
            }
        }
    }

    private static void darkenCanonicalPixel(NativeImage image, TexturePixel uv,
            float darkness, int salt) {
        float scale = image.getWidth() / (float) CANONICAL_WIDTH;
        int x0 = Mth.clamp(Mth.floor(uv.x * scale), 0, image.getWidth());
        int x1 = Mth.clamp(Mth.ceil((uv.x + 1) * scale), 0, image.getWidth());
        int y0 = Mth.clamp(Mth.floor(uv.y * scale), 0, image.getHeight());
        int y1 = Mth.clamp(Mth.ceil((uv.y + 1) * scale), 0, image.getHeight());
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                image.setPixelRGBA(x, y, MudSkinTextureCache.assimilationFracturePixel(
                        image.getPixelRGBA(x, y), darkness, salt));
            }
        }
    }

    private static void paintDirectTexturePixels(NativeImage image, ResourceLocation baseTexture,
            ArmorTextureMudData textureData, int patternSeed) {
        ArmorTextureMudData.Layer layer = textureData.layer(baseTexture, image.getWidth(), image.getHeight());
        if (layer == null) {
            return;
        }
        List<DirectPaint> sources = new ArrayList<>(layer.dirtyPixelCount());
        Set<Integer> corePixels = new HashSet<>(layer.dirtyPixelCount() * 2);
        layer.forEachVisual((pixel, coverage, medium, visualSource) -> {
            int coverageDomain = MudCoverageRules.textureDomain(
                    baseTexture.hashCode(), image.getWidth(), image.getHeight());
            if (!MudCoverageAppearance.allowsCoveragePixel(
                    medium, coverageDomain, pixel,
                    image.getWidth() * image.getHeight())) {
                return;
            }
            int x = pixel % image.getWidth();
            int y = pixel / image.getWidth();
            int alpha = Math.round(255.0F * Mth.clamp(coverage, 0.0F, 1.0F));
            paintPixel(image, x, y, pixel * 31, medium, visualSource, alpha,
                    patternSeed);
            sources.add(new DirectPaint(pixel, coverage, medium, visualSource));
            corePixels.add(pixel);
        });
        Map<Integer, DirectPaint> fringe = new HashMap<>();
        for (DirectPaint source : sources) {
            int x = source.pixel() % image.getWidth();
            int y = source.pixel() / image.getWidth();
            offerDirectFringe(image, baseTexture, corePixels, fringe, source, x - 1, y, 0, patternSeed);
            offerDirectFringe(image, baseTexture, corePixels, fringe, source, x + 1, y, 1, patternSeed);
            offerDirectFringe(image, baseTexture, corePixels, fringe, source, x, y - 1, 2, patternSeed);
            offerDirectFringe(image, baseTexture, corePixels, fringe, source, x, y + 1, 3, patternSeed);
        }
        for (DirectPaint paint : fringe.values()) {
            int x = paint.pixel() % image.getWidth();
            int y = paint.pixel() / image.getWidth();
            int alpha = Math.round(255.0F * paint.coverage());
            paintPixel(image, x, y, paint.pixel() * 31 + 503,
                    paint.medium(), paint.visualSource(), alpha, patternSeed);
        }
    }

    private static void offerDirectFringe(NativeImage image, ResourceLocation baseTexture,
            Set<Integer> corePixels, Map<Integer, DirectPaint> fringe, DirectPaint source,
            int x, int y, int direction, int patternSeed) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()
                || FastColor.ABGR32.alpha(SkinPixelCache.pixel(baseTexture, x, y)) <= 0) {
            return;
        }
        int pixel = y * image.getWidth() + x;
        if (corePixels.contains(pixel)) {
            return;
        }
        int coverageDomain = MudCoverageRules.textureDomain(
                baseTexture.hashCode(), image.getWidth(), image.getHeight());
        if (!MudCoverageAppearance.allowsCoveragePixel(
                source.medium(), coverageDomain, pixel,
                image.getWidth() * image.getHeight())) {
            return;
        }
        int salt = MudCoveragePatternSeed.mix(
                source.pixel() * 31 + direction * 101, patternSeed);
        float noise = directNoise(pixel, salt);
        float chance = source.medium().opaqueCoverage() ? 0.58F : 0.44F;
        if (noise > chance) {
            return;
        }
        float scale = source.medium().opaqueCoverage() ? 0.26F : 0.18F;
        DirectPaint candidate = new DirectPaint(pixel,
                Mth.clamp(source.coverage() * scale, 0.0F, 0.38F), source.medium(),
                source.visualSource());
        DirectPaint existing = fringe.get(pixel);
        if (existing == null || candidate.coverage() > existing.coverage()) {
            fringe.put(pixel, candidate);
        }
    }

    private static float directNoise(int pixel, int salt) {
        int value = pixel * 73428767 ^ salt * 9122719;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return (value & 1023) / 1023.0F;
    }

    private static boolean[] canonicalUvMask(MudBodyPart part, int width, int height) {
        boolean[] result = new boolean[width * height];
        for (MudSurface surface : MudSurface.values()) {
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
            for (int row = 0; row < face.height(); row++) {
                for (int column = 0; column < face.width(); column++) {
                    TexturePixel uv = texturePixel(part, surface, row, column);
                    float scale = width / (float) CANONICAL_WIDTH;
                    int x0 = Mth.clamp(Mth.floor(uv.x * scale), 0, width);
                    int x1 = Mth.clamp(Mth.ceil((uv.x + 1) * scale), 0, width);
                    int y0 = Mth.clamp(Mth.floor(uv.y * scale), 0, height);
                    int y1 = Mth.clamp(Mth.ceil((uv.y + 1) * scale), 0, height);
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            result[y * width + x] = true;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void paintPixel(NativeImage image, int x, int y, int cell,
            SinkingMedium medium, long visualSource, int alpha,
            int patternSeed) {
        int base = image.getPixelRGBA(x, y);
        if (FastColor.ABGR32.alpha(base) == 0) {
            return;
        }
        int salt = MudCoveragePatternSeed.mix(cell * 31, patternSeed);
        int variedAlpha = Math.round(alpha
                * MudCoverageAppearance.opacityScale(medium, x, y, salt));
        int texturePhase = cell >= 0 && cell < MudSurfaceLayout.CELL_COUNT
                ? MudSurfaceLayout.part(cell).ordinal() : 0;
        int textureX = MudSkinTextureCache.adaptiveCoverageSampleX(
                x, visualSource, texturePhase)
                + MudCoveragePatternSeed.sampleOffsetX(patternSeed);
        int textureY = MudSkinTextureCache.adaptiveCoverageSampleY(
                y, visualSource, texturePhase)
                + MudCoveragePatternSeed.sampleOffsetY(patternSeed);
        int mud = MudSkinTextureCache.skinCoverageTextureAbgr(
                medium, visualSource, textureX, textureY, salt, variedAlpha);
        image.setPixelRGBA(x, y, blendInsideBase(base, mud));
    }

    private static int tint(int abgr, int argbTint) {
        int alpha = FastColor.ABGR32.alpha(abgr) * FastColor.ARGB32.alpha(argbTint) / 255;
        int red = FastColor.ABGR32.red(abgr) * FastColor.ARGB32.red(argbTint) / 255;
        int green = FastColor.ABGR32.green(abgr) * FastColor.ARGB32.green(argbTint) / 255;
        int blue = FastColor.ABGR32.blue(abgr) * FastColor.ARGB32.blue(argbTint) / 255;
        return FastColor.ABGR32.color(alpha, blue, green, red);
    }

    private static int blendInsideBase(int base, int mud) {
        int amount = FastColor.ABGR32.alpha(mud);
        int inverse = 255 - amount;
        int red = (FastColor.ABGR32.red(base) * inverse + FastColor.ABGR32.red(mud) * amount) / 255;
        int green = (FastColor.ABGR32.green(base) * inverse + FastColor.ABGR32.green(mud) * amount) / 255;
        int blue = (FastColor.ABGR32.blue(base) * inverse + FastColor.ABGR32.blue(mud) * amount) / 255;
        return FastColor.ABGR32.color(FastColor.ABGR32.alpha(base), blue, green, red);
    }

    private static boolean hasCoverage(ArmorMudData data, MudBodyPart targetPart) {
        boolean[] found = {false};
        data.forEach((cell, coverage, medium) -> found[0] |= coverage > 0.001F
                && MudSurfaceLayout.part(cell) == targetPart);
        return found[0];
    }

    private static boolean hasAssimilation(int entityId, MudBodyPart targetPart) {
        ClientAssimilationState.View view = ClientAssimilationState.view(entityId);
        if (view == null || !view.profile().armorEnabled() || view.progress() <= 0.0001F) {
            return false;
        }
        for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
            if (MudSurfaceLayout.part(cell) == targetPart
                    && ClientAssimilationState.coverage(entityId, cell) > 0.001F) {
                return true;
            }
        }
        return false;
    }

    private static TexturePixel texturePixel(MudBodyPart part, MudSurface surface, int row, int column) {
        CubeUv cube = cubeUv(part);
        int uvColumn = (surface == MudSurface.LEFT || surface == MudSurface.BACK)
                ? MudSurfaceLayout.face(part, surface).width() - 1 - column
                : column;
        int uvRow = surface == MudSurface.TOP || surface == MudSurface.BOTTOM
                ? row
                : MudSurfaceLayout.face(part, surface).height() - 1 - row;
        return switch (surface) {
            case TOP -> new TexturePixel(cube.x + cube.depth + uvColumn, cube.y + uvRow);
            case BOTTOM -> new TexturePixel(cube.x + cube.depth + cube.width + uvColumn, cube.y + uvRow);
            case RIGHT -> new TexturePixel(cube.x + uvColumn, cube.y + cube.depth + uvRow);
            case FRONT -> new TexturePixel(cube.x + cube.depth + uvColumn, cube.y + cube.depth + uvRow);
            case LEFT -> new TexturePixel(cube.x + cube.depth + cube.width + uvColumn, cube.y + cube.depth + uvRow);
            case BACK -> new TexturePixel(cube.x + cube.depth * 2 + cube.width + uvColumn, cube.y + cube.depth + uvRow);
        };
    }

    private static CubeUv cubeUv(MudBodyPart part) {
        return switch (part) {
            case HEAD -> new CubeUv(0, 0, 8, 8, 8);
            case BODY -> new CubeUv(16, 16, 8, 12, 4);
            case LEFT_ARM, RIGHT_ARM -> new CubeUv(40, 16, 4, 12, 4);
            case LEFT_LEG, RIGHT_LEG -> new CubeUv(0, 16, 4, 12, 4);
        };
    }

    private static void prune(long gameTime) {
        if (lastPruneTick != Long.MIN_VALUE && gameTime - lastPruneTick >= 0L
                && gameTime - lastPruneTick < 100L) {
            return;
        }
        lastPruneTick = gameTime;
        Iterator<Entry> iterator = CACHE.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            long age = gameTime - entry.lastSeenTick;
            if (age > UNUSED_TICKS || age < 0L) {
                entry.close();
                iterator.remove();
            }
        }
    }

    private static void evictOldestIfFull() {
        while (CACHE.size() >= MAX_ENTRIES) {
            Key oldestKey = null;
            Entry oldest = null;
            for (Map.Entry<Key, Entry> candidate : CACHE.entrySet()) {
                if (oldest == null || candidate.getValue().lastSeenTick < oldest.lastSeenTick) {
                    oldestKey = candidate.getKey();
                    oldest = candidate.getValue();
                }
            }
            if (oldestKey == null) {
                return;
            }
            CACHE.remove(oldestKey);
            oldest.close();
        }
    }

    private record Key(int entityId, ResourceLocation baseTexture, int tint, String targetKey,
            MudBodyPart part, ModelPart modelPart, boolean accessory) {
    }

    private record CubeUv(int x, int y, int width, int height, int depth) {
    }

    private record TexturePixel(int x, int y) {
    }

    private record DirectPaint(int pixel, float coverage, SinkingMedium medium,
            long visualSource) {
    }

    private static final class Entry {
        private final DynamicTexture texture;
        private final ResourceLocation location;
        private int signature = Integer.MIN_VALUE;
        private long lastSeenTick;

        private Entry(DynamicTexture texture, ResourceLocation location) {
            this.texture = texture;
            this.location = location;
        }

        private void close() {
            DynamicTextureLifecycle.release(location, texture);
        }
    }
}
