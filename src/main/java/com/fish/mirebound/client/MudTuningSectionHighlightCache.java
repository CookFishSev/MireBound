package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import com.fish.mirebound.client.tuning.MudTuningClientSettings;
import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.MudLocalProfileCache;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.fish.mirebound.stain.MudFootprintBlock;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

/** Incrementally compiles ordinary-world tuning highlights into section-sized batches. */
public final class MudTuningSectionHighlightCache {
    private static final int SECTIONS_PER_TICK = 4;
    private static final int REPLAN_INTERVAL_TICKS = 20;
    private static final Predicate<BlockState> MUD_STATE =
            state -> state.getBlock() instanceof MudBlock;
    private static final Map<SectionKey, MudTuningSectionHighlightGeometry.SectionGeometry>
            GEOMETRY = new HashMap<>();
    private static final Set<SectionKey> PLANNED = new HashSet<>();
    private static final Set<SectionKey> DIRTY = new HashSet<>();
    private static final Set<SectionKey> QUEUED = new HashSet<>();
    private static final PriorityQueue<QueuedSection> PENDING = new PriorityQueue<>(
            Comparator.comparingLong(QueuedSection::distanceSquared)
                    .thenComparing(QueuedSection::key));

    private static ClientLevel level;
    private static SelectionBounds selection = SelectionBounds.NONE;
    private static int centerChunkX = Integer.MIN_VALUE;
    private static int centerSectionY = Integer.MIN_VALUE;
    private static int centerChunkZ = Integer.MIN_VALUE;
    private static int renderDistanceChunks = -1;
    private static int replanTicks;

    private MudTuningSectionHighlightCache() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null
                || MudTuningInputController.heldWandHand(minecraft.player) == null) {
            return;
        }
        if (level != minecraft.level) {
            reset();
            level = minecraft.level;
        }

        SelectionBounds nextSelection = SelectionBounds.current();
        int nextChunkX = SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
        int nextSectionY = SectionPos.blockToSectionCoord(minecraft.player.getBlockY());
        int nextChunkZ = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
        int nextRenderDistance = Math.max(2, minecraft.options.renderDistance().get());
        boolean moved = nextChunkX != centerChunkX || nextSectionY != centerSectionY
                || nextChunkZ != centerChunkZ;
        boolean selectionChanged = !nextSelection.equals(selection);
        if (selectionChanged) {
            clearGeometry();
            PLANNED.clear();
            PENDING.clear();
            QUEUED.clear();
            selection = nextSelection;
        }
        if (selectionChanged || moved || nextRenderDistance != renderDistanceChunks
                || replanTicks-- <= 0) {
            centerChunkX = nextChunkX;
            centerSectionY = nextSectionY;
            centerChunkZ = nextChunkZ;
            renderDistanceChunks = nextRenderDistance;
            replan();
            replanTicks = REPLAN_INTERVAL_TICKS;
        }

        for (int built = 0; built < SECTIONS_PER_TICK && !PENDING.isEmpty(); built++) {
            QueuedSection queued = PENDING.poll();
            QUEUED.remove(queued.key);
            if (PLANNED.contains(queued.key)) {
                GEOMETRY.put(queued.key, build(queued.key));
                DIRTY.remove(queued.key);
            }
        }
    }

    public static void invalidate(BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        invalidateSectionAndNeighbors(new SectionKey(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ())));
        replanTicks = 0;
    }

    public static void invalidateChunk(int chunkX, int chunkZ) {
        if (level == null) {
            return;
        }
        Set<SectionKey> affected = new HashSet<>();
        for (SectionKey key : PLANNED) {
            if (key.x == chunkX && key.z == chunkZ) {
                affected.add(key);
            }
        }
        for (SectionKey key : affected) {
            invalidateSectionAndNeighbors(key);
        }
        replanTicks = 0;
    }

    public static void reset() {
        level = null;
        selection = SelectionBounds.NONE;
        clearGeometry();
        PLANNED.clear();
        PENDING.clear();
        QUEUED.clear();
        centerChunkX = Integer.MIN_VALUE;
        centerSectionY = Integer.MIN_VALUE;
        centerChunkZ = Integer.MIN_VALUE;
        renderDistanceChunks = -1;
        replanTicks = 0;
    }

    static Collection<MudTuningSectionHighlightGeometry.SectionGeometry> sections() {
        return GEOMETRY.values();
    }

    private static void replan() {
        if (level == null) {
            return;
        }
        Set<SectionKey> desired = new HashSet<>();
        for (int chunkX = centerChunkX - renderDistanceChunks;
                chunkX <= centerChunkX + renderDistanceChunks; chunkX++) {
            for (int chunkZ = centerChunkZ - renderDistanceChunks;
                    chunkZ <= centerChunkZ + renderDistanceChunks; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    int sectionY = level.getSectionYFromSectionIndex(index);
                    SectionKey key = new SectionKey(chunkX, sectionY, chunkZ);
                    if (selection.intersects(key) || section.maybeHas(MUD_STATE)) {
                        desired.add(key);
                    }
                }
            }
        }

        for (SectionKey added : desired) {
            if (!PLANNED.contains(added)) {
                invalidateCachedNeighbors(added);
            }
        }
        PLANNED.clear();
        PLANNED.addAll(desired);
        List<SectionKey> removed = GEOMETRY.keySet().stream()
                .filter(key -> !desired.contains(key))
                .toList();
        for (SectionKey key : removed) {
            remove(key);
        }
        DIRTY.retainAll(desired);
        PENDING.clear();
        QUEUED.clear();
        for (SectionKey key : desired) {
            if (!GEOMETRY.containsKey(key) || DIRTY.contains(key)) {
                enqueue(key);
            }
        }
    }

    private static void enqueue(SectionKey key) {
        if (!PLANNED.contains(key) || !QUEUED.add(key)) {
            return;
        }
        long dx = (long) key.x - centerChunkX;
        long dy = (long) key.y - centerSectionY;
        long dz = (long) key.z - centerChunkZ;
        PENDING.add(new QueuedSection(key, dx * dx + dy * dy + dz * dz));
    }

    private static void invalidateSectionAndNeighbors(SectionKey center) {
        markDirty(center);
        for (Direction direction : Direction.values()) {
            markDirty(center.relative(direction));
        }
    }

    private static void invalidateCachedNeighbors(SectionKey center) {
        for (Direction direction : Direction.values()) {
            SectionKey neighbor = center.relative(direction);
            if (GEOMETRY.containsKey(neighbor)) {
                markDirty(neighbor);
            }
        }
    }

    private static void markDirty(SectionKey key) {
        if (!PLANNED.contains(key)) {
            return;
        }
        DIRTY.add(key);
        enqueue(key);
    }

    private static void remove(SectionKey key) {
        DIRTY.remove(key);
        GEOMETRY.remove(key);
        MudTuningSectionHighlightGpuCache.invalidate(key);
        if (PLANNED.contains(key)) {
            enqueue(key);
        }
    }

    private static void clearGeometry() {
        DIRTY.clear();
        GEOMETRY.clear();
        MudTuningSectionHighlightGpuCache.reset();
    }

    private static MudTuningSectionHighlightGeometry.SectionGeometry build(SectionKey key) {
        if (level == null || !level.hasChunk(key.x, key.z)) {
            return MudTuningSectionHighlightGeometry.empty(key);
        }
        LevelChunk chunk = level.getChunk(key.x, key.z);
        int index = level.getSectionIndexFromSectionY(key.y);
        LevelChunkSection[] sections = chunk.getSections();
        if (index < 0 || index >= sections.length || sections[index].hasOnlyAir()) {
            return MudTuningSectionHighlightGeometry.empty(key);
        }

        EnumMap<MudTuningSelectionPayload.HighlightKind, Set<Long>> local =
                new EnumMap<>(MudTuningSelectionPayload.HighlightKind.class);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = key.x << 4;
        int baseY = key.y << 4;
        int baseZ = key.z << 4;
        LevelChunkSection section = sections[index];
        Map<BlockState, Boolean> staticEligibility = new IdentityHashMap<>();
        for (int localY = 0; localY < 16; localY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    cursor.set(baseX + localX, baseY + localY, baseZ + localZ);
                    BlockState state = section.getBlockState(localX, localY, localZ);
                    MudTuningSelectionPayload.HighlightKind kind = classify(
                            cursor, state, staticEligibility);
                    if (kind != null) {
                        local.computeIfAbsent(kind, ignored -> new HashSet<>())
                                .add(cursor.asLong());
                    }
                }
            }
        }
        if (local.isEmpty()) {
            return MudTuningSectionHighlightGeometry.empty(key);
        }
        return MudTuningSectionHighlightGeometry.compile(
                key, local, MudTuningSectionHighlightCache::classifyAt);
    }

    private static MudTuningSelectionPayload.HighlightKind classifyAt(BlockPos pos) {
        if (level == null || level.isOutsideBuildHeight(pos)
                || !level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getZ()))) {
            return null;
        }
        return classify(pos, level.getBlockState(pos));
    }

    private static MudTuningSelectionPayload.HighlightKind classify(
            BlockPos pos, BlockState state) {
        return classify(pos, state, null);
    }

    private static MudTuningSelectionPayload.HighlightKind classify(
            BlockPos pos, BlockState state, Map<BlockState, Boolean> staticEligibility) {
        if (state.getBlock() instanceof AdaptiveMudBlock adaptive) {
            return modified(pos, state, adaptive.medium())
                    ? MudTuningSelectionPayload.HighlightKind.CONVERTED_MODIFIED
                    : MudTuningSelectionPayload.HighlightKind.CONVERTED_DEFAULT;
        }
        if (state.getBlock() instanceof MudBlock mud) {
            return modified(pos, state, mud.medium()) ? nativeKind(pos, state, mud.medium()) : null;
        }
        if (!selection.contains(pos) || state.isAir()
                || state.getBlock() instanceof MudFootprintBlock) {
            return null;
        }
        if (MudTuningClientSettings.unrestrictedConversionEnabled()) {
            return null;
        }
        boolean supported;
        if (staticEligibility == null || state.getBlock().hasDynamicShape()) {
            supported = AdaptiveMudEligibility.check(level, pos, state).supported();
        } else {
            Boolean cached = staticEligibility.get(state);
            if (cached == null) {
                cached = AdaptiveMudEligibility.check(level, pos, state).supported();
                staticEligibility.put(state, cached);
            }
            supported = cached;
        }
        return supported
                ? null : MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE;
    }

    private static boolean modified(BlockPos pos, BlockState state, SinkingMedium medium) {
        return MudBlock.variant(state) == MudBlockVariant.SPECIAL
                || MudLocalProfileCache.hasLocalProfile(level, pos, medium);
    }

    private static MudTuningSelectionPayload.HighlightKind nativeKind(
            BlockPos pos, BlockState state, SinkingMedium medium) {
        if (!MudMediumRuntime.flowProfile(level, pos, medium).enabled()) {
            return MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE;
        }
        for (MudPhysicsParameter parameter : MudPhysicsParameter.values()) {
            if (parameter.appliesTo(medium)
                    && !parameter.isFiniteVolumeFlowParameter()
                    && !sameSyncedValue(
                            MudMediumRuntime.value(level, pos, medium, parameter),
                            MudMediumRuntime.value(level, medium, parameter))) {
                return MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE_FLOW_MIXED;
            }
        }
        return MudBlock.variant(state) == MudBlockVariant.SPECIAL
                ? MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE_FLOW_MIXED
                : MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE_FLOW;
    }

    static boolean sameSyncedValue(double first, double second) {
        return Float.floatToIntBits((float) first) == Float.floatToIntBits((float) second);
    }

    record SectionKey(int x, int y, int z) implements Comparable<SectionKey> {
        private SectionKey relative(Direction direction) {
            return new SectionKey(x + direction.getStepX(), y + direction.getStepY(),
                    z + direction.getStepZ());
        }

        boolean contains(BlockPos pos) {
            return SectionPos.blockToSectionCoord(pos.getX()) == x
                    && SectionPos.blockToSectionCoord(pos.getY()) == y
                    && SectionPos.blockToSectionCoord(pos.getZ()) == z;
        }

        AABB bounds() {
            return new AABB(x << 4, y << 4, z << 4,
                    (x + 1) << 4, (y + 1) << 4, (z + 1) << 4);
        }

        @Override
        public int compareTo(SectionKey other) {
            int result = Integer.compare(x, other.x);
            result = result == 0 ? Integer.compare(y, other.y) : result;
            return result == 0 ? Integer.compare(z, other.z) : result;
        }
    }

    private record QueuedSection(SectionKey key, long distanceSquared) {
    }

    record SelectionBounds(boolean active, BlockPos minimum, BlockPos maximum) {
        private static final SelectionBounds NONE =
                new SelectionBounds(false, BlockPos.ZERO, BlockPos.ZERO);

        private static SelectionBounds current() {
            if (!MudTuningClientState.hasFirst() || !MudTuningClientState.hasSecond()
                    || MudTuningClientState.first().isSable()
                    || !MudTuningClientState.first().sameDomain(MudTuningClientState.second())) {
                return NONE;
            }
            BlockPos first = MudTuningClientState.first().pos();
            BlockPos second = MudTuningClientState.second().pos();
            return new SelectionBounds(true,
                    new BlockPos(Math.min(first.getX(), second.getX()),
                            Math.min(first.getY(), second.getY()),
                            Math.min(first.getZ(), second.getZ())),
                    new BlockPos(Math.max(first.getX(), second.getX()),
                            Math.max(first.getY(), second.getY()),
                            Math.max(first.getZ(), second.getZ())));
        }

        private boolean contains(BlockPos pos) {
            return active && pos.getX() >= minimum.getX() && pos.getX() <= maximum.getX()
                    && pos.getY() >= minimum.getY() && pos.getY() <= maximum.getY()
                    && pos.getZ() >= minimum.getZ() && pos.getZ() <= maximum.getZ();
        }

        boolean intersects(SectionKey section) {
            if (!active) {
                return false;
            }
            int minX = section.x << 4;
            int minY = section.y << 4;
            int minZ = section.z << 4;
            return maximum.getX() >= minX && minimum.getX() < minX + 16
                    && maximum.getY() >= minY && minimum.getY() < minY + 16
                    && maximum.getZ() >= minZ && minimum.getZ() < minZ + 16;
        }
    }
}
