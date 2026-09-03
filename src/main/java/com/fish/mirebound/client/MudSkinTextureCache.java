package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudCoveragePatternSeed;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class MudSkinTextureCache {
    private static final FaceSection[] WIDE_FACE_SECTIONS = createFaceSections(false);
    private static final FaceSection[] SLIM_FACE_SECTIONS = createFaceSections(true);
    private static final Map<Integer, Entry> CACHE_BY_ENTITY = new HashMap<>();
    private static final Map<Integer, Entry> BAKED_CACHE_BY_ENTITY = new HashMap<>();
    private static final Map<ResourceLocation, Entry> CACHE_BY_TEXTURE = new HashMap<>();
    private static final Map<ResourceLocation, Entry> BAKED_CACHE_BY_TEXTURE = new HashMap<>();
    private static final Map<ResourceLocation, MudTexturePixels> COVER_TEXTURE_PIXELS = new HashMap<>();
    private static final Map<ResourceLocation, AnimatedRenderEntry> ANIMATED_RENDER_TEXTURES = new HashMap<>();
    private static final float EDGE_BAND_THRESHOLD = 0.00035F;
    private static final float ASSIMILATION_BLEND_RADIUS = 1.65F;
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAXIMUM_ENTITY_ENTRIES = 256;
    private static final int UNUSED_ENTITY_TICKS = 200;
    private static final int PRUNE_INTERVAL_TICKS = 100;
    private static int clientTick;
    private static int pruneTicks;

    private MudSkinTextureCache() {
    }

    static ResourceLocation textureFor(int entityId, ResourceLocation skinTexture, boolean slimModel) {
        return textureFor(entityId, skinTexture, slimModel, false);
    }

    static ResourceLocation overlayTextureFor(int entityId, ResourceLocation skinTexture, boolean slimModel) {
        ResolvedSkin resolved = resolveSkin(skinTexture, slimModel);
        return textureFor(entityId, resolved.skinTexture, resolved.slimModel, false);
    }

    public static ResourceLocation bakedSkinFor(int entityId, ResourceLocation skinTexture, boolean slimModel) {
        return textureFor(entityId, skinTexture, slimModel, true);
    }

    public static ResourceLocation bakedSkinForResolved(int entityId, ResourceLocation skinTexture, boolean slimModel) {
        ResolvedSkin resolved = resolveSkin(skinTexture, slimModel);
        return textureFor(entityId, resolved.skinTexture, resolved.slimModel, true);
    }

    public static boolean isGeneratedSkin(ResourceLocation skinTexture) {
        return BAKED_CACHE_BY_TEXTURE.containsKey(skinTexture);
    }

    public static DebugExportResult exportDebugTextures(int entityId, ResourceLocation skinTexture, boolean slimModel, Path screenshotsDirectory) throws IOException {
        ResolvedSkin resolved = resolveSkin(skinTexture, slimModel);
        ResourceLocation bakedLocation = textureFor(entityId, resolved.skinTexture, resolved.slimModel, true);
        ResourceLocation overlayLocation = textureFor(entityId, resolved.skinTexture, resolved.slimModel, false);
        if (bakedLocation == null || overlayLocation == null) {
            throw new IOException("No visible mud coverage to export.");
        }

        Entry bakedEntry = BAKED_CACHE_BY_TEXTURE.get(bakedLocation);
        Entry overlayEntry = CACHE_BY_TEXTURE.get(overlayLocation);
        if (bakedEntry == null || overlayEntry == null) {
            throw new IOException("Mud texture cache entry is missing.");
        }

        Files.createDirectories(screenshotsDirectory);
        String prefix = "mirebound_" + entityId + "_" + LocalDateTime.now().format(EXPORT_TIMESTAMP);
        Path originalPath = screenshotsDirectory.resolve(prefix + "_original.png");
        Path bakedPath = screenshotsDirectory.resolve(prefix + "_baked.png");
        Path overlayPath = screenshotsDirectory.resolve(prefix + "_overlay.png");
        Path coverageMaskPath = screenshotsDirectory.resolve(prefix + "_coverage_mask.png");

        writeOriginalSkin(resolved.skinTexture, bakedEntry.width, bakedEntry.height, originalPath);
        writeEntryImage(bakedEntry, bakedPath);
        writeEntryImage(overlayEntry, overlayPath);
        writeCoverageMask(entityId, resolved.skinTexture, resolved.slimModel, bakedEntry.width, bakedEntry.height, coverageMaskPath);
        return new DebugExportResult(originalPath, bakedPath, overlayPath, coverageMaskPath);
    }

    private static ResourceLocation textureFor(int entityId, ResourceLocation skinTexture, boolean slimModel, boolean bakedSkin) {
        if (!MireboundClientSettings.clientOptionEnabled(
                ClientOption.PLAYER_COVERAGE)) {
            return null;
        }
        if (!SkinPixelCache.hasPixels(skinTexture)) {
            return null;
        }

        long signature = signature(entityId);
        if (signature == 0L) {
            return null;
        }

        Map<Integer, Entry> cacheByEntity = bakedSkin ? BAKED_CACHE_BY_ENTITY : CACHE_BY_ENTITY;
        Map<ResourceLocation, Entry> cacheByTexture = bakedSkin ? BAKED_CACHE_BY_TEXTURE : CACHE_BY_TEXTURE;
        if (!cacheByEntity.containsKey(entityId)
                && cacheByEntity.size() >= MAXIMUM_ENTITY_ENTRIES) {
            prune(Minecraft.getInstance(), cacheByEntity, cacheByTexture, true);
            if (cacheByEntity.size() >= MAXIMUM_ENTITY_ENTRIES) {
                return null;
            }
        }
        Entry entry = cacheByEntity.computeIfAbsent(entityId, ignored -> new Entry());
        entry.lastSeenTick = clientTick;
        if (entry.texture == null || entry.location == null) {
            int width = SkinPixelCache.width(skinTexture);
            int height = SkinPixelCache.height(skinTexture);
            entry.texture = new DynamicTexture(width, height, true);
            entry.texture.setFilter(false, false);
            entry.location = Minecraft.getInstance().getTextureManager().register(bakedSkin ? "mirebound_baked_mud_skin" : "mirebound_mud_skin", entry.texture);
            cacheByTexture.put(entry.location, entry);
            entry.width = width;
            entry.height = height;
        }

        if (!skinTexture.equals(entry.skinTexture) || signature != entry.signature || slimModel != entry.slimModel) {
            rebuild(entry, entityId, skinTexture, slimModel, signature, bakedSkin);
        }

        return entry.location;
    }

    static void reset() {
        for (Entry entry : CACHE_BY_ENTITY.values()) {
            closeEntry(entry, CACHE_BY_TEXTURE);
        }
        CACHE_BY_ENTITY.clear();
        for (Entry entry : BAKED_CACHE_BY_ENTITY.values()) {
            closeEntry(entry, BAKED_CACHE_BY_TEXTURE);
        }
        BAKED_CACHE_BY_ENTITY.clear();
        CACHE_BY_TEXTURE.clear();
        BAKED_CACHE_BY_TEXTURE.clear();
        for (AnimatedRenderEntry entry : ANIMATED_RENDER_TEXTURES.values()) {
            MudSurfaceDecalRenderTypes.release(entry.location);
            Minecraft.getInstance().getTextureManager().release(entry.location);
        }
        ANIMATED_RENDER_TEXTURES.clear();
        COVER_TEXTURE_PIXELS.clear();
        clientTick = 0;
        pruneTicks = 0;
    }

    static void tick(Minecraft minecraft) {
        clientTick++;
        if (++pruneTicks < PRUNE_INTERVAL_TICKS) {
            return;
        }
        pruneTicks = 0;
        prune(minecraft, CACHE_BY_ENTITY, CACHE_BY_TEXTURE, false);
        prune(minecraft, BAKED_CACHE_BY_ENTITY, BAKED_CACHE_BY_TEXTURE, false);
    }

    static void clearEntity(int entityId) {
        closeEntry(CACHE_BY_ENTITY.remove(entityId), CACHE_BY_TEXTURE);
        closeEntry(BAKED_CACHE_BY_ENTITY.remove(entityId), BAKED_CACHE_BY_TEXTURE);
    }

    static void invalidateOrdinaryEntity(int entityId) {
        if (ClientAssimilationState.signature(entityId) == 0L) {
            clearEntity(entityId);
            return;
        }
        Entry overlay = CACHE_BY_ENTITY.get(entityId);
        if (overlay != null) {
            overlay.signature = Long.MIN_VALUE;
        }
        Entry baked = BAKED_CACHE_BY_ENTITY.get(entityId);
        if (baked != null) {
            baked.signature = Long.MIN_VALUE;
        }
    }

    private static void prune(Minecraft minecraft, Map<Integer, Entry> cacheByEntity,
            Map<ResourceLocation, Entry> cacheByTexture, boolean makeRoom) {
        var iterator = cacheByEntity.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Entry> stored = iterator.next();
            Entry entry = stored.getValue();
            boolean stale = minecraft.level == null
                    || minecraft.level.getEntity(stored.getKey()) == null
                    || clientTick - entry.lastSeenTick > UNUSED_ENTITY_TICKS;
            if (!stale) {
                continue;
            }
            iterator.remove();
            closeEntry(entry, cacheByTexture);
            if (makeRoom && cacheByEntity.size() < MAXIMUM_ENTITY_ENTRIES) {
                return;
            }
        }
    }

    private static void closeEntry(Entry entry, Map<ResourceLocation, Entry> cacheByTexture) {
        if (entry == null) {
            return;
        }
        if (entry.location != null) {
            cacheByTexture.remove(entry.location);
            Minecraft.getInstance().getTextureManager().release(entry.location);
        } else if (entry.texture != null) {
            entry.texture.close();
        }
    }

    static int textureWidth(ResourceLocation mudTexture) {
        Entry entry = CACHE_BY_TEXTURE.get(mudTexture);
        return entry == null ? 64 : entry.width;
    }

    static int textureHeight(ResourceLocation mudTexture) {
        Entry entry = CACHE_BY_TEXTURE.get(mudTexture);
        return entry == null ? 64 : entry.height;
    }

    static int pixel(ResourceLocation mudTexture, int x, int y) {
        Entry entry = CACHE_BY_TEXTURE.get(mudTexture);
        if (entry == null || entry.texture == null || entry.texture.getPixels() == null) {
            return 0;
        }
        if (x < 0 || y < 0 || x >= entry.width || y >= entry.height) {
            return 0;
        }

        return entry.texture.getPixels().getPixelRGBA(x, y);
    }

    private static void rebuild(Entry entry, int entityId, ResourceLocation skinTexture, boolean slimModel, long signature, boolean bakedSkin) {
        NativeImage pixels = entry.texture.getPixels();
        if (pixels == null) {
            pixels = new NativeImage(entry.width, entry.height, true);
            entry.texture.setPixels(pixels);
        }

        if (bakedSkin) {
            copySkinPixels(pixels, skinTexture);
        } else {
            pixels.fillRect(0, 0, entry.width, entry.height, 0);
        }
        applyAssimilationLayer(pixels, entityId, skinTexture, slimModel, bakedSkin);
        PaintPlan plan = buildPaintPlan(entityId, skinTexture, slimModel, entry.width, entry.height);
        applyPaintPlan(pixels, skinTexture, bakedSkin, plan);

        entry.texture.upload();
        entry.texture.setFilter(false, false);
        entry.skinTexture = skinTexture;
        entry.slimModel = slimModel;
        entry.signature = signature;
    }

    private static void copySkinPixels(NativeImage target, ResourceLocation skinTexture) {
        for (int y = 0; y < target.getHeight(); y++) {
            for (int x = 0; x < target.getWidth(); x++) {
                target.setPixelRGBA(x, y, SkinPixelCache.pixel(skinTexture, x, y));
            }
        }
    }

    private static void applyAssimilationLayer(NativeImage target, int entityId,
            ResourceLocation skinTexture, boolean slimModel, boolean bakedSkin) {
        if (ClientAssimilationState.signature(entityId) == 0L) {
            return;
        }
        FaceSection[] sections = slimModel ? SLIM_FACE_SECTIONS : WIDE_FACE_SECTIONS;
        for (FaceSection section : sections) {
            SectionBounds bounds = sectionBounds(section, target.getWidth(), target.getHeight());
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(section.part, section.surface);
            for (int py = bounds.y; py < bounds.y + bounds.height; py++) {
                for (int px = bounds.x; px < bounds.x + bounds.width; px++) {
                    if (!writableSkinPixel(skinTexture, bounds, px, py)) {
                        continue;
                    }
                    int localX = px - bounds.x;
                    int localY = py - bounds.y;
                    if (section.reverseLane) {
                        localX = bounds.width - 1 - localX;
                    }
                    int column = Math.min(face.width() - 1,
                            localX * face.width() / Math.max(1, bounds.width));
                    int uvRow = Math.min(face.height() - 1,
                            localY * face.height() / Math.max(1, bounds.height));
                    int row = face.vertical() ? face.height() - 1 - uvRow : uvRow;
                    int cell = MudSurfaceLayout.cellIndex(section.part, section.surface, row, column);
                    float strength = ClientAssimilationState.coverage(entityId, cell);
                    if (strength <= EDGE_BAND_THRESHOLD) {
                        continue;
                    }
                    int original = bakedSkin
                            ? target.getPixelRGBA(px, py)
                            : SkinPixelCache.pixel(skinTexture, px, py);
                    int color = blendedAssimilationOverlayPixel(
                            entityId, cell, original, px, py, strength,
                            section.salt, bakedSkin);
                    if (ClientAssimilationState.rescueFractureEdge(entityId, cell)) {
                        ClientAssimilationState.View view = ClientAssimilationState.view(entityId);
                        float darkness = view == null ? 0.0F
                                : view.profile().rescueCrackDarkness();
                        color = assimilationFracturePixel(color, darkness, section.salt + cell);
                    }
                    target.setPixelRGBA(px, py, color);
                }
            }
        }
    }

    static int assimilationFracturePixel(int color, float darkness, int salt) {
        int hash = salt * 0x632BE5AB;
        hash ^= hash >>> 15;
        float variation = 0.82F + (hash & 0xFF) / 255.0F * 0.18F;
        float retained = 1.0F - Mth.clamp(darkness * variation, 0.0F, 0.92F);
        return FastColor.ABGR32.color(
                FastColor.ABGR32.alpha(color),
                Math.round(FastColor.ABGR32.blue(color) * retained),
                Math.round(FastColor.ABGR32.green(color) * retained),
                Math.round(FastColor.ABGR32.red(color) * retained));
    }

    private static ResolvedSkin resolveSkin(ResourceLocation skinTexture, boolean slimModel) {
        Entry bakedEntry = BAKED_CACHE_BY_TEXTURE.get(skinTexture);
        if (bakedEntry != null && bakedEntry.skinTexture != null) {
            return new ResolvedSkin(bakedEntry.skinTexture, bakedEntry.slimModel);
        }
        return new ResolvedSkin(skinTexture, slimModel);
    }

    private static void writeEntryImage(Entry entry, Path path) throws IOException {
        if (entry.texture == null || entry.texture.getPixels() == null) {
            throw new IOException("Mud texture pixels are not available.");
        }
        entry.texture.getPixels().writeToFile(path);
    }

    private static void writeOriginalSkin(ResourceLocation skinTexture, int width, int height, Path path) throws IOException {
        try (NativeImage image = new NativeImage(width, height, true)) {
            copySkinPixels(image, skinTexture);
            image.writeToFile(path);
        }
    }

    private static void writeCoverageMask(int entityId, ResourceLocation skinTexture, boolean slimModel, int width, int height, Path path) throws IOException {
        try (NativeImage image = new NativeImage(width, height, true)) {
            image.fillRect(0, 0, width, height, 0);
            PaintPlan plan = buildPaintPlan(entityId, skinTexture, slimModel, width, height);
            paintCoverageMask(image, plan);
            image.writeToFile(path);
        }
    }

    private static PaintPlan buildPaintPlan(int entityId, ResourceLocation skinTexture, boolean slimModel, int textureWidth, int textureHeight) {
        int patternSeed = ClientMudState.coveragePatternSeed(entityId);
        PaintPlan plan = new PaintPlan(textureWidth, textureHeight, patternSeed);
        FaceSection[] sections = slimModel ? SLIM_FACE_SECTIONS : WIDE_FACE_SECTIONS;
        ClientMudState.CoverageState display = ClientMudState.displaySnapshot(entityId);
        for (FaceSection section : sections) {
            planSection(plan, display, skinTexture, section, patternSeed);
        }
        diffusePlan(plan, skinTexture, sections);
        return plan;
    }

    private static void planSection(PaintPlan plan, ClientMudState.CoverageState display,
            ResourceLocation skinTexture, FaceSection section, int patternSeed) {
        SectionBounds bounds = sectionBounds(section, plan.width, plan.height);

        for (int py = bounds.y; py < bounds.y + bounds.height; py++) {
            for (int px = bounds.x; px < bounds.x + bounds.width; px++) {
                if (!writableSkinPixel(skinTexture, bounds, px, py)) {
                    continue;
                }

                PaintCandidate best = bestCandidateForPixel(
                        display, section, bounds, px, py, patternSeed);
                if (best == null) {
                    continue;
                }

                plan.offer(py * plan.width + px, best.medium, best.appearance,
                        best.visualSource,
                        best.strength, best.salt, best.texturePhase, best.score);
            }
        }
    }

    private static PaintCandidate bestCandidateForPixel(ClientMudState.CoverageState display,
            FaceSection section, SectionBounds bounds, int px, int py,
            int patternSeed) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(section.part, section.surface);
        int localX = px - bounds.x;
        int localY = py - bounds.y;
        if (section.reverseLane) {
            localX = bounds.width - 1 - localX;
        }
        int columnStart = localX * face.width() / bounds.width;
        int columnEnd = Math.min(face.width(), divideCeil((localX + 1) * face.width(), bounds.width));
        int uvRowStart = localY * face.height() / bounds.height;
        int uvRowEnd = Math.min(face.height(), divideCeil((localY + 1) * face.height(), bounds.height));
        int rowStart = face.vertical() ? face.height() - uvRowEnd : uvRowStart;
        int rowEnd = face.vertical() ? face.height() - uvRowStart : uvRowEnd;

        PaintCandidate best = null;
        for (int row = rowStart; row < rowEnd; row++) {
            for (int column = columnStart; column < columnEnd; column++) {
                PaintSample sample = paintSampleFor(display, section, row, column);
                if (sample.strength <= EDGE_BAND_THRESHOLD) {
                    continue;
                }
                int salt = MudCoveragePatternSeed.mix(
                        section.salt + row * 17 + column, patternSeed);
                PaintCandidate candidate = new PaintCandidate(
                        sample.medium, sample.appearance, sample.visualSource,
                        sample.strength, salt, section.part.ordinal(),
                        3.0F + sample.strength);
                if (best == null || candidate.score > best.score) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static int divideCeil(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static boolean writableSkinPixel(ResourceLocation skinTexture, SectionBounds bounds, int px, int py) {
        int maskPixel = SkinPixelCache.pixel(skinTexture, bounds.maskX + px - bounds.x, bounds.maskY + py - bounds.y);
        if (FastColor.ABGR32.alpha(maskPixel) <= 0) {
            return false;
        }

        int original = SkinPixelCache.pixel(skinTexture, px, py);
        return FastColor.ABGR32.alpha(original) > 0;
    }

    private static void applyPaintPlan(NativeImage target, ResourceLocation skinTexture, boolean bakedSkin, PaintPlan plan) {
        for (int y = 0; y < plan.height; y++) {
            for (int x = 0; x < plan.width; x++) {
                int index = y * plan.width + x;
                float strength = plan.strength[index];
                if (strength <= EDGE_BAND_THRESHOLD) {
                    continue;
                }

                int original = SkinPixelCache.pixel(skinTexture, x, y);
                int originalAlpha = FastColor.ABGR32.alpha(original);
                if (originalAlpha <= 0) {
                    continue;
                }

                int salt = plan.salt[index];
                float noise = pixelNoise(x, y, salt);
                SinkingMedium medium = SinkingMedium.byId(plan.medium[index] & 0xFF);
                int base = bakedSkin ? target.getPixelRGBA(x, y) : original;
                int textureX = adaptiveCoverageSampleX(
                        x, plan.visualSource[index], plan.texturePhase[index])
                        + plan.sampleOffsetX;
                int textureY = adaptiveCoverageSampleY(
                        y, plan.visualSource[index], plan.texturePhase[index])
                        + plan.sampleOffsetY;
                int covered = coverPixel(medium, plan.appearance[index],
                        plan.visualSource[index], base,
                        originalAlpha, textureX, textureY,
                        strength, noise, salt, bakedSkin);
                target.setPixelRGBA(x, y, bakedSkin
                        ? covered : sourceOver(covered, target.getPixelRGBA(x, y)));
            }
        }
    }

    private static int sourceOver(int foreground, int background) {
        int foregroundAlpha = FastColor.ABGR32.alpha(foreground);
        int backgroundAlpha = FastColor.ABGR32.alpha(background);
        int inverseForeground = 255 - foregroundAlpha;
        int outputAlpha = foregroundAlpha + (backgroundAlpha * inverseForeground + 127) / 255;
        if (outputAlpha <= 0) {
            return 0;
        }
        int backgroundWeight = (backgroundAlpha * inverseForeground + 127) / 255;
        int red = (FastColor.ABGR32.red(foreground) * foregroundAlpha
                + FastColor.ABGR32.red(background) * backgroundWeight) / outputAlpha;
        int green = (FastColor.ABGR32.green(foreground) * foregroundAlpha
                + FastColor.ABGR32.green(background) * backgroundWeight) / outputAlpha;
        int blue = (FastColor.ABGR32.blue(foreground) * foregroundAlpha
                + FastColor.ABGR32.blue(background) * backgroundWeight) / outputAlpha;
        return FastColor.ABGR32.color(outputAlpha, blue, green, red);
    }

    private static void diffusePlan(PaintPlan plan, ResourceLocation skinTexture, FaceSection[] sections) {
        float[] sourceStrength = plan.strength.clone();
        byte[] sourceMedium = plan.medium.clone();
        int[] sourceAppearance = plan.appearance.clone();
        long[] sourceVisualSource = plan.visualSource.clone();
        int[] sourceSalt = plan.salt.clone();
        byte[] sourceTexturePhase = plan.texturePhase.clone();
        float[] sourceScore = plan.score.clone();
        for (int cubeStart = 0; cubeStart < sections.length; cubeStart += MudSurface.COUNT) {
            for (int sectionOffset = 0; sectionOffset < MudSurface.COUNT; sectionOffset++) {
                FaceSection section = sections[cubeStart + sectionOffset];
                SectionBounds bounds = sectionBounds(section, plan.width, plan.height);
                for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
                    for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                        int index = y * plan.width + x;
                        float strength = sourceStrength[index];
                        if (strength <= EDGE_BAND_THRESHOLD) {
                            continue;
                        }

                        SinkingMedium medium = SinkingMedium.byId(sourceMedium[index] & 0xFF);
                        diffuseToNeighbor(plan, skinTexture, bounds, sourceStrength, sourceMedium,
                                sourceAppearance, sourceVisualSource,
                                sourceSalt, sourceTexturePhase, sourceScore,
                                x, y, index, x - 1, y, 0, medium);
                        diffuseToNeighbor(plan, skinTexture, bounds, sourceStrength, sourceMedium,
                                sourceAppearance, sourceVisualSource,
                                sourceSalt, sourceTexturePhase, sourceScore,
                                x, y, index, x + 1, y, 1, medium);
                        diffuseToNeighbor(plan, skinTexture, bounds, sourceStrength, sourceMedium,
                                sourceAppearance, sourceVisualSource,
                                sourceSalt, sourceTexturePhase, sourceScore,
                                x, y, index, x, y - 1, 2, medium);
                        diffuseToNeighbor(plan, skinTexture, bounds, sourceStrength, sourceMedium,
                                sourceAppearance, sourceVisualSource,
                                sourceSalt, sourceTexturePhase, sourceScore,
                                x, y, index, x, y + 1, 3, medium);
                        diffuseAcrossFaceEdges(plan, skinTexture, sections, cubeStart, section, bounds,
                                sourceStrength, sourceMedium, sourceAppearance, sourceVisualSource,
                                sourceSalt, sourceTexturePhase, sourceScore,
                                x, y, index, medium);
                    }
                }
            }
        }
    }

    private static void diffuseToNeighbor(PaintPlan plan, ResourceLocation skinTexture, SectionBounds bounds,
            float[] sourceStrength, byte[] sourceMedium, int[] sourceAppearance,
            long[] sourceVisualSource,
            int[] sourceSalt, byte[] sourceTexturePhase, float[] sourceScore,
            int x, int y, int index, int nx, int ny, int direction, SinkingMedium medium) {
        if (nx < bounds.x || ny < bounds.y || nx >= bounds.x + bounds.width || ny >= bounds.y + bounds.height) {
            return;
        }
        if (!writableSkinPixel(skinTexture, bounds, nx, ny)) {
            return;
        }

        int neighborIndex = ny * plan.width + nx;
        offerDiffusedNeighbor(plan, sourceStrength, sourceMedium, sourceAppearance,
                sourceVisualSource, sourceSalt, sourceTexturePhase, sourceScore,
                index, neighborIndex, nx, ny, direction, medium, false);
    }

    private static void diffuseAcrossFaceEdges(PaintPlan plan, ResourceLocation skinTexture, FaceSection[] sections,
            int cubeStart, FaceSection sourceSection, SectionBounds sourceBounds,
            float[] sourceStrength, byte[] sourceMedium, int[] sourceAppearance,
            long[] sourceVisualSource,
            int[] sourceSalt, byte[] sourceTexturePhase, float[] sourceScore,
            int x, int y, int index, SinkingMedium medium) {
        ModelCell sourceCell = modelCell(sourceSection, sourceBounds, x, y);
        MudSurfaceLayout.Face sourceFace = MudSurfaceLayout.face(sourceSection.part, sourceSection.surface);
        for (MudSurfaceLayout.Edge edge : MudSurfaceLayout.Edge.values()) {
            boolean onEdge = switch (edge) {
                case ROW_MIN -> sourceCell.row == 0;
                case ROW_MAX -> sourceCell.row == sourceFace.height() - 1;
                case COLUMN_MIN -> sourceCell.column == 0;
                case COLUMN_MAX -> sourceCell.column == sourceFace.width() - 1;
            };
            if (!onEdge) {
                continue;
            }

            MudSurfaceLayout.AdjacentCell adjacent = MudSurfaceLayout.neighborAcrossEdge(
                    sourceSection.part, sourceSection.surface, sourceCell.row, sourceCell.column, edge);
            FaceSection targetSection = sectionForSurface(sections, cubeStart, adjacent.surface());
            SectionBounds targetBounds = sectionBounds(targetSection, plan.width, plan.height);
            TexturePixel target = texturePixel(targetSection, targetBounds, adjacent.row(), adjacent.column());
            if (!writableSkinPixel(skinTexture, targetBounds, target.x, target.y)) {
                continue;
            }

            int neighborIndex = target.y * plan.width + target.x;
            offerDiffusedNeighbor(plan, sourceStrength, sourceMedium, sourceAppearance,
                    sourceVisualSource, sourceSalt, sourceTexturePhase, sourceScore,
                    index, neighborIndex, target.x, target.y, 4 + edge.ordinal(), medium, true);
        }
    }

    private static void offerDiffusedNeighbor(PaintPlan plan, float[] sourceStrength, byte[] sourceMedium,
            int[] sourceAppearance, long[] sourceVisualSource,
            int[] sourceSalt, byte[] sourceTexturePhase, float[] sourceScore,
            int index, int neighborIndex, int nx, int ny, int direction,
            SinkingMedium medium, boolean crossFace) {
        float neighborStrength = sourceStrength[neighborIndex];
        int salt = sourceSalt[index] + 503 + direction * 41;
        float noise = pixelNoise(nx + direction * 3, ny + direction * 5, salt);

        if (neighborStrength <= EDGE_BAND_THRESHOLD) {
            if (!MudCoverageAppearance.allowsCoveragePixel(
                    medium, sourceAppearance[index],
                    MudCoverageRules.DOMAIN_SKIN ^ 0x6D2B79F5,
                    neighborIndex,
                    plan.width * plan.height)) {
                return;
            }
            float crossScale = crossFace ? 0.90F : 1.0F;
            float diffusedStrength = sourceStrength[index] * outwardFringeStrength(medium, sourceStrength[index]) * crossScale;
            float keepThreshold = outwardKeepThreshold(medium, diffusedStrength) * crossScale;
            if (diffusedStrength <= EDGE_BAND_THRESHOLD || noise > keepThreshold) {
                return;
            }

            float score = (crossFace ? 1.14F : 1.20F) + diffusedStrength + sourceScore[index] * 0.025F + noise * 0.05F;
            plan.offer(neighborIndex, medium, sourceAppearance[index],
                    sourceVisualSource[index], diffusedStrength, salt,
                    sourceTexturePhase[index], score);
            return;
        }

        if (sourceMedium[neighborIndex] == sourceMedium[index]) {
            return;
        }

        float chance = Mth.clamp(0.10F + Math.min(sourceStrength[index], neighborStrength) * 0.18F, 0.10F, 0.26F);
        if (noise > chance) {
            return;
        }

        float mixedStrength = Mth.clamp(neighborStrength * 0.76F + sourceStrength[index] * 0.24F, 0.0F, 1.0F);
        plan.offer(neighborIndex, medium, sourceAppearance[index], sourceVisualSource[index],
                mixedStrength, salt, sourceTexturePhase[index],
                plan.score[neighborIndex] + 0.006F + noise * 0.002F);
    }

    private static ModelCell modelCell(FaceSection section, SectionBounds bounds, int x, int y) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(section.part, section.surface);
        int uvColumn = Math.min(face.width() - 1, (x - bounds.x) * face.width() / Math.max(1, bounds.width));
        int column = section.reverseLane ? face.width() - 1 - uvColumn : uvColumn;
        int uvRow = Math.min(face.height() - 1, (y - bounds.y) * face.height() / Math.max(1, bounds.height));
        int row = face.vertical() ? face.height() - 1 - uvRow : uvRow;
        return new ModelCell(row, column);
    }

    private static FaceSection sectionForSurface(FaceSection[] sections, int cubeStart, MudSurface surface) {
        for (int offset = 0; offset < MudSurface.COUNT; offset++) {
            FaceSection section = sections[cubeStart + offset];
            if (section.surface == surface) {
                return section;
            }
        }
        throw new IllegalStateException("Missing " + surface + " section at cube index " + cubeStart);
    }

    private static TexturePixel texturePixel(FaceSection section, SectionBounds bounds, int row, int column) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(section.part, section.surface);
        int uvColumn = section.reverseLane ? face.width() - 1 - column : column;
        int uvRow = face.vertical() ? face.height() - 1 - row : row;
        int x = bounds.x + Math.min(bounds.width - 1,
                ((uvColumn * 2 + 1) * bounds.width) / Math.max(2, face.width() * 2));
        int y = bounds.y + Math.min(bounds.height - 1,
                ((uvRow * 2 + 1) * bounds.height) / Math.max(2, face.height() * 2));
        return new TexturePixel(x, y);
    }

    private static void paintCoverageMask(NativeImage target, PaintPlan plan) {
        for (int y = 0; y < plan.height; y++) {
            for (int x = 0; x < plan.width; x++) {
                int index = y * plan.width + x;
                float strength = plan.strength[index];
                if (strength <= EDGE_BAND_THRESHOLD) {
                    continue;
                }

                SinkingMedium medium = SinkingMedium.byId(plan.medium[index] & 0xFF);
                int textureX = adaptiveCoverageSampleX(
                        x, plan.visualSource[index], plan.texturePhase[index])
                        + plan.sampleOffsetX;
                int textureY = adaptiveCoverageSampleY(
                        y, plan.visualSource[index], plan.texturePhase[index])
                        + plan.sampleOffsetY;
                int color = skinCoverageTexturePixel(
                        medium, plan.visualSource[index],
                        textureX, textureY, plan.salt[index]);
                color = applyBrightness(color,
                        MudCoverageAppearance.brightnessScale(
                                medium, plan.appearance[index], textureX, textureY));
                float opacityScale = MudCoverageAppearance.opacityScale(
                        medium, plan.appearance[index], x, y, plan.salt[index]);
                int alpha = Mth.clamp(Math.round(strength * opacityScale * 255.0F), 0, 255);
                target.setPixelRGBA(x, y, FastColor.ABGR32.color(
                        alpha,
                        FastColor.ABGR32.blue(color),
                        FastColor.ABGR32.green(color),
                        FastColor.ABGR32.red(color)));
            }
        }
    }

    private static PaintSample paintSampleFor(ClientMudState.CoverageState display,
            FaceSection section, int row, int column) {
        float strength = display.surfacePixelCoverage(section.part, section.surface, row, column);
        SinkingMedium medium = display.surfacePixelMedium(section.part, section.surface, row, column);
        int appearance = display.surfacePixelAppearance(section.part, section.surface, row, column);
        long visualSource = display.surfacePixelVisualSource(
                section.part, section.surface, row, column);
        int cell = MudSurfaceLayout.cellIndex(section.part, section.surface, row, column);
        if (!MudCoverageAppearance.allowsCoveragePixel(
                medium, appearance, MudCoverageRules.DOMAIN_SKIN,
                cell, MudSurfaceLayout.CELL_COUNT)) {
            strength = 0.0F;
        }
        return new PaintSample(strength, medium, appearance, visualSource);
    }

    private static SectionBounds sectionBounds(FaceSection section, int textureWidth, int textureHeight) {
        int x = scaleX(section.x, textureWidth);
        int y = scaleY(section.y, textureHeight);
        int maskX = scaleX(section.maskX, textureWidth);
        int maskY = scaleY(section.maskY, textureHeight);
        int width = Math.max(1, scaleX(section.x + section.width, textureWidth) - x);
        int height = Math.max(1, scaleY(section.y + section.height, textureHeight) - y);
        return new SectionBounds(x, y, maskX, maskY, width, height);
    }

    private static float outwardKeepThreshold(SinkingMedium medium, float strength) {
        return medium.opaqueCoverage()
                ? Mth.clamp(0.66F + strength * 0.36F, 0.0F, 1.0F)
                : Mth.clamp(0.50F + strength * 0.32F, 0.0F, 0.88F);
    }

    private static float outwardFringeStrength(SinkingMedium medium, float sourceStrength) {
        return medium.opaqueCoverage()
                ? Mth.clamp(0.34F + sourceStrength * 0.30F, 0.0F, 0.66F)
                : Mth.clamp(0.16F + sourceStrength * 0.24F, 0.0F, 0.42F);
    }

    static int overlayPixel(SinkingMedium medium, int original, int x, int y, float strength, int salt) {
        return overlayPixel(medium, 0, original, x, y, strength, salt);
    }

    static int overlayPixel(SinkingMedium medium, int appearance,
            int original, int x, int y, float strength, int salt) {
        return overlayPixel(medium, appearance, 0L,
                original, x, y, strength, salt);
    }

    static int overlayPixel(SinkingMedium medium, int appearance, long visualSource,
            int original, int x, int y, float strength, int salt) {
        int originalAlpha = FastColor.ABGR32.alpha(original);
        if (originalAlpha <= 0) {
            return 0;
        }
        return coverPixel(medium, appearance, visualSource,
                original, originalAlpha, x, y,
                strength, pixelNoise(x, y, salt), salt, false);
    }

    static int bakedOverlayPixel(SinkingMedium medium, int original,
            int x, int y, float strength, int salt) {
        return bakedOverlayPixel(medium, 0, original, x, y, strength, salt);
    }

    static int bakedOverlayPixel(SinkingMedium medium, int appearance, int original,
            int x, int y, float strength, int salt) {
        return bakedOverlayPixel(medium, appearance, 0L,
                original, x, y, strength, salt);
    }

    static int bakedOverlayPixel(SinkingMedium medium, int appearance,
            long visualSource, int original,
            int x, int y, float strength, int salt) {
        int originalAlpha = FastColor.ABGR32.alpha(original);
        if (originalAlpha <= 0) {
            return 0;
        }
        return coverPixel(medium, appearance, visualSource,
                original, originalAlpha, x, y,
                strength, pixelNoise(x, y, salt), salt, true);
    }

    static int blendedAssimilationOverlayPixel(int entityId, int cell, int original,
            int x, int y, float strength, int salt, boolean bakedSkin) {
        SinkingMedium center = ClientAssimilationState.medium(entityId, cell);
        long centerVisualSource = ClientAssimilationState.visualSource(entityId, cell);
        if (!ClientAssimilationState.hasMultipleMedia(entityId)) {
            return bakedSkin
                    ? bakedOverlayPixel(center, 0, centerVisualSource,
                            original, x, y, strength, salt + cell)
                    : overlayPixel(center, 0, centerVisualSource,
                            original, x, y, strength, salt + cell);
        }

        MudBodyPart part = MudSurfaceLayout.part(cell);
        MudSurface surface = MudSurfaceLayout.surface(cell);
        return blendedAssimilationOverlayPixel(entityId, part, surface,
                MudSurfaceLayout.row(cell), MudSurfaceLayout.column(cell), original,
                x, y, strength, salt, bakedSkin);
    }

    private static int blendedAssimilationOverlayPixel(int entityId,
            MudBodyPart part, MudSurface surface, float rowPosition, float columnPosition,
            int original, int x, int y, float strength, int salt, boolean bakedSkin) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int rowMin = Math.max(0, Mth.floor(rowPosition - ASSIMILATION_BLEND_RADIUS));
        int rowMax = Math.min(face.height() - 1,
                Mth.ceil(rowPosition + ASSIMILATION_BLEND_RADIUS));
        int columnMin = Math.max(0, Mth.floor(columnPosition - ASSIMILATION_BLEND_RADIUS));
        int columnMax = Math.min(face.width() - 1,
                Mth.ceil(columnPosition + ASSIMILATION_BLEND_RADIUS));
        float total = 0.0F;
        float alpha = 0.0F;
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        for (int row = rowMin; row <= rowMax; row++) {
            float rowWeight = assimilationBlendAxisWeight(rowPosition - row);
            if (rowWeight <= 0.0F) {
                continue;
            }
            for (int column = columnMin; column <= columnMax; column++) {
                float spatialWeight = rowWeight
                        * assimilationBlendAxisWeight(columnPosition - column);
                if (spatialWeight <= 0.0F) {
                    continue;
                }
                int neighbor = MudSurfaceLayout.cellIndex(part, surface, row, column);
                float neighborCoverage = ClientAssimilationState.coverage(entityId, neighbor);
                float weight = spatialWeight * neighborCoverage;
                if (weight <= 0.0001F) {
                    continue;
                }
                SinkingMedium medium = ClientAssimilationState.medium(entityId, neighbor);
                long visualSource = ClientAssimilationState.visualSource(entityId, neighbor);
                int mediumSalt = assimilationMediumSalt(entityId, part, surface, salt, medium);
                int sample = bakedSkin
                        ? bakedOverlayPixel(medium, 0, visualSource,
                                original, x, y, strength, mediumSalt)
                        : overlayPixel(medium, 0, visualSource,
                                original, x, y, strength, mediumSalt);
                total += weight;
                alpha += FastColor.ABGR32.alpha(sample) * weight;
                red += FastColor.ABGR32.red(sample) * weight;
                green += FastColor.ABGR32.green(sample) * weight;
                blue += FastColor.ABGR32.blue(sample) * weight;
            }
        }
        if (total <= 0.0001F) {
            SinkingMedium medium = ClientAssimilationState.medium(entityId,
                    MudSurfaceLayout.cellIndex(part, surface,
                            Mth.clamp(Math.round(rowPosition), 0, face.height() - 1),
                            Mth.clamp(Math.round(columnPosition), 0, face.width() - 1)));
            int cell = MudSurfaceLayout.cellIndex(part, surface,
                    Mth.clamp(Math.round(rowPosition), 0, face.height() - 1),
                    Mth.clamp(Math.round(columnPosition), 0, face.width() - 1));
            long visualSource = ClientAssimilationState.visualSource(entityId, cell);
            return bakedSkin
                    ? bakedOverlayPixel(medium, 0, visualSource,
                            original, x, y, strength, salt)
                    : overlayPixel(medium, 0, visualSource,
                            original, x, y, strength, salt);
        }
        return FastColor.ABGR32.color(
                Mth.clamp(Math.round(alpha / total), 0, 255),
                Mth.clamp(Math.round(blue / total), 0, 255),
                Mth.clamp(Math.round(green / total), 0, 255),
                Mth.clamp(Math.round(red / total), 0, 255));
    }

    static int blendedAssimilationTextureAbgr(int entityId,
            MudBodyPart part, MudSurface surface, float rowPosition, float columnPosition,
            int x, int y, int salt, int requestedAlpha) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        if (!ClientAssimilationState.hasMultipleMedia(entityId)) {
            int row = Mth.clamp(Math.round(rowPosition), 0, face.height() - 1);
            int column = Mth.clamp(Math.round(columnPosition), 0, face.width() - 1);
            SinkingMedium medium = ClientAssimilationState.medium(entityId,
                    MudSurfaceLayout.cellIndex(part, surface, row, column));
            int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
            return skinCoverageTextureAbgr(medium,
                    ClientAssimilationState.visualSource(entityId, cell),
                    x, y, salt, requestedAlpha);
        }

        int rowMin = Math.max(0, Mth.floor(rowPosition - ASSIMILATION_BLEND_RADIUS));
        int rowMax = Math.min(face.height() - 1,
                Mth.ceil(rowPosition + ASSIMILATION_BLEND_RADIUS));
        int columnMin = Math.max(0, Mth.floor(columnPosition - ASSIMILATION_BLEND_RADIUS));
        int columnMax = Math.min(face.width() - 1,
                Mth.ceil(columnPosition + ASSIMILATION_BLEND_RADIUS));
        float total = 0.0F;
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        for (int row = rowMin; row <= rowMax; row++) {
            float rowWeight = assimilationBlendAxisWeight(rowPosition - row);
            if (rowWeight <= 0.0F) {
                continue;
            }
            for (int column = columnMin; column <= columnMax; column++) {
                float weight = rowWeight
                        * assimilationBlendAxisWeight(columnPosition - column);
                if (weight <= 0.0F) {
                    continue;
                }
                int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                weight *= ClientAssimilationState.coverage(entityId, cell);
                if (weight <= 0.0001F) {
                    continue;
                }
                SinkingMedium medium = ClientAssimilationState.medium(entityId, cell);
                long visualSource = ClientAssimilationState.visualSource(entityId, cell);
                int mediumSalt = assimilationMediumSalt(entityId, part, surface, salt, medium);
                int sample = skinCoverageTextureAbgr(
                        medium, visualSource, x, y, mediumSalt, 255);
                total += weight;
                red += FastColor.ABGR32.red(sample) * weight;
                green += FastColor.ABGR32.green(sample) * weight;
                blue += FastColor.ABGR32.blue(sample) * weight;
            }
        }
        if (total <= 0.0001F) {
            int row = Mth.clamp(Math.round(rowPosition), 0, face.height() - 1);
            int column = Mth.clamp(Math.round(columnPosition), 0, face.width() - 1);
            SinkingMedium medium = ClientAssimilationState.medium(entityId,
                    MudSurfaceLayout.cellIndex(part, surface, row, column));
            int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
            return skinCoverageTextureAbgr(medium,
                    ClientAssimilationState.visualSource(entityId, cell),
                    x, y, salt, requestedAlpha);
        }
        return FastColor.ABGR32.color(Mth.clamp(requestedAlpha, 0, 255),
                Mth.clamp(Math.round(blue / total), 0, 255),
                Mth.clamp(Math.round(green / total), 0, 255),
                Mth.clamp(Math.round(red / total), 0, 255));
    }

    static float assimilationBlendAxisWeight(float distance) {
        float normalized = Mth.clamp(
                1.0F - Math.abs(distance) / ASSIMILATION_BLEND_RADIUS, 0.0F, 1.0F);
        return normalized * normalized * (3.0F - normalized * 2.0F);
    }

    private static int assimilationMediumSalt(int entityId, MudBodyPart part,
            MudSurface surface, int salt, SinkingMedium medium) {
        int value = salt * 31 + entityId * 0x1F123BB5;
        value ^= part.ordinal() * 0x632BE5AB;
        value ^= surface.ordinal() * 0x4CF5AD43;
        value ^= medium.id() * 0x2C9277B5;
        value ^= value >>> 16;
        return value;
    }

    private static int coverPixel(SinkingMedium medium, int appearance, int original, int originalAlpha,
            int x, int y, float strength, float noise, int salt, boolean bakedSkin) {
        return coverPixel(medium, appearance, 0L, original, originalAlpha,
                x, y, strength, noise, salt, bakedSkin);
    }

    private static int coverPixel(SinkingMedium medium, int appearance,
            long visualSource, int original, int originalAlpha,
            int x, int y, float strength, float noise, int salt, boolean bakedSkin) {
        int mud = skinCoverageTexturePixel(medium, visualSource, x, y, salt);
        int red = FastColor.ABGR32.red(original);
        int green = FastColor.ABGR32.green(original);
        int blue = FastColor.ABGR32.blue(original);
        int mudRed = FastColor.ABGR32.red(mud);
        int mudGreen = FastColor.ABGR32.green(mud);
        int mudBlue = FastColor.ABGR32.blue(mud);
        float brightness = MudCoverageAppearance.brightnessScale(
                medium, appearance, x, y);
        mudRed = scaledColor(mudRed, brightness);
        mudGreen = scaledColor(mudGreen, brightness);
        mudBlue = scaledColor(mudBlue, brightness);
        float fade = coverageStrengthOpacity(strength);
        float finalOpacity = MudCoverageAppearance.opacityScale(medium, appearance, x, y, salt);
        float sourceOpacity = visualSource == 0L
                ? 1.0F : FastColor.ABGR32.alpha(mud) / 255.0F;
        float visualOpacity = Mth.clamp(finalOpacity * fade * sourceOpacity, 0.0F, 1.0F);
        float mix = bakedSkin ? visualOpacity : 1.0F;
        int outRed = Mth.clamp(Mth.lerpInt(mix, red, mudRed), 0, 255);
        int outGreen = Mth.clamp(Mth.lerpInt(mix, green, mudGreen), 0, 255);
        int outBlue = Mth.clamp(Mth.lerpInt(mix, blue, mudBlue), 0, 255);
        int outAlpha = bakedSkin
                ? originalAlpha
                : Math.round(originalAlpha * visualOpacity);
        return FastColor.ABGR32.color(outAlpha, outBlue, outGreen, outRed);
    }

    static float coverageStrengthOpacity(float strength) {
        return Mth.clamp(strength, 0.0F, 1.0F);
    }

    private static int coverTexturePixel(SinkingMedium medium, int x, int y, int salt) {
        return sampledTexturePixel(medium.coverTexture(), medium, x, y, salt);
    }

    static int coverTexturePixelAbgr(SinkingMedium medium, int x, int y, int salt) {
        return coverTexturePixel(medium, x, y, salt);
    }

    static boolean hasCoverTexture(SinkingMedium medium) {
        return texturePixels(medium.coverTexture()) != null;
    }

    private static int skinCoverageTexturePixel(SinkingMedium medium, int x, int y, int salt) {
        return sampledTexturePixel(medium.skinCoverageTexture(), medium, x, y, salt);
    }

    private static int skinCoverageTexturePixel(SinkingMedium medium,
            long visualSource, int x, int y, int salt) {
        int fallback = skinCoverageTexturePixel(medium, x, y, salt);
        if (visualSource == 0L) {
            return fallback;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return MudSurfaceAppearance.resolve(
                minecraft.level, visualSource, medium.skinCoverageTexture())
                .sampleAbgr(x, y, salt, fallback);
    }

    private static int sampledTexturePixel(ResourceLocation texture, SinkingMedium medium,
            int x, int y, int salt) {
        MudTexturePixels pixels = texturePixels(texture);
        if (pixels == null) {
            return fallbackTexturePixel(medium);
        }

        MudTextureAnimation.Layout animation = pixels.animation;
        MudTextureAnimation.FrameSample frame = animation.frameAt(animationTick());
        int sx = Math.floorMod(x + salt * 3, animation.frameWidth());
        int sy = Math.floorMod(y + salt * 5, animation.frameHeight());
        int pixel = framePixel(pixels, frame, sx, sy);
        return FastColor.ABGR32.alpha(pixel) == 0 ? fallbackTexturePixel(medium) : pixel;
    }

    static ResourceLocation renderTexture(ResourceLocation texture) {
        MudTexturePixels pixels = texturePixels(texture);
        if (pixels == null || !pixels.animation.animated()) {
            return texture;
        }

        MudTextureAnimation.FrameSample frame = pixels.animation.frameAt(animationTick());
        AnimatedRenderEntry entry = ANIMATED_RENDER_TEXTURES.get(texture);
        if (entry == null || entry.width != pixels.animation.frameWidth()
                || entry.height != pixels.animation.frameHeight()) {
            if (entry != null) {
                MudSurfaceDecalRenderTypes.release(entry.location);
                Minecraft.getInstance().getTextureManager().release(entry.location);
            }
            int width = pixels.animation.frameWidth();
            int height = pixels.animation.frameHeight();
            DynamicTexture dynamicTexture = new DynamicTexture(width, height, true);
            dynamicTexture.setFilter(false, false);
            ResourceLocation location = Minecraft.getInstance().getTextureManager()
                    .register("mirebound_animated_mud_frame", dynamicTexture);
            entry = new AnimatedRenderEntry(dynamicTexture, location, width, height);
            ANIMATED_RENDER_TEXTURES.put(texture, entry);
        }
        if (entry.frameKey != frame.key()) {
            NativeImage image = entry.texture.getPixels();
            if (image == null) {
                image = new NativeImage(entry.width, entry.height, true);
                entry.texture.setPixels(image);
            }
            for (int y = 0; y < entry.height; y++) {
                for (int x = 0; x < entry.width; x++) {
                    image.setPixelRGBA(x, y, framePixel(pixels, frame, x, y));
                }
            }
            entry.texture.upload();
            entry.texture.setFilter(false, false);
            entry.frameKey = frame.key();
        }
        return entry.location;
    }

    static float averageTextureOpacity(ResourceLocation texture) {
        MudTexturePixels pixels = texturePixels(texture);
        return pixels == null ? 1.0F : pixels.averageOpacity;
    }

    static float averageOpacity(int[] pixels) {
        if (pixels == null || pixels.length == 0) {
            return 1.0F;
        }
        long alpha = 0L;
        for (int pixel : pixels) {
            alpha += FastColor.ABGR32.alpha(pixel);
        }
        return Mth.clamp(alpha / (255.0F * pixels.length), 0.0F, 1.0F);
    }

    private static int framePixel(MudTexturePixels pixels, MudTextureAnimation.FrameSample frame,
            int x, int y) {
        MudTextureAnimation.Layout animation = pixels.animation;
        int currentX = animation.frameX(frame.currentFrame()) + x;
        int currentY = animation.frameY(frame.currentFrame()) + y;
        int pixel = pixels.pixels[currentY * pixels.width + currentX];
        if (frame.blendStep() <= 0 || frame.nextFrame() == frame.currentFrame()) {
            return pixel;
        }
        int nextX = animation.frameX(frame.nextFrame()) + x;
        int nextY = animation.frameY(frame.nextFrame()) + y;
        return blendTexturePixels(pixel, pixels.pixels[nextY * pixels.width + nextX], frame.blend());
    }

    static long skinCoverageAnimationSignature(long mediumMask) {
        return skinCoverageAnimationSignature(mediumMask, animationTick());
    }

    static long skinCoverageAnimationSignature(long mediumMask, long tick) {
        long signature = 0L;
        while (mediumMask != 0L) {
            int mediumId = Long.numberOfTrailingZeros(mediumMask);
            mediumMask &= mediumMask - 1L;
            SinkingMedium medium = SinkingMedium.byId(mediumId);
            long frame = animationSignature(medium.skinCoverageTexture(), tick);
            if (frame != 0L) {
                signature = (signature * 31L + medium.id() + 1L) * 31L + frame;
            }
        }
        return signature;
    }

    static long coverTextureAnimationSignature(long mediumMask, long tick) {
        long signature = 0L;
        while (mediumMask != 0L) {
            int mediumId = Long.numberOfTrailingZeros(mediumMask);
            mediumMask &= mediumMask - 1L;
            SinkingMedium medium = SinkingMedium.byId(mediumId);
            long frame = animationSignature(medium.coverTexture(), tick);
            if (frame != 0L) {
                signature = (signature * 31L + medium.id() + 1L) * 31L + frame;
            }
        }
        return signature;
    }

    static long mediumBit(SinkingMedium medium) {
        return medium.id() >= 0 && medium.id() < Long.SIZE ? 1L << medium.id() : 0L;
    }

    private static long animationSignature(ResourceLocation texture, long tick) {
        MudTexturePixels pixels = texturePixels(texture);
        return pixels == null ? 0L : pixels.animation.frameAt(tick).key();
    }

    private static long animationTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private static int blendTexturePixels(int current, int next, float blend) {
        int alpha = Mth.lerpInt(blend, FastColor.ABGR32.alpha(current), FastColor.ABGR32.alpha(next));
        int blue = Mth.lerpInt(blend, FastColor.ABGR32.blue(current), FastColor.ABGR32.blue(next));
        int green = Mth.lerpInt(blend, FastColor.ABGR32.green(current), FastColor.ABGR32.green(next));
        int red = Mth.lerpInt(blend, FastColor.ABGR32.red(current), FastColor.ABGR32.red(next));
        return FastColor.ABGR32.color(alpha, blue, green, red);
    }

    static int coverTextureArgb(SinkingMedium medium, int x, int y, int salt, int alpha) {
        int pixel = coverTexturePixel(medium, x, y, salt);
        int red = FastColor.ABGR32.red(pixel);
        int green = FastColor.ABGR32.green(pixel);
        int blue = FastColor.ABGR32.blue(pixel);
        int clampedAlpha = Mth.clamp(alpha, 0, 255);
        return clampedAlpha << 24 | red << 16 | green << 8 | blue;
    }

    static int coverTextureArgb(SinkingMedium medium, long visualSource,
            int x, int y, int salt, int alpha) {
        int pixel = coverTexturePixel(medium, x, y, salt);
        if (visualSource != 0L) {
            Minecraft minecraft = Minecraft.getInstance();
            pixel = MudSurfaceAppearance.resolve(
                    minecraft.level, visualSource, medium.coverTexture())
                    .sampleAbgr(x, y, salt, pixel);
        }
        return sourceAdjustedAlpha(alpha, pixel, visualSource) << 24
                | FastColor.ABGR32.red(pixel) << 16
                | FastColor.ABGR32.green(pixel) << 8
                | FastColor.ABGR32.blue(pixel);
    }

    static int skinCoverageTextureArgb(SinkingMedium medium, int x, int y, int salt, int alpha) {
        int pixel = applyBrightness(
                skinCoverageTexturePixel(medium, x, y, salt),
                MudCoverageAppearance.brightnessScale(medium, x, y));
        int red = FastColor.ABGR32.red(pixel);
        int green = FastColor.ABGR32.green(pixel);
        int blue = FastColor.ABGR32.blue(pixel);
        int clampedAlpha = Mth.clamp(alpha, 0, 255);
        return clampedAlpha << 24 | red << 16 | green << 8 | blue;
    }

    static int skinCoverageTextureArgb(SinkingMedium medium, long visualSource,
            int x, int y, int salt, int alpha) {
        int pixel = applyBrightness(
                skinCoverageTexturePixel(medium, visualSource, x, y, salt),
                MudCoverageAppearance.brightnessScale(medium, x, y));
        return sourceAdjustedAlpha(alpha, pixel, visualSource) << 24
                | FastColor.ABGR32.red(pixel) << 16
                | FastColor.ABGR32.green(pixel) << 8
                | FastColor.ABGR32.blue(pixel);
    }

    static int coverTextureAbgr(SinkingMedium medium, int x, int y, int salt, int alpha) {
        int pixel = coverTexturePixel(medium, x, y, salt);
        int red = FastColor.ABGR32.red(pixel);
        int green = FastColor.ABGR32.green(pixel);
        int blue = FastColor.ABGR32.blue(pixel);
        return FastColor.ABGR32.color(Mth.clamp(alpha, 0, 255), blue, green, red);
    }

    static int skinCoverageTextureAbgr(SinkingMedium medium, int x, int y, int salt, int alpha) {
        int pixel = applyBrightness(
                skinCoverageTexturePixel(medium, x, y, salt),
                MudCoverageAppearance.brightnessScale(medium, x, y));
        int red = FastColor.ABGR32.red(pixel);
        int green = FastColor.ABGR32.green(pixel);
        int blue = FastColor.ABGR32.blue(pixel);
        return FastColor.ABGR32.color(Mth.clamp(alpha, 0, 255), blue, green, red);
    }

    public static int skinCoverageTextureAbgr(SinkingMedium medium, long visualSource,
            int x, int y, int salt, int alpha) {
        int pixel = applyBrightness(
                skinCoverageTexturePixel(medium, visualSource, x, y, salt),
                MudCoverageAppearance.brightnessScale(medium, x, y));
        return FastColor.ABGR32.color(
                sourceAdjustedAlpha(alpha, pixel, visualSource),
                FastColor.ABGR32.blue(pixel),
                FastColor.ABGR32.green(pixel),
                FastColor.ABGR32.red(pixel));
    }

    static int sourceAdjustedAlpha(int requestedAlpha, int sampledPixel, long visualSource) {
        int alpha = Mth.clamp(requestedAlpha, 0, 255);
        if (visualSource == 0L) {
            return alpha;
        }
        return (alpha * FastColor.ABGR32.alpha(sampledPixel) + 127) / 255;
    }

    private static int applyBrightness(int pixel, float brightness) {
        return FastColor.ABGR32.color(
                FastColor.ABGR32.alpha(pixel),
                scaledColor(FastColor.ABGR32.blue(pixel), brightness),
                scaledColor(FastColor.ABGR32.green(pixel), brightness),
                scaledColor(FastColor.ABGR32.red(pixel), brightness));
    }

    private static int scaledColor(int component, float brightness) {
        return Mth.clamp(Math.round(component * brightness), 0, 255);
    }

    private static int fallbackTexturePixel(SinkingMedium medium) {
        return switch (medium) {
            case RED_QUICKSAND -> abgr(178, 76, 43);
            case ASH_QUICKSAND -> abgr(108, 105, 102);
            case SOUL_SILT -> abgr(87, 65, 54);
            case SOFT_QUICKSAND -> abgr(202, 184, 121);
            case SILT -> abgr(126, 122, 108);
            case JUNGLE_QUICKSAND -> abgr(112, 105, 62);
            case THIN_MUD -> abgr(116, 82, 53);
            case SHALLOW_MUD -> abgr(88, 63, 43);
            case TIDAL_MUD -> abgr(82, 89, 82);
            case GEL_CLAY -> abgr(93, 118, 123);
            case LIME_MUD -> abgr(177, 172, 143);
            case SCULK_MIRE -> abgr(18, 62, 68);
            case PEAT_BOG -> abgr(48, 37, 25);
            case LIVING_SLIME -> abgr(84, 165, 67);
            case ASSIMILATION_SLIME -> abgr(216, 123, 132);
            case TENDER_FLESH -> abgr(244, 244, 220);
            case MIRE -> abgr(42, 31, 24);
            case TAR -> abgr(14, 13, 13);
            default -> abgr(88, 67, 39);
        };
    }

    private static int abgr(int red, int green, int blue) {
        return FastColor.ABGR32.color(255, blue, green, red);
    }

    private static MudTexturePixels texturePixels(ResourceLocation texture) {
        MudTexturePixels cached = COVER_TEXTURE_PIXELS.get(texture);
        if (cached != null) {
            return cached;
        }

        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture);
            try (InputStream stream = resource.open();
                    NativeImage image = BoundedResourceReader.readImage(stream)) {
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        pixels[y * width + x] = image.getPixelRGBA(x, y);
                    }
                }
                MudTextureAnimation.Layout animation = MudTextureAnimation.load(
                        Minecraft.getInstance().getResourceManager(), texture, width, height);
                cached = new MudTexturePixels(
                        width, height, pixels, animation, averageOpacity(pixels));
                COVER_TEXTURE_PIXELS.put(texture, cached);
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
        return cached;
    }

    private static long signature(int entityId) {
        long mudSignature = ClientMudState.displaySurfaceSignature(entityId);
        long assimilationSignature = ClientAssimilationState.signature(entityId);
        if (mudSignature == 0L && assimilationSignature == 0L) {
            return 0L;
        }
        long signature = mudSignature * 31L + assimilationSignature;
        long mediumMask = ClientMudState.displaySurfaceMediumMask(entityId);
        if (assimilationSignature != 0L) {
            ClientAssimilationState.View view = ClientAssimilationState.view(entityId);
            if (view == null) {
                mediumMask |= mediumBit(SinkingMedium.ASSIMILATION_SLIME);
            } else {
                for (SinkingMedium medium : SinkingMedium.values()) {
                    if (view.mediumContribution(medium) > 0.0001F) {
                        mediumMask |= mediumBit(medium);
                    }
                }
            }
        }
        long animation = skinCoverageAnimationSignature(mediumMask);
        return animation == 0L ? signature : signature * 31L + animation;
    }

    private static FaceSection[] createFaceSections(boolean slimModel) {
        List<FaceSection> sections = new ArrayList<>();
        addCube(sections, MudBodyPart.HEAD, 0, 0, 8, 8, 8, 0);
        addCube(sections, MudBodyPart.HEAD, 32, 0, 8, 8, 8, 100);
        addMaskedCube(sections, MudBodyPart.HEAD, 0, 0, 32, 0, 8, 8, 8, 1200);
        addCube(sections, MudBodyPart.RIGHT_LEG, 0, 16, 4, 12, 4, 200);
        addCube(sections, MudBodyPart.RIGHT_LEG, 0, 32, 4, 12, 4, 300);
        addMaskedCube(sections, MudBodyPart.RIGHT_LEG, 0, 16, 0, 32, 4, 12, 4, 1300);
        addCube(sections, MudBodyPart.BODY, 16, 16, 8, 12, 4, 400);
        addCube(sections, MudBodyPart.BODY, 16, 32, 8, 12, 4, 500);
        addMaskedCube(sections, MudBodyPart.BODY, 16, 16, 16, 32, 8, 12, 4, 1400);
        int armWidth = slimModel ? 3 : 4;
        addCube(sections, MudBodyPart.RIGHT_ARM, 40, 16, armWidth, 12, 4, 600);
        addCube(sections, MudBodyPart.RIGHT_ARM, 40, 32, armWidth, 12, 4, 700);
        addMaskedCube(sections, MudBodyPart.RIGHT_ARM, 40, 16, 40, 32, armWidth, 12, 4, 1500);
        addCube(sections, MudBodyPart.LEFT_LEG, 16, 48, 4, 12, 4, 800);
        addCube(sections, MudBodyPart.LEFT_LEG, 0, 48, 4, 12, 4, 900);
        addMaskedCube(sections, MudBodyPart.LEFT_LEG, 16, 48, 0, 48, 4, 12, 4, 1600);
        addCube(sections, MudBodyPart.LEFT_ARM, 32, 48, armWidth, 12, 4, 1000);
        addCube(sections, MudBodyPart.LEFT_ARM, 48, 48, armWidth, 12, 4, 1100);
        addMaskedCube(sections, MudBodyPart.LEFT_ARM, 32, 48, 48, 48, armWidth, 12, 4, 1700);
        return sections.toArray(FaceSection[]::new);
    }

    private static void addCube(List<FaceSection> sections, MudBodyPart part, int x, int y, int width, int height, int depth, int salt) {
        addMaskedCube(sections, part, x, y, x, y, width, height, depth, salt);
    }

    private static void addMaskedCube(List<FaceSection> sections, MudBodyPart part, int x, int y, int maskX, int maskY, int width, int height, int depth, int salt) {
        sections.add(new FaceSection(part, MudSurface.TOP, x + depth, y, maskX + depth, maskY, width, depth, false, false, salt));
        sections.add(new FaceSection(part, MudSurface.BOTTOM, x + depth + width, y, maskX + depth + width, maskY, width, depth, false, false, salt + 1));
        sections.add(new FaceSection(part, MudSurface.RIGHT, x, y + depth, maskX, maskY + depth, depth, height, true, false, salt + 2));
        sections.add(new FaceSection(part, MudSurface.FRONT, x + depth, y + depth, maskX + depth, maskY + depth, width, height, true, false, salt + 3));
        sections.add(new FaceSection(part, MudSurface.LEFT, x + depth + width, y + depth, maskX + depth + width, maskY + depth, depth, height, true, true, salt + 4));
        sections.add(new FaceSection(part, MudSurface.BACK, x + depth + width + depth, y + depth, maskX + depth + width + depth, maskY + depth, width, height, true, true, salt + 5));
    }

    private static int scaleX(int value, int textureWidth) {
        return Math.round(value * textureWidth / 64.0F);
    }

    private static int scaleY(int value, int textureHeight) {
        return Math.round(value * textureHeight / 64.0F);
    }

    private static float pixelNoise(int x, int y, int salt) {
        int value = x * 73428767 ^ y * 9122719 ^ salt * 42317861;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return (value & 1023) / 1023.0F;
    }

    static int adaptiveCoverageSampleX(int coordinate, long visualSource, int texturePhase) {
        return visualSource == 0L
                ? coordinate
                : coordinate + Math.floorMod(texturePhase * 5 + 3, 16);
    }

    static int adaptiveCoverageSampleY(int coordinate, long visualSource, int texturePhase) {
        return visualSource == 0L
                ? coordinate
                : coordinate + Math.floorMod(texturePhase * 7 + 1, 16);
    }

    private record FaceSection(MudBodyPart part, MudSurface surface, int x, int y, int maskX, int maskY, int width, int height, boolean vertical, boolean reverseLane, int salt) {
    }

    private record PaintSample(float strength, SinkingMedium medium,
            int appearance, long visualSource) {
    }

    private record PaintCandidate(SinkingMedium medium, int appearance,
            long visualSource, float strength, int salt, int texturePhase,
            float score) {
    }

    private record SectionBounds(int x, int y, int maskX, int maskY, int width, int height) {
    }

    private record ModelCell(int row, int column) {
    }

    private record TexturePixel(int x, int y) {
    }

    private record MudTexturePixels(int width, int height, int[] pixels,
            MudTextureAnimation.Layout animation, float averageOpacity) {
    }

    private record ResolvedSkin(ResourceLocation skinTexture, boolean slimModel) {
    }

    public record DebugExportResult(Path originalSkin, Path bakedSkin, Path overlaySkin, Path coverageMask) {
    }

    private static final class PaintPlan {
        private final int width;
        private final int height;
        private final int sampleOffsetX;
        private final int sampleOffsetY;
        private final float[] strength;
        private final byte[] medium;
        private final int[] appearance;
        private final long[] visualSource;
        private final int[] salt;
        private final byte[] texturePhase;
        private final float[] score;

        private PaintPlan(int width, int height, int patternSeed) {
            this.width = width;
            this.height = height;
            sampleOffsetX = MudCoveragePatternSeed.sampleOffsetX(patternSeed);
            sampleOffsetY = MudCoveragePatternSeed.sampleOffsetY(patternSeed);
            int size = width * height;
            strength = new float[size];
            medium = new byte[size];
            appearance = new int[size];
            visualSource = new long[size];
            salt = new int[size];
            texturePhase = new byte[size];
            score = new float[size];
        }

        private void offer(int index, SinkingMedium candidateMedium,
                int candidateAppearance, long candidateVisualSource,
                float candidateStrength, int candidateSalt,
                int candidateTexturePhase, float candidateScore) {
            if (candidateStrength <= EDGE_BAND_THRESHOLD || candidateScore <= score[index]) {
                return;
            }

            strength[index] = candidateStrength;
            medium[index] = (byte) candidateMedium.id();
            appearance[index] = candidateAppearance;
            visualSource[index] = candidateVisualSource;
            salt[index] = candidateSalt;
            texturePhase[index] = (byte) candidateTexturePhase;
            score[index] = candidateScore;
        }
    }

    private static final class Entry {
        private DynamicTexture texture;
        private ResourceLocation location;
        private ResourceLocation skinTexture;
        private long signature = Long.MIN_VALUE;
        private int width = 64;
        private int height = 64;
        private boolean slimModel;
        private int lastSeenTick;
    }

    private static final class AnimatedRenderEntry {
        private final DynamicTexture texture;
        private final ResourceLocation location;
        private final int width;
        private final int height;
        private long frameKey = Long.MIN_VALUE;

        private AnimatedRenderEntry(DynamicTexture texture, ResourceLocation location, int width, int height) {
            this.texture = texture;
            this.location = location;
            this.width = width;
            this.height = height;
        }
    }
}
