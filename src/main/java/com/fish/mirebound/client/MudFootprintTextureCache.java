package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.stain.MudFootprintBlock;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Collections;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fuses every ordinary footprint on one block face into a single shader-safe texture. */
final class MudFootprintTextureCache {
    static final float CANVAS_MIN = -1.0F;
    static final float CANVAS_MAX = 2.0F;
    private static final int PIXELS_PER_BLOCK = 16;
    private static final int TEXTURE_SIZE = 3 * PIXELS_PER_BLOCK;
    private static final float CORE_SIZE = 0.25F;
    private static final float MIN_SPREAD = 0.030F;
    private static final float SPREAD_VARIATION = 0.025F;
    private static final int FADE_BUCKETS = 64;
    private static final long UNUSED_TICKS = 400L;
    private static final Map<Key, CachedTexture> CACHE = new HashMap<>();
    private static long lastPruneTick = Long.MIN_VALUE;

    private MudFootprintTextureCache() {
    }

    static TextureView textureFor(MudFootprintBlockEntity blockEntity, Direction face,
            List<MudFootprintBlockEntity.Entry> entries) {
        if (blockEntity.getLevel() == null) {
            return null;
        }
        boolean hasFootprint = false;
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (!entry.wallStain() && entry.face() == face) {
                hasFootprint = true;
                break;
            }
        }
        if (!hasFootprint) {
            return null;
        }

        long gameTime = blockEntity.getLevel().getGameTime();
        boolean shaderSafe = ClientRenderCompat.useShaderSafeTransparency();
        Key key = new Key(blockEntity.getLevel().dimension().location(), blockEntity.getBlockPos().asLong(), face);
        CachedTexture cached = CACHE.computeIfAbsent(key, ignored -> createTexture());
        cached.lastSeenTick = gameTime;
        float surfacePlane = surfacePlane(blockEntity, entries, face);
        long signature = signature(blockEntity, entries, face, surfacePlane, gameTime, shaderSafe);
        if (cached.signature != signature) {
            rebuild(cached, blockEntity, entries, face, surfacePlane, gameTime, shaderSafe);
            cached.signature = signature;
        }
        prune(gameTime);
        return new TextureView(cached.stableLocation, cached.translucentLocation,
                cached.hasStablePixels, cached.hasTranslucentPixels);
    }

    static void reset() {
        for (CachedTexture cached : CACHE.values()) {
            cached.close();
        }
        CACHE.clear();
        lastPruneTick = Long.MIN_VALUE;
    }

    private static CachedTexture createTexture() {
        DynamicTexture stable = new DynamicTexture(TEXTURE_SIZE, TEXTURE_SIZE, true);
        DynamicTexture translucent = new DynamicTexture(TEXTURE_SIZE, TEXTURE_SIZE, true);
        stable.setFilter(false, false);
        translucent.setFilter(false, false);
        ResourceLocation stableLocation = Minecraft.getInstance().getTextureManager()
                .register("mirebound_fused_footprint_stable", stable);
        ResourceLocation translucentLocation = Minecraft.getInstance().getTextureManager()
                .register("mirebound_fused_footprint_fade", translucent);
        return new CachedTexture(stable, translucent, stableLocation, translucentLocation);
    }

    private static long signature(MudFootprintBlockEntity blockEntity,
            List<MudFootprintBlockEntity.Entry> entries, Direction face, float surfacePlane,
            long gameTime, boolean shaderSafe) {
        long value = (0xcbf29ce484222325L ^ (shaderSafe ? 1L : 0L)) * 0x100000001b3L;
        value = hash(value, Float.floatToIntBits(surfacePlane));
        value = supportSignature(blockEntity, face, value);
        long mediumMask = 0L;
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (entry.wallStain() || entry.face() != face) {
                continue;
            }
            value = hash(value, entry.id());
            value = hash(value, Float.floatToIntBits(entry.localX()));
            value = hash(value, Float.floatToIntBits(entry.localY()));
            value = hash(value, Float.floatToIntBits(entry.localZ()));
            value = hash(value, Float.floatToIntBits(entry.yawDegrees()));
            value = hash(value, Float.floatToIntBits(entry.width()));
            value = hash(value, Float.floatToIntBits(entry.height()));
            value = hash(value, Float.floatToIntBits(entry.strength()));
            value = hash(value, Float.floatToIntBits(entry.fade()));
            value = hash(value, entry.medium().id());
            value = hash(value, entry.visualSource());
            value = hash(value, AdaptiveMudClientCache.appearanceRevision(
                    blockEntity.getLevel(), entry.visualSource()));
            value = hash(value, Math.round(ageVisibility(entry, gameTime) * (FADE_BUCKETS - 1)));
            mediumMask |= MudSkinTextureCache.mediumBit(entry.medium());
        }
        long animation = MudSkinTextureCache.skinCoverageAnimationSignature(mediumMask, gameTime);
        if (animation != 0L) {
            value = hash(value, animation);
        }
        return value;
    }

    private static long hash(long value, long next) {
        return (value ^ next) * 0x100000001b3L;
    }

    private static void rebuild(CachedTexture cached, MudFootprintBlockEntity blockEntity,
            List<MudFootprintBlockEntity.Entry> entries, Direction face, float surfacePlane,
            long gameTime, boolean shaderSafe) {
        NativeImage stableImage = imageFor(cached.stableTexture);
        NativeImage translucentImage = imageFor(cached.translucentTexture);
        stableImage.fillRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE, 0);
        translucentImage.fillRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE, 0);
        cached.hasStablePixels = false;
        cached.hasTranslucentPixels = false;

        int[] fused = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        boolean[] supported = supportMask(blockEntity, face, surfacePlane);
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (!entry.wallStain() && entry.face() == face) {
                paintEntry(fused, supported, entry, gameTime);
            }
        }
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int color = fused[x + y * TEXTURE_SIZE];
                int alpha = FastColor.ABGR32.alpha(color);
                if (alpha <= 1) {
                    continue;
                }
                if (shaderSafe && alpha >= 245) {
                    stableImage.setPixelRGBA(x, y, FastColor.ABGR32.color(255,
                            FastColor.ABGR32.blue(color), FastColor.ABGR32.green(color),
                            FastColor.ABGR32.red(color)));
                    cached.hasStablePixels = true;
                } else {
                    translucentImage.setPixelRGBA(x, y, color);
                    cached.hasTranslucentPixels = true;
                }
            }
        }
        cached.stableTexture.upload();
        cached.translucentTexture.upload();
        cached.stableTexture.setFilter(false, false);
        cached.translucentTexture.setFilter(false, false);
    }

    private static void paintEntry(int[] fused, boolean[] supported,
            MudFootprintBlockEntity.Entry entry, long gameTime) {
        float visibility = Mth.clamp(entry.fade(), 0.0F, 1.0F) * ageVisibility(entry, gameTime);
        float strength = smooth(Mth.clamp(entry.strength(), 0.0F, 1.0F));
        if (visibility * strength <= 0.004F) {
            return;
        }
        float centerHorizontal = switch (entry.face().getAxis()) {
            case X -> entry.localZ();
            case Y, Z -> entry.localX();
        };
        float centerVertical = switch (entry.face().getAxis()) {
            case Y -> entry.localZ();
            case X, Z -> entry.localY();
        };
        float radians = entry.yawDegrees() * Mth.DEG_TO_RAD;
        float cos = Mth.cos(radians);
        float sin = Mth.sin(radians);
        long entryHash = mix(entry.id());
        int baseAlpha = Math.round(baseAlpha(entry.medium()) * visibility * strength);
        int salt = (int) entryHash;

        for (int y = 0; y < TEXTURE_SIZE; y++) {
            float vertical = CANVAS_MIN + (y + 0.5F) / PIXELS_PER_BLOCK;
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int index = x + y * TEXTURE_SIZE;
                if (!supported[index]) {
                    continue;
                }
                float horizontal = CANVAS_MIN + (x + 0.5F) / PIXELS_PER_BLOCK;
                float deltaHorizontal = horizontal - centerHorizontal;
                float deltaVertical = vertical - centerVertical;
                float localX = deltaHorizontal * cos + deltaVertical * sin;
                float localY = -deltaHorizontal * sin + deltaVertical * cos;
                float coverage = coverage(entry, entryHash, localX, localY);
                int alpha = Math.round(baseAlpha * coverage);
                if (alpha <= 1) {
                    continue;
                }
                int source = MudSkinTextureCache.skinCoverageTextureAbgr(
                        entry.medium(), entry.visualSource(), x, y, salt, alpha);
                fused[index] = sourceOver(fused[index], source);
            }
        }
    }

    private static float surfacePlane(MudFootprintBlockEntity blockEntity,
            List<MudFootprintBlockEntity.Entry> entries, Direction face) {
        for (MudFootprintBlockEntity.Entry entry : entries) {
            if (!entry.wallStain() && entry.face() == face) {
                float stored = switch (face.getAxis()) {
                    case X -> entry.localX();
                    case Y -> entry.localY();
                    case Z -> entry.localZ();
                };
                return closestSurfacePlane(blockEntity, face, stored);
            }
        }
        return 0.0F;
    }

    private static float closestSurfacePlane(MudFootprintBlockEntity blockEntity, Direction face, float stored) {
        if (blockEntity.getLevel() == null) {
            return stored - face.getAxisDirection().getStep() * 0.006F;
        }
        BlockPos containerPos = blockEntity.getBlockPos();
        BlockPos supportPos = containerPos.relative(face.getOpposite());
        Object subLevel = SableCompat.containingSubLevel(blockEntity);
        BlockState support = SableCompat.subLevelBlockState(blockEntity.getLevel(), subLevel, supportPos);
        float closest = Float.NaN;
        float closestDistance = Float.MAX_VALUE;
        for (AABB box : support.getCollisionShape(blockEntity.getLevel(), supportPos).toAabbs()) {
            double absolutePlane = switch (face) {
                case WEST -> supportPos.getX() + box.minX;
                case EAST -> supportPos.getX() + box.maxX;
                case DOWN -> supportPos.getY() + box.minY;
                case UP -> supportPos.getY() + box.maxY;
                case NORTH -> supportPos.getZ() + box.minZ;
                case SOUTH -> supportPos.getZ() + box.maxZ;
            };
            float localPlane = (float) (absolutePlane - switch (face.getAxis()) {
                case X -> containerPos.getX();
                case Y -> containerPos.getY();
                case Z -> containerPos.getZ();
            });
            float distance = Math.abs(localPlane - stored);
            if (distance < closestDistance) {
                closest = localPlane;
                closestDistance = distance;
            }
        }
        return Float.isNaN(closest)
                ? stored - face.getAxisDirection().getStep() * 0.006F
                : closest;
    }

    private static long supportSignature(MudFootprintBlockEntity blockEntity, Direction face, long signature) {
        if (blockEntity.getLevel() == null) {
            return signature;
        }
        Object subLevel = SableCompat.containingSubLevel(blockEntity);
        BlockPos container = blockEntity.getBlockPos();
        for (int first = -1; first <= 1; first++) {
            for (int second = -1; second <= 1; second++) {
                BlockPos support = supportPos(container, face, first, second);
                BlockState state = SableCompat.subLevelBlockState(blockEntity.getLevel(), subLevel, support);
                signature = hash(signature, support.asLong());
                signature = hash(signature, state.hashCode());
                MudRenderedSurfaceGeometry.RenderedSurface rendered =
                        renderedSupport(blockEntity, subLevel, support, state, face);
                if (rendered != null) {
                    signature = hash(signature, 0x76697369626c65L);
                    signature = hash(signature, rendered.geometryKey());
                }
            }
        }
        return signature;
    }

    private static boolean[] supportMask(MudFootprintBlockEntity blockEntity,
            Direction face, float surfacePlane) {
        boolean[] supported = new boolean[TEXTURE_SIZE * TEXTURE_SIZE];
        if (blockEntity.getLevel() == null) {
            return supported;
        }
        Object subLevel = SableCompat.containingSubLevel(blockEntity);
        Map<Long, List<AABB>> shapeCache = new HashMap<>();
        Map<Long, BlockState> stateCache = new HashMap<>();
        Map<Long, RenderedSupport> renderedCache = new HashMap<>();
        BlockPos container = blockEntity.getBlockPos();
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            float vertical = CANVAS_MIN + (y + 0.5F) / PIXELS_PER_BLOCK;
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                float horizontal = CANVAS_MIN + (x + 0.5F) / PIXELS_PER_BLOCK;
                Vec3 surface = surfacePoint(container, face, surfacePlane, horizontal, vertical);
                Vec3 inside = surface.subtract(face.getStepX() * 0.003D,
                        face.getStepY() * 0.003D, face.getStepZ() * 0.003D);
                BlockPos supportPos = BlockPos.containing(inside);
                long supportKey = supportPos.asLong();
                BlockState state = stateCache.computeIfAbsent(supportKey, ignored ->
                        SableCompat.subLevelBlockState(
                                blockEntity.getLevel(), subLevel, supportPos));
                if (MudSurfaceClientSettings.preciseModelGeometry()
                        && subLevel == null
                        && MudFootprintBlock.isValidSupport(
                                state, blockEntity.getLevel(), supportPos)) {
                    RenderedSupport precise = renderedCache.computeIfAbsent(
                            supportKey, ignored -> new RenderedSupport(
                                    MudRenderedSurfaceGeometry.renderedSurface(
                                            blockEntity.getLevel(), supportPos,
                                            state, face)));
                    if (precise.surface() != null) {
                        supported[x + y * TEXTURE_SIZE] =
                                precise.surface().surfaceHit(
                                        surface.x - supportPos.getX(),
                                        surface.y - supportPos.getY(),
                                        surface.z - supportPos.getZ()) != null;
                        continue;
                    }
                }
                List<AABB> shapes = shapeCache.computeIfAbsent(supportPos.asLong(), ignored -> {
                    if (!MudFootprintBlock.isValidSupport(
                            state, blockEntity.getLevel(), supportPos)) {
                        return Collections.emptyList();
                    }
                    return MudFootprintBlock.supportShape(
                            state, blockEntity.getLevel(), supportPos).toAabbs().stream()
                            .map(box -> box.move(supportPos))
                            .toList();
                });
                for (AABB shape : shapes) {
                    if (supportsSurfaceFace(shape, face, surface)) {
                        supported[x + y * TEXTURE_SIZE] = true;
                        break;
                    }
                }
            }
        }
        return supported;
    }

    private static MudRenderedSurfaceGeometry.RenderedSurface renderedSupport(
            MudFootprintBlockEntity blockEntity, Object subLevel,
            BlockPos supportPos, BlockState state, Direction face) {
        if (subLevel != null || !MudSurfaceClientSettings.preciseModelGeometry()
                || blockEntity.getLevel() == null
                || !MudFootprintBlock.isValidSupport(
                        state, blockEntity.getLevel(), supportPos)) {
            return null;
        }
        return MudRenderedSurfaceGeometry.renderedSurface(
                blockEntity.getLevel(), supportPos, state, face);
    }

    private static boolean supportsSurfaceFace(AABB shape, Direction face, Vec3 surface) {
        final double planeTolerance = 0.010D;
        final double edgeTolerance = 0.001D;
        double shapePlane = switch (face) {
            case WEST -> shape.minX;
            case EAST -> shape.maxX;
            case DOWN -> shape.minY;
            case UP -> shape.maxY;
            case NORTH -> shape.minZ;
            case SOUTH -> shape.maxZ;
        };
        double surfacePlane = switch (face.getAxis()) {
            case X -> surface.x;
            case Y -> surface.y;
            case Z -> surface.z;
        };
        if (Math.abs(shapePlane - surfacePlane) > planeTolerance) {
            return false;
        }
        return switch (face.getAxis()) {
            case X -> surface.y >= shape.minY - edgeTolerance && surface.y <= shape.maxY + edgeTolerance
                    && surface.z >= shape.minZ - edgeTolerance && surface.z <= shape.maxZ + edgeTolerance;
            case Y -> surface.x >= shape.minX - edgeTolerance && surface.x <= shape.maxX + edgeTolerance
                    && surface.z >= shape.minZ - edgeTolerance && surface.z <= shape.maxZ + edgeTolerance;
            case Z -> surface.x >= shape.minX - edgeTolerance && surface.x <= shape.maxX + edgeTolerance
                    && surface.y >= shape.minY - edgeTolerance && surface.y <= shape.maxY + edgeTolerance;
        };
    }

    private static BlockPos supportPos(BlockPos container, Direction face, int first, int second) {
        BlockPos base = container.relative(face.getOpposite());
        return switch (face.getAxis()) {
            case X -> base.offset(0, second, first);
            case Y -> base.offset(first, 0, second);
            case Z -> base.offset(first, second, 0);
        };
    }

    private static Vec3 surfacePoint(BlockPos container, Direction face, float plane,
            float horizontal, float vertical) {
        return switch (face.getAxis()) {
            case X -> new Vec3(container.getX() + plane, container.getY() + vertical,
                    container.getZ() + horizontal);
            case Y -> new Vec3(container.getX() + horizontal, container.getY() + plane,
                    container.getZ() + vertical);
            case Z -> new Vec3(container.getX() + horizontal, container.getY() + vertical,
                    container.getZ() + plane);
        };
    }

    private static float coverage(MudFootprintBlockEntity.Entry entry, long hash, float x, float y) {
        float halfWidth = entry.width() * 0.5F;
        float halfHeight = entry.height() * 0.5F;
        if (Math.abs(x) <= halfWidth && Math.abs(y) <= halfHeight) {
            return 1.0F;
        }
        float result = 0.0F;
        for (int side = 0; side < 4; side++) {
            long sideHash = mix(hash + side * 0x9e3779b97f4a7c15L);
            if (side == 3 && (sideHash & 3L) == 0L) {
                continue;
            }
            float sizeScale = Mth.clamp(Math.max(entry.width(), entry.height()) / CORE_SIZE, 0.85F, 1.45F);
            float extension = (MIN_SPREAD + ((sideHash >>> 4) & 15L) / 15.0F * SPREAD_VARIATION)
                    * sizeScale;
            float sideLength = side < 2 ? entry.width() : entry.height();
            float length = sideLength * (0.42F + ((sideHash >>> 8) & 15L) / 15.0F * 0.34F);
            float travel = Math.max(0.0F, (sideLength - length) * 0.5F);
            float tangent = (((sideHash >>> 12) & 31L) / 31.0F * 2.0F - 1.0F) * travel;
            float centerX;
            float centerY;
            float spreadHalfWidth;
            float spreadHalfHeight;
            if (side < 2) {
                centerX = tangent;
                centerY = (side == 0 ? -1.0F : 1.0F) * (halfHeight + extension * 0.5F);
                spreadHalfWidth = length * 0.5F;
                spreadHalfHeight = extension * 0.5F;
            } else {
                centerX = (side == 2 ? -1.0F : 1.0F) * (halfWidth + extension * 0.5F);
                centerY = tangent;
                spreadHalfWidth = extension * 0.5F;
                spreadHalfHeight = length * 0.5F;
            }
            if (Math.abs(x - centerX) <= spreadHalfWidth && Math.abs(y - centerY) <= spreadHalfHeight) {
                result = Math.max(result, 0.48F + ((sideHash >>> 17) & 7L) / 7.0F * 0.18F);
            }
        }
        return result;
    }

    private static int sourceOver(int destination, int source) {
        int sourceAlpha = FastColor.ABGR32.alpha(source);
        if (sourceAlpha >= 255 || FastColor.ABGR32.alpha(destination) == 0) {
            return source;
        }
        int destinationAlpha = FastColor.ABGR32.alpha(destination);
        float sourceAmount = sourceAlpha / 255.0F;
        float destinationAmount = destinationAlpha / 255.0F * (1.0F - sourceAmount);
        float total = sourceAmount + destinationAmount;
        if (total <= 0.0F) {
            return 0;
        }
        float sourceWeight = sourceAmount / total;
        return FastColor.ABGR32.color(
                Mth.clamp(Math.round(total * 255.0F), 0, 255),
                Mth.lerpInt(sourceWeight, FastColor.ABGR32.blue(destination), FastColor.ABGR32.blue(source)),
                Mth.lerpInt(sourceWeight, FastColor.ABGR32.green(destination), FastColor.ABGR32.green(source)),
                Mth.lerpInt(sourceWeight, FastColor.ABGR32.red(destination), FastColor.ABGR32.red(source)));
    }

    private static NativeImage imageFor(DynamicTexture texture) {
        NativeImage image = texture.getPixels();
        if (image == null) {
            image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
            texture.setPixels(image);
        }
        return image;
    }

    private static float ageVisibility(MudFootprintBlockEntity.Entry entry, long gameTime) {
        if (MudPhysicsSettings.footprintPermanent()) {
            return 1.0F;
        }
        long duration = Math.max(1L, entry.expiresAt() - entry.createdAt());
        double fadeStart = entry.createdAt() + duration * 0.55D;
        if (gameTime <= fadeStart) {
            return 1.0F;
        }
        return Mth.clamp((float) ((entry.expiresAt() - gameTime)
                / Math.max(1.0D, entry.expiresAt() - fadeStart)), 0.0F, 1.0F);
    }

    private static int baseAlpha(SinkingMedium medium) {
        if (medium == SinkingMedium.LIVING_SLIME) {
            return 150;
        }
        return medium.translucentSkinCoverage() ? 178 : 255;
    }

    private static float smooth(float value) {
        return value * value * (3.0F - value * 2.0F);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    private static void prune(long gameTime) {
        if (lastPruneTick != Long.MIN_VALUE && gameTime - lastPruneTick < 100L) {
            return;
        }
        lastPruneTick = gameTime;
        Iterator<CachedTexture> iterator = CACHE.values().iterator();
        while (iterator.hasNext()) {
            CachedTexture cached = iterator.next();
            if (gameTime - cached.lastSeenTick > UNUSED_TICKS) {
                cached.close();
                iterator.remove();
            }
        }
    }

    record TextureView(ResourceLocation stableLocation, ResourceLocation translucentLocation,
            boolean hasStablePixels, boolean hasTranslucentPixels) {
    }

    private record Key(ResourceLocation dimension, long blockPos, Direction face) {
    }

    private record RenderedSupport(
            MudRenderedSurfaceGeometry.RenderedSurface surface) {
    }

    private static final class CachedTexture {
        private final DynamicTexture stableTexture;
        private final DynamicTexture translucentTexture;
        private final ResourceLocation stableLocation;
        private final ResourceLocation translucentLocation;
        private long signature = Long.MIN_VALUE;
        private long lastSeenTick;
        private boolean hasStablePixels;
        private boolean hasTranslucentPixels;

        private CachedTexture(DynamicTexture stableTexture, DynamicTexture translucentTexture,
                ResourceLocation stableLocation, ResourceLocation translucentLocation) {
            this.stableTexture = stableTexture;
            this.translucentTexture = translucentTexture;
            this.stableLocation = stableLocation;
            this.translucentLocation = translucentLocation;
        }

        private void close() {
            MudSurfaceDecalRenderTypes.release(stableLocation);
            MudSurfaceDecalRenderTypes.release(translucentLocation);
            Minecraft.getInstance().getTextureManager().release(stableLocation);
            Minecraft.getInstance().getTextureManager().release(translucentLocation);
        }
    }
}
