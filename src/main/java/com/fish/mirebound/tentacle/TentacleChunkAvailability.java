package com.fish.mirebound.tentacle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Unknown terrain must pause a procedural tentacle instead of being treated as empty space. */
final class TentacleChunkAvailability {
    private static final long MAX_CHUNKS_PER_SEGMENT = 128;

    private TentacleChunkAvailability() {
    }

    static boolean loaded(List<List<Vec3>> paths, double padding, ChunkLookup chunks) {
        if (paths == null || !Double.isFinite(padding) || padding < 0.0D) {
            return false;
        }
        Set<Long> checked = new HashSet<>();
        for (List<Vec3> path : paths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            Vec3 previous = path.getFirst();
            if (!finite(previous)) {
                return false;
            }
            if (path.size() == 1) {
                if (!loadedSegment(previous, previous, padding, checked, chunks)) {
                    return false;
                }
                continue;
            }
            for (int index = 1; index < path.size(); index++) {
                Vec3 point = path.get(index);
                if (!finite(point)
                        || !loadedSegment(previous, point, padding, checked, chunks)) {
                    return false;
                }
                previous = point;
            }
        }
        return true;
    }

    private static boolean loadedSegment(Vec3 from, Vec3 to, double padding,
            Set<Long> checked, ChunkLookup chunks) {
        int minX = Mth.floor(Math.min(from.x, to.x) - padding) >> 4;
        int maxX = Mth.floor(Math.max(from.x, to.x) + padding) >> 4;
        int minZ = Mth.floor(Math.min(from.z, to.z) - padding) >> 4;
        int maxZ = Mth.floor(Math.max(from.z, to.z) + padding) >> 4;
        long count = ((long) maxX - minX + 1L) * ((long) maxZ - minZ + 1L);
        if (count <= 0L || count > MAX_CHUNKS_PER_SEGMENT) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (checked.add(ChunkPos.asLong(x, z)) && !chunks.loaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    @FunctionalInterface
    interface ChunkLookup {
        boolean loaded(int x, int z);
    }
}
