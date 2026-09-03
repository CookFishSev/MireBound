package com.fish.mirebound.compat.sable;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.adaptive.AdaptiveMudSourceSync;
import com.fish.mirebound.mud.MudLocalProfileSync;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Sends mod position data after Sable starts tracking a physical sub-level. */
public final class SableTrackingSync {
    private static final Map<Class<?>, Optional<SubLevelBridge>> SUB_LEVEL_BRIDGES =
            new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> HOLDER_CHUNK_METHODS =
            new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> CHUNK_POSITION_METHODS =
            new ConcurrentHashMap<>();

    private SableTrackingSync() {
    }

    public static void onFullSync(ServerPlayer player, Object subLevel) {
        if (player == null || subLevel == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<ChunkPos> chunks = loadedStorageChunks(subLevel);
        int adaptiveSources = 0;
        int localProfiles = 0;
        for (ChunkPos chunk : chunks) {
            adaptiveSources += AdaptiveMudSourceSync.sendChunk(player, level, chunk);
            localProfiles += MudLocalProfileSync.sendChunk(player, level, chunk);
        }
        if (adaptiveSources > 0 || localProfiles > 0) {
            Mirebound.LOGGER.info(
                    "Sable tracking sync sent {} adaptive sources and {} local profiles "
                            + "across {} hidden chunks to {}",
                    adaptiveSources, localProfiles, chunks.size(),
                    player.getGameProfile().getName());
        }
    }

    static List<ChunkPos> loadedStorageChunks(Object subLevel) {
        if (subLevel == null) {
            return List.of();
        }
        Optional<SubLevelBridge> bridge = SUB_LEVEL_BRIDGES.computeIfAbsent(
                subLevel.getClass(), SableTrackingSync::findSubLevelBridge);
        if (bridge.isEmpty()) {
            return List.of();
        }
        try {
            Object plot = bridge.get().getPlot().invoke(subLevel);
            Object loaded = bridge.get().getLoadedChunks().invoke(plot);
            if (!(loaded instanceof Collection<?> holders)) {
                return List.of();
            }
            Set<Long> seen = new HashSet<>();
            List<ChunkPos> chunks = new ArrayList<>(holders.size());
            for (Object holder : holders) {
                ChunkPos chunk = chunkPosition(holder);
                if (chunk != null && seen.add(chunk.toLong())) {
                    chunks.add(chunk);
                }
            }
            chunks.sort(Comparator.comparingLong(ChunkPos::toLong));
            return List.copyOf(chunks);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return List.of();
        }
    }

    private static Optional<SubLevelBridge> findSubLevelBridge(Class<?> subLevelType) {
        try {
            Method getPlot = subLevelType.getMethod("getPlot");
            Method getLoadedChunks = getPlot.getReturnType().getMethod("getLoadedChunks");
            return Optional.of(new SubLevelBridge(getPlot, getLoadedChunks));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private static ChunkPos chunkPosition(Object holder) throws ReflectiveOperationException {
        if (holder == null) {
            return null;
        }
        Optional<Method> getChunk = HOLDER_CHUNK_METHODS.computeIfAbsent(
                holder.getClass(), type -> findNoArgMethod(type, "getChunk"));
        if (getChunk.isEmpty()) {
            return null;
        }
        Object chunk = getChunk.get().invoke(holder);
        if (chunk == null) {
            return null;
        }
        Optional<Method> getPos = CHUNK_POSITION_METHODS.computeIfAbsent(
                chunk.getClass(), type -> findNoArgMethod(type, "getPos"));
        if (getPos.isEmpty()) {
            return null;
        }
        Object pos = getPos.get().invoke(chunk);
        return pos instanceof ChunkPos chunkPos ? chunkPos : null;
    }

    private static Optional<Method> findNoArgMethod(Class<?> type, String name) {
        try {
            return Optional.of(type.getMethod(name));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private record SubLevelBridge(Method getPlot, Method getLoadedChunks) {
    }
}
