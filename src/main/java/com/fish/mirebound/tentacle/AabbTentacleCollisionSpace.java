package com.fish.mirebound.tentacle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class AabbTentacleCollisionSpace implements TentacleCollisionSpace {
    private static final double EPSILON = 1.0E-8D;
    private static final double MAXIMUM_CORRIDOR_SAMPLE_STEP = 0.75D;
    /** Horizontal and upward slack when matching a box against the root anchor. */
    private static final double ROOT_SUPPORT_TOLERANCE = 0.035D;
    /**
     * How far below the anchor a box top may sit and still count as its support. One block, so a
     * root placed anywhere inside its own supporting block is still exempt.
     */
    private static final double ROOT_SUPPORT_DEPTH = 1.0D;
    private final List<AABB> boxes;
    private final Map<Long, int[]> spatialBins;
    private final int[] visitedBoxes;
    private final double slop;
    private int[] candidateBuffer;
    private int queryStamp = 1;

    AabbTentacleCollisionSpace(List<AABB> boxes, double slop) {
        this.boxes = List.copyOf(boxes);
        this.spatialBins = buildSpatialBins(this.boxes);
        this.visitedBoxes = new int[this.boxes.size()];
        this.candidateBuffer = new int[Math.max(1, Math.min(64, this.boxes.size()))];
        this.slop = Math.max(0.0001D, slop);
    }

    static AabbTentacleCollisionSpace capture(ServerLevel level, AABB bounds, double slop) {
        return capture(level, bounds, slop, null);
    }

    static AabbTentacleCollisionSpace capture(ServerLevel level, AABB bounds,
            double slop, Vec3 rootAnchor) {
        List<AABB> boxes = new ArrayList<>();
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            if (!level.getChunkSource().hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            for (AABB local : state.getCollisionShape(level, cursor).toAabbs()) {
                AABB worldBox = local.move(cursor);
                if (!supportsRoot(worldBox, rootAnchor)) {
                    boxes.add(worldBox);
                }
            }
        }
        return new AabbTentacleCollisionSpace(boxes, slop);
    }

    static AabbTentacleCollisionSpace captureAlongPaths(ServerLevel level,
            List<List<Vec3>> paths, double padding, double slop, Vec3 rootAnchor,
            int maximumBlockSamples) {
        Set<Long> visited = new HashSet<>();
        List<AABB> boxes = new ArrayList<>();
        double corridor = Math.max(0.05D, padding);
        int budget = Math.max(1, maximumBlockSamples);
        for (List<Vec3> path : paths) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            if (path.size() == 1) {
                if (collect(level, new AABB(path.getFirst(), path.getFirst()).inflate(corridor),
                        rootAnchor, visited, boxes, budget)) {
                    return new AabbTentacleCollisionSpace(boxes, slop);
                }
                continue;
            }
            for (int index = 1; index < path.size(); index++) {
                Vec3 from = path.get(index - 1);
                Vec3 to = path.get(index);
                if (collectCorridorSegment(level, from, to, corridor,
                        rootAnchor, visited, boxes, budget)) {
                    return new AabbTentacleCollisionSpace(boxes, slop);
                }
            }
        }
        return new AabbTentacleCollisionSpace(boxes, slop);
    }

    /**
     * Samples an overlapping swept corridor instead of the complete axis-aligned prism between
     * two endpoints. Long diagonal routes therefore scale with route length and cross-section,
     * rather than with the volume of the diagonal's enclosing box.
     */
    private static boolean collectCorridorSegment(ServerLevel level, Vec3 from, Vec3 to,
            double corridor, Vec3 rootAnchor, Set<Long> visited, List<AABB> boxes, int budget) {
        double length = from.distanceTo(to);
        if (length <= EPSILON) {
            return collect(level, new AABB(from, from).inflate(corridor),
                    rootAnchor, visited, boxes, budget);
        }
        int sampleCount = Math.max(1, Mth.ceil(length / MAXIMUM_CORRIDOR_SAMPLE_STEP));
        double sampleStep = length / sampleCount;
        double samplePadding = corridor + sampleStep * 0.51D;
        for (int sample = 0; sample <= sampleCount; sample++) {
            Vec3 point = from.lerp(to, sample / (double) sampleCount);
            if (collect(level, new AABB(point, point).inflate(samplePadding),
                    rootAnchor, visited, boxes, budget)) {
                return true;
            }
        }
        return false;
    }

    private static boolean collect(ServerLevel level, AABB bounds, Vec3 rootAnchor,
            Set<Long> visited, List<AABB> boxes, int maximumBlockSamples) {
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            long packed = cursor.asLong();
            if (visited.contains(packed)) {
                continue;
            }
            if (visited.size() >= maximumBlockSamples) {
                return true;
            }
            visited.add(packed);
            if (!level.getChunkSource().hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            for (AABB local : state.getCollisionShape(level, cursor).toAabbs()) {
                AABB worldBox = local.move(cursor);
                if (!supportsRoot(worldBox, rootAnchor)) {
                    boxes.add(worldBox);
                }
            }
        }
        return false;
    }

    /**
     * Whether a box is the ground the tentacle emerges from, and so must not collide with it.
     *
     * <p>The vertical test is deliberately one-sided rather than a symmetric tolerance around
     * {@code box.maxY}. A tentacle's root anchor is not guaranteed to sit on a block surface: the
     * tuning wand places it at a free-space camera point, and the solver then lifts it by a further
     * {@code collisionSlop * 1.5}. An exact-surface match therefore missed the anchor's own
     * supporting block in almost every real placement, so that block was treated as an obstacle
     * with the root pinned inside it. Collision then pushed {@code points[1]} out while
     * {@code points[0]} stayed put, which fights the solver every tick at the one joint that has
     * the least freedom to respond.
     *
     * <p>Exempting any box whose top is at or below the anchor, within a segment-scale band, covers
     * both the "standing on the surface" and "embedded slightly inside the surface" cases while
     * still colliding with anything genuinely above the root.
     */
    private static boolean supportsRoot(AABB box, Vec3 rootAnchor) {
        if (rootAnchor == null) {
            return false;
        }
        double aboveAnchor = box.maxY - rootAnchor.y;
        return aboveAnchor <= ROOT_SUPPORT_TOLERANCE
                && aboveAnchor >= -ROOT_SUPPORT_DEPTH
                && rootAnchor.x >= box.minX - ROOT_SUPPORT_TOLERANCE
                && rootAnchor.x <= box.maxX + ROOT_SUPPORT_TOLERANCE
                && rootAnchor.z >= box.minZ - ROOT_SUPPORT_TOLERANCE
                && rootAnchor.z <= box.maxZ + ROOT_SUPPORT_TOLERANCE;
    }

    @Override
    public Vec3 move(Vec3 from, Vec3 desired, double radius) {
        Vec3 start = project(from, radius);
        Vec3 target = desired.add(start.subtract(from));
        for (int pass = 0; pass < 2; pass++) {
            Vec3 delta = target.subtract(start);
            if (delta.lengthSqr() < EPSILON) {
                break;
            }
            Hit hit = firstHit(start, delta, radius);
            if (hit == null) {
                start = target;
                break;
            }
            double travel = Math.max(0.0D, hit.time - slop / Math.max(delta.length(), slop));
            Vec3 contact = start.add(delta.scale(travel));
            Vec3 remainder = target.subtract(contact);
            double intoSurface = remainder.dot(hit.normal);
            if (intoSurface < 0.0D) {
                remainder = remainder.subtract(hit.normal.scale(intoSurface));
            }
            start = contact.add(hit.normal.scale(slop));
            target = start.add(remainder);
        }
        return project(start, radius);
    }

    @Override
    public Vec3 project(Vec3 point, double radius) {
        Vec3 result = point;
        double expansion = radius + slop;
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;
            int candidateCount = fillCandidates(new AABB(result, result).inflate(expansion));
            for (int candidate = 0; candidate < candidateCount; candidate++) {
                AABB box = boxes.get(candidateBuffer[candidate]);
                AABB expanded = box.inflate(expansion);
                if (!inside(expanded, result)) {
                    continue;
                }
                result = nearestOutside(expanded, result);
                changed = true;
            }
            if (!changed) {
                break;
            }
        }
        return result;
    }

    @Override
    public boolean clear(Vec3 from, Vec3 to, double radius) {
        if (!clear(from, radius) || !clear(to, radius)) {
            return false;
        }
        return firstHit(from, to.subtract(from), radius) == null;
    }

    @Override
    public boolean clear(Vec3 point, double radius) {
        double expansion = radius + slop;
        int candidateCount = fillCandidates(new AABB(point, point).inflate(expansion));
        for (int candidate = 0; candidate < candidateCount; candidate++) {
            AABB box = boxes.get(candidateBuffer[candidate]);
            if (inside(box.inflate(expansion), point)) {
                return false;
            }
        }
        return true;
    }

    int boxCount() {
        return boxes.size();
    }

    private Hit firstHit(Vec3 origin, Vec3 delta, double radius) {
        Hit nearest = null;
        double expansion = radius + slop;
        Vec3 target = origin.add(delta);
        AABB sweep = new AABB(
                Math.min(origin.x, target.x), Math.min(origin.y, target.y), Math.min(origin.z, target.z),
                Math.max(origin.x, target.x), Math.max(origin.y, target.y), Math.max(origin.z, target.z))
                .inflate(expansion);
        int candidateCount = fillCandidates(sweep);
        for (int candidate = 0; candidate < candidateCount; candidate++) {
            AABB box = boxes.get(candidateBuffer[candidate]);
            Hit hit = clip(box.inflate(expansion), origin, delta);
            if (hit != null && (nearest == null || hit.time < nearest.time)) {
                nearest = hit;
            }
        }
        return nearest;
    }

    private int fillCandidates(AABB bounds) {
        if (boxes.isEmpty()) {
            return 0;
        }
        int stamp = nextQueryStamp();
        int count = 0;
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX);
        int maxY = Mth.floor(bounds.maxY);
        int maxZ = Mth.floor(bounds.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int[] bin = spatialBins.get(BlockPos.asLong(x, y, z));
                    if (bin == null) {
                        continue;
                    }
                    for (int index : bin) {
                        if (visitedBoxes[index] == stamp) {
                            continue;
                        }
                        visitedBoxes[index] = stamp;
                        if (count >= candidateBuffer.length) {
                            candidateBuffer = Arrays.copyOf(candidateBuffer,
                                    Math.min(boxes.size(), candidateBuffer.length * 2));
                        }
                        candidateBuffer[count++] = index;
                    }
                }
            }
        }
        return count;
    }

    private int nextQueryStamp() {
        if (queryStamp == Integer.MAX_VALUE) {
            Arrays.fill(visitedBoxes, 0);
            queryStamp = 1;
        }
        return queryStamp++;
    }

    private static Map<Long, int[]> buildSpatialBins(List<AABB> boxes) {
        Map<Long, List<Integer>> building = new HashMap<>();
        for (int index = 0; index < boxes.size(); index++) {
            AABB box = boxes.get(index);
            int minX = Mth.floor(box.minX);
            int minY = Mth.floor(box.minY);
            int minZ = Mth.floor(box.minZ);
            int maxX = Mth.floor(box.maxX - EPSILON);
            int maxY = Mth.floor(box.maxY - EPSILON);
            int maxZ = Mth.floor(box.maxZ - EPSILON);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        building.computeIfAbsent(BlockPos.asLong(x, y, z), ignored -> new ArrayList<>())
                                .add(index);
                    }
                }
            }
        }
        Map<Long, int[]> result = new HashMap<>(building.size());
        building.forEach((key, indices) -> {
            int[] packed = new int[indices.size()];
            for (int index = 0; index < indices.size(); index++) {
                packed[index] = indices.get(index);
            }
            result.put(key, packed);
        });
        return result;
    }

    private static Hit clip(AABB box, Vec3 origin, Vec3 delta) {
        double enter = 0.0D;
        double exit = 1.0D;
        Vec3 normal = Vec3.ZERO;
        for (int axis = 0; axis < 3; axis++) {
            double start = component(origin, axis);
            double movement = component(delta, axis);
            double minimum = minimum(box, axis);
            double maximum = maximum(box, axis);
            if (Math.abs(movement) < EPSILON) {
                if (start < minimum || start > maximum) {
                    return null;
                }
                continue;
            }
            double near = (minimum - start) / movement;
            double far = (maximum - start) / movement;
            Vec3 nearNormal = axisVector(axis, movement > 0.0D ? -1.0D : 1.0D);
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            if (near > enter) {
                enter = near;
                normal = nearNormal;
            }
            exit = Math.min(exit, far);
            if (enter > exit) {
                return null;
            }
        }
        return enter >= 0.0D && enter <= 1.0D ? new Hit(enter, normal) : null;
    }

    private static Vec3 nearestOutside(AABB box, Vec3 point) {
        double[] distances = {
                point.x - box.minX,
                box.maxX - point.x,
                point.y - box.minY,
                box.maxY - point.y,
                point.z - box.minZ,
                box.maxZ - point.z
        };
        int nearest = 0;
        for (int index = 1; index < distances.length; index++) {
            if (distances[index] < distances[nearest]) {
                nearest = index;
            }
        }
        return switch (nearest) {
            case 0 -> new Vec3(box.minX, point.y, point.z);
            case 1 -> new Vec3(box.maxX, point.y, point.z);
            case 2 -> new Vec3(point.x, box.minY, point.z);
            case 3 -> new Vec3(point.x, box.maxY, point.z);
            case 4 -> new Vec3(point.x, point.y, box.minZ);
            default -> new Vec3(point.x, point.y, box.maxZ);
        };
    }

    private static boolean inside(AABB box, Vec3 point) {
        return point.x > box.minX && point.x < box.maxX
                && point.y > box.minY && point.y < box.maxY
                && point.z > box.minZ && point.z < box.maxZ;
    }

    private static double component(Vec3 vector, int axis) {
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    private static double minimum(AABB box, int axis) {
        return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
    }

    private static double maximum(AABB box, int axis) {
        return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
    }

    private static Vec3 axisVector(int axis, double value) {
        return axis == 0 ? new Vec3(value, 0.0D, 0.0D)
                : axis == 1 ? new Vec3(0.0D, value, 0.0D)
                : new Vec3(0.0D, 0.0D, value);
    }

    private record Hit(double time, Vec3 normal) {
    }
}
