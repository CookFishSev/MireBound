package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.client.compat.IrisDecalMaterials;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.stain.MudFootprintBlock;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import com.fish.mirebound.stain.WallStainGeometry;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Builds one fused texture and one render layer for every precise stained block face. */
final class MudWallTextureCache {
    private static final int GRID_SIZE = 16;
    private static final int FACE_WIDTH = GRID_SIZE;
    private static final int FACE_HEIGHT = GRID_SIZE;
    private static final int CELL_COUNT = GRID_SIZE * GRID_SIZE;
    private static final int FADE_SIGNATURE_STEPS = 128;
    private static final int MAXIMUM_REBUILDS_PER_TICK = 24;
    private static final long UNUSED_TICKS = 400L;
    private static final float CARDINAL_COLOR_MIX = 0.13F;
    private static final float DIAGONAL_COLOR_MIX = 0.045F;
    private static final int[][] CARDINAL_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] DIAGONAL_OFFSETS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static long lastPruneTick = Long.MIN_VALUE;
    private static long rebuildBudgetTick = Long.MIN_VALUE;
    private static int rebuildsThisTick;
    private static final FlowScratch FLOW = new FlowScratch();

    private MudWallTextureCache() {
    }

    static TextureView textureFor(MudFootprintBlockEntity blockEntity, Direction face,
            List<MudFootprintBlockEntity.Entry> stains) {
        if (blockEntity.getLevel() == null) {
            return null;
        }
        long gameTime = blockEntity.getLevel().getGameTime();
        boolean shaderSafe = ClientRenderCompat.useShaderSafeTransparency();
        int height = FACE_HEIGHT;
        Key key = new Key(
                blockEntity.getLevel().dimension().location(),
                blockEntity.getBlockPos().asLong(),
                face);
        Entry cached = CACHE.get(key);
        boolean reservedRebuild = false;
        if (cached == null || cached.height != height) {
            // Include allocation/registration in the upload budget when many faces first appear.
            if (!reserveTextureRebuild(gameTime)) {
                prune(gameTime);
                return cached == null ? null : cached.view;
            }
            reservedRebuild = true;
            if (cached != null) cached.close();
            cached = createEntry(height);
            CACHE.put(key, cached);
        }
        cached.lastSeenTick = gameTime;

        if (cached.evaluatedTick != gameTime
                || cached.evaluatedShaderSafe != shaderSafe) {
            cached.evaluatedSurfaceClip = surfaceClip(blockEntity, face, cached.evaluatedSurfaceClip);
            cached.flowDirection = flowDirection(blockEntity, face);
            collectContour(cached.contour, blockEntity, face);
            cached.evaluatedState = buildState(
                    blockEntity, face, stains, gameTime, shaderSafe,
                    cached.evaluatedSurfaceClip, cached.flowDirection, cached.contour);
            cached.evaluatedTick = gameTime;
            cached.evaluatedShaderSafe = shaderSafe;
        }
        BuildState state = cached.evaluatedState;
        if (!state.hasPixels) {
            return null;
        }
        if (cached.signature != state.signature) {
            if (!reservedRebuild && !reserveTextureRebuild(gameTime)) {
                prune(gameTime);
                return cached.signature == Long.MIN_VALUE
                        ? null
                        : cached.view;
            }
            rebuild(cached, blockEntity, face, stains, gameTime,
                    shaderSafe, cached.evaluatedSurfaceClip);
            cached.signature = state.signature;
        }
        prune(gameTime);
        return cached.view;
    }

    static void reset() {
        for (Entry entry : CACHE.values()) {
            entry.close();
        }
        CACHE.clear();
        lastPruneTick = Long.MIN_VALUE;
        resetRebuildBudget();
    }

    static boolean reserveTextureRebuild(long gameTime) {
        if (rebuildBudgetTick != gameTime) {
            rebuildBudgetTick = gameTime;
            rebuildsThisTick = 0;
        }
        if (rebuildsThisTick >= MAXIMUM_REBUILDS_PER_TICK) {
            return false;
        }
        rebuildsThisTick++;
        return true;
    }

    static void resetRebuildBudget() {
        rebuildBudgetTick = Long.MIN_VALUE;
        rebuildsThisTick = 0;
    }

    private static Entry createEntry(int height) {
        DynamicTexture stableTexture = IrisDecalMaterials.createTexture(FACE_WIDTH, height);
        DynamicTexture translucentTexture = IrisDecalMaterials.createTexture(FACE_WIDTH, height);
        stableTexture.setFilter(false, false);
        translucentTexture.setFilter(false, false);
        ResourceLocation stableLocation = Minecraft.getInstance().getTextureManager()
                .register("mirebound_fused_wall_stain_stable", stableTexture);
        ResourceLocation translucentLocation = Minecraft.getInstance().getTextureManager()
                .register("mirebound_fused_wall_stain_fade", translucentTexture);
        return new Entry(stableTexture, translucentTexture, stableLocation, translucentLocation, height);
    }

    private static BuildState buildState(MudFootprintBlockEntity blockEntity,
            Direction face,
            List<MudFootprintBlockEntity.Entry> stains, long gameTime,
            boolean shaderSafe, SurfaceClip surfaceClip, Vec3 flowDirection, int[] contour) {
        long signature = 0xcbf29ce484222325L;
        for (int row : contour) signature = (signature ^ row) * 0x100000001b3L;
        signature = (signature ^ (shaderSafe ? 1L : 0L)) * 0x100000001b3L;
        signature = (signature ^ Math.round(flowDirection.x * 128)) * 0x100000001b3L;
        signature = (signature ^ Math.round(flowDirection.y * 128)) * 0x100000001b3L;
        signature = (signature ^ Float.floatToIntBits(MudPhysicsSettings.wallStainFlowPreviewCells()))
                * 0x100000001b3L;
        signature = (signature ^ Float.floatToIntBits(MudPhysicsSettings.wallStainDripChance()))
                * 0x100000001b3L;
        if (surfaceClip != null) {
            signature = (signature ^ surfaceClip.geometryKey()) * 0x100000001b3L;
            signature = (signature ^ (surfaceClip.model() ? 1 : 0)) * 0x100000001b3L;
            signature = (signature ^ 0x76697369626c65L) * 0x100000001b3L;
        }
        long mediumMask = 0L;
        boolean hasPixels = false;
        int lifetimeTicks = MudPhysicsSettings.wallStainLifetimeTicks();
        for (MudFootprintBlockEntity.Entry stain : stains) {
            if (!isPreciseLayer(stain, face)) {
                continue;
            }
            hasPixels = true;
            signature = (signature ^ stain.id()) * 0x100000001b3L;
            signature = (signature ^ stain.visualSource()) * 0x100000001b3L;
            signature = (signature ^ AdaptiveMudClientCache.appearanceRevision(
                    blockEntity.getLevel(), stain.visualSource()))
                    * 0x100000001b3L;
            signature = (signature ^ stain.medium().id()) * 0x100000001b3L;
            int lastAge = -1;
            int growth = 0;
            int fadeStep = 0;
            for (long pixel : stain.wallPixels()) {
                signature = (signature ^ pixel) * 0x100000001b3L;
                mediumMask |= MudSkinTextureCache.mediumBit(MudFootprintBlockEntity.wallPixelMedium(pixel));
                if (MudFootprintBlockEntity.wallPixelSecondaryWeight(pixel) > 0.0F) {
                    mediumMask |= MudSkinTextureCache.mediumBit(
                            MudFootprintBlockEntity.wallPixelSecondaryMedium(pixel));
                }
                int age = pixelAge(pixel, gameTime);
                // Contact batches commonly share a timestamp; evaluate expensive growth once.
                if (age != lastAge) {
                    lastAge = age;
                    fadeStep = fadeSignatureStep(age, lifetimeTicks);
                    growth = flowDirection == Vec3.ZERO ? 0 : MudWallFlowLayout.growthStep(age,
                            MudPhysicsSettings.wallStainFadeInTicks(), MudPhysicsSettings.wallStainFlowDurationTicks());
                }
                signature = (signature ^ fadeStep) * 0x100000001b3L;
                if (flowDirection != Vec3.ZERO) {
                    signature = (signature ^ growth) * 0x100000001b3L;
                }
            }
        }
        long textureAnimation = MudSkinTextureCache.skinCoverageAnimationSignature(mediumMask, gameTime);
        if (textureAnimation != 0L) {
            signature = (signature ^ textureAnimation) * 0x100000001b3L;
        }
        return new BuildState(signature, hasPixels);
    }

    private static void rebuild(Entry cached, MudFootprintBlockEntity blockEntity,
            Direction face, List<MudFootprintBlockEntity.Entry> stains,
            long gameTime, boolean shaderSafe, SurfaceClip surfaceClip) {
        NativeImage stableImage = imageFor(cached.stableTexture, cached.height);
        NativeImage translucentImage = imageFor(cached.translucentTexture, cached.height);
        cached.hasStablePixels = false;
        cached.hasTranslucentPixels = false;

        cached.clearScratch();
        boolean physicalized = SableCompat.containingSubLevel(blockEntity) != null;
        int sampleOffsetX = faceSampleOffsetX(blockEntity, face);
        int sampleOffsetY = faceSampleOffsetY(blockEntity, face);
        for (MudFootprintBlockEntity.Entry stain : stains) {
            if (!isPreciseLayer(stain, face)) {
                continue;
            }
            for (long pixel : stain.wallPixels()) {
                int x = MudFootprintBlockEntity.wallPixelHorizontal(pixel);
                int y = MudFootprintBlockEntity.wallPixelVertical(pixel);
                int cell = x | y << 4;
                if (surfaceClip != null && !surfaceClip.visible()[cell]) {
                    continue;
                }
                int color = wallPixelColor(pixel,
                        sampleOffsetX + x,
                        MudWallTextureContinuity.textureSampleY(sampleOffsetY, y),
                        face, stain.visualSource(), gameTime);
                cached.addLayer(cell, color,
                        isStableOpaquePixel(pixel, gameTime)
                                && FastColor.ABGR32.alpha(color) >= 245);
            }
        }

        // Flow uses the same source colors, lifetime, clipping and final draw as the imprint.
        // Finish all actual contact pixels first so previews cannot darken them a second time.
        System.arraycopy(cached.occupied, 0, FLOW.contact, 0, CELL_COUNT);
        int flowBudget = MudWallFlowLayout.MAX_FLOWS_PER_FACE;
        for (MudFootprintBlockEntity.Entry stain : stains) {
            if (flowBudget > 0 && isPreciseLayer(stain, face) && cached.flowDirection != Vec3.ZERO) {
                flowBudget -= addFlows(cached, blockEntity, stain, gameTime, surfaceClip,
                        sampleOffsetX, sampleOffsetY, flowBudget);
            }
        }
        cached.finishColors(shaderSafe);
        boolean stableChanged = false;
        boolean translucentChanged = false;
        int plane = switch (face.getAxis()) {
            case X -> blockEntity.getBlockPos().getX();
            case Y -> blockEntity.getBlockPos().getY();
            case Z -> blockEntity.getBlockPos().getZ();
        };
        int contourSeed = plane * 0x27d4eb2d ^ face.get3DDataValue() * 0x165667b1;
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int cell = x | y << 4;
                int color = cached.occupied[cell] ? blendNeighborColors(
                        cached.continuousColors[cell], cached.continuousColors, cached.occupied, x, y) : 0;
                if (FLOW.contact[cell]) {
                    color = MudWallStainVariation.apply(color, x, y, sampleOffsetX + x, sampleOffsetY + y,
                            contourSeed, cached.contour);
                }
                boolean stable = cached.stableCells[cell] && FastColor.ABGR32.alpha(color) >= 245;
                int stableColor = stable ? color : 0;
                int translucentColor = stable ? 0 : color;
                int row = textureRow(y, physicalized);
                stableChanged |= setPixelIfChanged(stableImage, x, row, stableColor);
                translucentChanged |= setPixelIfChanged(translucentImage, x, row, translucentColor);
                cached.hasStablePixels |= stableColor != 0;
                cached.hasTranslucentPixels |= translucentColor != 0;
            }
        }
        if (stableChanged) cached.stableTexture.upload();
        if (translucentChanged) cached.translucentTexture.upload();
        cached.view = new TextureView(cached.stableLocation, cached.translucentLocation,
                cached.hasStablePixels, cached.hasTranslucentPixels);
    }

    static boolean setPixelIfChanged(NativeImage image, int x, int y, int color) {
        if (image.getPixelRGBA(x, y) == color) return false;
        image.setPixelRGBA(x, y, color);
        return true;
    }

    private static void collectContour(int[] rows, MudFootprintBlockEntity blockEntity, Direction face) {
        Arrays.fill(rows, 0);
        addContour(rows, blockEntity, face, 0, 0);
        // Only the four coplanar neighbors are needed for the two-pixel cardinal halo.
        for (int side = 0; side < 4; side++) {
            int du = side == 0 ? -16 : side == 1 ? 16 : 0;
            int dv = side == 2 ? -16 : side == 3 ? 16 : 0;
            BlockPos adjacent = switch (face.getAxis()) {
                case X -> blockEntity.getBlockPos().offset(0, dv / 16, du / 16);
                case Y -> blockEntity.getBlockPos().offset(du / 16, 0, dv / 16);
                case Z -> blockEntity.getBlockPos().offset(du / 16, dv / 16, 0);
            };
            if (blockEntity.getLevel().getChunkSource().hasChunk(adjacent.getX() >> 4, adjacent.getZ() >> 4)
                    && blockEntity.getLevel().getBlockEntity(adjacent) instanceof MudFootprintBlockEntity neighbor) {
                addContour(rows, neighbor, face, du, dv);
            }
        }
    }

    private static void addContour(int[] rows, MudFootprintBlockEntity blockEntity,
            Direction face, int du, int dv) {
        int first = dv < 0 ? GRID_SIZE - MudWallStainVariation.HALO : 0;
        int last = dv > 0 ? MudWallStainVariation.HALO : GRID_SIZE;
        for (int row = first; row < last; row++) {
            MudWallStainVariation.addRow(rows, blockEntity.preciseWallRow(face, row), row, du, dv);
        }
    }

    private static Vec3 flowDirection(MudFootprintBlockEntity blockEntity, Direction face) {
        Object subLevel = SableCompat.containingSubLevel(blockEntity);
        Vec3 down = subLevel == null ? new Vec3(0, -1, 0)
                : SableCompat.toLocalDirection(subLevel, new Vec3(0, -1, 0));
        if (down == null) return Vec3.ZERO;
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double gravity = normal.dot(down);
        if (gravity > 0.30) return Vec3.ZERO;
        Vec3 tangent = down.subtract(normal.scale(gravity));
        if (tangent.lengthSqr() <= 0.025) return Vec3.ZERO;
        tangent = tangent.normalize();
        return switch (face.getAxis()) {
            case X -> new Vec3(tangent.z, tangent.y, 0);
            case Y -> new Vec3(tangent.x, tangent.z, 0);
            case Z -> new Vec3(tangent.x, tangent.y, 0);
        };
    }

    private static int addFlows(Entry cached, MudFootprintBlockEntity blockEntity,
            MudFootprintBlockEntity.Entry stain, long gameTime, SurfaceClip clip,
            int offsetX, int offsetY, int remaining) {
        Arrays.fill(FLOW.cells, 0);
        for (long pixel : stain.wallPixels()) FLOW.cells[(int) pixel & 255] = pixel;
        Vec3 direction = cached.flowDirection;
        int count = MudWallFlowLayout.select(FLOW.cells, blockEntity.getBlockPos(), stain.face(),
                (int) Math.round(direction.x), (int) Math.round(direction.y), false,
                MudPhysicsSettings.wallStainDripChance(), FLOW.pixels, FLOW.hashes, FLOW.scores);
        count = Math.min(count, remaining);
        for (int i = 0; i < count; i++) {
            long pixel = FLOW.pixels[i];
            MudWallFlowLayout.rasterize(pixel, FLOW.hashes[i],
                    MudWallFlowLayout.growthStep(pixelAge(pixel, gameTime),
                            MudPhysicsSettings.wallStainFadeInTicks(), MudPhysicsSettings.wallStainFlowDurationTicks()),
                    MudPhysicsSettings.wallStainFlowPreviewCells(), direction.x, direction.y,
                    clip == null ? null : clip.visible(), FLOW.coverage);
            for (int cell = 0; cell < CELL_COUNT; cell++) {
                if (FLOW.contact[cell] || FLOW.coverage[cell] <= 0) continue;
                int color = wallPixelColor(pixel, offsetX + (cell & 15),
                        MudWallTextureContinuity.textureSampleY(offsetY, cell >>> 4),
                        stain.face(), stain.visualSource(), gameTime);
                int alpha = Math.round(FastColor.ABGR32.alpha(color) * FLOW.coverage[cell]);
                cached.addLayer(cell, FastColor.ABGR32.color(alpha,
                        FastColor.ABGR32.blue(color), FastColor.ABGR32.green(color), FastColor.ABGR32.red(color)), false);
            }
        }
        return count;
    }

    private static SurfaceClip surfaceClip(
            MudFootprintBlockEntity blockEntity, Direction face, SurfaceClip previous) {
        if (blockEntity.getLevel() == null) {
            return null;
        }
        BlockPos supportPos = blockEntity.getBlockPos()
                .relative(face.getOpposite());
        BlockState support = blockEntity.getLevel().getBlockState(supportPos);
        if (!MudFootprintBlock.isValidSupport(
                support, blockEntity.getLevel(), supportPos)) {
            return null;
        }
        MudRenderedSurfaceGeometry.RenderedSurface rendered = MudSurfaceClientSettings.preciseModelGeometry()
                && SableCompat.containingSubLevel(blockEntity) == null
                ? MudRenderedSurfaceGeometry.renderedSurface(blockEntity.getLevel(), supportPos, support, face)
                : null;
        List<AABB> boxes = rendered == null
                ? support.getCollisionShape(blockEntity.getLevel(), supportPos).toAabbs() : List.of();
        int geometryKey = rendered == null ? boxes.hashCode() : rendered.geometryKey();
        boolean model = rendered != null;
        if (previous != null && previous.geometryKey() == geometryKey && previous.model() == model) {
            return previous;
        }
        boolean[] visible = new boolean[CELL_COUNT];
        for (int y = 0; y < GRID_SIZE; y++) {
            double vertical = (y + 0.5D) / GRID_SIZE;
            for (int x = 0; x < GRID_SIZE; x++) {
                double horizontal = (x + 0.5D) / GRID_SIZE;
                Vec3 point = facePoint(face, horizontal, vertical);
                visible[x | y << 4] = rendered == null
                        ? WallStainGeometry.contains(boxes, face, horizontal, vertical)
                        : rendered.surfaceHit(point.x, point.y, point.z) != null;
            }
        }
        return new SurfaceClip(visible, geometryKey, model);
    }

    private static Vec3 facePoint(
            Direction face, double horizontal, double vertical) {
        return switch (face.getAxis()) {
            case X -> new Vec3(0.0D, vertical, horizontal);
            case Y -> new Vec3(horizontal, 0.0D, vertical);
            case Z -> new Vec3(horizontal, vertical, 0.0D);
        };
    }

    static int textureRow(int logicalVertical) {
        return Mth.clamp(logicalVertical, 0, GRID_SIZE - 1);
    }

    static int textureRow(int logicalVertical, boolean physicalized) {
        int row = textureRow(logicalVertical);
        return physicalized ? GRID_SIZE - 1 - row : row;
    }

    private static int fadeSignatureStep(int age, int lifetimeTicks) {
        if (MudPhysicsSettings.footprintPermanent()) {
            return 0;
        }
        int fadeStart = Math.round(lifetimeTicks * 0.55F);
        if (age < fadeStart) {
            return 0;
        }
        if (age >= lifetimeTicks) {
            return FADE_SIGNATURE_STEPS;
        }
        return 1 + Mth.clamp(
                Math.round((age - fadeStart) * (FADE_SIGNATURE_STEPS - 1.0F)
                        / Math.max(1, lifetimeTicks - fadeStart)),
                0,
                FADE_SIGNATURE_STEPS - 1);
    }

    private static NativeImage imageFor(DynamicTexture texture, int height) {
        NativeImage image = texture.getPixels();
        if (image == null) {
            image = new NativeImage(FACE_WIDTH, height, true);
            texture.setPixels(image);
        }
        return image;
    }

    private static boolean isPreciseLayer(MudFootprintBlockEntity.Entry stain, Direction face) {
        return stain.wallStain() && stain.face() == face && stain.wallPixels().length > 0;
    }

    private static int faceSampleOffsetX(MudFootprintBlockEntity blockEntity, Direction face) {
        return switch (face.getAxis()) {
            case X -> blockEntity.getBlockPos().getZ() * GRID_SIZE;
            case Y, Z -> blockEntity.getBlockPos().getX() * GRID_SIZE;
        };
    }

    private static int faceSampleOffsetY(MudFootprintBlockEntity blockEntity, Direction face) {
        return (face.getAxis() == Direction.Axis.Y
                ? blockEntity.getBlockPos().getZ()
                : blockEntity.getBlockPos().getY()) * GRID_SIZE;
    }

    private static int blendNeighborColors(int color, int[] colors, boolean[] occupied, int x, int y) {
        int mixed = blendNeighborGroup(color, colors, occupied, x, y,
                CARDINAL_OFFSETS, CARDINAL_COLOR_MIX);
        return blendNeighborGroup(mixed, colors, occupied, x, y,
                DIAGONAL_OFFSETS, DIAGONAL_COLOR_MIX);
    }

    private static int blendNeighborGroup(int color, int[] colors, boolean[] occupied,
            int x, int y, int[][] offsets, float amount) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int[] offset : offsets) {
            int neighborX = x + offset[0];
            int neighborY = y + offset[1];
            if (neighborX < 0 || neighborX >= GRID_SIZE || neighborY < 0 || neighborY >= GRID_SIZE) {
                continue;
            }
            int cell = neighborX | neighborY << 4;
            if (!occupied[cell]) {
                continue;
            }
            int neighbor = colors[cell];
            red += FastColor.ABGR32.red(neighbor);
            green += FastColor.ABGR32.green(neighbor);
            blue += FastColor.ABGR32.blue(neighbor);
            count++;
        }
        if (count == 0) {
            return color;
        }
        int neighborAverage = FastColor.ABGR32.color(
                FastColor.ABGR32.alpha(color), blue / count, green / count, red / count);
        return blendRgbPreserveAlpha(color, neighborAverage, amount);
    }

    private static int wallPixelColor(long pixel, int x, int y, Direction face,
            long visualSource, long gameTime) {
        SinkingMedium primary = MudFootprintBlockEntity.wallPixelMedium(pixel);
        float strength = MudFootprintBlockEntity.wallPixelStrength(pixel);
        float lifetimeVisibility = pixelLifetimeVisibility(pixel, gameTime);
        // The server-side transfer strength already contains the source body's
        // effective coverage opacity. Do not apply a second fixed medium alpha.
        int alpha = effectiveWallAlpha(strength, lifetimeVisibility);
        int color = MudWallTextureContinuity.sampleAbgr(
                primary, visualSource, face, x, y, alpha);
        float secondaryWeight = MudFootprintBlockEntity.wallPixelSecondaryWeight(pixel);
        if (secondaryWeight > 0.0F) {
            SinkingMedium secondary = MudFootprintBlockEntity.wallPixelSecondaryMedium(pixel);
            int secondaryColor = MudWallTextureContinuity.sampleAbgr(
                    secondary, visualSource, face, x, y, alpha);
            color = blendAbgr(color, secondaryColor, secondaryWeight);
        }
        return color;
    }

    static int effectiveWallAlpha(float strength, float lifetimeVisibility) {
        return Math.round(Mth.clamp(strength * lifetimeVisibility, 0.0F, 1.0F)
                * 255.0F);
    }

    private static boolean isStableOpaquePixel(long pixel, long gameTime) {
        if (MudFootprintBlockEntity.wallPixelMedium(pixel).translucentSkinCoverage()) {
            return false;
        }
        if (MudFootprintBlockEntity.wallPixelSecondaryWeight(pixel) > 0.0F
                && MudFootprintBlockEntity.wallPixelSecondaryMedium(pixel).translucentSkinCoverage()) {
            return false;
        }
        return MudFootprintBlockEntity.wallPixelStrength(pixel) >= 0.90F
                && pixelLifetimeVisibility(pixel, gameTime) >= 0.999F;
    }

    private static int blendAbgr(int first, int second, float amount) {
        float blend = Mth.clamp(amount, 0.0F, 1.0F);
        return FastColor.ABGR32.color(
                Math.max(FastColor.ABGR32.alpha(first), FastColor.ABGR32.alpha(second)),
                Mth.lerpInt(blend, FastColor.ABGR32.blue(first), FastColor.ABGR32.blue(second)),
                Mth.lerpInt(blend, FastColor.ABGR32.green(first), FastColor.ABGR32.green(second)),
                Mth.lerpInt(blend, FastColor.ABGR32.red(first), FastColor.ABGR32.red(second)));
    }

    static int blendRgbPreserveAlpha(int first, int second, float amount) {
        float blend = Mth.clamp(amount, 0.0F, 1.0F);
        return FastColor.ABGR32.color(
                FastColor.ABGR32.alpha(first),
                Mth.lerpInt(blend, FastColor.ABGR32.blue(first), FastColor.ABGR32.blue(second)),
                Mth.lerpInt(blend, FastColor.ABGR32.green(first), FastColor.ABGR32.green(second)),
                Mth.lerpInt(blend, FastColor.ABGR32.red(first), FastColor.ABGR32.red(second)));
    }

    private static int pixelAge(long pixel, long gameTime) {
        return MudFootprintBlockEntity.wallPixelAge(pixel, gameTime);
    }

    private static float pixelLifetimeVisibility(long pixel, long gameTime) {
        return visibilityAtAge(pixel, pixelAge(pixel, gameTime));
    }

    static float visibilityAtAge(long pixel, float age) {
        if (MudPhysicsSettings.footprintPermanent() || !MudFootprintBlockEntity.wallPixelHasCreationTime(pixel)) {
            return 1.0F;
        }
        int lifetime = Math.max(1, MudPhysicsSettings.wallStainLifetimeTicks());
        float fadeStart = lifetime * 0.55F;
        if (age <= fadeStart) {
            return 1.0F;
        }
        return Mth.clamp((lifetime - age) / Math.max(1.0F, lifetime - fadeStart), 0.0F, 1.0F);
    }

    private static void prune(long gameTime) {
        if (lastPruneTick != Long.MIN_VALUE && gameTime - lastPruneTick < 100L) {
            return;
        }
        lastPruneTick = gameTime;
        Iterator<Entry> iterator = CACHE.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (gameTime - entry.lastSeenTick > UNUSED_TICKS) {
                entry.close();
                iterator.remove();
            }
        }
    }

    record TextureView(ResourceLocation stableLocation, ResourceLocation translucentLocation,
            boolean hasStablePixels, boolean hasTranslucentPixels) {
    }

    private record Key(ResourceLocation dimension, long blockPos, Direction face) {
    }

    private record BuildState(long signature, boolean hasPixels) {
    }

    private record SurfaceClip(boolean[] visible, int geometryKey, boolean model) {
    }

    private static final class FlowScratch {
        private final long[] cells = new long[CELL_COUNT];
        private final boolean[] contact = new boolean[CELL_COUNT];
        private final float[] coverage = new float[CELL_COUNT];
        private final long[] pixels = new long[MudWallFlowLayout.MAX_FLOWS_PER_FACE];
        private final long[] hashes = new long[MudWallFlowLayout.MAX_FLOWS_PER_FACE];
        private final float[] scores = new float[MudWallFlowLayout.MAX_FLOWS_PER_FACE];
    }

    private static final class Entry {
        private final DynamicTexture stableTexture;
        private final DynamicTexture translucentTexture;
        private final ResourceLocation stableLocation;
        private final ResourceLocation translucentLocation;
        private final int height;
        private long signature = Long.MIN_VALUE;
        private long lastSeenTick;
        private long evaluatedTick = Long.MIN_VALUE;
        private boolean evaluatedShaderSafe;
        private BuildState evaluatedState = new BuildState(Long.MIN_VALUE, false);
        private SurfaceClip evaluatedSurfaceClip;
        private Vec3 flowDirection = Vec3.ZERO;
        private final int[] contour = new int[MudWallStainVariation.ROWS];
        private TextureView view;
        private boolean hasStablePixels;
        private boolean hasTranslucentPixels;
        private final float[] weightedRed = new float[CELL_COUNT];
        private final float[] weightedGreen = new float[CELL_COUNT];
        private final float[] weightedBlue = new float[CELL_COUNT];
        private final float[] colorWeight = new float[CELL_COUNT];
        private final float[] transmittance = new float[CELL_COUNT];
        private final boolean[] occupied = new boolean[CELL_COUNT];
        private final boolean[] stableCells = new boolean[CELL_COUNT];
        private final int[] colors = new int[CELL_COUNT];
        private final int[] continuousColors = new int[CELL_COUNT];

        private Entry(DynamicTexture stableTexture, DynamicTexture translucentTexture,
                ResourceLocation stableLocation, ResourceLocation translucentLocation, int height) {
            this.stableTexture = stableTexture;
            this.translucentTexture = translucentTexture;
            this.stableLocation = stableLocation;
            this.translucentLocation = translucentLocation;
            this.height = height;
        }

        private void clearScratch() {
            Arrays.fill(weightedRed, 0.0F);
            Arrays.fill(weightedGreen, 0.0F);
            Arrays.fill(weightedBlue, 0.0F);
            Arrays.fill(colorWeight, 0.0F);
            Arrays.fill(transmittance, 1.0F);
            Arrays.fill(occupied, false);
            Arrays.fill(stableCells, true);
            Arrays.fill(colors, 0);
            Arrays.fill(continuousColors, 0);
        }

        private void addLayer(int cell, int color, boolean stable) {
            float alpha = FastColor.ABGR32.alpha(color) / 255.0F;
            if (alpha <= 0.0F) {
                return;
            }
            occupied[cell] = true;
            stableCells[cell] &= stable;
            weightedRed[cell] += FastColor.ABGR32.red(color) * alpha;
            weightedGreen[cell] += FastColor.ABGR32.green(color) * alpha;
            weightedBlue[cell] += FastColor.ABGR32.blue(color) * alpha;
            colorWeight[cell] += alpha;
            transmittance[cell] *= 1.0F - alpha;
        }

        private void finishColors(boolean shaderSafe) {
            for (int cell = 0; cell < CELL_COUNT; cell++) {
                if (!occupied[cell]) {
                    continue;
                }
                float inverseWeight = 1.0F / Math.max(0.0001F, colorWeight[cell]);
                int alpha = Mth.clamp(Math.round((1.0F - transmittance[cell]) * 255.0F), 0, 255);
                int red = Mth.clamp(Math.round(weightedRed[cell] * inverseWeight), 0, 255);
                int green = Mth.clamp(Math.round(weightedGreen[cell] * inverseWeight), 0, 255);
                int blue = Mth.clamp(Math.round(weightedBlue[cell] * inverseWeight), 0, 255);
                colors[cell] = FastColor.ABGR32.color(alpha, blue, green, red);
                stableCells[cell] &= shaderSafe;
            }
            MudWallTextureContinuity.stabilizeLowOpacity(
                    colors, continuousColors, occupied, stableCells, GRID_SIZE);
        }

        private void close() {
            MudSurfaceDecalRenderTypes.release(stableLocation);
            MudSurfaceDecalRenderTypes.release(translucentLocation);
            Minecraft.getInstance().getTextureManager().release(stableLocation);
            Minecraft.getInstance().getTextureManager().release(translucentLocation);
        }
    }
}
