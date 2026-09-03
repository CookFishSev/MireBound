package com.fish.mirebound.adaptive;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.network.payload.AdaptiveMudSourcesPayload;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends adaptive appearance state with the chunk that owns each proxy. */
public final class AdaptiveMudSourceSync {
    private AdaptiveMudSourceSync() {
    }

    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            sendChunk(event.getPlayer(), level, event.getPos());
        }
    }

    public static int sendChunk(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        int entries = 0;
        for (AdaptiveMudSourcesPayload payload : payloads(level, chunk)) {
            PacketDistributor.sendToPlayer(player, payload);
            entries += payload.entries().size();
        }
        return entries;
    }

    public static void broadcastChunk(ServerLevel level, ChunkPos chunk, Object subLevel) {
        for (AdaptiveMudSourcesPayload payload : payloads(level, chunk)) {
            if (subLevel == null) {
                PacketDistributor.sendToPlayersTrackingChunk(level, chunk, payload);
            } else {
                for (ServerPlayer player : SableCompat.trackingPlayers(subLevel)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    private static List<AdaptiveMudSourcesPayload> payloads(ServerLevel level, ChunkPos chunk) {
        List<AdaptiveMudSourcesPayload.Entry> entries =
                AdaptiveMudSourceStore.get(level).entriesInChunk(level, chunk).stream()
                        .sorted(Comparator
                                .comparingInt((AdaptiveMudSourceStore.Entry entry) ->
                                        SectionPos.blockToSectionCoord(entry.pos().getY()))
                                .thenComparingLong(entry -> entry.pos().asLong()))
                        .map(entry -> new AdaptiveMudSourcesPayload.Entry(
                                entry.pos().asLong(), entry.sourceState()))
                        .toList();
        if (entries.isEmpty()) {
            return List.of(new AdaptiveMudSourcesPayload(
                    level.dimension().location(), chunk.x, chunk.z, true, List.of()));
        }
        List<AdaptiveMudSourcesPayload> payloads = new ArrayList<>(
                (entries.size() + AdaptiveMudSourcesPayload.MAX_ENTRIES - 1)
                        / AdaptiveMudSourcesPayload.MAX_ENTRIES);
        for (int start = 0; start < entries.size();
                start += AdaptiveMudSourcesPayload.MAX_ENTRIES) {
            int end = Math.min(entries.size(), start + AdaptiveMudSourcesPayload.MAX_ENTRIES);
            payloads.add(new AdaptiveMudSourcesPayload(
                    level.dimension().location(), chunk.x, chunk.z, start == 0,
                    List.copyOf(entries.subList(start, end))));
        }
        return List.copyOf(payloads);
    }
}
