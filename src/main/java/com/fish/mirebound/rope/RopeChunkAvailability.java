package com.fish.mirebound.rope;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** An unloaded collision corridor is unknown terrain, not empty space. */
final class RopeChunkAvailability {
    private static final long MAX_CHUNKS_PER_SEGMENT = 64;

    private RopeChunkAvailability() {
    }

    static boolean loaded(List<Vec3> nodes, double padding, ChunkLookup chunks) {
        if (nodes.isEmpty() || !Double.isFinite(padding) || padding < 0.0D) {
            return false;
        }
        LongOpenHashSet checked = new LongOpenHashSet();
        Vec3 previous = nodes.getFirst();
        if (!finite(previous)) {
            return false;
        }
        if (nodes.size() == 1) {
            return loadedSegment(previous, previous, padding, checked, chunks);
        }
        for (int index = 1; index < nodes.size(); index++) {
            Vec3 point = nodes.get(index);
            if (!finite(point)) {
                return false;
            }
            if (!loadedSegment(previous, point, padding, checked, chunks)) {
                return false;
            }
            previous = point;
        }
        return true;
    }

    private static boolean loadedSegment(Vec3 from, Vec3 to, double padding,
            LongOpenHashSet checked, ChunkLookup chunks) {
        int minX = Mth.floor(Math.min(from.x, to.x) - padding) >> 4;
        int maxX = Mth.floor(Math.max(from.x, to.x) + padding) >> 4;
        int minZ = Mth.floor(Math.min(from.z, to.z) - padding) >> 4;
        int maxZ = Mth.floor(Math.max(from.z, to.z) + padding) >> 4;
        long count = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
        if (count <= 0 || count > MAX_CHUNKS_PER_SEGMENT) {
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
