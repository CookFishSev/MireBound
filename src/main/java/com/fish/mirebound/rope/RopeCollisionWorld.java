package com.fish.mirebound.rope;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Cached ordinary/Sable collision geometry for one rope solver tick. */
public final class RopeCollisionWorld {
    private static final double CONTACT_EPSILON = 1.0E-4D;
    private static final double NORMAL_TIE_EPSILON = 1.0E-6D;
    private static final double MIN_DISTANCE_SQUARED = 1.0E-12D;
    private static final double MUD_SINK_DEPTH = 1.25D / 16.0D;
    private static final double MUD_RESURFACE_SPEED = 0.025D;
    private static final double MUD_SHALLOW_CAPTURE_DEPTH = 0.30D;
    private static final int MAX_COLLISION_BOXES = 16_384;

    private final CollisionSpace world;
    private final MudSupportSpace mud;
    private final List<LocalSpace> sable;
    private final List<LocalMudSpace> sableMud;

    private RopeCollisionWorld(
            CollisionSpace world, MudSupportSpace mud, List<LocalSpace> sable,
            List<LocalMudSpace> sableMud) {
        this.world = world;
        this.mud = mud;
        this.sable = List.copyOf(sable);
        this.sableMud = List.copyOf(sableMud);
    }

    public static RopeCollisionWorld captureCorridors(ServerLevel level,
            List<List<Vec3>> corridors, double padding, int budget) {
        int safeBudget = Math.max(128, budget);
        WorldCapture captured = captureWorld(
                level, corridors, padding, safeBudget);
        CollisionSpace world = new CollisionSpace(captured.collisionBoxes());
        MudSupportSpace mud = new MudSupportSpace(captured.mudVolumes());
        List<LocalSpace> localSpaces = new ArrayList<>();
        List<LocalMudSpace> localMudSpaces = new ArrayList<>();
        if (SableCompat.isLoaded()) {
            for (SableCompat.SubLevelCollisionGeometry geometry
                    : SableCompat.collisionGeometry(
                            level, corridors, padding, safeBudget, false)) {
                SableCompat.AffineTransform toLocal =
                        geometry.transform().resolveLocal();
                SableCompat.AffineTransform toWorld =
                        geometry.transform().resolveWorld();
                if (toLocal == null || toWorld == null) {
                    continue;
                }
                if (!geometry.localBoxes().isEmpty()) {
                    localSpaces.add(new LocalSpace(toLocal, toWorld,
                            new CollisionSpace(geometry.localBoxes())));
                }
                if (!geometry.localMudBoxes().isEmpty()) {
                    localMudSpaces.add(new LocalMudSpace(
                            toLocal, toWorld,
                            new MudSupportSpace(geometry.localMudBoxes())));
                }
            }
        }
        return new RopeCollisionWorld(world, mud, localSpaces, localMudSpaces);
    }

    static RopeCollisionWorld testing(List<AABB> boxes) {
        return testing(boxes, List.of());
    }

    static RopeCollisionWorld testing(
            List<AABB> boxes, List<AABB> mudVolumes) {
        return new RopeCollisionWorld(
                new CollisionSpace(boxes), new MudSupportSpace(mudVolumes),
                List.of(), List.of());
    }

    public boolean isEmpty() {
        return world.isEmpty() && mud.isEmpty() && sable.isEmpty()
                && sableMud.isEmpty();
    }

    /** Resolves a moving rope node and returns the earliest safe position. */
    public Vec3 sweep(Vec3 from, Vec3 desired, double radius) {
        Vec3 result = world.sweep(from, desired, radius);
        double progress = progress(from, desired, result);
        for (LocalSpace local : sable) {
            Vec3 candidate = local.sweep(from, desired, radius);
            double candidateProgress = progress(from, desired, candidate);
            if (candidateProgress < progress) {
                result = candidate;
                progress = candidateProgress;
            }
        }
        result = mud.support(from, result, radius);
        for (LocalMudSpace local : sableMud) {
            Vec3 candidate = local.support(from, result, radius);
            double candidateProgress = progress(from, desired, candidate);
            if (candidateProgress <= progress + CONTACT_EPSILON) {
                result = candidate;
                progress = candidateProgress;
            }
        }
        return project(result, radius);
    }

    /** Returns whether a segment can travel from start to end without contact. */
    public boolean clear(Vec3 start, Vec3 end, double radius) {
        Vec3 resolved = sweep(start, end, radius);
        return resolved.distanceToSqr(end) <= CONTACT_EPSILON * CONTACT_EPSILON;
    }

    /** Projects a stationary rope sample out of all overlapping collision spaces. */
    public Vec3 project(Vec3 point, double radius) {
        Vec3 result = world.project(point, radius);
        for (int pass = 0; pass < 2; pass++) {
            for (LocalSpace local : sable) {
                result = local.project(result, radius);
            }
            result = world.project(result, radius);
        }
        return result;
    }

    private static double progress(Vec3 from, Vec3 desired, Vec3 result) {
        Vec3 movement = desired.subtract(from);
        double lengthSquared = movement.lengthSqr();
        return lengthSquared <= MIN_DISTANCE_SQUARED ? 0.0D
                : Mth.clamp(result.subtract(from).dot(movement) / lengthSquared,
                        0.0D, 1.0D);
    }

    private static WorldCapture captureWorld(Level level,
            List<List<Vec3>> corridors, double padding, int budget) {
        Set<Long> visited = new HashSet<>();
        List<AABB> boxes = new ArrayList<>(Math.min(budget, MAX_COLLISION_BOXES));
        List<AABB> mudVolumes = new ArrayList<>();
        for (List<Vec3> path : corridors) {
            for (int index = 0; index < path.size(); index++) {
                Vec3 point = path.get(index);
                AABB bounds = new AABB(point, point).inflate(padding);
                if (index > 0) {
                    Vec3 previous = path.get(index - 1);
                    bounds = bounds.minmax(new AABB(previous, previous).inflate(padding));
                }
                BlockPos minimum = BlockPos.containing(
                        bounds.minX, bounds.minY, bounds.minZ);
                BlockPos maximum = BlockPos.containing(
                        bounds.maxX, bounds.maxY, bounds.maxZ);
                for (BlockPos cursor : BlockPos.betweenClosed(minimum, maximum)) {
                    if (visited.size() >= budget) {
                        return new WorldCapture(
                                List.copyOf(boxes), List.copyOf(mudVolumes));
                    }
                    long packed = cursor.asLong();
                    if (!visited.add(packed)
                            || !level.getChunkSource().hasChunk(
                                    cursor.getX() >> 4, cursor.getZ() >> 4)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.getBlock() instanceof MudBlock mudBlock
                            && MudMediumRuntime.enabled(
                                    level, cursor, mudBlock.medium())) {
                        for (AABB local : MudBlock.localShape(
                                level, cursor, state, mudBlock.medium()).toAabbs()) {
                            if (mudVolumes.size() >= MAX_COLLISION_BOXES) {
                                return new WorldCapture(
                                        List.copyOf(boxes), List.copyOf(mudVolumes));
                            }
                            mudVolumes.add(local.move(cursor));
                        }
                    }
                    for (AABB local : state.getCollisionShape(level, cursor).toAabbs()) {
                        if (boxes.size() >= MAX_COLLISION_BOXES) {
                            return new WorldCapture(
                                    List.copyOf(boxes), List.copyOf(mudVolumes));
                        }
                        boxes.add(local.move(cursor));
                    }
                }
            }
        }
        return new WorldCapture(List.copyOf(boxes), List.copyOf(mudVolumes));
    }

    private record WorldCapture(
            List<AABB> collisionBoxes, List<AABB> mudVolumes) {
    }

    /** One-way shallow support that leaves mud sides and upward travel open. */
    private static final class MudSupportSpace {
        private final List<AABB> volumes;
        private final Map<Long, List<Integer>> bins;
        private final int[] candidateMarks;
        private final List<AABB> candidateBuffer = new ArrayList<>();
        private int candidateStamp;

        private MudSupportSpace(List<AABB> volumes) {
            this.volumes = List.copyOf(volumes);
            this.candidateMarks = new int[this.volumes.size()];
            this.bins = buildBins(this.volumes);
        }

        private boolean isEmpty() {
            return volumes.isEmpty();
        }

        Vec3 support(Vec3 from, Vec3 desired, double radius) {
            if (volumes.isEmpty()) {
                return desired;
            }
            AABB bounds = new AABB(
                    Math.min(from.x, desired.x), Math.min(from.y, desired.y),
                    Math.min(from.z, desired.z), Math.max(from.x, desired.x),
                    Math.max(from.y, desired.y), Math.max(from.z, desired.z))
                    .inflate(radius);
            double verticalTravel = from.y - desired.y;
            double earliest = Double.POSITIVE_INFINITY;
            double supportY = Double.NaN;
            for (AABB volume : candidates(bounds)) {
                double restY = volume.maxY - MUD_SINK_DEPTH + radius;
                if (insideMud(from, volume, radius)
                        && desired.y < restY - CONTACT_EPSILON) {
                    double raisedY = Math.min(restY, from.y + MUD_RESURFACE_SPEED);
                    if (!Double.isFinite(supportY) || raisedY > supportY) {
                        supportY = raisedY;
                    }
                    continue;
                }
                if (desired.y >= from.y - CONTACT_EPSILON) {
                    continue;
                }
                if (desired.y >= restY
                        || from.y < volume.maxY - MUD_SHALLOW_CAPTURE_DEPTH) {
                    continue;
                }
                double fraction = from.y <= restY
                        ? 0.0D : Mth.clamp((from.y - restY) / verticalTravel,
                                0.0D, 1.0D);
                double x = Mth.lerp(fraction, from.x, desired.x);
                double z = Mth.lerp(fraction, from.z, desired.z);
                if (x < volume.minX - radius || x > volume.maxX + radius
                        || z < volume.minZ - radius || z > volume.maxZ + radius
                        || fraction >= earliest) {
                    continue;
                }
                earliest = fraction;
                // Let a falling rope enter a small amount first. Subsequent
                // sweeps bring it back to the stable surface instead of snapping.
                supportY = Math.max(restY - MUD_SINK_DEPTH, desired.y);
            }
            return Double.isFinite(supportY)
                    ? new Vec3(desired.x, supportY, desired.z) : desired;
        }

        private static boolean insideMud(Vec3 point, AABB volume, double radius) {
            return point.x >= volume.minX - radius && point.x <= volume.maxX + radius
                    && point.z >= volume.minZ - radius && point.z <= volume.maxZ + radius
                    && point.y >= volume.minY - radius && point.y < volume.maxY + radius;
        }

        private List<AABB> candidates(AABB bounds) {
            candidateBuffer.clear();
            int stamp = ++candidateStamp;
            if (stamp == 0) {
                java.util.Arrays.fill(candidateMarks, 0);
                stamp = ++candidateStamp;
            }
            int minX = Mth.floor(bounds.minX);
            int minY = Mth.floor(bounds.minY);
            int minZ = Mth.floor(bounds.minZ);
            int maxX = Mth.floor(bounds.maxX);
            int maxY = Mth.floor(bounds.maxY);
            int maxZ = Mth.floor(bounds.maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        List<Integer> bin = bins.get(BlockPos.asLong(x, y, z));
                        if (bin == null) {
                            continue;
                        }
                        for (int index : bin) {
                            if (candidateMarks[index] != stamp) {
                                candidateMarks[index] = stamp;
                                candidateBuffer.add(volumes.get(index));
                            }
                        }
                    }
                }
            }
            return candidateBuffer;
        }

        private static Map<Long, List<Integer>> buildBins(List<AABB> volumes) {
            Map<Long, List<Integer>> result = new HashMap<>();
            for (int index = 0; index < volumes.size(); index++) {
                AABB volume = volumes.get(index);
                int minX = Mth.floor(volume.minX);
                int minY = Mth.floor(volume.minY);
                int minZ = Mth.floor(volume.minZ);
                int maxX = Mth.floor(volume.maxX - MIN_DISTANCE_SQUARED);
                int maxY = Mth.floor(volume.maxY - MIN_DISTANCE_SQUARED);
                int maxZ = Mth.floor(volume.maxZ - MIN_DISTANCE_SQUARED);
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            result.computeIfAbsent(BlockPos.asLong(x, y, z),
                                    ignored -> new ArrayList<>()).add(index);
                        }
                    }
                }
            }
            return result;
        }
    }

    private record LocalSpace(
            SableCompat.AffineTransform toLocal,
            SableCompat.AffineTransform toWorld,
            CollisionSpace collision) {
        Vec3 sweep(Vec3 from, Vec3 desired, double radius) {
            Vec3 localFrom = toLocal.toWorld(from);
            Vec3 localDesired = toLocal.toWorld(desired);
            Vec3 localResult = collision.sweep(localFrom, localDesired, radius);
            return toWorld.toWorld(localResult);
        }

        Vec3 project(Vec3 point, double radius) {
            Vec3 localPoint = toLocal.toWorld(point);
            Vec3 localResult = collision.project(localPoint, radius);
            return toWorld.toWorld(localResult);
        }
    }

    private record LocalMudSpace(
            SableCompat.AffineTransform toLocal,
            SableCompat.AffineTransform toWorld,
            MudSupportSpace support) {
        Vec3 support(Vec3 from, Vec3 desired, double radius) {
            Vec3 localFrom = toLocal.toWorld(from);
            Vec3 localDesired = toLocal.toWorld(desired);
            Vec3 localResult = support.support(localFrom, localDesired, radius);
            return toWorld.toWorld(localResult);
        }
    }

    /**
     * One-way swept-sphere collision adapted from Memento In Abyss' MIT
     * implementation. Spatial bins keep repeated solver queries bounded.
     */
    private static final class CollisionSpace {
        private final List<AABB> boxes;
        private final Map<Long, List<Integer>> bins;
        private final int[] candidateMarks;
        private final List<AABB> candidateBuffer = new ArrayList<>();
        private int candidateStamp;

        private CollisionSpace(List<AABB> boxes) {
            this.boxes = boxes.size() <= MAX_COLLISION_BOXES
                    ? List.copyOf(boxes)
                    : List.copyOf(boxes.subList(0, MAX_COLLISION_BOXES));
            this.candidateMarks = new int[this.boxes.size()];
            this.bins = buildBins(this.boxes);
        }

        private boolean isEmpty() {
            return boxes.isEmpty();
        }

        Vec3 sweep(Vec3 from, Vec3 desired, double radius) {
            Vec3 start = project(from, radius);
            Vec3 target = desired.add(start.subtract(from));
            return sweepDirect(start, target, radius);
        }

        private Vec3 sweepDirect(Vec3 start, Vec3 target, double radius) {
            Vec3 movement = target.subtract(start);
            double length = movement.length();
            if (length <= 1.0E-9D) {
                return project(target, radius);
            }

            AABB sweptBounds = new AABB(
                    Math.min(start.x, target.x), Math.min(start.y, target.y),
                    Math.min(start.z, target.z), Math.max(start.x, target.x),
                    Math.max(start.y, target.y), Math.max(start.z, target.z))
                    .inflate(radius);
            int samples = Mth.clamp((int) Math.ceil(
                    length / Math.max(radius * 0.4D, 1.0E-3D)), 1, 128);
            Hit hit = firstHit(start, movement, radius, samples, sweptBounds);
            if (hit == null) {
                return project(target, radius);
            }

            double travel = Math.max(0.0D,
                    hit.time() - CONTACT_EPSILON / length);
            Vec3 contact = start.add(movement.scale(travel));
            Vec3 remainder = target.subtract(contact);
            Vec3 normal = collisionNormal(hit.collider(), contact, movement);
            Vec3 tangent = remainder.subtract(normal.scale(remainder.dot(normal)));
            if (tangent.lengthSqr() <= MIN_DISTANCE_SQUARED) {
                return project(contact, radius);
            }

            // Allow one tangent continuation. A second hit stops it, so this
            // can slide along a face without turning into unrestricted corner
            // or multi-axis wall traversal.
            Vec3 slideStart = contact.add(normal.scale(CONTACT_EPSILON * 2.0D));
            double slideLength = tangent.length();
            AABB slideBounds = new AABB(
                    Math.min(slideStart.x, slideStart.x + tangent.x),
                    Math.min(slideStart.y, slideStart.y + tangent.y),
                    Math.min(slideStart.z, slideStart.z + tangent.z),
                    Math.max(slideStart.x, slideStart.x + tangent.x),
                    Math.max(slideStart.y, slideStart.y + tangent.y),
                    Math.max(slideStart.z, slideStart.z + tangent.z))
                    .inflate(radius);
            Hit slideHit = firstHit(slideStart, tangent, radius,
                    Mth.clamp((int) Math.ceil(slideLength
                            / Math.max(radius * 0.4D, 1.0E-3D)), 1, 128),
                    slideBounds);
            if (slideHit == null) {
                return project(slideStart.add(tangent), radius);
            }
            double slideTravel = Math.max(0.0D,
                    slideHit.time() - CONTACT_EPSILON
                            / Math.max(slideLength, CONTACT_EPSILON));
            return project(slideStart.add(tangent.scale(slideTravel)), radius);
        }

        private Hit firstHit(Vec3 start, Vec3 movement, double radius,
                int samples, AABB sweptBounds) {
            double earliest = Double.POSITIVE_INFINITY;
            AABB earliestCollider = null;
            for (AABB collider : candidates(sweptBounds)) {
                if (!lineIntersects(collider.inflate(radius), start, movement)) {
                    continue;
                }
                double hit = sweptSphereIntersection(
                        collider, start, movement, radius, samples);
                if (hit < earliest) {
                    earliest = hit;
                    earliestCollider = collider;
                }
            }
            return earliestCollider == null ? null
                    : new Hit(earliestCollider, earliest);
        }

        private static Vec3 collisionNormal(AABB collider, Vec3 point,
                Vec3 movement) {
            Vec3 closest = new Vec3(
                    Mth.clamp(point.x, collider.minX, collider.maxX),
                    Mth.clamp(point.y, collider.minY, collider.maxY),
                    Mth.clamp(point.z, collider.minZ, collider.maxZ));
            Vec3 normal = point.subtract(closest);
            if (normal.lengthSqr() > MIN_DISTANCE_SQUARED) {
                return normal.normalize();
            }
            double ax = Math.abs(movement.x);
            double ay = Math.abs(movement.y);
            double az = Math.abs(movement.z);
            if (ax >= ay && ax >= az) {
                return new Vec3(movement.x < 0.0D ? 1.0D : -1.0D,
                        0.0D, 0.0D);
            }
            if (ay >= az) {
                return new Vec3(0.0D, movement.y < 0.0D ? 1.0D : -1.0D,
                        0.0D);
            }
            return new Vec3(0.0D, 0.0D, movement.z < 0.0D ? 1.0D : -1.0D);
        }

        Vec3 project(Vec3 point, double radius) {
            Vec3 result = point;
            double queryRadius = radius + CONTACT_EPSILON;
            for (int pass = 0; pass < 4; pass++) {
                boolean changed = false;
                for (AABB collider : candidates(
                        new AABB(result, result).inflate(queryRadius))) {
                    Vec3 projected = projectFromBox(result, collider, radius);
                    if (!projected.equals(result)) {
                        result = projected;
                        changed = true;
                    }
                }
                if (!changed) {
                    break;
                }
            }
            return result;
        }

        private static Vec3 projectFromBox(Vec3 point, AABB collider, double radius) {
            double closestX = Mth.clamp(point.x, collider.minX, collider.maxX);
            double closestY = Mth.clamp(point.y, collider.minY, collider.maxY);
            double closestZ = Mth.clamp(point.z, collider.minZ, collider.maxZ);
            double dx = point.x - closestX;
            double dy = point.y - closestY;
            double dz = point.z - closestZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= radius * radius) {
                return point;
            }
            if (distanceSquared > MIN_DISTANCE_SQUARED) {
                double distance = Math.sqrt(distanceSquared);
                double correction = (radius + CONTACT_EPSILON - distance) / distance;
                return point.add(dx * correction, dy * correction, dz * correction);
            }

            double correction = collider.minX - radius - point.x - CONTACT_EPSILON;
            int axis = 0;
            double candidate = collider.maxX + radius - point.x + CONTACT_EPSILON;
            if (betterCorrection(candidate, correction)) {
                correction = candidate;
            }
            candidate = collider.minY - radius - point.y - CONTACT_EPSILON;
            if (betterCorrection(candidate, correction)) {
                correction = candidate;
                axis = 1;
            }
            candidate = collider.maxY + radius - point.y + CONTACT_EPSILON;
            if (betterCorrection(candidate, correction)) {
                correction = candidate;
                axis = 1;
            }
            candidate = collider.minZ - radius - point.z - CONTACT_EPSILON;
            if (betterCorrection(candidate, correction)) {
                correction = candidate;
                axis = 2;
            }
            candidate = collider.maxZ + radius - point.z + CONTACT_EPSILON;
            if (betterCorrection(candidate, correction)) {
                correction = candidate;
                axis = 2;
            }
            return axis == 0 ? point.add(correction, 0.0D, 0.0D)
                    : axis == 1 ? point.add(0.0D, correction, 0.0D)
                    : point.add(0.0D, 0.0D, correction);
        }

        private static boolean betterCorrection(double candidate, double current) {
            return Math.abs(candidate) + NORMAL_TIE_EPSILON < Math.abs(current);
        }

        private static double sweptSphereIntersection(AABB collider, Vec3 start,
                Vec3 movement, double radius, int sampleCount) {
            double outside = 0.0D;
            for (int sample = 1; sample <= sampleCount; sample++) {
                double fraction = sample / (double) sampleCount;
                Vec3 point = start.add(movement.scale(fraction));
                if (!sphereIntersects(collider, point, radius)) {
                    outside = fraction;
                    continue;
                }
                double inside = fraction;
                for (int iteration = 0; iteration < 10; iteration++) {
                    double middle = (outside + inside) * 0.5D;
                    if (sphereIntersects(collider,
                            start.add(movement.scale(middle)), radius)) {
                        inside = middle;
                    } else {
                        outside = middle;
                    }
                }
                return outside;
            }
            return Double.POSITIVE_INFINITY;
        }

        private record Hit(AABB collider, double time) {
        }

        private static boolean sphereIntersects(
                AABB collider, Vec3 point, double radius) {
            double closestX = Mth.clamp(point.x, collider.minX, collider.maxX);
            double closestY = Mth.clamp(point.y, collider.minY, collider.maxY);
            double closestZ = Mth.clamp(point.z, collider.minZ, collider.maxZ);
            double dx = point.x - closestX;
            double dy = point.y - closestY;
            double dz = point.z - closestZ;
            double penetratingRadius = Math.max(0.0D, radius - CONTACT_EPSILON);
            return dx * dx + dy * dy + dz * dz
                    < penetratingRadius * penetratingRadius;
        }

        private static boolean lineIntersects(AABB box, Vec3 start, Vec3 movement) {
            double enter = 0.0D;
            double exit = 1.0D;
            for (int axis = 0; axis < 3; axis++) {
                double origin = component(start, axis);
                double delta = component(movement, axis);
                double minimum = component(box, axis, false);
                double maximum = component(box, axis, true);
                if (Math.abs(delta) < MIN_DISTANCE_SQUARED) {
                    if (origin < minimum || origin > maximum) {
                        return false;
                    }
                    continue;
                }
                double first = (minimum - origin) / delta;
                double second = (maximum - origin) / delta;
                if (first > second) {
                    double swap = first;
                    first = second;
                    second = swap;
                }
                enter = Math.max(enter, first);
                exit = Math.min(exit, second);
                if (enter > exit) {
                    return false;
                }
            }
            return true;
        }

        private List<AABB> candidates(AABB bounds) {
            if (boxes.isEmpty()) {
                return List.of();
            }
            candidateBuffer.clear();
            int stamp = ++candidateStamp;
            if (stamp == 0) {
                java.util.Arrays.fill(candidateMarks, 0);
                stamp = ++candidateStamp;
            }
            int minX = Mth.floor(bounds.minX);
            int minY = Mth.floor(bounds.minY);
            int minZ = Mth.floor(bounds.minZ);
            int maxX = Mth.floor(bounds.maxX);
            int maxY = Mth.floor(bounds.maxY);
            int maxZ = Mth.floor(bounds.maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        List<Integer> bin = bins.get(BlockPos.asLong(x, y, z));
                        if (bin == null) {
                            continue;
                        }
                        for (int index : bin) {
                            if (candidateMarks[index] != stamp) {
                                candidateMarks[index] = stamp;
                                candidateBuffer.add(boxes.get(index));
                            }
                        }
                    }
                }
            }
            return candidateBuffer;
        }

        private static Map<Long, List<Integer>> buildBins(List<AABB> boxes) {
            Map<Long, List<Integer>> result = new HashMap<>();
            for (int index = 0; index < boxes.size(); index++) {
                AABB box = boxes.get(index);
                int minX = Mth.floor(box.minX);
                int minY = Mth.floor(box.minY);
                int minZ = Mth.floor(box.minZ);
                int maxX = Mth.floor(box.maxX - MIN_DISTANCE_SQUARED);
                int maxY = Mth.floor(box.maxY - MIN_DISTANCE_SQUARED);
                int maxZ = Mth.floor(box.maxZ - MIN_DISTANCE_SQUARED);
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            result.computeIfAbsent(BlockPos.asLong(x, y, z),
                                    ignored -> new ArrayList<>()).add(index);
                        }
                    }
                }
            }
            return result;
        }

        private static double component(Vec3 point, int axis) {
            return axis == 0 ? point.x : axis == 1 ? point.y : point.z;
        }

        private static double component(AABB box, int axis, boolean maximum) {
            if (axis == 0) {
                return maximum ? box.maxX : box.minX;
            }
            if (axis == 1) {
                return maximum ? box.maxY : box.minY;
            }
            return maximum ? box.maxZ : box.minZ;
        }
    }
}
