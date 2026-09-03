package com.fish.mirebound.adaptive;

import com.fish.mirebound.Mirebound;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Sparse per-dimension source-state storage for adaptive full-cube mud proxies. */
public final class AdaptiveMudSourceStore extends SavedData {
    private static final String DATA_NAME = "mirebound_adaptive_mud_sources";
    private static final int MAX_PERSISTED_SOURCES = 262_144;
    private static final int MAX_BLOCK_ENTITY_NBT_DEPTH = 16;
    private static final int MAX_BLOCK_ENTITY_NBT_NODES = 8_192;
    private static final int MAX_BLOCK_ENTITY_NBT_CONTAINER_ENTRIES = 1_024;
    private static final int MAX_BLOCK_ENTITY_NBT_ARRAY_ELEMENTS = 16_384;
    private static final int MAX_BLOCK_ENTITY_NBT_STRING_LENGTH = 32_768;
    private static final Factory<AdaptiveMudSourceStore> FACTORY =
            new Factory<>(AdaptiveMudSourceStore::new, AdaptiveMudSourceStore::load);

    private final Map<Long, StoredSource> sources = new HashMap<>();
    private final Map<Long, Set<Long>> sourcesByChunk = new HashMap<>();

    public static AdaptiveMudSourceStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static AdaptiveMudSourceStore load(
            CompoundTag tag, HolderLookup.Provider registries) {
        AdaptiveMudSourceStore store = new AdaptiveMudSourceStore();
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_PERSISTED_SOURCES, entries.size());
        if (entries.size() > count) {
            Mirebound.LOGGER.warn("Ignoring {} adaptive mud source entries beyond the limit of {}",
                    entries.size() - count, MAX_PERSISTED_SOURCES);
        }
        for (int index = 0; index < count; index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.contains("State", Tag.TAG_COMPOUND)) {
                continue;
            }
            BlockState state;
            try {
                state = NbtUtils.readBlockState(
                        registries.lookupOrThrow(Registries.BLOCK),
                        entry.getCompound("State"));
            } catch (RuntimeException exception) {
                Mirebound.LOGGER.warn("Ignoring invalid adaptive mud source state at saved entry {}",
                        index);
                continue;
            }
            if (state.isAir() || state.getBlock() instanceof AdaptiveMudBlock) {
                continue;
            }
            long packed = entry.getLong("Pos");
            boolean hasBlockEntityData = entry.contains("BlockEntity", Tag.TAG_COMPOUND);
            CompoundTag blockEntityData = hasBlockEntityData
                    ? boundedBlockEntityData(entry.getCompound("BlockEntity")) : null;
            if (hasBlockEntityData && blockEntityData == null) {
                Mirebound.LOGGER.warn("Ignoring adaptive mud source with oversized block entity data at {}",
                        BlockPos.of(packed));
                continue;
            }
            store.sources.put(packed, new StoredSource(state, blockEntityData));
            store.addToChunkIndex(packed);
        }
        return store;
    }

    public BlockState sourceState(BlockPos pos) {
        StoredSource source = sources.get(pos.asLong());
        return source == null ? null : source.state();
    }

    @Nullable
    public CompoundTag sourceBlockEntityData(BlockPos pos) {
        StoredSource source = sources.get(pos.asLong());
        return source == null || source.blockEntityData() == null
                ? null : source.blockEntityData().copy();
    }

    public void put(BlockPos pos, BlockState sourceState) {
        put(pos, sourceState, null);
    }

    public boolean put(BlockPos pos, BlockState sourceState, CompoundTag blockEntityData) {
        return put(pos, new StoredSource(sourceState, blockEntityData));
    }

    boolean put(BlockPos pos, StoredSource source) {
        if (pos == null || source == null || source.state() == null
                || source.state().isAir() || source.state().getBlock() instanceof AdaptiveMudBlock) {
            return false;
        }
        CompoundTag boundedData = source.blockEntityData() == null
                ? null : boundedBlockEntityData(source.blockEntityData());
        if (source.blockEntityData() != null && boundedData == null) {
            return false;
        }
        long packed = pos.asLong();
        if (!sources.containsKey(packed) && sources.size() >= MAX_PERSISTED_SOURCES) {
            return false;
        }
        if (!sources.containsKey(packed)) {
            addToChunkIndex(packed);
        }
        sources.put(packed, new StoredSource(source.state(), boundedData));
        setDirty();
        return true;
    }

    boolean canStore(BlockPos pos) {
        return pos != null && (sources.containsKey(pos.asLong())
                || sources.size() < MAX_PERSISTED_SOURCES);
    }

    public BlockState remove(BlockPos pos) {
        StoredSource removed = take(pos);
        return removed == null ? null : removed.state();
    }

    StoredSource take(BlockPos pos) {
        long packed = pos.asLong();
        StoredSource removed = sources.remove(packed);
        if (removed == null) {
            return null;
        }
        long chunkKey = ChunkPos.asLong(pos);
        Set<Long> positions = sourcesByChunk.get(chunkKey);
        if (positions != null) {
            positions.remove(packed);
            if (positions.isEmpty()) {
                sourcesByChunk.remove(chunkKey);
            }
        }
        setDirty();
        return removed;
    }

    public List<Entry> entriesInChunk(ServerLevel level, ChunkPos chunk) {
        Set<Long> indexed = sourcesByChunk.get(chunk.toLong());
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }
        List<Entry> result = new ArrayList<>(indexed.size());
        List<Long> stale = new ArrayList<>();
        for (long packed : indexed) {
            BlockPos pos = BlockPos.of(packed);
            StoredSource stored = sources.get(packed);
            BlockState source = stored == null ? null : stored.state();
            if (source == null
                    || !(level.getBlockState(pos).getBlock() instanceof AdaptiveMudBlock)) {
                stale.add(packed);
                continue;
            }
            result.add(new Entry(pos, source));
        }
        if (!stale.isEmpty()) {
            for (long packed : stale) {
                remove(BlockPos.of(packed));
            }
        }
        return List.copyOf(result);
    }

    private void addToChunkIndex(long packed) {
        BlockPos pos = BlockPos.of(packed);
        sourcesByChunk.computeIfAbsent(ChunkPos.asLong(pos), ignored -> new HashSet<>())
                .add(packed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<Long, StoredSource> stored : sources.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", stored.getKey());
            entry.put("State", NbtUtils.writeBlockState(stored.getValue().state()));
            if (stored.getValue().blockEntityData() != null) {
                entry.put("BlockEntity", stored.getValue().blockEntityData().copy());
            }
            entries.add(entry);
        }
        tag.put("Entries", entries);
        return tag;
    }

    public record Entry(BlockPos pos, BlockState sourceState) {
    }

    record StoredSource(BlockState state, CompoundTag blockEntityData) {
    }

    /** Copies block-entity data without retaining arbitrarily large nested mod data. */
    static CompoundTag boundedBlockEntityData(CompoundTag source) {
        if (source == null) {
            return null;
        }
        return new NbtCopyBudget().copy(source, 0) instanceof CompoundTag bounded
                ? bounded : null;
    }

    private static final class NbtCopyBudget {
        private int nodes = MAX_BLOCK_ENTITY_NBT_NODES;
        private int arrayElements = MAX_BLOCK_ENTITY_NBT_ARRAY_ELEMENTS;

        private Tag copy(Tag source, int depth) {
            if (source == null || depth > MAX_BLOCK_ENTITY_NBT_DEPTH || nodes-- <= 0) {
                return null;
            }
            if (source instanceof CompoundTag compound) {
                if (compound.size() > MAX_BLOCK_ENTITY_NBT_CONTAINER_ENTRIES) {
                    return null;
                }
                CompoundTag result = new CompoundTag();
                int entries = 0;
                for (String key : compound.getAllKeys()) {
                    if (++entries > MAX_BLOCK_ENTITY_NBT_CONTAINER_ENTRIES
                            || key.length() > MAX_BLOCK_ENTITY_NBT_STRING_LENGTH) {
                        return null;
                    }
                    Tag value = copy(compound.get(key), depth + 1);
                    if (value == null) {
                        return null;
                    }
                    result.put(key, value);
                }
                return result;
            }
            if (source instanceof ListTag list) {
                if (list.size() > MAX_BLOCK_ENTITY_NBT_CONTAINER_ENTRIES) {
                    return null;
                }
                ListTag result = new ListTag();
                for (Tag value : list) {
                    Tag copied = copy(value, depth + 1);
                    if (copied == null) {
                        return null;
                    }
                    result.add(copied);
                }
                return result;
            }
            if (source instanceof ByteArrayTag bytes) {
                byte[] value = bytes.getAsByteArray();
                return array(value.length) ? new ByteArrayTag(value.clone()) : null;
            }
            if (source instanceof IntArrayTag ints) {
                int[] value = ints.getAsIntArray();
                return array(value.length) ? new IntArrayTag(value.clone()) : null;
            }
            if (source instanceof LongArrayTag longs) {
                long[] value = longs.getAsLongArray();
                return array(value.length) ? new LongArrayTag(value.clone()) : null;
            }
            if (source instanceof StringTag string
                    && string.getAsString().length() > MAX_BLOCK_ENTITY_NBT_STRING_LENGTH) {
                return null;
            }
            return source.copy();
        }

        private boolean array(int length) {
            if (length > arrayElements) {
                return false;
            }
            arrayElements -= length;
            return true;
        }
    }
}
