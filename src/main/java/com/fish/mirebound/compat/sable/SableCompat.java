package com.fish.mirebound.compat.sable;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.registry.ModBlocks;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import com.fish.mirebound.compat.sable.SableReflectionApi.Api;

public final class SableCompat {
    private static final String PHYSICS_BAKE_CONTEXT =
            "dev.ryanhcode.sable.physics.impl.SableCollisionContextImpl";
    private static final String EMBEDDED_PLOT_ACCESSOR =
            "dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor";
    private static final double POINT_QUERY_RADIUS = 0.001D;
    private static final int MAXIMUM_SINKING_VOLUME_BLOCK_SAMPLES = 64;
    private static final int MAXIMUM_ROPE_COLLISION_BOXES = 16_384;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<Class<?>, Method> TRACKING_PLAYERS_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> SET_TRACKING_SUBLEVEL_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> SET_LAST_TRACKING_SUBLEVEL_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> SET_PLOT_POSITION_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<EmbeddedAccessorBridge>> EMBEDDED_ACCESSORS =
            new ConcurrentHashMap<>();

    private SableCompat() {
    }

    public static boolean isPhysicsBakeContext(CollisionContext context) {
        return context != null
                && PHYSICS_BAKE_CONTEXT.equals(context.getClass().getName());
    }

    public static boolean isLoaded() {
        return api() != null;
    }

    /** Resolves Sable's center-relative block view to the parent plot-storage position. */
    public static StorageAccess storageAccess(BlockGetter view, BlockPos pos) {
        if (view == null || pos == null) {
            return null;
        }
        if (view instanceof Level level) {
            return new StorageAccess(level, pos.immutable());
        }
        Class<?> type = view.getClass();
        if (!EMBEDDED_PLOT_ACCESSOR.equals(type.getName())) {
            return null;
        }
        Optional<EmbeddedAccessorBridge> bridge = EMBEDDED_ACCESSORS.computeIfAbsent(
                type, SableCompat::findEmbeddedAccessorBridge);
        if (bridge.isEmpty()) {
            return null;
        }
        try {
            Object parent = bridge.get().level().invoke(view);
            Object center = bridge.get().center().get(view);
            if (parent instanceof Level level && center instanceof BlockPos centerPos) {
                return new StorageAccess(level, embeddedStoragePos(pos, centerPos));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional compatibility: unresolved access falls back to the proxy behavior.
        }
        return null;
    }

    static BlockPos embeddedStoragePos(BlockPos localPos, BlockPos centerPos) {
        return localPos.offset(centerPos);
    }

    private static Optional<EmbeddedAccessorBridge> findEmbeddedAccessorBridge(Class<?> type) {
        try {
            Method level = type.getMethod("getLevel");
            Field center = type.getDeclaredField("center");
            if (!center.trySetAccessible()) {
                return Optional.empty();
            }
            return Optional.of(new EmbeddedAccessorBridge(level, center));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Resolves the Sable sub-level owning one hidden plot-storage position. */
    public static Object subLevelAtStorage(Level level, BlockPos pos) {
        Api current = api();
        if (current == null || current.getContainingPosition == null) {
            return null;
        }
        try {
            return current.getContainingPosition.invoke(current.helper, level, pos);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static UUID subLevelIdAtStorage(Level level, BlockPos pos) {
        return subLevelId(subLevelAtStorage(level, pos));
    }

    /** Resolves the current client/server sub-level instance from its persistent ID. */
    public static Object subLevelById(Level level, UUID subLevelId) {
        Api current = api();
        if (current == null || current.getContainer == null
                || current.getSubLevelById == null || subLevelId == null) {
            return null;
        }
        try {
            Object container = current.getContainer.invoke(null, level);
            return container == null ? null : current.getSubLevelById.invoke(container, subLevelId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static UUID subLevelId(Object subLevel) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object value = subLevel.getClass().getMethod("getUniqueId").invoke(subLevel);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /** Returns a bounded snapshot of physical structures intersecting a world-space box. */
    public static List<Object> subLevelsIntersecting(Level level, AABB worldBounds) {
        Api current = api();
        if (current == null || current.getAllIntersecting == null
                || current.boundingBoxConstructor == null) {
            return List.of();
        }
        try {
            Object bounds = current.boundingBoxConstructor.newInstance(
                    worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                    worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ);
            Object value = current.getAllIntersecting.invoke(current.helper, level, bounds);
            if (!(value instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<Object> result = new ArrayList<>();
            for (Object subLevel : iterable) {
                if (subLevel != null && !result.contains(subLevel)) {
                    result.add(subLevel);
                }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    public static SinkingSample sampleSinking(Level level, Vec3 point) {
        Api current = api();
        if (current == null) {
            return null;
        }

        try {
            try {
                SinkingSample best = sampleBestIntersecting(level, point, current);
                if (best != null) {
                    return best;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to Sable's first-hit helper if the optional candidate enumeration is unavailable.
            }

            @SuppressWarnings("unchecked")
            BiFunction<Object, BlockPos, SinkingSample> sampler = (subLevel, pos) -> {
                return sampleSubLevel(level, subLevel, point, pos);
            };
            return (SinkingSample) current.runIncludingSubLevels.invoke(current.helper, level, point, false, null, sampler);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static SinkingSample sampleSinking(Level level, Vec3 point, Entity entity) {
        // Callers pass actual world/render points. A player can be "tracking" a
        // physicalized sub-level while their body is still above it, so using the
        // tracking sub-level as an unconditional fallback can falsely stain the
        // whole player with lower-layer material.
        return sampleSinking(level, point);
    }

    /**
     * Freezes the Sable structures intersecting a player's body for one physics pass.
     * Sable itself uses an entity-sized broadphase for entityInside callbacks; using the
     * same scope avoids alternating point-query hits at large or sleeping structures.
     */
    public static SinkingVolumeProbe sinkingVolumeProbe(Level level, AABB worldBounds, Entity entity) {
        Api current = api();
        if (current == null) {
            return SinkingVolumeProbe.empty(level);
        }

        List<Object> subLevels = new ArrayList<>();
        boolean broadphaseResolved = false;
        if (current.getAllIntersecting != null && current.boundingBoxConstructor != null) {
            try {
                Object bounds = current.boundingBoxConstructor.newInstance(
                        worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                        worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ);
                Object value = current.getAllIntersecting.invoke(current.helper, level, bounds);
                if (value instanceof Iterable<?> iterable) {
                    broadphaseResolved = true;
                    for (Object subLevel : iterable) {
                        if (subLevel != null && !subLevels.contains(subLevel)) {
                            subLevels.add(subLevel);
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The probe retains the legacy point lookup only when enumeration itself failed.
            }
        }

        Object tracked = trackingSubLevel(current, entity);
        if (tracked != null && !subLevels.contains(tracked)) {
            subLevels.add(tracked);
        }
        return new SinkingVolumeProbe(level, List.copyOf(subLevels), broadphaseResolved);
    }

    public static boolean isTracking(Entity entity) {
        Api current = api();
        return current != null && trackingSubLevel(current, entity) != null;
    }

    /** Converts a Sable plot-storage point to its physical world point when necessary. */
    public static Vec3 projectOutOfSubLevel(Level level, Vec3 point) {
        Api current = api();
        if (current == null || current.projectOutOfSubLevel == null || level == null || point == null) {
            return point;
        }
        try {
            Vec3 projected = toVec3(current.projectOutOfSubLevel.invoke(current.helper, level, point));
            return projected == null ? point : projected;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return point;
        }
    }

    /** Clears transient Sable tracking without retaining a hidden plot login point. */
    public static void clearEntityTracking(Entity entity) {
        if (entity == null || api() == null) {
            return;
        }
        invokeNullableSetter(entity, SET_TRACKING_SUBLEVEL_METHODS, "sable$setTrackingSubLevel");
        invokeNullableSetter(entity, SET_LAST_TRACKING_SUBLEVEL_METHODS, "sable$setLastTrackingSubLevelID");
        invokeNullableSetter(entity, SET_PLOT_POSITION_METHODS, "sable$setPlotPosition");
    }

    private static void invokeNullableSetter(Entity entity,
            Map<Class<?>, Optional<Method>> cache, String name) {
        Optional<Method> method = cache.computeIfAbsent(entity.getClass(), type -> {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == 1) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        });
        if (method.isEmpty()) {
            return;
        }
        try {
            method.get().invoke(entity, new Object[] { null });
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional compatibility: failure leaves Sable's normal behavior untouched.
        }
    }

    public static Vec3 feetPosition(Entity entity, float distanceDown) {
        Api current = api();
        if (current == null || current.getFeetPos == null) {
            return null;
        }

        try {
            return toVec3(current.getFeetPos.invoke(current.helper, entity, distanceDown));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static Vec3 eyePosition(Entity entity, float partialTick) {
        Api current = api();
        if (current == null || current.getEyePositionInterpolated == null) {
            return null;
        }

        try {
            return toVec3(current.getEyePositionInterpolated.invoke(current.helper, entity, partialTick));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static SinkingSample sampleBestIntersecting(Level level, Vec3 point, Api current) throws ReflectiveOperationException {
        if (current.getAllIntersecting == null || current.boundingBoxConstructor == null) {
            return null;
        }

        Object bounds = current.boundingBoxConstructor.newInstance(
                point.x - POINT_QUERY_RADIUS,
                point.y - POINT_QUERY_RADIUS,
                point.z - POINT_QUERY_RADIUS,
                point.x + POINT_QUERY_RADIUS,
                point.y + POINT_QUERY_RADIUS,
                point.z + POINT_QUERY_RADIUS);
        Object value = current.getAllIntersecting.invoke(current.helper, level, bounds);
        if (!(value instanceof Iterable<?> iterable)) {
            return null;
        }

        SinkingSample best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Object subLevel : iterable) {
            SinkingSample sample = sampleSubLevel(level, subLevel, point, null);
            if (sample == null) {
                continue;
            }

                double score = sampleScore(level, sample);
            if (score > bestScore) {
                bestScore = score;
                best = sample;
            }
        }
        return best;
    }

    private static SinkingSample sampleSubLevel(Level level, Object subLevel, Vec3 point, BlockPos providedPos) {
        if (subLevel == null) {
            return null;
        }

        Vec3 localPoint = toLocal(subLevel, point);
        if (localPoint == null) {
            return null;
        }

        return sampleSubLevelLocal(level, subLevel, localPoint, providedPos, point);
    }

    private static SinkingSample sampleSubLevelLocal(Level level, Object subLevel, Vec3 localPoint, BlockPos providedPos, Vec3 worldPoint) {
        BlockPos pos = providedPos == null ? BlockPos.containing(localPoint) : providedPos;
        BlockState state = blockState(level, subLevel, pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium == null) {
            return null;
        }
        if (!insideMudShape(level, pos, state, medium, localPoint)) {
            return null;
        }

        ColumnInfo column = sinkingColumn(level, subLevel, pos, state, medium);
        return new SinkingSample(
                subLevel,
                pos.immutable(),
                state,
                medium,
                localPoint,
                column.topPos(),
                column.topState(),
                column.topMedium(),
                column.bottomPos(),
                worldPoint,
                MudVisualSource.capture(level, pos),
                MudVisualSource.capture(level, column.topPos()));
    }

    private static boolean insideMudShape(Level level, BlockPos pos, BlockState state,
            SinkingMedium medium, Vec3 localPoint) {
        double tolerance = 0.004D;
        if (state.getBlock() instanceof AdaptiveMudBlock) {
            return MudBlock.containsLocalPoint(
                    level, pos, state, medium,
                    localPoint.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    tolerance);
        }
        if (state.getBlock() instanceof MudBlock) {
            return MudBlock.containsLocalPoint(
                    state,
                    medium,
                    localPoint.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    tolerance);
        }
        double surface = pos.getY()
                + MudMediumRuntime.surfaceHeight(level, pos, state, medium);
        return localPoint.x >= pos.getX() - tolerance
                && localPoint.x <= pos.getX() + 1.0D + tolerance
                && localPoint.y >= pos.getY() - tolerance
                && localPoint.y <= surface + tolerance
                && localPoint.z >= pos.getZ() - tolerance
                && localPoint.z <= pos.getZ() + 1.0D + tolerance;
    }

    public static boolean isWorldPointAboveLocalSurface(SinkingSample sample, double surfaceLocalY, double tolerance) {
        if (sample.state().getBlock() instanceof MudBlock
                && !MudBlock.supportsVerticalSinking(
                        sample.state(), sample.medium())) {
            return false;
        }
        Vec3 surfaceWorld = toWorld(sample.subLevel(), new Vec3(sample.localPoint().x, surfaceLocalY, sample.localPoint().z));
        return surfaceWorld != null && sample.worldPoint().y > surfaceWorld.y + tolerance;
    }

    private static Object trackingSubLevel(Api current, Entity entity) {
        if (entity == null) {
            return null;
        }

        try {
            if (current.getTrackingOrVehicleSubLevel != null) {
                Object value = current.getTrackingOrVehicleSubLevel.invoke(current.helper, entity);
                if (value != null) {
                    return value;
                }
            }
            if (current.getTrackingSubLevel != null) {
                return current.getTrackingSubLevel.invoke(current.helper, entity);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static Vec3 toVec3(Object value) {
        if (value instanceof Vec3 vec3) {
            return vec3;
        }
        if (value instanceof Vector3dc vector) {
            return new Vec3(vector.x(), vector.y(), vector.z());
        }
        return null;
    }

    private static double sampleScore(Level level, SinkingSample sample) {
        double surfaceLocalY = sample.pos().getY()
                + MudMediumRuntime.surfaceHeightAt(
                        level, sample.pos(), sample.state(), sample.medium(),
                        sample.localPoint().x, sample.localPoint().z);
        Vec3 surfaceWorld = toWorld(sample.subLevel(), new Vec3(sample.localPoint().x, surfaceLocalY, sample.localPoint().z));
        double surfaceY = surfaceWorld == null ? surfaceLocalY : surfaceWorld.y;
        double depth = Math.max(0.0D, surfaceLocalY - sample.localPoint().y);
        return surfaceY * 8.0D - depth * 0.05D + sample.pos().getY() * 0.002D;
    }

    private static ColumnInfo sinkingColumn(Level level, Object subLevel, BlockPos pos, BlockState state, SinkingMedium medium) {
        BlockPos topPos = pos.immutable();
        BlockState topState = state;
        SinkingMedium topMedium = medium;
        int maxY = level.getMaxBuildHeight() - 1;
        for (int guard = 0; guard < 16 && topPos.getY() < maxY; guard++) {
            BlockPos nextPos = topPos.above();
            BlockState nextState = blockState(level, subLevel, nextPos);
            SinkingMedium nextMedium = ModBlocks.mediumOf(nextState.getBlock());
            if (nextMedium == null) {
                break;
            }

            topPos = nextPos.immutable();
            topState = nextState;
            topMedium = nextMedium;
        }

        BlockPos bottomPos = pos.immutable();
        int minY = level.getMinBuildHeight();
        for (int guard = 0; guard < 16 && bottomPos.getY() > minY; guard++) {
            BlockPos nextPos = bottomPos.below();
            BlockState nextState = blockState(level, subLevel, nextPos);
            if (ModBlocks.mediumOf(nextState.getBlock()) == null) {
                break;
            }

            bottomPos = nextPos.immutable();
        }

        return new ColumnInfo(topPos, topState, topMedium, bottomPos);
    }

    private static BlockState blockState(Level level, Object subLevel, BlockPos pos) {
        // Sable poses transform world positions directly into the hidden plot's
        // global storage coordinates. The parent Level routes those coordinates
        // to the plot chunk. EmbeddedPlotLevelAccessor expects center-relative
        // coordinates and would add the plot center a second time here.
        return level.getBlockState(pos);
    }

    public static Vec3 toWorld(Object subLevel, Vec3 localPoint) {
        return SablePoseTransform.position(subLevel, localPoint, false);
    }

    /** Uses Sable's interpolated client render pose and falls back to the logical pose on servers. */
    public static Vec3 toRenderWorld(Object subLevel, Vec3 localPoint) {
        return SablePoseTransform.renderPosition(subLevel, localPoint);
    }

    public static Vec3 toLocal(Object subLevel, Vec3 worldPoint) {
        return SablePoseTransform.position(subLevel, worldPoint, true);
    }

    public static Vec3 toWorldDirection(Object subLevel, Vec3 localDirection) {
        return SablePoseTransform.direction(subLevel, localDirection, false);
    }

    public static Vec3 toLocalDirection(Object subLevel, Vec3 worldDirection) {
        return SablePoseTransform.direction(subLevel, worldDirection, true);
    }

    public static SurfaceProbe surfaceProbe(Level level, AABB worldBounds) {
        return surfaceProbe(level, worldBounds, null);
    }

    public static SurfaceProbe surfaceProbe(Level level, AABB worldBounds, Entity trackingEntity) {
        Api current = api();
        if (current == null || current.getAllIntersecting == null || current.boundingBoxConstructor == null) {
            return SurfaceProbe.empty(level);
        }
        try {
            Object bounds = current.boundingBoxConstructor.newInstance(
                    worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                    worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ);
            Object value = current.getAllIntersecting.invoke(current.helper, level, bounds);
            if (!(value instanceof Iterable<?> iterable)) {
                return SurfaceProbe.empty(level);
            }
            List<Object> uniqueSubLevels = new ArrayList<>();
            for (Object subLevel : iterable) {
                if (subLevel != null && !uniqueSubLevels.contains(subLevel)) {
                    uniqueSubLevels.add(subLevel);
                }
            }
            // Exact contact can lie on the global bounds seam. A tracked sublevel is only
            // admitted as a candidate; local face distance/normal checks still decide contact.
            Object tracked = trackingSubLevel(current, trackingEntity);
            if (tracked != null && !uniqueSubLevels.contains(tracked)) {
                uniqueSubLevels.add(tracked);
            }
            List<SubLevelSurfaceData> surfaces = new ArrayList<>(uniqueSubLevels.size());
            for (Object subLevel : uniqueSubLevels) {
                SubLevelSurfaceData data = buildSurfaceData(level, subLevel, worldBounds);
                if (data != null && !data.faces().isEmpty()) {
                    surfaces.add(data);
                }
            }
            return new SurfaceProbe(level, List.copyOf(surfaces), uniqueSubLevels.size());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return SurfaceProbe.empty(level);
        }
    }

    /**
     * Returns exposed mud faces in Sable-local coordinates. Consumers retain the
     * local identity and transform it on demand so moving structures never leave
     * world-space effects behind.
     */
    public static List<MudSurfaceFace> mudSurfaceFaces(
            Level level, AABB worldBounds, Entity trackingEntity) {
        Api current = api();
        if (current == null || current.getAllIntersecting == null
                || current.boundingBoxConstructor == null) {
            return List.of();
        }
        try {
            Object bounds = current.boundingBoxConstructor.newInstance(
                    worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                    worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ);
            Object value = current.getAllIntersecting.invoke(
                    current.helper, level, bounds);
            if (!(value instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<Object> subLevels = new ArrayList<>();
            for (Object subLevel : iterable) {
                if (subLevel != null && !subLevels.contains(subLevel)) {
                    subLevels.add(subLevel);
                }
            }
            Object tracked = trackingSubLevel(current, trackingEntity);
            if (tracked != null && !subLevels.contains(tracked)) {
                subLevels.add(tracked);
            }
            List<MudSurfaceFace> result = new ArrayList<>();
            for (Object subLevel : subLevels) {
                collectMudSurfaceFaces(
                        level, subLevel, worldBounds, result);
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    /**
     * Builds one bounded local-volume snapshot for precise body contact. Sampling
     * the returned probe performs no reflective pose lookup and no block query.
     */
    public static MudVolumeProbe mudVolumeProbe(
            Level level, AABB worldBounds, Entity trackingEntity) {
        Api current = api();
        if (current == null || current.getAllIntersecting == null
                || current.boundingBoxConstructor == null) {
            return MudVolumeProbe.EMPTY;
        }
        try {
            Object bounds = current.boundingBoxConstructor.newInstance(
                    worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                    worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ);
            Object value = current.getAllIntersecting.invoke(
                    current.helper, level, bounds);
            if (!(value instanceof Iterable<?> iterable)) {
                return MudVolumeProbe.EMPTY;
            }
            List<Object> subLevels = new ArrayList<>();
            for (Object subLevel : iterable) {
                if (subLevel != null && !subLevels.contains(subLevel)) {
                    subLevels.add(subLevel);
                }
            }
            Object tracked = trackingSubLevel(current, trackingEntity);
            if (tracked != null && !subLevels.contains(tracked)) {
                subLevels.add(tracked);
            }
            List<MudVolumeGroup> groups = new ArrayList<>();
            for (Object subLevel : subLevels) {
                RigidTransform rigid = rigidTransform(subLevel);
                AffineTransform inverse =
                        rigid == null ? null : rigid.resolveLocal();
                AABB localBounds = localBounds(subLevel, worldBounds);
                if (inverse == null || localBounds == null) {
                    continue;
                }
                List<MudVolume> volumes = collectMudVolumes(
                        level, subLevel, localBounds);
                if (!volumes.isEmpty()) {
                    groups.add(new MudVolumeGroup(
                            subLevel, inverse, List.copyOf(volumes)));
                }
            }
            return groups.isEmpty()
                    ? MudVolumeProbe.EMPTY
                    : new MudVolumeProbe(List.copyOf(groups));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return MudVolumeProbe.EMPTY;
        }
    }

    /**
     * Freezes nearby physicalized water into local-space boxes. Point sampling
     * then performs no reflection and follows the structure's current pose.
     */
    public static WaterVolumeProbe waterVolumeProbe(
            Level level, AABB worldBounds, Entity trackingEntity) {
        Api current = api();
        if (current == null || current.getAllIntersecting == null
                || current.boundingBoxConstructor == null) {
            return WaterVolumeProbe.EMPTY;
        }
        try {
            Object bounds = current.boundingBoxConstructor.newInstance(
                    worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                    worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ);
            Object value = current.getAllIntersecting.invoke(
                    current.helper, level, bounds);
            if (!(value instanceof Iterable<?> iterable)) {
                return WaterVolumeProbe.EMPTY;
            }
            List<Object> subLevels = new ArrayList<>();
            for (Object subLevel : iterable) {
                if (subLevel != null && !subLevels.contains(subLevel)) {
                    subLevels.add(subLevel);
                }
            }
            Object tracked = trackingSubLevel(current, trackingEntity);
            if (tracked != null && !subLevels.contains(tracked)) {
                subLevels.add(tracked);
            }

            List<WaterVolumeGroup> groups = new ArrayList<>();
            for (Object subLevel : subLevels) {
                RigidTransform rigid = rigidTransform(subLevel);
                AffineTransform inverse =
                        rigid == null ? null : rigid.resolveLocal();
                AABB localBounds = localBounds(subLevel, worldBounds);
                if (inverse == null || localBounds == null) {
                    continue;
                }
                List<WaterVolume> volumes = collectWaterVolumes(
                        level, subLevel, localBounds);
                if (!volumes.isEmpty()) {
                    groups.add(new WaterVolumeGroup(
                            inverse, List.copyOf(volumes)));
                }
            }
            return groups.isEmpty()
                    ? WaterVolumeProbe.EMPTY
                    : new WaterVolumeProbe(List.copyOf(groups));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return WaterVolumeProbe.EMPTY;
        }
    }

    private static List<WaterVolume> collectWaterVolumes(
            Level level, Object subLevel, AABB localBounds) {
        List<WaterVolume> result = new ArrayList<>();
        int minX = Mth.floor(localBounds.minX - 0.06D);
        int maxX = Mth.floor(localBounds.maxX + 0.06D);
        int minY = Mth.floor(localBounds.minY - 0.06D);
        int maxY = Mth.floor(localBounds.maxY + 0.06D);
        int minZ = Mth.floor(localBounds.minZ - 0.06D);
        int maxZ = Mth.floor(localBounds.maxZ + 0.06D);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    FluidState fluid =
                            blockState(level, subLevel, pos).getFluidState();
                    if (!fluid.is(FluidTags.WATER)) {
                        continue;
                    }
                    double height = Mth.clamp(
                            fluid.getHeight(level, pos), 0.0D, 1.0D);
                    if (height > 0.0D) {
                        result.add(new WaterVolume(
                                pos.immutable(), height));
                    }
                }
            }
        }
        return result;
    }

    private static List<MudVolume> collectMudVolumes(
            Level level, Object subLevel, AABB localBounds) {
        List<MudVolume> result = new ArrayList<>();
        int minX = Mth.floor(localBounds.minX - 0.06D);
        int maxX = Mth.floor(localBounds.maxX + 0.06D);
        int minY = Mth.floor(localBounds.minY - 0.06D);
        int maxY = Mth.floor(localBounds.maxY + 0.06D);
        int minZ = Mth.floor(localBounds.minZ - 0.06D);
        int maxZ = Mth.floor(localBounds.maxZ + 0.06D);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = blockState(level, subLevel, pos);
                    SinkingMedium medium =
                            ModBlocks.mediumOf(state.getBlock());
                    if (medium == null
                            || !(state.getBlock() instanceof MudBlock)
                            || !MudMediumRuntime.enabled(level, pos, medium)) {
                        continue;
                    }
                    result.add(new MudVolume(
                            pos.immutable(), state, medium,
                            MudVisualSource.capture(level, pos),
                            MudBlock.localShape(level, pos, state, medium)));
                }
            }
        }
        return result;
    }

    private static void collectMudSurfaceFaces(Level level, Object subLevel,
            AABB worldBounds, List<MudSurfaceFace> result) {
        AABB bounds = localBounds(subLevel, worldBounds);
        if (bounds == null) {
            return;
        }
        int minX = Mth.floor(bounds.minX - 0.08D);
        int maxX = Mth.floor(bounds.maxX + 0.08D);
        int minY = Mth.floor(bounds.minY - 0.08D);
        int maxY = Mth.floor(bounds.maxY + 0.08D);
        int minZ = Mth.floor(bounds.minZ - 0.08D);
        int maxZ = Mth.floor(bounds.maxZ + 0.08D);
        Set<MudFaceKey> visited = new HashSet<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = blockState(level, subLevel, pos);
                    SinkingMedium medium =
                            ModBlocks.mediumOf(state.getBlock());
                    if (medium == null
                            || !(state.getBlock() instanceof MudBlock)
                            || !MudMediumRuntime.enabled(level, pos, medium)) {
                        continue;
                    }
                    for (AABB local : MudBlock.localShape(
                            level, pos, state, medium).toAabbs()) {
                        AABB box = local.move(pos);
                        for (Direction face : DIRECTIONS) {
                            double plane = facePlane(box, face);
                            MudFaceKey key = new MudFaceKey(
                                    pos.asLong(),
                                    face,
                                    quantizeFace(plane),
                                    quantizeFace(faceMinimumU(box, face)),
                                    quantizeFace(faceMaximumU(box, face)),
                                    quantizeFace(faceMinimumV(box, face)),
                                    quantizeFace(faceMaximumV(box, face)));
                            if (!visited.add(key)
                                    || occupiedImmediatelyOutside(
                                            level,
                                            subLevel,
                                            faceCenter(box, face).add(
                                                    face.getStepX() * 0.0025D,
                                                    face.getStepY() * 0.0025D,
                                                    face.getStepZ() * 0.0025D))) {
                                continue;
                            }
                            result.add(new MudSurfaceFace(
                                    subLevel,
                                    pos.immutable(),
                                    state,
                                    medium,
                                    face,
                                    box,
                                    plane));
                        }
                    }
                }
            }
        }
    }

    private static boolean occupiedImmediatelyOutside(
            Level level, Object subLevel, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = blockState(level, subLevel, pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (state.isAir() || state.getBlock() == ModBlocks.MUD_FOOTPRINT.get()) {
            return false;
        }
        if (medium != null && state.getBlock() instanceof MudBlock) {
            return MudBlock.containsLocalPoint(
                    level, pos, state, medium,
                    point.subtract(pos.getX(), pos.getY(), pos.getZ()),
                    0.0005D);
        }
        return true;
    }

    private static Vec3 faceCenter(AABB box, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(
                    facePlane(box, face),
                    (box.minY + box.maxY) * 0.5D,
                    (box.minZ + box.maxZ) * 0.5D);
            case Y -> new Vec3(
                    (box.minX + box.maxX) * 0.5D,
                    facePlane(box, face),
                    (box.minZ + box.maxZ) * 0.5D);
            case Z -> new Vec3(
                    (box.minX + box.maxX) * 0.5D,
                    (box.minY + box.maxY) * 0.5D,
                    facePlane(box, face));
        };
    }

    private static double faceMinimumU(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.X ? box.minZ : box.minX;
    }

    private static double faceMaximumU(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.X ? box.maxZ : box.maxX;
    }

    private static double faceMinimumV(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.Y ? box.minZ : box.minY;
    }

    private static double faceMaximumV(AABB box, Direction face) {
        return face.getAxis() == Direction.Axis.Y ? box.maxZ : box.maxY;
    }

    private static int quantizeFace(double value) {
        return Mth.floor(value * 4096.0D + 0.5D);
    }

    public static List<SubLevelCollisionGeometry> collisionGeometry(
            Level level, List<List<Vec3>> worldCorridors, double padding,
            int maximumBlockSamples) {
        return collisionGeometry(level, worldCorridors, padding,
                maximumBlockSamples, true);
    }

    /**
     * Builds the same bounded collision snapshot, optionally keeping sinking
     * media as support-only geometry for consumers such as the rope solver.
     */
    public static List<SubLevelCollisionGeometry> collisionGeometry(
            Level level, List<List<Vec3>> worldCorridors, double padding,
            int maximumBlockSamples, boolean includeSinkingMudCollision) {
        Api current = api();
        if (current == null || current.getAllIntersecting == null
                || current.boundingBoxConstructor == null || maximumBlockSamples <= 0) {
            return List.of();
        }
        AABB worldBounds = corridorBounds(worldCorridors, padding);
        if (worldBounds == null) {
            return List.of();
        }
        try {
            Object bounds = current.boundingBoxConstructor.newInstance(
                    worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                    worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ);
            Object value = current.getAllIntersecting.invoke(current.helper, level, bounds);
            if (!(value instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<SubLevelCollisionGeometry> result = new ArrayList<>();
            List<Object> visited = new ArrayList<>();
            int remaining = maximumBlockSamples;
            for (Object subLevel : iterable) {
                if (subLevel == null || visited.contains(subLevel) || remaining <= 0) {
                    continue;
                }
                visited.add(subLevel);
                RigidTransform transform = rigidTransform(subLevel);
                if (transform == null) {
                    continue;
                }
                CollisionBuild build = buildCollisionGeometry(
                        level, subLevel, transform, worldCorridors, padding, remaining,
                        includeSinkingMudCollision);
                remaining -= build.sampledBlocks();
                if (!build.boxes().isEmpty() || !build.mudBoxes().isEmpty()) {
                    result.add(new SubLevelCollisionGeometry(
                            subLevel, transform, build.boxes(), build.mudBoxes()));
                }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static CollisionBuild buildCollisionGeometry(Level level, Object subLevel,
            RigidTransform transform, List<List<Vec3>> worldCorridors,
            double padding, int maximumBlockSamples, boolean includeSinkingMudCollision) {
        List<AABB> boxes = new ArrayList<>(Math.min(
                MAXIMUM_ROPE_COLLISION_BOXES, Math.max(0, maximumBlockSamples)));
        List<AABB> mudBoxes = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        double corridor = Math.max(0.05D, padding);
        for (List<Vec3> worldPath : worldCorridors) {
            if (worldPath == null || worldPath.isEmpty()) {
                continue;
            }
            Vec3 localFrom = transform.toLocal(worldPath.getFirst());
            if (localFrom == null) {
                continue;
            }
            if (worldPath.size() == 1) {
                if (collectCollisionGeometry(level, subLevel,
                        new AABB(localFrom, localFrom).inflate(corridor),
                        visited, boxes, mudBoxes, maximumBlockSamples,
                        includeSinkingMudCollision)) {
                    break;
                }
                continue;
            }
            for (int index = 1; index < worldPath.size(); index++) {
                Vec3 localTo = transform.toLocal(worldPath.get(index));
                if (localTo == null) {
                    break;
                }
                AABB segmentBounds = new AABB(
                        Math.min(localFrom.x, localTo.x), Math.min(localFrom.y, localTo.y),
                        Math.min(localFrom.z, localTo.z), Math.max(localFrom.x, localTo.x),
                        Math.max(localFrom.y, localTo.y), Math.max(localFrom.z, localTo.z))
                        .inflate(corridor);
                if (collectCollisionGeometry(level, subLevel, segmentBounds,
                        visited, boxes, mudBoxes, maximumBlockSamples,
                        includeSinkingMudCollision)) {
                    return new CollisionBuild(
                            List.copyOf(boxes), List.copyOf(mudBoxes), visited.size());
                }
                localFrom = localTo;
            }
        }
        return new CollisionBuild(
                List.copyOf(boxes), List.copyOf(mudBoxes), visited.size());
    }

    private static boolean collectCollisionGeometry(Level level, Object subLevel, AABB bounds,
            Set<Long> visited, List<AABB> boxes, List<AABB> mudBoxes,
            int maximumBlockSamples, boolean includeSinkingMudCollision) {
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            long packed = cursor.asLong();
            if (visited.contains(packed)) {
                continue;
            }
            if (visited.size() >= maximumBlockSamples) {
                return true;
            }
            visited.add(packed);
            BlockState state = blockState(level, subLevel, cursor);
            if (state.isAir()) {
                continue;
            }
            if (state.getBlock() instanceof MudBlock mudBlock
                    && MudMediumRuntime.enabled(level, cursor, mudBlock.medium())) {
                for (AABB box : MudBlock.localShape(
                        level, cursor, state, mudBlock.medium()).toAabbs()) {
                    if (mudBoxes.size() >= MAXIMUM_ROPE_COLLISION_BOXES) {
                        return true;
                    }
                    mudBoxes.add(box.move(cursor));
                }
            }
            VoxelShape shape = collisionShape(
                    level, cursor, state, includeSinkingMudCollision);
            for (AABB box : shape.toAabbs()) {
                if (boxes.size() >= MAXIMUM_ROPE_COLLISION_BOXES) {
                    return true;
                }
                boxes.add(box.move(cursor));
            }
        }
        return false;
    }

    private static VoxelShape collisionShape(
            Level level, BlockPos pos, BlockState state,
            boolean includeSinkingMudCollision) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty() && state.getBlock() instanceof MudBlock mudBlock) {
            if (!includeSinkingMudCollision) {
                return shape;
            }
            return MudBlock.localShape(level, pos, state, mudBlock.medium());
        }
        return shape;
    }

    private static AABB corridorBounds(List<List<Vec3>> corridors, double padding) {
        AABB bounds = null;
        for (List<Vec3> corridor : corridors) {
            if (corridor == null) {
                continue;
            }
            for (Vec3 point : corridor) {
                AABB pointBounds = new AABB(point, point);
                bounds = bounds == null ? pointBounds : bounds.minmax(pointBounds);
            }
        }
        return bounds == null ? null : bounds.inflate(Math.max(0.05D, padding));
    }

    public static SurfaceContact findSurface(SurfaceProbe probe, Vec3 worldPoint, Vec3 preferredWorldNormal,
            double reach, double minimumAlignment) {
        if (probe == null || probe.surfaces.isEmpty() || preferredWorldNormal.lengthSqr() <= 1.0E-8D) {
            return null;
        }
        Vec3 preferred = preferredWorldNormal.normalize();
        SurfaceContact best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (SubLevelSurfaceData data : probe.surfaces) {
            Vec3 localPoint = toLocal(data.subLevel(), worldPoint);
            if (localPoint == null) {
                continue;
            }
            for (SurfaceFace face : data.faces()) {
                SurfaceContact candidate = surfaceCandidate(
                        data.subLevel(), localPoint, worldPoint, preferred, reach, minimumAlignment, face);
                if (candidate == null) {
                    continue;
                }
                double score = candidate.distance() - candidate.alignment() * 0.018D;
                if (score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    public static String diagnoseSurface(SurfaceProbe probe, Vec3 worldPoint, Vec3 preferredWorldNormal) {
        if (probe == null) {
            return "probe=null";
        }
        if (probe.surfaces.isEmpty()) {
            return "sublevels=" + probe.discoveredSubLevels + " built=0 faces=0";
        }
        Vec3 preferred = preferredWorldNormal.lengthSqr() > 1.0E-8D
                ? preferredWorldNormal.normalize()
                : Vec3.ZERO;
        SurfaceFace nearest = null;
        Vec3 nearestLocal = null;
        double nearestSigned = Double.POSITIVE_INFINITY;
        double nearestAlignment = -2.0D;
        boolean nearestInside = false;
        for (SubLevelSurfaceData data : probe.surfaces) {
            Vec3 localPoint = toLocal(data.subLevel(), worldPoint);
            if (localPoint == null) {
                continue;
            }
            for (SurfaceFace face : data.faces()) {
                double coordinate = switch (face.face().getAxis()) {
                    case X -> localPoint.x;
                    case Y -> localPoint.y;
                    case Z -> localPoint.z;
                };
                double signed = (coordinate - face.plane()) * face.face().getAxisDirection().getStep();
                double alignment = face.worldNormal().dot(preferred);
                boolean inside = insideFaceBounds(localPoint, face.box(), face.face(), 0.028D);
                double score = Math.abs(signed) + (inside ? 0.0D : 2.0D) + Math.max(0.0D, 0.28D - alignment);
                double nearestScore = nearest == null
                        ? Double.POSITIVE_INFINITY
                        : Math.abs(nearestSigned) + (nearestInside ? 0.0D : 2.0D)
                                + Math.max(0.0D, 0.28D - nearestAlignment);
                if (score < nearestScore) {
                    nearest = face;
                    nearestLocal = localPoint;
                    nearestSigned = signed;
                    nearestAlignment = alignment;
                    nearestInside = inside;
                }
            }
        }
        if (nearest == null) {
            return "sublevels=" + probe.discoveredSubLevels + " built=" + probe.surfaces.size()
                    + " faces=" + probe.faceCount() + " transform=failed";
        }
        return String.format(Locale.ROOT,
                "sublevels=%d built=%d faces=%d nearestFace=%s local=(%.3f,%.3f,%.3f) signed=%.4f align=%.3f inside=%s",
                probe.discoveredSubLevels, probe.surfaces.size(), probe.faceCount(), nearest.face(),
                nearestLocal.x, nearestLocal.y, nearestLocal.z,
                nearestSigned, nearestAlignment, nearestInside);
    }

    public static int trackingPlayerCount(Object subLevel) {
        if (subLevel == null) {
            return 0;
        }
        try {
            Method method = TRACKING_PLAYERS_METHODS.computeIfAbsent(
                    subLevel.getClass(), SableCompat::findTrackingPlayersMethod);
            Object value = method.invoke(subLevel);
            return value instanceof Collection<?> collection ? collection.size() : -1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    public static boolean containsBlockEntity(BlockEntity blockEntity) {
        return containingSubLevel(blockEntity) != null;
    }

    public static Object containingSubLevel(BlockEntity blockEntity) {
        Api current = api();
        if (current == null || current.getContainingBlockEntity == null || blockEntity == null) {
            return null;
        }
        try {
            return current.getContainingBlockEntity.invoke(current.helper, blockEntity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static SurfaceContact surfaceCandidate(Object subLevel, Vec3 point, Vec3 worldPoint,
            Vec3 preferredWorldNormal, double reach, double minimumAlignment, SurfaceFace cached) {
        Direction face = cached.face();
        AABB box = cached.box();
        if (!insideFaceBounds(point, box, face, 0.028D)) {
            return null;
        }
        double plane = cached.plane();
        double coordinate = switch (face.getAxis()) {
            case X -> point.x;
            case Y -> point.y;
            case Z -> point.z;
        };
        double signedDistance = (coordinate - plane) * face.getAxisDirection().getStep();
        if (signedDistance < -0.055D || signedDistance > reach) {
            return null;
        }
        double alignment = cached.worldNormal().dot(preferredWorldNormal);
        if (alignment < minimumAlignment) {
            return null;
        }
        Vec3 localSurface = switch (face.getAxis()) {
            case X -> new Vec3(plane + face.getStepX() * 0.006D, point.y, point.z);
            case Y -> new Vec3(point.x, plane + face.getStepY() * 0.006D, point.z);
            case Z -> new Vec3(point.x, point.y, plane + face.getStepZ() * 0.006D);
        };
        Vec3 exactWorldPoint = toWorld(subLevel, localSurface);
        return new SurfaceContact(subLevel, cached.supportPos(), cached.containerPos(), face,
                localSurface, exactWorldPoint == null ? worldPoint : exactWorldPoint,
                Math.abs(signedDistance), alignment);
    }

    private static SubLevelSurfaceData buildSurfaceData(Level level, Object subLevel, AABB worldBounds) {
        AABB localBounds = localBounds(subLevel, worldBounds);
        if (localBounds == null) {
            return null;
        }
        List<SurfaceFace> faces = new ArrayList<>();
        int minX = (int) Math.floor(localBounds.minX - 0.08D);
        int maxX = (int) Math.floor(localBounds.maxX + 0.08D);
        int minY = (int) Math.floor(localBounds.minY - 0.08D);
        int maxY = (int) Math.floor(localBounds.maxY + 0.08D);
        int minZ = (int) Math.floor(localBounds.minZ - 0.08D);
        int maxZ = (int) Math.floor(localBounds.maxZ + 0.08D);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos supportPos = new BlockPos(x, y, z);
                    BlockState support = level.getBlockState(supportPos);
                    if (support.isAir() || ModBlocks.isSinkingBlock(support.getBlock())
                            || support.getBlock() == ModBlocks.MUD_FOOTPRINT.get()) {
                        continue;
                    }
                    for (AABB localBox : support.getCollisionShape(level, supportPos).toAabbs()) {
                        AABB box = localBox.move(supportPos);
                        for (Direction direction : DIRECTIONS) {
                            if (!onBlockBoundary(box, supportPos, direction)) {
                                continue;
                            }
                            BlockPos containerPos = supportPos.relative(direction);
                            BlockState container = level.getBlockState(containerPos);
                            if (!container.isAir() && container.getBlock() != ModBlocks.MUD_FOOTPRINT.get()) {
                                continue;
                            }
                            Vec3 worldNormal = toWorldDirection(subLevel,
                                    new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ()));
                            if (worldNormal == null || worldNormal.lengthSqr() <= 1.0E-8D) {
                                continue;
                            }
                            faces.add(new SurfaceFace(
                                    supportPos.immutable(), containerPos.immutable(), direction, box,
                                    facePlane(box, direction), worldNormal.normalize()));
                        }
                    }
                }
            }
        }
        return new SubLevelSurfaceData(subLevel, List.copyOf(faces));
    }

    public static AABB localBounds(Object subLevel, AABB worldBounds) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 local = toLocal(subLevel, new Vec3(
                    (corner & 1) == 0 ? worldBounds.minX : worldBounds.maxX,
                    (corner & 2) == 0 ? worldBounds.minY : worldBounds.maxY,
                    (corner & 4) == 0 ? worldBounds.minZ : worldBounds.maxZ));
            if (local == null) {
                return null;
            }
            minX = Math.min(minX, local.x);
            minY = Math.min(minY, local.y);
            minZ = Math.min(minZ, local.z);
            maxX = Math.max(maxX, local.x);
            maxY = Math.max(maxY, local.y);
            maxZ = Math.max(maxZ, local.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean onBlockBoundary(AABB box, BlockPos pos, Direction face) {
        return switch (face) {
            case WEST -> box.minX <= pos.getX() + 0.01D;
            case EAST -> box.maxX >= pos.getX() + 0.99D;
            case DOWN -> box.minY <= pos.getY() + 0.01D;
            case UP -> box.maxY >= pos.getY() + 0.99D;
            case NORTH -> box.minZ <= pos.getZ() + 0.01D;
            case SOUTH -> box.maxZ >= pos.getZ() + 0.99D;
        };
    }

    private static double facePlane(AABB box, Direction face) {
        return switch (face) {
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
        };
    }

    private static boolean insideFaceBounds(Vec3 point, net.minecraft.world.phys.AABB box, Direction face,
            double tolerance) {
        return switch (face.getAxis()) {
            case X -> point.y >= box.minY - tolerance && point.y <= box.maxY + tolerance
                    && point.z >= box.minZ - tolerance && point.z <= box.maxZ + tolerance;
            case Y -> point.x >= box.minX - tolerance && point.x <= box.maxX + tolerance
                    && point.z >= box.minZ - tolerance && point.z <= box.maxZ + tolerance;
            case Z -> point.x >= box.minX - tolerance && point.x <= box.maxX + tolerance
                    && point.y >= box.minY - tolerance && point.y <= box.maxY + tolerance;
        };
    }

    public static BlockState subLevelBlockState(Level level, Object subLevel, BlockPos pos) {
        return blockState(level, subLevel, pos);
    }

    public static BlockEntity subLevelBlockEntity(Level level, Object subLevel, BlockPos pos) {
        return subLevel == null ? null : level.getBlockEntity(pos);
    }

    public static boolean setSubLevelBlock(ServerLevel level, Object subLevel, BlockPos pos, BlockState state) {
        if (subLevel == null) {
            return false;
        }
        boolean changed = level.setBlock(
                pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE, 512);
        if (changed) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            sendToTrackingPlayers(subLevel, new ClientboundBlockUpdatePacket(pos, state));
            if (blockEntity != null) {
                Packet<?> packet = blockEntity.getUpdatePacket();
                if (packet != null) {
                    sendToTrackingPlayers(subLevel, packet);
                }
            }
        }
        return changed;
    }

    public static boolean removeSubLevelBlock(ServerLevel level, Object subLevel, BlockPos pos) {
        if (subLevel == null) {
            return false;
        }
        boolean changed = level.setBlock(
                pos, Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE, 512);
        if (changed) {
            sendToTrackingPlayers(subLevel,
                    new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
        }
        return changed;
    }

    public static void syncSubLevelBlockEntity(BlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel)) {
            return;
        }
        Api current = api();
        if (current == null || current.getContainingBlockEntity == null) {
            return;
        }
        try {
            Object subLevel = current.getContainingBlockEntity.invoke(current.helper, blockEntity);
            if (subLevel == null) {
                return;
            }
            Packet<?> packet = blockEntity.getUpdatePacket();
            if (packet != null) {
                sendToTrackingPlayers(subLevel, packet);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void sendToTrackingPlayers(Object subLevel, Packet<?> packet) {
        if (subLevel == null || packet == null) {
            return;
        }
        try {
            Method method = TRACKING_PLAYERS_METHODS.computeIfAbsent(
                    subLevel.getClass(), SableCompat::findTrackingPlayersMethod);
            Object value = method.invoke(subLevel);
            if (!(value instanceof Collection<?> tracking)) {
                return;
            }
            Method getLevel = subLevel.getClass().getMethod("getLevel");
            if (!(getLevel.invoke(subLevel) instanceof ServerLevel level)) {
                return;
            }
            for (Object id : tracking) {
                if (!(id instanceof UUID uuid)) {
                    continue;
                }
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    player.connection.send(packet);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public static List<ServerPlayer> trackingPlayers(Object subLevel) {
        if (subLevel == null) {
            return List.of();
        }
        try {
            Method method = TRACKING_PLAYERS_METHODS.computeIfAbsent(
                    subLevel.getClass(), SableCompat::findTrackingPlayersMethod);
            Object value = method.invoke(subLevel);
            if (!(value instanceof Collection<?> tracking)) {
                return List.of();
            }
            Method getLevel = subLevel.getClass().getMethod("getLevel");
            if (!(getLevel.invoke(subLevel) instanceof ServerLevel level)) {
                return List.of();
            }
            List<ServerPlayer> players = new ArrayList<>(tracking.size());
            for (Object id : tracking) {
                if (id instanceof UUID uuid) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        players.add(player);
                    }
                }
            }
            return List.copyOf(players);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static Method findTrackingPlayersMethod(Class<?> subLevelClass) {
        try {
            return subLevelClass.getMethod("getTrackingPlayers");
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static SinkingMedium sinkingMediumAt(Level level, Object subLevel, BlockPos pos) {
        if (subLevel == null) {
            return null;
        }
        return ModBlocks.mediumOf(blockState(level, subLevel, pos).getBlock());
    }

    public static RigidTransform rigidTransform(Object subLevel) {
        return SablePoseTransform.rigid(subLevel);
    }

    private static Api api() {
        return SableReflectionApi.api();
    }

    private record ColumnInfo(BlockPos topPos, BlockState topState, SinkingMedium topMedium, BlockPos bottomPos) {
    }

    public record SinkingSample(Object subLevel, BlockPos pos, BlockState state,
            SinkingMedium medium, Vec3 localPoint, BlockPos topPos,
            BlockState topState, SinkingMedium topMedium, BlockPos bottomPos,
            Vec3 worldPoint, long visualSource, long topVisualSource) {
    }

    public record StorageAccess(Level level, BlockPos pos) {
    }

    private record EmbeddedAccessorBridge(Method level, Field center) {
    }

    public static final class SinkingVolumeProbe {
        private final Level level;
        private final List<Object> subLevels;
        private final boolean broadphaseResolved;

        private SinkingVolumeProbe(Level level, List<Object> subLevels, boolean broadphaseResolved) {
            this.level = level;
            this.subLevels = subLevels;
            this.broadphaseResolved = broadphaseResolved;
        }

        private static SinkingVolumeProbe empty(Level level) {
            return new SinkingVolumeProbe(level, List.of(), true);
        }

        public SinkingSample sample(Vec3 worldPoint) {
            SinkingSample best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Object subLevel : subLevels) {
                SinkingSample sample = sampleSubLevel(level, subLevel, worldPoint, null);
                if (sample == null) {
                    continue;
                }

                double score = sampleScore(level, sample);
                if (score > bestScore) {
                    bestScore = score;
                    best = sample;
                }
            }
            if (best != null || broadphaseResolved) {
                return best;
            }
            return sampleSinking(level, worldPoint);
        }

        /** Finds actual overlap between a world-axis entity box and local mud geometry. */
        public SinkingSample sampleIntersecting(AABB worldBounds) {
            SinkingSample best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Object subLevel : subLevels) {
                SinkingSample sample = sampleIntersectingSubLevel(
                        level, subLevel, worldBounds);
                if (sample == null) {
                    continue;
                }
                double score = sampleScore(level, sample);
                if (score > bestScore) {
                    bestScore = score;
                    best = sample;
                }
            }
            return best;
        }

        /** Builds collision geometry from the same frozen sub-level set as this probe. */
        public List<SubLevelCollisionGeometry> collisionGeometry(
                List<List<Vec3>> worldCorridors, double padding,
                int maximumBlockSamples) {
            if (worldCorridors == null || worldCorridors.isEmpty()
                    || maximumBlockSamples <= 0) {
                return List.of();
            }
            List<SubLevelCollisionGeometry> result = new ArrayList<>();
            int remaining = maximumBlockSamples;
            for (Object subLevel : subLevels) {
                if (remaining <= 0) {
                    break;
                }
                RigidTransform transform = rigidTransform(subLevel);
                if (transform == null) {
                    continue;
                }
                CollisionBuild build = buildCollisionGeometry(
                        level, subLevel, transform, worldCorridors, padding, remaining, true);
                remaining -= build.sampledBlocks();
                if (!build.boxes().isEmpty() || !build.mudBoxes().isEmpty()) {
                    result.add(new SubLevelCollisionGeometry(
                            subLevel, transform, build.boxes(), build.mudBoxes()));
                }
            }
            return List.copyOf(result);
        }

        public int candidateCount() {
            return subLevels.size();
        }
    }

    private static SinkingSample sampleIntersectingSubLevel(
            Level level, Object subLevel, AABB worldBounds) {
        RigidTransform transform = rigidTransform(subLevel);
        SableOrientedBox itemBox = SableOrientedBox.fromWorldBounds(worldBounds, transform);
        if (itemBox == null) {
            return null;
        }

        AABB search = itemBox.enclosingBounds().inflate(0.005D);
        BlockPos minimum = BlockPos.containing(search.minX, search.minY, search.minZ);
        BlockPos maximum = BlockPos.containing(search.maxX, search.maxY, search.maxZ);
        int sampled = 0;
        SinkingSample best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (BlockPos cursor : BlockPos.betweenClosed(minimum, maximum)) {
            if (++sampled > MAXIMUM_SINKING_VOLUME_BLOCK_SAMPLES) {
                break;
            }
            BlockState state = blockState(level, subLevel, cursor);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium == null || !MudMediumRuntime.enabled(level, cursor, medium)) {
                continue;
            }
            for (AABB shapeBox : MudBlock.localShape(
                    level, cursor, state, medium).toAabbs()) {
                AABB localMudBox = shapeBox.move(cursor);
                if (!itemBox.intersects(localMudBox)) {
                    continue;
                }
                Vec3 localPoint = closestPoint(itemBox.center(), localMudBox);
                ColumnInfo column = sinkingColumn(level, subLevel, cursor, state, medium);
                SinkingSample sample = new SinkingSample(
                        subLevel,
                        cursor.immutable(),
                        state,
                        medium,
                        localPoint,
                        column.topPos(),
                        column.topState(),
                        column.topMedium(),
                        column.bottomPos(),
                        worldBounds.getCenter(),
                        MudVisualSource.capture(level, cursor),
                        MudVisualSource.capture(level, column.topPos()));
                double score = sampleScore(level, sample);
                if (score > bestScore) {
                    bestScore = score;
                    best = sample;
                }
                break;
            }
        }
        return best;
    }

    private static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    public record SubLevelCollisionGeometry(
            Object subLevel, RigidTransform transform, List<AABB> localBoxes,
            List<AABB> localMudBoxes) {
        public SubLevelCollisionGeometry(
                Object subLevel, RigidTransform transform, List<AABB> localBoxes) {
            this(subLevel, transform, localBoxes, List.of());
        }

        public SubLevelCollisionGeometry {
            localBoxes = List.copyOf(localBoxes);
            localMudBoxes = List.copyOf(localMudBoxes);
        }
    }

    public static final class RigidTransform {
        private final Object pose;
        private final Method toWorld;
        private final Method toLocal;
        private final Method toWorldDirection;
        private final Method toLocalDirection;

        RigidTransform(Object pose, Method toWorld, Method toLocal,
                Method toWorldDirection, Method toLocalDirection) {
            this.pose = pose;
            this.toWorld = toWorld;
            this.toLocal = toLocal;
            this.toWorldDirection = toWorldDirection;
            this.toLocalDirection = toLocalDirection;
        }

        public Vec3 toWorld(Vec3 point) {
            return apply(toWorld, point);
        }

        public Vec3 toLocal(Vec3 point) {
            return apply(toLocal, point);
        }

        public Vec3 toWorldDirection(Vec3 direction) {
            return apply(toWorldDirection, direction);
        }

        public Vec3 toLocalDirection(Vec3 direction) {
            return apply(toLocalDirection, direction);
        }

        public AffineTransform resolveWorld() {
            Vec3 origin = toWorld(Vec3.ZERO);
            Vec3 axisX = toWorldDirection(new Vec3(1.0D, 0.0D, 0.0D));
            Vec3 axisY = toWorldDirection(new Vec3(0.0D, 1.0D, 0.0D));
            Vec3 axisZ = toWorldDirection(new Vec3(0.0D, 0.0D, 1.0D));
            return origin == null || axisX == null || axisY == null || axisZ == null
                    ? null
                    : new AffineTransform(origin, axisX, axisY, axisZ);
        }

        public AffineTransform resolveLocal() {
            Vec3 origin = toLocal(Vec3.ZERO);
            Vec3 axisX = toLocalDirection(new Vec3(1.0D, 0.0D, 0.0D));
            Vec3 axisY = toLocalDirection(new Vec3(0.0D, 1.0D, 0.0D));
            Vec3 axisZ = toLocalDirection(new Vec3(0.0D, 0.0D, 1.0D));
            return origin == null || axisX == null || axisY == null || axisZ == null
                    ? null
                    : new AffineTransform(origin, axisX, axisY, axisZ);
        }

        private Vec3 apply(Method method, Vec3 point) {
            if (method == null) {
                return null;
            }
            try {
                Vector3d vector = new Vector3d(point.x, point.y, point.z);
                method.invoke(pose, vector);
                return new Vec3(vector.x, vector.y, vector.z);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }

    public record AffineTransform(
            Vec3 origin, Vec3 axisX, Vec3 axisY, Vec3 axisZ) {
        public Vec3 toWorld(Vec3 point) {
            return origin
                    .add(axisX.scale(point.x))
                    .add(axisY.scale(point.y))
                    .add(axisZ.scale(point.z));
        }

        public Vec3 toWorldDirection(Vec3 direction) {
            return axisX.scale(direction.x)
                    .add(axisY.scale(direction.y))
                    .add(axisZ.scale(direction.z));
        }
    }

    private record CollisionBuild(
            List<AABB> boxes, List<AABB> mudBoxes, int sampledBlocks) {
    }

    public static final class SurfaceProbe {
        private final Level level;
        private final List<SubLevelSurfaceData> surfaces;
        private final int discoveredSubLevels;

        private SurfaceProbe(Level level, List<SubLevelSurfaceData> surfaces, int discoveredSubLevels) {
            this.level = level;
            this.surfaces = surfaces;
            this.discoveredSubLevels = discoveredSubLevels;
        }

        private static SurfaceProbe empty(Level level) {
            return new SurfaceProbe(level, List.of(), 0);
        }

        public boolean isEmpty() {
            return surfaces.isEmpty();
        }

        public int discoveredSubLevels() {
            return discoveredSubLevels;
        }

        public int faceCount() {
            int count = 0;
            for (SubLevelSurfaceData data : surfaces) {
                count += data.faces().size();
            }
            return count;
        }
    }

    private record SubLevelSurfaceData(Object subLevel, List<SurfaceFace> faces) {
    }

    private record SurfaceFace(BlockPos supportPos, BlockPos containerPos, Direction face,
            AABB box, double plane, Vec3 worldNormal) {
    }

    private record MudFaceKey(long blockPos, Direction face, int plane,
            int minimumU, int maximumU, int minimumV, int maximumV) {
    }

    private record MudVolume(
            BlockPos pos, BlockState state, SinkingMedium medium,
            long visualSource, VoxelShape localShape) {
        private boolean contains(Vec3 localPoint, double tolerance) {
            Vec3 point = localPoint.subtract(pos.getX(), pos.getY(), pos.getZ());
            for (AABB box : localShape.toAabbs()) {
                if (point.x >= box.minX - tolerance
                        && point.x <= box.maxX + tolerance
                        && point.y >= box.minY - tolerance
                        && point.y <= box.maxY + tolerance
                        && point.z >= box.minZ - tolerance
                        && point.z <= box.maxZ + tolerance) {
                    return true;
                }
            }
            return false;
        }
    }

    private record MudVolumeGroup(
            Object subLevel,
            AffineTransform inverse,
            List<MudVolume> volumes) {
    }

    private record WaterVolume(BlockPos pos, double height) {
        private boolean contains(
                Vec3 localPoint, double tolerance) {
            return localPoint.x >= pos.getX() - tolerance
                    && localPoint.x <= pos.getX() + 1.0D + tolerance
                    && localPoint.y >= pos.getY() - tolerance
                    && localPoint.y <= pos.getY() + height + tolerance
                    && localPoint.z >= pos.getZ() - tolerance
                    && localPoint.z <= pos.getZ() + 1.0D + tolerance;
        }
    }

    private record WaterVolumeGroup(
            AffineTransform inverse,
            List<WaterVolume> volumes) {
    }

    public static final class MudVolumeProbe {
        private static final MudVolumeProbe EMPTY =
                new MudVolumeProbe(List.of());
        private final List<MudVolumeGroup> groups;

        private MudVolumeProbe(List<MudVolumeGroup> groups) {
            this.groups = groups;
        }

        public boolean isEmpty() {
            return groups.isEmpty();
        }

        public MudVolumeSample sample(Vec3 worldPoint, double tolerance) {
            for (MudVolumeGroup group : groups) {
                Vec3 localPoint = group.inverse().toWorld(worldPoint);
                for (MudVolume volume : group.volumes()) {
                    if (volume.contains(localPoint, tolerance)) {
                        return new MudVolumeSample(
                                group.subLevel(),
                                volume.pos(),
                                volume.state(),
                                volume.medium(),
                                localPoint,
                                worldPoint,
                                volume.visualSource());
                    }
                }
            }
            return null;
        }
    }

    public static final class WaterVolumeProbe {
        private static final WaterVolumeProbe EMPTY =
                new WaterVolumeProbe(List.of());
        private final List<WaterVolumeGroup> groups;

        private WaterVolumeProbe(List<WaterVolumeGroup> groups) {
            this.groups = groups;
        }

        public boolean isEmpty() {
            return groups.isEmpty();
        }

        public boolean contains(
                Vec3 worldPoint, double tolerance) {
            for (WaterVolumeGroup group : groups) {
                Vec3 localPoint =
                        group.inverse().toWorld(worldPoint);
                for (WaterVolume volume : group.volumes()) {
                    if (volume.contains(localPoint, tolerance)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public record MudVolumeSample(
            Object subLevel,
            BlockPos pos,
            BlockState state,
            SinkingMedium medium,
            Vec3 localPoint,
            Vec3 worldPoint,
            long visualSource) {
    }

    public record MudSurfaceFace(
            Object subLevel,
            BlockPos pos,
            BlockState state,
            SinkingMedium medium,
            Direction face,
            AABB localBox,
            double plane) {
    }

    public record SurfaceContact(Object subLevel, BlockPos supportPos, BlockPos containerPos, Direction face,
            Vec3 localPoint, Vec3 worldPoint, double distance, double alignment) {
    }
}
