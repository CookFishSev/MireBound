package com.fish.mirebound.stain;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPhysicsSettings;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent global FIFO used to enforce a footprint limit without scanning chunks. */
public final class MudFootprintLedger extends SavedData {
    private static final String DATA_NAME = "mirebound_footprints";
    private static final int MAX_PERSISTED_ENTRIES = 4096;
    private static final Factory<MudFootprintLedger> FACTORY =
            new Factory<>(MudFootprintLedger::new, MudFootprintLedger::load);

    private final ArrayDeque<Reference> ordered = new ArrayDeque<>();
    private final Map<Long, Reference> byId = new HashMap<>();
    private long nextId;

    public static MudFootprintLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static MudFootprintLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        MudFootprintLedger ledger = new MudFootprintLedger();
        ledger.nextId = tag.getLong("NextId");
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        int count = Math.min(MAX_PERSISTED_ENTRIES, entries.size());
        if (entries.size() > count) {
            Mirebound.LOGGER.warn("Ignoring {} footprint ledger entries beyond the limit of {}",
                    entries.size() - count, MAX_PERSISTED_ENTRIES);
        }
        for (int i = 0; i < count; i++) {
            CompoundTag entry = entries.getCompound(i);
            Reference reference = new Reference(
                    entry.getLong("Id"),
                    entry.getString("Dimension"),
                    entry.getLong("Pos"),
                    entry.getLong("Expires"));
            if (reference.id() > 0L && ledger.byId.putIfAbsent(reference.id(), reference) == null) {
                ledger.ordered.addLast(reference);
                ledger.nextId = Math.max(ledger.nextId, reference.id());
            }
        }
        return ledger;
    }

    long allocate(ServerLevel level, BlockPos pos, long expiresAt) {
        nextId++;
        Reference reference = new Reference(nextId, level.dimension().location().toString(), pos.asLong(), expiresAt);
        ordered.addLast(reference);
        byId.put(reference.id(), reference);
        setDirty();
        return reference.id();
    }

    void unregister(long id) {
        Reference reference = byId.remove(id);
        if (reference != null) {
            ordered.remove(reference);
            setDirty();
        }
    }

    void refresh(ServerLevel level, long id, BlockPos pos, long expiresAt) {
        Reference previous = byId.remove(id);
        if (previous != null) {
            ordered.remove(previous);
        }
        Reference refreshed = new Reference(id, level.dimension().location().toString(), pos.asLong(), expiresAt);
        ordered.addLast(refreshed);
        byId.put(id, refreshed);
        nextId = Math.max(nextId, id);
        setDirty();
    }

    void unregisterAll(Iterable<MudFootprintBlockEntity.Entry> entries) {
        for (MudFootprintBlockEntity.Entry entry : entries) {
            unregister(entry.id());
        }
    }

    void reconcileLoaded(ServerLevel level, BlockPos pos, MudFootprintBlockEntity blockEntity) {
        long knownHighWater = nextId;
        Iterator<MudFootprintBlockEntity.Entry> iterator = blockEntity.mutableEntries().iterator();
        while (iterator.hasNext()) {
            MudFootprintBlockEntity.Entry entry = iterator.next();
            if (byId.containsKey(entry.id())) {
                continue;
            }
            if (entry.id() <= knownHighWater) {
                iterator.remove();
                continue;
            }
            Reference reference = new Reference(
                    entry.id(), level.dimension().location().toString(), pos.asLong(), entry.expiresAt());
            ordered.addLast(reference);
            byId.put(reference.id(), reference);
            nextId = Math.max(nextId, reference.id());
            setDirty();
        }
        enforceLimit(level.getServer(), level.getGameTime());
    }

    void enforceLimit(MinecraftServer server, long gameTime) {
        if (!MudPhysicsSettings.footprintPermanent()) {
            while (!ordered.isEmpty() && ordered.peekFirst().expiresAt() <= gameTime) {
                evict(server, ordered.peekFirst());
            }
        }
        int maximum = MudPhysicsSettings.maximumFootprints();
        while (ordered.size() > maximum) {
            evict(server, ordered.peekFirst());
        }
    }

    public int clearAll(MinecraftServer server) {
        int cleared = ordered.size();
        while (!ordered.isEmpty()) {
            evict(server, ordered.peekFirst());
        }
        setDirty();
        return cleared;
    }

    private void evict(MinecraftServer server, Reference reference) {
        ordered.remove(reference);
        byId.remove(reference.id());
        setDirty();

        ResourceLocation dimensionId = ResourceLocation.tryParse(reference.dimension());
        if (dimensionId == null) {
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        BlockPos pos = BlockPos.of(reference.pos());
        if (level != null && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockEntity(pos) instanceof MudFootprintBlockEntity blockEntity) {
            blockEntity.removeFromLedger(reference.id());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextId", nextId);
        ListTag entries = new ListTag();
        for (Reference reference : ordered) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Id", reference.id());
            entry.putString("Dimension", reference.dimension());
            entry.putLong("Pos", reference.pos());
            entry.putLong("Expires", reference.expiresAt());
            entries.add(entry);
        }
        tag.put("Entries", entries);
        return tag;
    }

    private record Reference(long id, String dimension, long pos, long expiresAt) {
    }
}
