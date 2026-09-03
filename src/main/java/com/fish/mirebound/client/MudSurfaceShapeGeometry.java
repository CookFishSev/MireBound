package com.fish.mirebound.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Cached one-pixel exposed-face masks for arbitrary proxy voxel shapes. */
final class MudSurfaceShapeGeometry {
    private static final int RESOLUTION = 16;
    private static final int CELL_COUNT = RESOLUTION * RESOLUTION;
    private static final int MAXIMUM_CACHED_FACES = 1024;
    private static final double SAMPLE_EPSILON = 0.001D;
    private static final double BOUNDS_EPSILON = 1.0E-7D;
    private static final Map<FaceKey, FaceMask> CACHE =
            new LinkedHashMap<>(128, 0.75F, true);

    private MudSurfaceShapeGeometry() {
    }

    static FaceMask faceMask(
            List<AABB> shapeBoxes, AABB component, Direction face) {
        long shapeKey = shapeKey(shapeBoxes);
        FaceKey key = new FaceKey(shapeKey, boxKey(component), face);
        FaceMask cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        boolean[] cells = new boolean[CELL_COUNT];
        boolean any = false;
        double plane = facePlane(component, face);
        Vec3 normal = new Vec3(
                face.getStepX(), face.getStepY(), face.getStepZ());
        for (int v = 0; v < RESOLUTION; v++) {
            for (int u = 0; u < RESOLUTION; u++) {
                Vec3 point = cellCenter(face, plane, u, v);
                if (!onComponentFace(component, face, point)) {
                    continue;
                }
                Vec3 inside = point.subtract(normal.scale(SAMPLE_EPSILON));
                Vec3 outside = point.add(normal.scale(SAMPLE_EPSILON));
                if (contains(shapeBoxes, inside) && !contains(shapeBoxes, outside)) {
                    cells[u | v << 4] = true;
                    any = true;
                }
            }
        }

        int geometryKey = fold(shapeKey
                ^ Long.rotateLeft(boxKey(component), 19)
                ^ face.ordinal() * 0x9e3779b97f4a7c15L);
        FaceMask result = new FaceMask(cells, geometryKey, any);
        if (CACHE.size() >= MAXIMUM_CACHED_FACES) {
            CACHE.remove(CACHE.keySet().iterator().next());
        }
        CACHE.put(key, result);
        return result;
    }

    static Vec3 cellCenter(
            Direction face, double plane, int u, int v) {
        double first = (u + 0.5D) / RESOLUTION;
        double second = (v + 0.5D) / RESOLUTION;
        return switch (face) {
            case UP, DOWN -> new Vec3(first, plane, second);
            case NORTH -> new Vec3(1.0D - first, second, plane);
            case SOUTH -> new Vec3(first, second, plane);
            case WEST -> new Vec3(plane, second, first);
            case EAST -> new Vec3(plane, second, 1.0D - first);
        };
    }

    static void reset() {
        CACHE.clear();
    }

    private static boolean onComponentFace(
            AABB box, Direction face, Vec3 point) {
        return switch (face.getAxis()) {
            case X -> between(point.y, box.minY, box.maxY)
                    && between(point.z, box.minZ, box.maxZ);
            case Y -> between(point.x, box.minX, box.maxX)
                    && between(point.z, box.minZ, box.maxZ);
            case Z -> between(point.x, box.minX, box.maxX)
                    && between(point.y, box.minY, box.maxY);
        };
    }

    private static boolean contains(List<AABB> boxes, Vec3 point) {
        for (AABB box : boxes) {
            if (between(point.x, box.minX, box.maxX)
                    && between(point.y, box.minY, box.maxY)
                    && between(point.z, box.minZ, box.maxZ)) {
                return true;
            }
        }
        return false;
    }

    private static boolean between(double value, double minimum, double maximum) {
        return value >= minimum - BOUNDS_EPSILON
                && value <= maximum + BOUNDS_EPSILON;
    }

    private static double facePlane(AABB box, Direction face) {
        return switch (face) {
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
        };
    }

    private static long shapeKey(List<AABB> boxes) {
        long key = boxes.size();
        for (AABB box : boxes) {
            key = key * 31L + boxKey(box);
        }
        return key;
    }

    private static long boxKey(AABB box) {
        long key = Double.doubleToLongBits(box.minX);
        key = key * 31L + Double.doubleToLongBits(box.minY);
        key = key * 31L + Double.doubleToLongBits(box.minZ);
        key = key * 31L + Double.doubleToLongBits(box.maxX);
        key = key * 31L + Double.doubleToLongBits(box.maxY);
        return key * 31L + Double.doubleToLongBits(box.maxZ);
    }

    private static int fold(long value) {
        return (int) (value ^ value >>> 32);
    }

    record FaceMask(boolean[] cells, int geometryKey, boolean any) {
        boolean exposed(int u, int v) {
            return u >= 0 && u < RESOLUTION && v >= 0 && v < RESOLUTION
                    && cells[u | v << 4];
        }
    }

    private record FaceKey(long shapeKey, long boxKey, Direction face) {
    }
}
