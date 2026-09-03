package com.fish.mirebound.eruption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One tick-local player snapshot shared by all vent collision and audience checks. */
final class MudEruptionPlayerIndex {
    private final Map<Long, List<ServerPlayer>> collisionPlayersByChunk = new HashMap<>();
    private final List<ServerPlayer> spawnPlayers = new ArrayList<>();
    private final List<ServerPlayer> observers = new ArrayList<>();

    static MudEruptionPlayerIndex capture(ServerLevel level) {
        MudEruptionPlayerIndex index = new MudEruptionPlayerIndex();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive()) {
                continue;
            }
            index.observers.add(player);
            if (player.isSpectator()) {
                continue;
            }
            index.spawnPlayers.add(player);
            AABB bounds = player.getBoundingBox();
            int minimumChunkX = ((int) Math.floor(bounds.minX)) >> 4;
            int maximumChunkX = ((int) Math.floor(bounds.maxX)) >> 4;
            int minimumChunkZ = ((int) Math.floor(bounds.minZ)) >> 4;
            int maximumChunkZ = ((int) Math.floor(bounds.maxZ)) >> 4;
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                    index.collisionPlayersByChunk
                            .computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), ignored -> new ArrayList<>())
                            .add(player);
                }
            }
        }
        return index;
    }

    static List<ServerPlayer> captureSpawnPlayers(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()) {
                players.add(player);
            }
        }
        return players;
    }

    List<ServerPlayer> spawnPlayers() {
        return spawnPlayers;
    }

    int crossingPlayer(MudEruptionVent vent) {
        double radius = vent.radiusPixels / 16.0D;
        Vec3 origin = vent.worldOrigin();
        if (origin == null) {
            return -1;
        }
        int minimumChunkX = ((int) Math.floor(origin.x - radius)) >> 4;
        int maximumChunkX = ((int) Math.floor(origin.x + radius)) >> 4;
        int minimumChunkZ = ((int) Math.floor(origin.z - radius)) >> 4;
        int maximumChunkZ = ((int) Math.floor(origin.z + radius)) >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                List<ServerPlayer> candidates = collisionPlayersByChunk.get(
                        ChunkPos.asLong(chunkX, chunkZ));
                if (candidates == null) {
                    continue;
                }
                for (ServerPlayer player : candidates) {
                    if (MudEruptionSystem.intersectsPlayerSurface(
                            player.getBoundingBox(), origin, vent.worldNormal(), radius)) {
                        return player.getId();
                    }
                }
            }
        }
        return -1;
    }

    boolean hasObserverNear(Vec3 origin, double range) {
        double rangeSquared = range * range;
        for (ServerPlayer player : observers) {
            if (player.position().distanceToSqr(origin) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }
}
