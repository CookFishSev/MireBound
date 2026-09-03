package com.fish.mirebound.adaptive;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudLocalProfileSync;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.tuning.MudTuningManager;
import com.fish.mirebound.registry.ModBlocks;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Transactional conversion and restoration for adaptive mud selections. */
public final class AdaptiveMudService {
    private static final ThreadLocal<Integer> MUTATION_DEPTH = ThreadLocal.withInitial(() -> 0);
    // Functional source blocks must recalculate neighbor-derived state after replacement.
    static final int FUNCTIONAL_REPLACEMENT_FLAGS = Block.UPDATE_ALL;

    private AdaptiveMudService() {
    }

    public static MutationResult convert(ServerLevel level, BlockPos minimum, BlockPos maximum,
            SinkingMedium medium, Object subLevel) {
        return convert(level, minimum, maximum, medium, subLevel, null);
    }

    public static MutationResult convert(ServerLevel level, BlockPos minimum, BlockPos maximum,
            SinkingMedium medium, Object subLevel, ResourceLocation sourceFilter) {
        return convert(level, minimum, maximum, medium, subLevel, sourceFilter, false);
    }

    public static MutationResult convert(ServerLevel level, BlockPos minimum, BlockPos maximum,
            SinkingMedium medium, Object subLevel, ResourceLocation sourceFilter,
            boolean forceAllBlocks) {
        return convertPositions(level, BlockPos.betweenClosed(minimum, maximum),
                medium, subLevel, sourceFilter, forceAllBlocks, ignored -> {
                });
    }

    public static MutationResult convertPositions(
            ServerLevel level, Iterable<BlockPos> positions,
            SinkingMedium medium, Object subLevel, ResourceLocation sourceFilter,
            Consumer<BlockPos> changedPosition) {
        return convertPositions(level, positions, medium, subLevel, sourceFilter,
                false, changedPosition);
    }

    public static MutationResult convertPositions(
            ServerLevel level, Iterable<BlockPos> positions,
            SinkingMedium medium, Object subLevel, ResourceLocation sourceFilter,
            boolean forceAllBlocks, Consumer<BlockPos> changedPosition) {
        AdaptiveMudBlock proxy = ModBlocks.adaptiveBlockFor(medium);
        if (proxy == null) {
            return MutationResult.EMPTY;
        }
        AdaptiveMudSourceStore store = AdaptiveMudSourceStore.get(level);
        MudBlockProfileStore profiles = MudBlockProfileStore.get(level);
        Set<ChunkPos> changedChunks = new HashSet<>();
        int changed = 0;
        int rejected = 0;
        beginMutation();
        try {
            for (BlockPos pos : positions) {
                BlockPos immutable = pos.immutable();
                BlockState source = level.getBlockState(immutable);
                if (sourceFilter != null && !sourceFilter.equals(
                        BuiltInRegistries.BLOCK.getKey(source.getBlock()))) {
                    continue;
                }
                AdaptiveMudEligibility.Result eligibility =
                        AdaptiveMudEligibility.check(level, immutable, source);
                if (!canConvert(eligibility, forceAllBlocks)) {
                    if (eligibility != AdaptiveMudEligibility.Result.AIR
                            && eligibility != AdaptiveMudEligibility.Result.ALREADY_ADAPTIVE) {
                        rejected++;
                    }
                    continue;
                }
                if (!store.canStore(immutable)) {
                    rejected++;
                    continue;
                }
                BlockEntity sourceBlockEntity = level.getBlockEntity(immutable);
                CompoundTag sourceBlockEntityData = sourceBlockEntity == null ? null
                        : saveBlockEntityData(sourceBlockEntity, level);
                if (sourceBlockEntity != null && sourceBlockEntityData == null) {
                    rejected++;
                    continue;
                }
                CompoundTag renderBlockEntityData = sourceBlockEntity == null ? null
                        : renderBlockEntityData(sourceBlockEntity, sourceBlockEntityData);
                if (sourceBlockEntity != null && renderBlockEntityData == null) {
                    rejected++;
                    continue;
                }
                profiles.removeAll(immutable);
                if (!store.put(immutable, source, sourceBlockEntityData)) {
                    rejected++;
                    continue;
                }
                BlockState replacement = proxy.defaultBlockState().setValue(
                        AdaptiveMudBlock.SOURCE_BLOCK_ENTITY,
                        sourceBlockEntity != null);
                if (sourceBlockEntity != null) {
                    // Container blocks drop their inventory from onRemove. Detaching the
                    // source entity first makes replacement transactional instead.
                    level.removeBlockEntity(immutable);
                }
                if (level.setBlock(
                        immutable, replacement, FUNCTIONAL_REPLACEMENT_FLAGS)) {
                    BlockEntity replacementEntity = level.getBlockEntity(immutable);
                    if (replacementEntity instanceof AdaptiveMudBlockEntity adaptiveEntity) {
                        adaptiveEntity.configure(source, renderBlockEntityData);
                        level.sendBlockUpdated(
                                immutable, replacement, replacement, Block.UPDATE_CLIENTS);
                    }
                    changed++;
                    changedChunks.add(new ChunkPos(immutable));
                    changedPosition.accept(immutable);
                } else {
                    store.remove(immutable);
                    if (sourceBlockEntity != null) {
                        sourceBlockEntity.clearRemoved();
                        level.setBlockEntity(sourceBlockEntity);
                    }
                }
            }
        } finally {
            endMutation();
        }
        finish(level, changedChunks, subLevel);
        return new MutationResult(changed, rejected);
    }

    public static MutationResult restore(
            ServerLevel level, BlockPos minimum, BlockPos maximum, Object subLevel) {
        return restore(level, minimum, maximum, subLevel, null);
    }

    public static MutationResult restore(ServerLevel level, BlockPos minimum,
            BlockPos maximum, Object subLevel, ResourceLocation sourceFilter) {
        return restorePositions(level, BlockPos.betweenClosed(minimum, maximum),
                subLevel, sourceFilter);
    }

    public static MutationResult restorePositions(
            ServerLevel level, Iterable<BlockPos> positions,
            Object subLevel, ResourceLocation sourceFilter) {
        AdaptiveMudSourceStore store = AdaptiveMudSourceStore.get(level);
        MudBlockProfileStore profiles = MudBlockProfileStore.get(level);
        Set<ChunkPos> changedChunks = new HashSet<>();
        int changed = 0;
        int rejected = 0;
        beginMutation();
        try {
            for (BlockPos pos : positions) {
                BlockPos immutable = pos.immutable();
                if (!(level.getBlockState(immutable).getBlock() instanceof AdaptiveMudBlock)) {
                    continue;
                }
                BlockState storedSource = store.sourceState(immutable);
                ResourceLocation storedSourceId = storedSource == null ? null
                        : BuiltInRegistries.BLOCK.getKey(storedSource.getBlock());
                if (!matchesSourceFilter(storedSourceId, sourceFilter)) {
                    continue;
                }
                AdaptiveMudSourceStore.StoredSource stored = store.take(immutable);
                if (stored == null) {
                    rejected++;
                    continue;
                }
                BlockState source = stored.state();
                if (level.setBlock(
                        immutable, source, FUNCTIONAL_REPLACEMENT_FLAGS)) {
                    restoreBlockEntity(level, immutable, stored);
                    profiles.removeAll(immutable);
                    changed++;
                    changedChunks.add(new ChunkPos(immutable));
                } else {
                    store.put(immutable, stored);
                }
            }
        } finally {
            endMutation();
        }
        finish(level, changedChunks, subLevel);
        return new MutationResult(changed, rejected);
    }

    static boolean matchesSourceFilter(
            ResourceLocation sourceId, ResourceLocation sourceFilter) {
        return sourceFilter == null || sourceFilter.equals(sourceId);
    }

    static boolean canConvert(
            AdaptiveMudEligibility.Result eligibility, boolean forceAllBlocks) {
        return AdaptiveMudEligibility.canConvert(eligibility, forceAllBlocks);
    }

    /** Replaces legacy adaptive proxies with the canonical universal proxy in place. */
    public static Set<ChunkPos> canonicalize(ServerLevel level, List<BlockPos> positions) {
        AdaptiveMudBlock canonical = ModBlocks.adaptiveBlockFor(SinkingMedium.MUD);
        if (canonical == null || positions.isEmpty()) {
            return Set.of();
        }
        Set<ChunkPos> changedChunks = new HashSet<>();
        beginMutation();
        try {
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof AdaptiveMudBlock adaptive)
                        || adaptive == canonical) {
                    continue;
                }
                BlockState replacement = canonical.defaultBlockState().setValue(
                        AdaptiveMudBlock.SOURCE_BLOCK_ENTITY,
                        state.getValue(AdaptiveMudBlock.SOURCE_BLOCK_ENTITY));
                if (level.setBlock(pos, replacement,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                    changedChunks.add(new ChunkPos(pos));
                }
            }
        } finally {
            endMutation();
        }
        if (!changedChunks.isEmpty()) {
            MudTuningManager.markMudChanged(level);
        }
        return Set.copyOf(changedChunks);
    }

    static boolean mutationActive() {
        return MUTATION_DEPTH.get() > 0;
    }

    static void forgetRemovedProxy(ServerLevel level, BlockPos pos) {
        if (AdaptiveMudSourceStore.get(level).remove(pos) != null) {
            AdaptiveMudSourceSync.broadcastChunk(
                    level, new ChunkPos(pos), SableCompat.subLevelAtStorage(level, pos));
            MudTuningManager.markMudChanged(level);
        }
    }

    private static void finish(ServerLevel level, Set<ChunkPos> changedChunks, Object subLevel) {
        if (changedChunks.isEmpty()) {
            return;
        }
        for (ChunkPos chunk : changedChunks) {
            AdaptiveMudSourceSync.broadcastChunk(level, chunk, subLevel);
            MudLocalProfileSync.broadcastChunk(level, chunk, subLevel);
        }
        MudTuningManager.markMudChanged(level);
    }

    private static void restoreBlockEntity(ServerLevel level, BlockPos pos,
            AdaptiveMudSourceStore.StoredSource stored) {
        if (stored.blockEntityData() == null) {
            return;
        }
        BlockEntity restored = level.getBlockEntity(pos);
        if (restored != null) {
            restored.loadWithComponents(
                    stored.blockEntityData().copy(), level.registryAccess());
            restored.setChanged();
        }
    }

    private static CompoundTag renderBlockEntityData(
            BlockEntity source, CompoundTag fullData) {
        // The full snapshot was already bounded before conversion. Reusing it keeps the
        // render path under the same NBT budget instead of asking third-party entities for
        // an unbounded second snapshot.
        CompoundTag renderData = fullData.copy();
        renderData.putString("id", fullData.getString("id"));
        renderData.putInt("x", source.getBlockPos().getX());
        renderData.putInt("y", source.getBlockPos().getY());
        renderData.putInt("z", source.getBlockPos().getZ());
        return AdaptiveMudSourceStore.boundedBlockEntityData(renderData);
    }

    private static CompoundTag saveBlockEntityData(
            BlockEntity source, ServerLevel level) {
        try {
            return AdaptiveMudSourceStore.boundedBlockEntityData(
                    source.saveWithFullMetadata(level.registryAccess()));
        } catch (RuntimeException exception) {
            Mirebound.LOGGER.warn("Skipping adaptive conversion for block entity {} at {}",
                    source.getType().toString(), source.getBlockPos(), exception);
            return null;
        }
    }

    private static void beginMutation() {
        MUTATION_DEPTH.set(MUTATION_DEPTH.get() + 1);
    }

    private static void endMutation() {
        int depth = MUTATION_DEPTH.get() - 1;
        if (depth <= 0) {
            MUTATION_DEPTH.remove();
        } else {
            MUTATION_DEPTH.set(depth);
        }
    }

    public record MutationResult(int changed, int rejected) {
        static final MutationResult EMPTY = new MutationResult(0, 0);
    }
}
