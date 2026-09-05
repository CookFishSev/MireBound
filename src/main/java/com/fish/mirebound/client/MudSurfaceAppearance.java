package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Resolves the rendered source face used by position-owned adaptive mud effects. */
final class MudSurfaceAppearance {
    private static final int MAX_CACHED_FACES = 4096;
    private static final int ADAPTIVE_PALETTE_SIZE = 16;
    private static final int ADAPTIVE_WARP_CELL_SIZE = 6;
    private static final float ADAPTIVE_WARP_RADIUS = 3.0F;
    private static final float ADAPTIVE_MIX_MINIMUM = 0.22F;
    private static final float ADAPTIVE_MIX_RANGE = 0.16F;
    private static final Map<FaceKey, CachedFace> FACE_CACHE =
            new LinkedHashMap<>(256, 0.75F, true);
    private static final Map<Long, CachedSource> SOURCE_CACHE =
            new LinkedHashMap<>(128, 0.75F, true);

    private MudSurfaceAppearance() {
    }

    static Appearance resolve(Level level, BlockPos pos, Direction face,
            ResourceLocation fallbackTexture) {
        return resolve(level, pos, face, MudVisualSource.NONE, fallbackTexture);
    }

    static Appearance resolve(Level level, BlockPos pos, Direction face,
            long fallbackVisualSource, ResourceLocation fallbackTexture) {
        if (level == null || pos == null || face == null) {
            return resolve(level, fallbackVisualSource, fallbackTexture);
        }
        Appearance resolved = resolvePosition(level, pos, face);
        return resolved == null
                ? resolve(level, fallbackVisualSource, fallbackTexture)
                : resolved;
    }

    private static Appearance resolvePosition(
            Level level, BlockPos pos, Direction face) {
        BlockState source = AdaptiveMudClientCache.sourceState(level, pos);
        if (source == null || source.getBlock() instanceof AdaptiveMudBlock) {
            return null;
        }

        AdaptiveSettings settings = adaptiveSettings(level, pos);
        FaceKey key = new FaceKey(level.dimension().location(), pos.asLong(), face);
        CachedFace cached = FACE_CACHE.get(key);
        long gameTime = level.getGameTime();
        if (cached != null && cached.source.equals(source)
                && cached.settings.equals(settings)
                && gameTime >= cached.resolvedAt
                && gameTime - cached.resolvedAt < 20L) {
            return cached.appearance;
        }
        Appearance resolved = resolveSource(level, pos, face, source,
                -1, settings.smoothingRadius, settings.textureDetail);
        if (resolved == null) {
            return null;
        }
        FACE_CACHE.put(key, new CachedFace(source, settings, resolved, gameTime));
        trimCache();
        return resolved;
    }

    static Appearance resolve(Level level, long visualSource,
            ResourceLocation fallbackTexture) {
        if (level == null || visualSource == MudVisualSource.NONE) {
            return fallback(fallbackTexture);
        }
        CachedSource cached = SOURCE_CACHE.get(visualSource);
        if (MudVisualSource.positionBacked(visualSource)) {
            long gameTime = level.getGameTime();
            int appearanceRevision = AdaptiveMudClientCache.appearanceRevision(
                    level, visualSource);
            if (cached != null
                    && cached.appearanceRevision == appearanceRevision
                    && gameTime >= cached.resolvedAt
                    && gameTime - cached.resolvedAt < 20L) {
                return cached.appearance;
            }
            BlockPos origin = MudVisualSource.position(visualSource);
            Appearance resolved = origin == null ? null : resolvePosition(
                    level, origin, MudVisualSource.face(visualSource));
            if (resolved != null) {
                SOURCE_CACHE.put(visualSource, new CachedSource(
                        resolved, gameTime, appearanceRevision));
                trimCache();
                return resolved;
            }
            if (cached != null
                    && cached.appearanceRevision == appearanceRevision) {
                return cached.appearance;
            }
            if (origin != null) {
                BlockState removedSource = AdaptiveMudClientCache.removedSourceState(
                        level, origin);
                if (removedSource != null) {
                    Appearance removedAppearance = resolveSource(
                            level, origin, MudVisualSource.face(visualSource),
                            removedSource, -1,
                            MudVisualSource.smoothingRadius(visualSource),
                            MudVisualSource.textureDetail(visualSource));
                    if (removedAppearance != null) {
                        SOURCE_CACHE.put(visualSource, new CachedSource(
                                removedAppearance, gameTime, appearanceRevision));
                        trimCache();
                        return removedAppearance;
                    }
                }
            }
            return fallback(fallbackTexture, MudVisualSource.color(visualSource));
        }
        if (cached != null) {
            return cached.appearance;
        }
        BlockState source = MudVisualSource.state(visualSource);
        if (source == null || source.isAir()
                || source.getBlock() instanceof AdaptiveMudBlock) {
            return fallback(fallbackTexture);
        }
        Appearance resolved = resolveSource(
                level,
                BlockPos.ZERO,
                MudVisualSource.face(visualSource),
                source,
                MudVisualSource.color(visualSource),
                MudVisualSource.smoothingRadius(visualSource),
                MudVisualSource.textureDetail(visualSource));
        if (resolved == null) {
            return fallback(fallbackTexture);
        }
        SOURCE_CACHE.put(visualSource,
                new CachedSource(resolved, level.getGameTime(), 0));
        trimCache();
        return resolved;
    }

    static long captureVisualSource(Level level, BlockPos pos, Direction face) {
        if (level == null || pos == null) {
            return MudVisualSource.NONE;
        }
        BlockState proxy = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(proxy.getBlock());
        BlockState source = AdaptiveMudClientCache.sourceState(level, pos);
        if (medium == null || source == null || source.isAir()
                || source.getBlock() instanceof AdaptiveMudBlock) {
            return MudVisualSource.NONE;
        }
        Direction sourceFace = face == null ? Direction.UP : face;
        BlockAndTintGetter sourceLevel = AdaptiveMudModels.sourceView(
                level, pos, source);
        int color = source.getMapColor(sourceLevel, pos).col;
        int blockTint = blockTint(
                Minecraft.getInstance(), source, sourceLevel, pos, 0);
        if (blockTint >= 0) {
            color = blockTint;
        }
        long positioned = MudVisualSource.position(pos, sourceFace, color);
        if (positioned != MudVisualSource.NONE) {
            return positioned;
        }
        return MudVisualSource.pack(source, sourceFace, color,
                MudMediumRuntime.adaptiveCoverageSmoothingRadius(level, pos, medium),
                MudMediumRuntime.adaptiveCoverageTextureDetail(level, pos, medium));
    }

    static void reset() {
        FACE_CACHE.clear();
        SOURCE_CACHE.clear();
    }

    private static Appearance fallback(ResourceLocation texture) {
        return Appearance.texture(
                MudSkinTextureCache.renderTexture(texture),
                MudSkinTextureCache.averageTextureOpacity(texture));
    }

    private static Appearance fallback(ResourceLocation texture, int color) {
        return Appearance.tintedTexture(
                MudSkinTextureCache.renderTexture(texture),
                MudSkinTextureCache.averageTextureOpacity(texture), color);
    }

    private static Appearance resolveSource(
            Level level, BlockPos pos, Direction face, BlockState source) {
        return resolveSource(level, pos, face, source, -1,
                MudVisualSource.DEFAULT_SMOOTHING_RADIUS,
                MudVisualSource.DEFAULT_TEXTURE_DETAIL);
    }

    private static Appearance resolveSource(
            Level level, BlockPos pos, Direction face, BlockState source,
            int tintOverride, int smoothingRadius, float textureDetail) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel model = minecraft.getBlockRenderer().getBlockModel(source);
            BlockAndTintGetter sourceLevel = AdaptiveMudModels.sourceView(
                    level, pos, source);
            BlockEntity sourceEntity = sourceLevel.getBlockEntity(pos);
            ModelData baseData = sourceEntity == null
                    ? ModelData.EMPTY : sourceEntity.getModelData();
            ModelData modelData = model.getModelData(
                    sourceLevel, pos, source, baseData);
            BakedQuad quad = faceQuad(model, source, modelData, pos, face);
            TextureAtlasSprite sprite = quad == null
                    ? model.getParticleIcon(modelData) : quad.getSprite();
            if (sprite == null || MissingTextureAtlasSprite.getLocation()
                    .equals(sprite.contents().name())) {
                return null;
            }
            int representativeColor = tintOverride >= 0
                    ? tintOverride
                    : source.getMapColor(sourceLevel, pos).col;
            int tint = -1;
            if (quad != null && quad.isTinted()) {
                tint = tintOverride >= 0
                         ? tintOverride
                         : blockTint(minecraft, source, sourceLevel, pos,
                                 quad.getTintIndex());
            }
            if (tint < 0 && tintOverride < 0 && sourceEntity != null) {
                tint = blockTint(minecraft, source, sourceLevel, pos, 0);
            }
            if (tint >= 0) {
                representativeColor = tint;
            }
            return Appearance.sprite(sprite, tint, representativeColor,
                    smoothingRadius, textureDetail);
        } catch (RuntimeException ignored) {
            // A third-party baked model must not be able to break surface rendering.
            return null;
        }
    }

    private static int blockTint(Minecraft minecraft, BlockState source,
            BlockAndTintGetter sourceLevel, BlockPos pos, int tintIndex) {
        try {
            int color = minecraft.getBlockColors().getColor(
                    source, sourceLevel, pos, tintIndex);
            return normalizeBlockTint(color);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    static int normalizeBlockTint(int color) {
        // BlockColor uses -1 as its no-tint sentinel, but modern providers may
        // return opaque ARGB values whose sign bit is also set.
        return color == -1 ? -1 : color & 0xFFFFFF;
    }

    private static BakedQuad faceQuad(BakedModel model, BlockState source,
            ModelData modelData, BlockPos pos, Direction face) {
        long seed = source.getSeed(pos);
        RandomSource random = RandomSource.create(seed);
        BakedQuad best = null;
        for (RenderType renderType : model.getRenderTypes(source, random, modelData)) {
            random.setSeed(seed);
            best = largerQuad(best, largestUsefulQuad(
                    model.getQuads(source, face, random, modelData, renderType), face));
        }
        random.setSeed(seed);
        best = largerQuad(best, largestUsefulQuad(
                model.getQuads(source, face, random, modelData, null), face));
        random.setSeed(seed);
        return largerQuad(best, largestUsefulQuad(
                model.getQuads(source, null, random, modelData, null), face));
    }

    private static BakedQuad largerQuad(BakedQuad first, BakedQuad second) {
        if (first == null) {
            return second;
        }
        return second != null && quadArea(second) > quadArea(first)
                ? second : first;
    }

    private static BakedQuad largestUsefulQuad(List<BakedQuad> quads, Direction face) {
        BakedQuad best = null;
        double bestArea = -1.0D;
        for (BakedQuad quad : quads) {
            if (quad.getDirection() == face
                    && !MissingTextureAtlasSprite.getLocation()
                            .equals(quad.getSprite().contents().name())) {
                double area = quadArea(quad);
                if (area > bestArea) {
                    best = quad;
                    bestArea = area;
                }
            }
        }
        return best;
    }

    private static double quadArea(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        if (stride < 3) {
            return 0.0D;
        }
        double[] x = new double[4];
        double[] y = new double[4];
        double[] z = new double[4];
        for (int index = 0; index < 4; index++) {
            int offset = index * stride;
            x[index] = Float.intBitsToFloat(vertices[offset]);
            y[index] = Float.intBitsToFloat(vertices[offset + 1]);
            z[index] = Float.intBitsToFloat(vertices[offset + 2]);
        }
        return triangleArea(x, y, z, 0, 1, 2)
                + triangleArea(x, y, z, 0, 2, 3);
    }

    private static double triangleArea(double[] x, double[] y, double[] z,
            int first, int second, int third) {
        double firstX = x[second] - x[first];
        double firstY = y[second] - y[first];
        double firstZ = z[second] - z[first];
        double secondX = x[third] - x[first];
        double secondY = y[third] - y[first];
        double secondZ = z[third] - z[first];
        double crossX = firstY * secondZ - firstZ * secondY;
        double crossY = firstZ * secondX - firstX * secondZ;
        double crossZ = firstX * secondY - firstY * secondX;
        return Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) * 0.5D;
    }

    private static void trimCache() {
        while (FACE_CACHE.size() > MAX_CACHED_FACES) {
            FACE_CACHE.remove(FACE_CACHE.keySet().iterator().next());
        }
        while (SOURCE_CACHE.size() > MAX_CACHED_FACES) {
            SOURCE_CACHE.remove(SOURCE_CACHE.keySet().iterator().next());
        }
    }

    private static AdaptiveSettings adaptiveSettings(Level level, BlockPos pos) {
        BlockState proxy = level.getBlockState(pos);
        if (!(proxy.getBlock() instanceof AdaptiveMudBlock adaptive)) {
            return AdaptiveSettings.DEFAULT;
        }
        return new AdaptiveSettings(
                MudMediumRuntime.adaptiveCoverageSmoothingRadius(
                        level, pos, adaptive.medium()),
                MudMediumRuntime.adaptiveCoverageTextureDetail(
                        level, pos, adaptive.medium()));
    }

    record Appearance(ResourceLocation texture,
            float minimumU, float minimumV, float maximumU, float maximumV,
            int red, int green, int blue, TextureAtlasSprite sprite,
            int[] adaptivePixels, float baseOpacity) {
        private static final Appearance UNTINTED = new Appearance(
                MissingTextureAtlasSprite.getLocation(),
                0.0F, 0.0F, 1.0F, 1.0F, 255, 255, 255, null);

        Appearance(ResourceLocation texture,
                float minimumU, float minimumV, float maximumU, float maximumV,
                int red, int green, int blue, TextureAtlasSprite sprite) {
            this(texture, minimumU, minimumV, maximumU, maximumV,
                    red, green, blue, sprite, null, 1.0F);
        }

        Appearance(ResourceLocation texture,
                float minimumU, float minimumV, float maximumU, float maximumV,
                int red, int green, int blue, TextureAtlasSprite sprite,
                int[] adaptivePixels) {
            this(texture, minimumU, minimumV, maximumU, maximumV,
                    red, green, blue, sprite, adaptivePixels,
                    averageOpacity(adaptivePixels));
        }

        static Appearance untinted() {
            return UNTINTED;
        }

        private static Appearance texture(ResourceLocation texture, float baseOpacity) {
            return new Appearance(texture, 0.0F, 0.0F, 1.0F, 1.0F,
                    255, 255, 255, null, null, baseOpacity);
        }

        private static Appearance tintedTexture(
                ResourceLocation texture, float baseOpacity, int color) {
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            int pixel = FastColor.ABGR32.color(255, blue, green, red);
            int[] pixels = new int[ADAPTIVE_PALETTE_SIZE * ADAPTIVE_PALETTE_SIZE];
            java.util.Arrays.fill(pixels, pixel);
            return new Appearance(texture, 0.0F, 0.0F, 1.0F, 1.0F,
                    red, green, blue, null, pixels, baseOpacity);
        }

        private static Appearance sprite(TextureAtlasSprite sprite, int tint,
                int representativeColor, int smoothingRadius,
                float textureDetail) {
            int red = tint < 0 ? 255 : tint >> 16 & 0xFF;
            int green = tint < 0 ? 255 : tint >> 8 & 0xFF;
            int blue = tint < 0 ? 255 : tint & 0xFF;
            return new Appearance(
                    sprite.atlasLocation(),
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                    red, green, blue, sprite,
                    buildAdaptivePixels(sprite, red, green, blue,
                            representativeColor, smoothingRadius,
                            textureDetail),
                    averageOpacity(sprite));
        }

        int sampleAbgr(int x, int y, int salt, int fallback) {
            int sourceAlpha = Mth.clamp(Math.round(baseOpacity * 255.0F), 0, 255);
            if (adaptivePixels != null
                    && adaptivePixels.length == ADAPTIVE_PALETTE_SIZE * ADAPTIVE_PALETTE_SIZE) {
                float warpX = adaptiveNoise(x, y, 0x6D2B79F5) * 2.0F - 1.0F;
                float warpY = adaptiveNoise(x + 23, y - 17, 0x51ED270B) * 2.0F - 1.0F;
                int first = adaptivePixel(
                        x + Math.round(warpX * ADAPTIVE_WARP_RADIUS),
                        y + Math.round(warpY * ADAPTIVE_WARP_RADIUS));

                float secondaryWarpX = adaptiveNoise(x - 31, y + 11, 0x2C9277B5) * 2.0F - 1.0F;
                float secondaryWarpY = adaptiveNoise(x + 7, y + 29, 0x58F38DED) * 2.0F - 1.0F;
                int second = adaptivePixel(
                        x + 5 + Math.round(secondaryWarpX * ADAPTIVE_WARP_RADIUS),
                        y - 3 + Math.round(secondaryWarpY * ADAPTIVE_WARP_RADIUS));
                float amount = ADAPTIVE_MIX_MINIMUM
                        + ADAPTIVE_MIX_RANGE * adaptiveNoise(x + 13, y + 5, 0x165667B1);
                int mixed = blendAbgr(first, second, amount);
                return withAlpha(FastColor.ABGR32.alpha(mixed) == 0 ? fallback : mixed,
                        sourceAlpha);
            }
            if (sprite == null || sprite.contents().width() <= 0
                    || sprite.contents().height() <= 0) {
                return fallback;
            }
            int sampleX = Math.floorMod(x + salt * 3, sprite.contents().width());
            int sampleY = Math.floorMod(y + salt * 5, sprite.contents().height());
            int pixel;
            try {
                pixel = sprite.getPixelRGBA(0, sampleX, sampleY);
            } catch (RuntimeException ignored) {
                return fallback;
            }
            if (FastColor.ABGR32.alpha(pixel) == 0) {
                return withAlpha(fallback, sourceAlpha);
            }
            return FastColor.ABGR32.color(
                    sourceAlpha,
                    FastColor.ABGR32.blue(pixel) * blue / 255,
                    FastColor.ABGR32.green(pixel) * green / 255,
                    FastColor.ABGR32.red(pixel) * red / 255);
        }

        private int adaptivePixel(int x, int y) {
            int pixel = adaptivePixels[
                    Math.floorMod(x, ADAPTIVE_PALETTE_SIZE)
                            + Math.floorMod(y, ADAPTIVE_PALETTE_SIZE) * ADAPTIVE_PALETTE_SIZE];
            return FastColor.ABGR32.alpha(pixel) == 0 ? 0 : pixel;
        }

        private static int blendAbgr(int first, int second, float amount) {
            if (FastColor.ABGR32.alpha(first) == 0) {
                return second;
            }
            if (FastColor.ABGR32.alpha(second) == 0) {
                return first;
            }
            float blend = Mth.clamp(amount, 0.0F, 1.0F);
            return FastColor.ABGR32.color(
                    Mth.lerpInt(blend,
                            FastColor.ABGR32.alpha(first), FastColor.ABGR32.alpha(second)),
                    Mth.lerpInt(blend,
                            FastColor.ABGR32.blue(first), FastColor.ABGR32.blue(second)),
                    Mth.lerpInt(blend,
                            FastColor.ABGR32.green(first), FastColor.ABGR32.green(second)),
                    Mth.lerpInt(blend,
                            FastColor.ABGR32.red(first), FastColor.ABGR32.red(second)));
        }

        private static int withAlpha(int color, int alpha) {
            return FastColor.ABGR32.color(
                    Mth.clamp(alpha, 0, 255),
                    FastColor.ABGR32.blue(color),
                    FastColor.ABGR32.green(color),
                    FastColor.ABGR32.red(color));
        }

        private static float adaptiveNoise(int x, int y, int seed) {
            int gridX = Math.floorDiv(x, ADAPTIVE_WARP_CELL_SIZE);
            int gridY = Math.floorDiv(y, ADAPTIVE_WARP_CELL_SIZE);
            float localX = Math.floorMod(x, ADAPTIVE_WARP_CELL_SIZE)
                    / (float) ADAPTIVE_WARP_CELL_SIZE;
            float localY = Math.floorMod(y, ADAPTIVE_WARP_CELL_SIZE)
                    / (float) ADAPTIVE_WARP_CELL_SIZE;
            float smoothX = smooth(localX);
            float smoothY = smooth(localY);
            float top = Mth.lerp(smoothX,
                    adaptiveNoiseNode(gridX, gridY, seed),
                    adaptiveNoiseNode(gridX + 1, gridY, seed));
            float bottom = Mth.lerp(smoothX,
                    adaptiveNoiseNode(gridX, gridY + 1, seed),
                    adaptiveNoiseNode(gridX + 1, gridY + 1, seed));
            return Mth.lerp(smoothY, top, bottom);
        }

        private static float adaptiveNoiseNode(int gridX, int gridY, int seed) {
            int hash = seed;
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

        static int adaptiveCoordinate(int coordinate, int ignoredSalt, int size) {
            return size <= 0 ? 0 : Math.floorMod(coordinate, size);
        }

        private static int[] buildAdaptivePixels(TextureAtlasSprite sprite,
                int tintRed, int tintGreen, int tintBlue,
                int representativeColor, int smoothingRadius,
                float textureDetail) {
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            if (width <= 0 || height <= 0) {
                return null;
            }
            int[] pixels = new int[ADAPTIVE_PALETTE_SIZE * ADAPTIVE_PALETTE_SIZE];
            int representativeRed = representativeColor >> 16 & 0xFF;
            int representativeGreen = representativeColor >> 8 & 0xFF;
            int representativeBlue = representativeColor & 0xFF;
            int filterRadius = Mth.clamp(smoothingRadius, 0, 3);
            float sourceDetail = Mth.clamp(textureDetail, 0.0F, 1.0F);
            for (int y = 0; y < ADAPTIVE_PALETTE_SIZE; y++) {
                int centerY = Mth.clamp(
                        Mth.floor((y + 0.5F) * height / ADAPTIVE_PALETTE_SIZE),
                        0, height - 1);
                for (int x = 0; x < ADAPTIVE_PALETTE_SIZE; x++) {
                    int centerX = Mth.clamp(
                            Mth.floor((x + 0.5F) * width / ADAPTIVE_PALETTE_SIZE),
                            0, width - 1);
                    long alpha = 0L;
                    long red = 0L;
                    long green = 0L;
                    long blue = 0L;
                    int samples = 0;
                    for (int offsetY = -filterRadius;
                            offsetY <= filterRadius; offsetY++) {
                        int sampleY = Math.floorMod(centerY + offsetY, height);
                        for (int offsetX = -filterRadius;
                                offsetX <= filterRadius; offsetX++) {
                            int sampleX = Math.floorMod(centerX + offsetX, width);
                            int pixel;
                            try {
                                pixel = sprite.getPixelRGBA(0, sampleX, sampleY);
                            } catch (RuntimeException ignored) {
                                return null;
                            }
                            int sampleAlpha = FastColor.ABGR32.alpha(pixel);
                            if (sampleAlpha == 0) {
                                continue;
                            }
                            alpha += sampleAlpha;
                            red += FastColor.ABGR32.red(pixel) * tintRed / 255;
                            green += FastColor.ABGR32.green(pixel) * tintGreen / 255;
                            blue += FastColor.ABGR32.blue(pixel) * tintBlue / 255;
                            samples++;
                        }
                    }
                    if (samples == 0) {
                        continue;
                    }
                    int filteredRed = (int) (red / samples);
                    int filteredGreen = (int) (green / samples);
                    int filteredBlue = (int) (blue / samples);
                    pixels[x + y * ADAPTIVE_PALETTE_SIZE] = FastColor.ABGR32.color(
                            (int) (alpha / samples),
                            Mth.lerpInt(sourceDetail,
                                    representativeBlue, filteredBlue),
                            Mth.lerpInt(sourceDetail,
                                    representativeGreen, filteredGreen),
                            Mth.lerpInt(sourceDetail,
                                    representativeRed, filteredRed));
                }
            }
            return pixels;
        }

        private static float averageOpacity(TextureAtlasSprite sprite) {
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            if (width <= 0 || height <= 0) {
                return 1.0F;
            }
            long alpha = 0L;
            try {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        alpha += FastColor.ABGR32.alpha(sprite.getPixelRGBA(0, x, y));
                    }
                }
            } catch (RuntimeException ignored) {
                return 1.0F;
            }
            return Mth.clamp(alpha / (255.0F * width * height), 0.0F, 1.0F);
        }

        private static float averageOpacity(int[] pixels) {
            if (pixels == null || pixels.length == 0) {
                return 1.0F;
            }
            long alpha = 0L;
            for (int pixel : pixels) {
                alpha += FastColor.ABGR32.alpha(pixel);
            }
            return Mth.clamp(alpha / (255.0F * pixels.length), 0.0F, 1.0F);
        }

        float u(float normalized) {
            return Mth.lerp(normalized, minimumU, maximumU);
        }

        float v(float normalized) {
            return Mth.lerp(normalized, minimumV, maximumV);
        }

        int shadedRed(int shade) {
            return red * Mth.clamp(shade, 0, 255) / 255;
        }

        int shadedGreen(int shade) {
            return green * Mth.clamp(shade, 0, 255) / 255;
        }

        int shadedBlue(int shade) {
            return blue * Mth.clamp(shade, 0, 255) / 255;
        }
    }

    private record FaceKey(ResourceLocation dimension, long blockPos, Direction face) {
    }

    private record AdaptiveSettings(int smoothingRadius, float textureDetail) {
        private static final AdaptiveSettings DEFAULT = new AdaptiveSettings(
                MudVisualSource.DEFAULT_SMOOTHING_RADIUS,
                MudVisualSource.DEFAULT_TEXTURE_DETAIL);
    }

    private record CachedFace(BlockState source, AdaptiveSettings settings,
            Appearance appearance, long resolvedAt) {
    }

    private record CachedSource(
            Appearance appearance, long resolvedAt, int appearanceRevision) {
    }
}
