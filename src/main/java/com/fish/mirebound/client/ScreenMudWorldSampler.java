package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import java.util.Arrays;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Owns shallow camera-local ordinary/Sable mud sampling and its tick caches. */
final class ScreenMudWorldSampler {
    private static final float MIN_VISIBLE_COVERAGE = 0.025F;
    private static final int CACHE_SIZE = 128;
    private static final int CACHE_MASK = CACHE_SIZE - 1;
    private static final double WORLD_SURFACE_TOLERANCE = 0.025D;
    private static final double FACE_FORWARD = 0.075D;
    private static final double FACE_FAR_FORWARD = 0.145D;
    private static final double FACE_HALF_WIDTH = 0.210D;
    private static final double FACE_HALF_HEIGHT = 0.220D;
    private static final double FACE_CENTER_Y_OFFSET = -0.045D;
    private static final double FACE_MIN_HORIZONTAL_INSET = -0.025D;
    private static final double FACE_HORIZONTAL_FEATHER = 0.080D;
    private static final double FACE_DEPTH_FEATHER = 0.070D;
    private static final double FACE_SEAM_SEARCH = 0.055D;
    private static final float FACE_SEAM_WEIGHT = 0.72F;
    private static final boolean[] CACHE_USED = new boolean[CACHE_SIZE];
    private static final long[] CACHE_KEYS = new long[CACHE_SIZE];
    private static final BlockState[] CACHE_STATES = new BlockState[CACHE_SIZE];
    private static final SinkingMedium[] CACHE_MEDIA = new SinkingMedium[CACHE_SIZE];
    private static final long[] CACHE_VISUAL_SOURCE = new long[CACHE_SIZE];
    private static final BlockPos[] CACHE_TOP_POSITIONS = new BlockPos[CACHE_SIZE];
    private static final BlockState[] CACHE_TOP_STATES = new BlockState[CACHE_SIZE];
    private static final SinkingMedium[] CACHE_TOP_MEDIA = new SinkingMedium[CACHE_SIZE];
    private static final long[] CACHE_TOP_VISUAL_SOURCE = new long[CACHE_SIZE];
    private static final double[] CACHE_SURFACE_Y = new double[CACHE_SIZE];
    private static Level cachedSableLevel;
    private static int cachedSableEntityId = Integer.MIN_VALUE;
    private static long cachedSableGameTime = Long.MIN_VALUE;
    private static SableCompat.SinkingVolumeProbe cachedSableProbe;

    private ScreenMudWorldSampler() {
    }

    static void beginFrame() {
        Arrays.fill(CACHE_USED, false);
    }

    static void reset() {
        Arrays.fill(CACHE_USED, false);
        cachedSableLevel = null;
        cachedSableEntityId = Integer.MIN_VALUE;
        cachedSableGameTime = Long.MIN_VALUE;
        cachedSableProbe = null;
    }

    static boolean shouldBuildFaceMask(Level level, Vec3 origin,
            Vec3 forward, Vec3 up, Vec3 right, float verticalSampleScale) {
        float edgeY = verticalSampleScale * 0.96F;
        return facePoint(level, origin, forward, up, right,
                        0.0F, 0.0F).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        -0.86F, -0.86F).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        0.86F, -0.86F).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        -0.86F, 0.86F).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        0.86F, 0.86F).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        -0.66F, -edgeY).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        0.0F, -edgeY).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        0.66F, -edgeY).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        -0.66F, edgeY).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        0.0F, edgeY).strength() > MIN_VISIBLE_COVERAGE
                || facePoint(level, origin, forward, up, right,
                        0.66F, edgeY).strength() > MIN_VISIBLE_COVERAGE
                || worldPoint(level, origin).strength() > MIN_VISIBLE_COVERAGE;
    }

    static Sample facePoint(Level level, Vec3 origin, Vec3 forward,
            Vec3 up, Vec3 right, float xNorm, float yNorm) {
        Vec3 nearPoint = origin
                .add(forward.scale(FACE_FORWARD))
                .add(right.scale(xNorm * FACE_HALF_WIDTH))
                .add(up.scale(yNorm * FACE_HALF_HEIGHT + FACE_CENTER_Y_OFFSET));
        Vec3 farPoint = origin
                .add(forward.scale(FACE_FAR_FORWARD))
                .add(right.scale(xNorm * FACE_HALF_WIDTH))
                .add(up.scale(yNorm * FACE_HALF_HEIGHT + FACE_CENTER_Y_OFFSET));
        return strongest(facePoint(level, nearPoint), facePoint(level, farPoint));
    }

    static boolean belowSurface(Level level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            SinkingSample sample = sampleSable(level, point,
                    Minecraft.getInstance().player);
            if (sample == null) {
                return false;
            }
            if (!MudBlock.supportsVerticalSinking(
                    sample.state(), sample.medium())) {
                return true;
            }
            SinkingMedium surfaceMedium = sample.topMedium() == null
                    ? sample.medium() : sample.topMedium();
            return sample.localPoint().y <= sample.topPos().getY()
                    + MudMediumRuntime.surfaceHeightAt(
                            level, sample.topPos(), sample.topState(), surfaceMedium,
                            sample.localPoint().x, sample.localPoint().z)
                    + WORLD_SURFACE_TOLERANCE
                    && sample.localPoint().y
                            >= sample.bottomPos().getY() - 0.030D;
        }
        if (!MudBlock.supportsVerticalSinking(state, medium)) {
            return MudBlock.containsLocalPoint(
                    level, pos, state, medium,
                    point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    WORLD_SURFACE_TOLERANCE);
        }
        BlockPos topPos = findTop(level, pos);
        BlockState topState = level.getBlockState(topPos);
        SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
        SinkingMedium surfaceMedium = topMedium == null ? medium : topMedium;
        return point.y <= topPos.getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, topPos, topState, surfaceMedium, point.x, point.z)
                + WORLD_SURFACE_TOLERANCE;
    }

    private static Sample facePoint(Level level, Vec3 point) {
        Sample best = facePointInBlock(level, point, BlockPos.containing(point));
        best = strongest(best, facePointOffset(level, point, FACE_SEAM_SEARCH, 0.0D));
        best = strongest(best, facePointOffset(level, point, -FACE_SEAM_SEARCH, 0.0D));
        best = strongest(best, facePointOffset(level, point, 0.0D, FACE_SEAM_SEARCH));
        return strongest(best, facePointOffset(level, point, 0.0D, -FACE_SEAM_SEARCH));
    }

    private static Sample facePointOffset(
            Level level, Vec3 point, double dx, double dz) {
        Vec3 shifted = point.add(dx, 0.0D, dz);
        return scaled(facePointInBlock(
                level, shifted, BlockPos.containing(shifted)), FACE_SEAM_WEIGHT);
    }

    private static Sample facePointInBlock(
            Level level, Vec3 point, BlockPos pos) {
        int slot = cacheSlot(level, pos);
        BlockState state = slot >= 0 ? CACHE_STATES[slot] : level.getBlockState(pos);
        SinkingMedium medium = slot >= 0
                ? CACHE_MEDIA[slot] : ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            return sableFacePoint(level, point, Minecraft.getInstance().player);
        }
        if (!MudBlock.supportsVerticalSinking(state, medium)) {
            return MudBlock.containsLocalPoint(
                    level, pos, state, medium,
                    point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    WORLD_SURFACE_TOLERANCE)
                    ? new Sample(1.0F, medium,
                            slot >= 0 ? CACHE_VISUAL_SOURCE[slot]
                                    : visualSource(level, pos, state, medium))
                    : Sample.NONE;
        }

        BlockPos topPos = slot >= 0 ? CACHE_TOP_POSITIONS[slot] : findTop(level, pos);
        BlockState topState = slot >= 0
                ? CACHE_TOP_STATES[slot] : level.getBlockState(topPos);
        SinkingMedium topMedium = slot >= 0
                ? CACHE_TOP_MEDIA[slot] : ModBlocks.mediumOf(topState.getBlock());
        SinkingMedium surfaceMedium = topMedium == null ? medium : topMedium;
        boolean useTopSource = point.y >= topPos.getY() - 0.08D;
        double surfaceY = topState.getBlock() instanceof AdaptiveMudBlock
                ? topPos.getY() + MudMediumRuntime.surfaceHeightAt(
                        level, topPos, topState, surfaceMedium, point.x, point.z)
                : slot >= 0 ? CACHE_SURFACE_Y[slot]
                        : topPos.getY() + MudMediumRuntime.surfaceHeight(
                                level, topState, surfaceMedium);
        double depth = surfaceY - point.y;
        if (depth < -WORLD_SURFACE_TOLERANCE) {
            return Sample.NONE;
        }
        double localX = point.x - pos.getX();
        double localZ = point.z - pos.getZ();
        double inset = Math.min(
                Math.min(localX, 1.0D - localX),
                Math.min(localZ, 1.0D - localZ));
        if (inset <= FACE_MIN_HORIZONTAL_INSET) {
            return Sample.NONE;
        }
        float horizontal = smootherStep(Mth.clamp((float)
                ((inset - FACE_MIN_HORIZONTAL_INSET)
                        / FACE_HORIZONTAL_FEATHER), 0.0F, 1.0F));
        float depthStrength = smootherStep(Mth.clamp((float)
                ((depth + WORLD_SURFACE_TOLERANCE) / FACE_DEPTH_FEATHER),
                0.0F, 1.0F));
        float strength = Mth.clamp(horizontal * depthStrength, 0.0F, 1.0F);
        SinkingMedium contactMedium = columnMedium(
                medium, surfaceMedium, topPos, point.y, 0.08D);
        long visualSource = useTopSource
                ? (slot >= 0 ? CACHE_TOP_VISUAL_SOURCE[slot]
                        : visualSource(level, topPos, topState, surfaceMedium))
                : (slot >= 0 ? CACHE_VISUAL_SOURCE[slot]
                        : visualSource(level, pos, state, medium));
        return strength <= MIN_VISIBLE_COVERAGE
                ? Sample.NONE : new Sample(strength, contactMedium, visualSource);
    }

    private static Sample sableFacePoint(
            Level level, Vec3 point, Entity entity) {
        SinkingSample sample = sampleSable(level, point, entity);
        if (sample == null) {
            return Sample.NONE;
        }
        if (!MudBlock.supportsVerticalSinking(sample.state(), sample.medium())) {
            return new Sample(1.0F, sample.medium(), sableVisualSource(level, sample));
        }
        double surfaceLocalY = sample.pos().getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, sample.pos(), sample.state(), sample.medium(),
                        sample.localPoint().x, sample.localPoint().z);
        double depth = surfaceLocalY - sample.localPoint().y;
        if (depth < -WORLD_SURFACE_TOLERANCE
                || sample.localPoint().y < sample.pos().getY() - 0.030D) {
            return Sample.NONE;
        }
        double localX = sample.localPoint().x - Math.floor(sample.localPoint().x);
        double localZ = sample.localPoint().z - Math.floor(sample.localPoint().z);
        double inset = Math.min(
                Math.min(localX, 1.0D - localX),
                Math.min(localZ, 1.0D - localZ));
        if (inset <= FACE_MIN_HORIZONTAL_INSET) {
            return Sample.NONE;
        }
        float horizontal = smootherStep(Mth.clamp((float)
                ((inset - FACE_MIN_HORIZONTAL_INSET)
                        / FACE_HORIZONTAL_FEATHER), 0.0F, 1.0F));
        float depthStrength = smootherStep(Mth.clamp((float)
                ((depth + WORLD_SURFACE_TOLERANCE) / FACE_DEPTH_FEATHER),
                0.0F, 1.0F));
        float strength = Mth.clamp(horizontal * depthStrength, 0.0F, 1.0F);
        return strength <= MIN_VISIBLE_COVERAGE
                ? Sample.NONE : new Sample(
                        strength, sample.medium(), sableVisualSource(level, sample));
    }

    private static Sample worldPoint(Level level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = level.getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            return sableWorldPoint(level, point, Minecraft.getInstance().player);
        }
        if (!MudBlock.supportsVerticalSinking(state, medium)) {
            return MudBlock.containsLocalPoint(
                    level, pos, state, medium,
                    point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    WORLD_SURFACE_TOLERANCE)
                    ? new Sample(1.0F, medium,
                            visualSource(level, pos, state, medium))
                    : Sample.NONE;
        }
        BlockPos topPos = findTop(level, pos);
        SinkingMedium topMedium = ModBlocks.mediumOf(
                level.getBlockState(topPos).getBlock());
        SinkingMedium surfaceMedium = topMedium == null ? medium : topMedium;
        BlockState topState = level.getBlockState(topPos);
        double surfaceY = topPos.getY() + MudMediumRuntime.surfaceHeightAt(
                level, topPos, topState, surfaceMedium, point.x, point.z);
        if (point.y > surfaceY + WORLD_SURFACE_TOLERANCE) {
            return Sample.NONE;
        }
        double localX = point.x - pos.getX();
        double localY = point.y - pos.getY();
        double localZ = point.z - pos.getZ();
        double inset = Math.min(
                Math.min(Math.min(localX, 1.0D - localX),
                        Math.min(localY, 1.0D - localY)),
                Math.min(localZ, 1.0D - localZ));
        float strength = smootherStep(Mth.clamp(
                (float) ((inset + 0.022D) / 0.070D), 0.0F, 1.0F));
        if (strength <= 0.0F) {
            return Sample.NONE;
        }
        boolean useTopSource = point.y >= topPos.getY() - 0.08D;
        BlockState sourceState = useTopSource ? level.getBlockState(topPos) : state;
        SinkingMedium sourceMedium = useTopSource ? surfaceMedium : medium;
        return new Sample(strength, columnMedium(
                medium, surfaceMedium, topPos, point.y, 0.08D),
                visualSource(level, useTopSource ? topPos : pos,
                        sourceState, sourceMedium));
    }

    private static Sample sableWorldPoint(
            Level level, Vec3 point, Entity entity) {
        SinkingSample sample = sampleSable(level, point, entity);
        if (sample == null) {
            return Sample.NONE;
        }
        if (!MudBlock.supportsVerticalSinking(sample.state(), sample.medium())) {
            return new Sample(1.0F, sample.medium(), sableVisualSource(level, sample));
        }
        double surfaceLocalY = sample.pos().getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, sample.pos(), sample.state(), sample.medium(),
                        sample.localPoint().x, sample.localPoint().z);
        if (sample.localPoint().y > surfaceLocalY + WORLD_SURFACE_TOLERANCE
                || sample.localPoint().y < sample.pos().getY() - 0.030D) {
            return Sample.NONE;
        }
        double localX = sample.localPoint().x - Math.floor(sample.localPoint().x);
        double localY = sample.localPoint().y - Math.floor(sample.localPoint().y);
        double localZ = sample.localPoint().z - Math.floor(sample.localPoint().z);
        double inset = Math.min(
                Math.min(Math.min(localX, 1.0D - localX),
                        Math.min(localY, 1.0D - localY)),
                Math.min(localZ, 1.0D - localZ));
        float strength = smootherStep(Mth.clamp(
                (float) ((inset + 0.022D) / 0.070D), 0.0F, 1.0F));
        return strength <= 0.0F
                ? Sample.NONE : new Sample(
                        strength, sample.medium(), sableVisualSource(level, sample));
    }

    private static long sableVisualSource(Level level, SinkingSample sample) {
        return sample.visualSource() != MudVisualSource.NONE
                ? sample.visualSource()
                : visualSource(level, sample.pos(), sample.state(), sample.medium());
    }

    private static SinkingSample sampleSable(
            Level level, Vec3 point, Entity entity) {
        if (entity == null) {
            return SableCompat.sampleSinking(level, point);
        }
        long gameTime = level.getGameTime();
        if (cachedSableProbe == null || cachedSableLevel != level
                || cachedSableEntityId != entity.getId()
                || cachedSableGameTime != gameTime) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 center = camera == null ? entity.getEyePosition() : camera.getPosition();
            double radius = 1.05D;
            AABB bounds = new AABB(
                    center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius);
            cachedSableProbe = SableCompat.sinkingVolumeProbe(level, bounds, entity);
            cachedSableLevel = level;
            cachedSableEntityId = entity.getId();
            cachedSableGameTime = gameTime;
        }
        return cachedSableProbe.sample(point);
    }

    private static int cacheSlot(Level level, BlockPos pos) {
        long key = pos.asLong();
        int slot = Long.hashCode(key * 0x9E3779B97F4A7C15L) & CACHE_MASK;
        for (int probe = 0; probe < CACHE_SIZE; probe++) {
            if (!CACHE_USED[slot]) {
                BlockState state = level.getBlockState(pos);
                SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
                CACHE_USED[slot] = true;
                CACHE_KEYS[slot] = key;
                CACHE_STATES[slot] = state;
                CACHE_MEDIA[slot] = medium;
                CACHE_VISUAL_SOURCE[slot] = visualSource(level, pos, state, medium);
                if (medium != null
                        && MudBlock.supportsVerticalSinking(state, medium)) {
                    BlockPos topPos = findTop(level, pos);
                    BlockState topState = level.getBlockState(topPos);
                    SinkingMedium topMedium = ModBlocks.mediumOf(topState.getBlock());
                    SinkingMedium surfaceMedium = topMedium == null ? medium : topMedium;
                    CACHE_TOP_POSITIONS[slot] = topPos;
                    CACHE_TOP_STATES[slot] = topState;
                    CACHE_TOP_MEDIA[slot] = topMedium;
                    CACHE_TOP_VISUAL_SOURCE[slot] = visualSource(
                            level, topPos, topState, surfaceMedium);
                    CACHE_SURFACE_Y[slot] = topPos.getY()
                            + MudMediumRuntime.surfaceHeight(
                                    level, topPos, topState, surfaceMedium);
                } else {
                    CACHE_TOP_POSITIONS[slot] = pos;
                    CACHE_TOP_STATES[slot] = state;
                    CACHE_TOP_MEDIA[slot] = medium;
                    CACHE_TOP_VISUAL_SOURCE[slot] = CACHE_VISUAL_SOURCE[slot];
                    CACHE_SURFACE_Y[slot] = pos.getY();
                }
                return slot;
            }
            if (CACHE_KEYS[slot] == key) {
                return slot;
            }
            slot = slot + 1 & CACHE_MASK;
        }
        return -1;
    }

    private static BlockPos findTop(Level level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        while (cursor.getY() < level.getMaxBuildHeight() - 1) {
            cursor.move(Direction.UP);
            if (!ModBlocks.isSinkingBlock(level.getBlockState(cursor).getBlock())) {
                cursor.move(Direction.DOWN);
                break;
            }
        }
        return cursor.immutable();
    }

    private static SinkingMedium columnMedium(SinkingMedium sampled,
            SinkingMedium surface, BlockPos topPos,
            double pointY, double topLayerBias) {
        if (surface == null || surface == sampled) {
            return sampled;
        }
        return pointY >= topPos.getY() - topLayerBias ? surface : sampled;
    }

    private static Sample scaled(Sample sample, float scale) {
        if (sample.strength() <= MIN_VISIBLE_COVERAGE) {
            return Sample.NONE;
        }
        float strength = Mth.clamp(sample.strength() * scale, 0.0F, 1.0F);
        return strength <= MIN_VISIBLE_COVERAGE
                ? Sample.NONE : new Sample(
                        strength, sample.medium(), sample.visualSource());
    }

    private static Sample strongest(Sample first, Sample second) {
        return second.strength() > first.strength() ? second : first;
    }

    private static float smootherStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * clamped
                * (clamped * (clamped * 6.0F - 15.0F) + 10.0F);
    }

    private static long visualSource(Level level, BlockPos pos,
            BlockState state, SinkingMedium medium) {
        if (!(state.getBlock() instanceof AdaptiveMudBlock) || medium == null) {
            return MudVisualSource.NONE;
        }
        return MudSurfaceAppearance.captureVisualSource(
                level, pos, MudBlock.surfaceDirection(state, medium));
    }

    record Sample(float strength, SinkingMedium medium, long visualSource) {
        static final Sample NONE = new Sample(
                0.0F, SinkingMedium.MUD, MudVisualSource.NONE);
    }
}
