package com.fish.mirebound.client;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.network.payload.AdaptiveMudSourcesPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded client mirror of adaptive source states used during chunk compilation. */
public final class AdaptiveMudClientCache {
    private static final int MAX_RETAINED_CHUNKS = 1024;
    private static final int PENDING_REMOVAL_BUDGET = 256;
    private static final Map<PositionKey, BlockState> SOURCES = new HashMap<>();
    private static final AppearanceRevisionIndex<PositionKey> APPEARANCE_REVISIONS =
            new AppearanceRevisionIndex<>();
    private static final LinkedHashMap<ChunkKey, Set<PositionKey>> CHUNKS =
            new LinkedHashMap<>(64, 0.75F, true);
    private static final PendingRemovalQueue<PositionKey> PENDING_REMOVALS =
            new PendingRemovalQueue<>();
    private static long appearanceEpoch;

    private AdaptiveMudClientCache() {
    }

    public static Update accept(AdaptiveMudSourcesPayload payload, Level level) {
        return accept(payload, (dimension, pos) -> level != null
                && level.isClientSide()
                && level.dimension().location().equals(dimension)
                && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockState(pos).getBlock() instanceof AdaptiveMudBlock);
    }

    static synchronized Update accept(AdaptiveMudSourcesPayload payload,
            BiPredicate<ResourceLocation, BlockPos> proxyStillPresent) {
        ChunkKey chunk = new ChunkKey(
                payload.dimension(), ChunkPos.asLong(payload.chunkX(), payload.chunkZ()));
        Set<BlockPos> dirtyPositions = new HashSet<>();
        boolean appearanceChanged = false;
        Set<PositionKey> previous = payload.replaceChunk()
                ? new HashSet<>(CHUNKS.getOrDefault(chunk, Set.of()))
                : Set.of();
        Set<PositionKey> positions = payload.replaceChunk()
                ? new HashSet<>()
                : CHUNKS.computeIfAbsent(chunk, ignored -> new HashSet<>());
        if (payload.replaceChunk()) {
            CHUNKS.remove(chunk);
        }
        for (AdaptiveMudSourcesPayload.Entry entry : payload.entries()) {
            BlockPos pos = BlockPos.of(entry.blockPos());
            if (pos.getX() >> 4 != payload.chunkX() || pos.getZ() >> 4 != payload.chunkZ()
                    || entry.sourceState() == null || entry.sourceState().isAir()) {
                continue;
            }
            PositionKey key = new PositionKey(payload.dimension(), entry.blockPos());
            BlockState previousSource = SOURCES.put(key, entry.sourceState());
            if (!entry.sourceState().equals(previousSource)) {
                APPEARANCE_REVISIONS.update(key);
                appearanceChanged = true;
            }
            positions.add(key);
            PENDING_REMOVALS.cancel(key);
            dirtyPositions.add(pos.immutable());
        }
        for (PositionKey key : previous) {
            BlockPos pos = BlockPos.of(key.blockPos());
            dirtyPositions.add(pos.immutable());
            if (positions.contains(key)) {
                continue;
            }
            if (proxyStillPresent.test(key.dimension(), pos)) {
                positions.add(key);
                PENDING_REMOVALS.stage(key);
            } else {
                appearanceChanged |= SOURCES.remove(key) != null;
                APPEARANCE_REVISIONS.remove(key);
                PENDING_REMOVALS.cancel(key);
            }
        }
        if (!positions.isEmpty()) {
            CHUNKS.put(chunk, positions);
        }
        trimChunks();
        if (appearanceChanged) {
            appearanceEpoch++;
        }
        return new Update(Set.copyOf(dirtyPositions));
    }

    public static void tick(Level level) {
        if (level == null || !level.isClientSide()) {
            return;
        }
        reconcilePending(PENDING_REMOVAL_BUDGET, (dimension, pos) ->
                !level.dimension().location().equals(dimension)
                        || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                        || level.getBlockState(pos).getBlock() instanceof AdaptiveMudBlock);
    }

    static synchronized int reconcilePending(int budget,
            BiPredicate<ResourceLocation, BlockPos> proxyStillPresent) {
        List<PositionKey> removedKeys = PENDING_REMOVALS.pollRemovable(
                budget,
                key -> proxyStillPresent.test(
                        key.dimension(), BlockPos.of(key.blockPos())));
        for (PositionKey key : removedKeys) {
            SOURCES.remove(key);
            APPEARANCE_REVISIONS.remove(key);
            BlockPos pos = BlockPos.of(key.blockPos());
            ChunkKey chunk = new ChunkKey(
                    key.dimension(), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            Set<PositionKey> positions = CHUNKS.get(chunk);
            if (positions != null) {
                positions.remove(key);
                if (positions.isEmpty()) {
                    CHUNKS.remove(chunk);
                }
            }
        }
        if (!removedKeys.isEmpty()) {
            appearanceEpoch++;
        }
        return removedKeys.size();
    }

    public static synchronized BlockState sourceState(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isClientSide()) {
            return null;
        }
        ResourceLocation dimension = level.dimension().location();
        BlockState source = SOURCES.get(new PositionKey(dimension, pos.asLong()));
        if (source != null) {
            CHUNKS.get(new ChunkKey(dimension,
                    ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)));
        }
        return source;
    }

    public static int appearanceRevision(
            Level level, long visualSource) {
        if (level == null || !level.isClientSide()
                || !MudVisualSource.positionBacked(visualSource)) {
            return 0;
        }
        BlockPos pos = MudVisualSource.position(visualSource);
        if (pos == null) {
            return 0;
        }
        return appearanceRevision(level.dimension().location(), pos.asLong());
    }

    static synchronized int appearanceRevision(
            ResourceLocation dimension, long blockPos) {
        return APPEARANCE_REVISIONS.getOrDefault(
                new PositionKey(dimension, blockPos));
    }

    public static synchronized long appearanceEpoch() {
        return appearanceEpoch;
    }

    public static synchronized void reset() {
        SOURCES.clear();
        APPEARANCE_REVISIONS.clear();
        CHUNKS.clear();
        PENDING_REMOVALS.clear();
        appearanceEpoch++;
    }

    private static void trimChunks() {
        while (CHUNKS.size() > MAX_RETAINED_CHUNKS) {
            removeChunk(CHUNKS.keySet().iterator().next());
        }
    }

    private static void removeChunk(ChunkKey chunk) {
        Set<PositionKey> removed = CHUNKS.remove(chunk);
        if (removed != null) {
            boolean appearanceChanged = false;
            for (PositionKey key : removed) {
                appearanceChanged |= SOURCES.remove(key) != null;
                APPEARANCE_REVISIONS.remove(key);
                PENDING_REMOVALS.cancel(key);
            }
            if (appearanceChanged) {
                appearanceEpoch++;
            }
        }
    }

    static final class PendingRemovalQueue<K> {
        private final Set<K> pending = new LinkedHashSet<>();

        void stage(K key) {
            pending.add(key);
        }

        void cancel(K key) {
            pending.remove(key);
        }

        void clear() {
            pending.clear();
        }

        int size() {
            return pending.size();
        }

        List<K> pollRemovable(int budget, Predicate<K> shouldRetain) {
            if (budget <= 0 || pending.isEmpty()) {
                return List.of();
            }
            List<K> retained = new ArrayList<>(Math.min(budget, pending.size()));
            List<K> removed = new ArrayList<>(Math.min(budget, pending.size()));
            int inspected = 0;
            var iterator = pending.iterator();
            while (iterator.hasNext() && inspected++ < budget) {
                K key = iterator.next();
                iterator.remove();
                if (shouldRetain.test(key)) {
                    retained.add(key);
                } else {
                    removed.add(key);
                }
            }
            pending.addAll(retained);
            return List.copyOf(removed);
        }
    }

    static final class AppearanceRevisionIndex<K> {
        private final Map<K, Integer> revisions = new HashMap<>();
        private int nextRevision = 1;

        int update(K key) {
            int revision = nextRevision++;
            if (nextRevision <= 0) {
                nextRevision = 1;
            }
            revisions.put(key, revision);
            return revision;
        }

        int getOrDefault(K key) {
            return revisions.getOrDefault(key, 0);
        }

        void remove(K key) {
            revisions.remove(key);
        }

        void clear() {
            revisions.clear();
            nextRevision = 1;
        }
    }

    public record Update(Set<BlockPos> dirtyPositions) {
    }

    private record PositionKey(ResourceLocation dimension, long blockPos) {
    }

    private record ChunkKey(ResourceLocation dimension, long chunkPos) {
    }
}
