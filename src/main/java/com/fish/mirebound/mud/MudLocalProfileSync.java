package com.fish.mirebound.mud;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.network.payload.MudLocalProfilesPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends local block profiles alongside the chunks that make those blocks visible. */
public final class MudLocalProfileSync {
    private static final Map<ServerLevel, Set<Long>> PENDING_CHUNKS = new IdentityHashMap<>();

    private MudLocalProfileSync() {
    }

    public static void queueChunk(ServerLevel level, ChunkPos chunk) {
        PENDING_CHUNKS.computeIfAbsent(level, ignored -> new HashSet<>()).add(chunk.toLong());
    }

    public static void flush(ServerLevel level) {
        Set<Long> pending = PENDING_CHUNKS.remove(level);
        if (pending == null) {
            return;
        }
        for (long packed : pending) {
            broadcastChunk(level, new ChunkPos(packed));
        }
    }

    public static void clear(ServerLevel level) {
        PENDING_CHUNKS.remove(level);
    }

    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            sendChunk(event.getPlayer(), level, event.getPos());
        }
    }

    public static int sendChunk(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        int entries = 0;
        for (MudLocalProfilesPayload payload : payloads(level, chunk)) {
            PacketDistributor.sendToPlayer(player, payload);
            entries += payload.entries().size();
        }
        return entries;
    }

    /**
     * Sends the exact local profile used by player prediction. Chunk-watch
     * synchronization is visibility-oriented and can race client session
     * resets, while movement prediction must have the profile before it can
     * agree with the server's depth cap.
     */
    static boolean sendActiveProfile(ServerPlayer player, ServerLevel level,
            BlockPos pos, SinkingMedium medium) {
        MudBlockProfileStore.Profile profile =
                MudBlockProfileStore.get(level).profile(level, pos, medium);
        if (profile == null) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, activeProfilePayload(
                level.dimension().location(), pos, profile));
        return true;
    }

    public static void broadcastChunk(ServerLevel level, ChunkPos chunk) {
        for (MudLocalProfilesPayload payload : payloads(level, chunk)) {
            PacketDistributor.sendToPlayersTrackingChunk(level, chunk, payload);
        }
    }

    public static void broadcastChunk(ServerLevel level, ChunkPos chunk, Object subLevel) {
        if (subLevel == null) {
            broadcastChunk(level, chunk);
            return;
        }
        List<ServerPlayer> players = SableCompat.trackingPlayers(subLevel);
        for (MudLocalProfilesPayload payload : payloads(level, chunk)) {
            for (ServerPlayer player : players) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    static List<MudLocalProfilesPayload> payloads(ServerLevel level, ChunkPos chunk) {
        List<MudBlockProfileStore.StoredProfile> stored =
                MudBlockProfileStore.get(level).profilesInChunk(level, chunk);
        if (stored.isEmpty()) {
            return List.of(new MudLocalProfilesPayload(
                    level.dimension().location(), chunk.x, chunk.z, true, List.of(), List.of()));
        }

        List<MudLocalProfilesPayload> result = new ArrayList<>();
        Map<FloatArrayKey, Integer> paletteIndices = new LinkedHashMap<>();
        List<MudLocalProfilesPayload.Palette> palettes = new ArrayList<>();
        List<MudLocalProfilesPayload.Entry> entries = new ArrayList<>();
        boolean replace = true;
        for (MudBlockProfileStore.StoredProfile entry : stored) {
            float[] values = toFloats(entry.profile().values());
            FloatArrayKey key = new FloatArrayKey(values);
            Integer palette = paletteIndices.get(key);
            boolean needsPalette = palette == null;
            if (!entries.isEmpty() && (entries.size() >= MudLocalProfilesPayload.MAX_ENTRIES
                    || needsPalette && palettes.size() >= MudLocalProfilesPayload.MAX_PALETTES)) {
                result.add(payload(level, chunk, replace, palettes, entries));
                replace = false;
                paletteIndices = new LinkedHashMap<>();
                palettes = new ArrayList<>();
                entries = new ArrayList<>();
                palette = null;
                needsPalette = true;
            }
            if (needsPalette) {
                palette = palettes.size();
                paletteIndices.put(key, palette);
                palettes.add(new MudLocalProfilesPayload.Palette(values));
            }
            entries.add(new MudLocalProfilesPayload.Entry(
                    entry.pos().asLong(), entry.profile().medium().id(), palette));
        }
        if (!entries.isEmpty()) {
            result.add(payload(level, chunk, replace, palettes, entries));
        }
        return List.copyOf(result);
    }

    private static MudLocalProfilesPayload payload(ServerLevel level, ChunkPos chunk,
            boolean replace, List<MudLocalProfilesPayload.Palette> palettes,
            List<MudLocalProfilesPayload.Entry> entries) {
        return new MudLocalProfilesPayload(level.dimension().location(), chunk.x, chunk.z, replace,
                List.copyOf(palettes), List.copyOf(entries));
    }

    static MudLocalProfilesPayload activeProfilePayload(
            net.minecraft.resources.ResourceLocation dimension,
            BlockPos pos, MudBlockProfileStore.Profile profile) {
        return new MudLocalProfilesPayload(
                dimension,
                pos.getX() >> 4,
                pos.getZ() >> 4,
                false,
                List.of(new MudLocalProfilesPayload.Palette(
                        toFloats(profile.values()))),
                List.of(new MudLocalProfilesPayload.Entry(
                        pos.asLong(), profile.medium().id(), 0)));
    }

    private static float[] toFloats(double[] values) {
        float[] result = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (float) values[index];
        }
        return result;
    }

    private static final class FloatArrayKey {
        private final float[] values;
        private final int hash;

        private FloatArrayKey(float[] values) {
            this.values = values;
            this.hash = Arrays.hashCode(values);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FloatArrayKey key && Arrays.equals(values, key.values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
