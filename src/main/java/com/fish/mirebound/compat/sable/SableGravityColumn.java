package com.fish.mirebound.compat.sable;

import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finite world-gravity overlap span through physicalized mud geometry. */
public final class SableGravityColumn {
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double SEARCH_STEP = 1.0D / 8.0D;
    private static final double MAXIMUM_SEARCH_DISTANCE = 64.0D;
    private static final int BOUNDARY_REFINEMENT_STEPS = 9;
    private static final int MAXIMUM_BLOCKS_PER_SAMPLE = 32;
    private static final double AXIS_MATCH_DOT = 0.99995D;

    private SableGravityColumn() {
    }

    public static Span resolve(Level level, SableCompat.RigidTransform transform,
            AABB worldItemBounds, Vec3 worldItemPosition) {
        if (level == null || transform == null
                || worldItemBounds == null || worldItemPosition == null) {
            return null;
        }
        SableOrientedBox itemBox = SableOrientedBox.fromWorldBounds(worldItemBounds, transform);
        Vec3 localUp = transform.toLocalDirection(WORLD_UP);
        Vec3 localOrigin = transform.toLocal(worldItemPosition);
        if (itemBox == null || localOrigin == null
                || localUp == null || localUp.lengthSqr() < 1.0E-8D) {
            return null;
        }
        localUp = localUp.normalize();
        VolumeLookup volumes = new VolumeLookup(level);
        Hit origin = volumes.overlap(itemBox, localUp);
        if (origin == null) {
            return null;
        }

        Boundary upper = scanBoundary(volumes, itemBox, localUp, 1.0D, origin);
        Boundary lower = scanBoundary(volumes, itemBox, localUp, -1.0D, origin);
        Vec3 surfacePosition = localOrigin.add(localUp.scale(upper.distance()));
        return new Span(
                localUp,
                surfacePosition,
                upper.distance(),
                upper.distance() + lower.distance(),
                upper.hit().pos(),
                upper.hit().medium());
    }

    private static Boundary scanBoundary(VolumeLookup volumes, SableOrientedBox originBox,
            Vec3 localUp, double direction, Hit originHit) {
        double lastDistance = 0.0D;
        Hit lastHit = originHit;
        for (double distance = SEARCH_STEP;
                distance <= MAXIMUM_SEARCH_DISTANCE + 1.0E-7D;
                distance += SEARCH_STEP) {
            Hit hit = volumes.overlap(
                    originBox.moved(localUp.scale(direction * distance)), localUp);
            if (hit != null) {
                lastDistance = distance;
                lastHit = hit;
                continue;
            }

            double inside = lastDistance;
            double outside = distance;
            for (int refinement = 0;
                    refinement < BOUNDARY_REFINEMENT_STEPS; refinement++) {
                double middle = (inside + outside) * 0.5D;
                Hit middleHit = volumes.overlap(
                        originBox.moved(localUp.scale(direction * middle)), localUp);
                if (middleHit == null) {
                    outside = middle;
                } else {
                    inside = middle;
                    lastHit = middleHit;
                }
            }
            return new Boundary(inside, lastHit);
        }
        return new Boundary(MAXIMUM_SEARCH_DISTANCE, lastHit);
    }

    private static double supportMaximum(AABB box, Vec3 axis) {
        Vec3 center = box.getCenter();
        double radius = Math.abs(axis.x) * box.getXsize() * 0.5D
                + Math.abs(axis.y) * box.getYsize() * 0.5D
                + Math.abs(axis.z) * box.getZsize() * 0.5D;
        return center.dot(axis) + radius;
    }

    private record Boundary(double distance, Hit hit) {
    }

    private record Hit(BlockPos pos, SinkingMedium medium) {
    }

    private record MudVolume(AABB box, SinkingMedium medium) {
    }

    /** Reuses block states and local shapes throughout one bounded boundary search. */
    private static final class VolumeLookup {
        private final Level level;
        private final Map<Long, List<MudVolume>> volumesByBlock = new HashMap<>();

        private VolumeLookup(Level level) {
            this.level = level;
        }

        private Hit overlap(SableOrientedBox itemBox, Vec3 localUp) {
            AABB search = itemBox.enclosingBounds().inflate(0.002D);
            BlockPos minimum = BlockPos.containing(search.minX, search.minY, search.minZ);
            BlockPos maximum = BlockPos.containing(search.maxX, search.maxY, search.maxZ);
            int sampled = 0;
            Hit best = null;
            double bestSurface = Double.NEGATIVE_INFINITY;
            for (BlockPos cursor : BlockPos.betweenClosed(minimum, maximum)) {
                if (++sampled > MAXIMUM_BLOCKS_PER_SAMPLE) {
                    break;
                }
                for (MudVolume volume : volumes(cursor)) {
                    if (!itemBox.intersects(volume.box())) {
                        continue;
                    }
                    double surface = supportMaximum(volume.box(), localUp);
                    if (surface > bestSurface) {
                        bestSurface = surface;
                        best = new Hit(cursor.immutable(), volume.medium());
                    }
                }
            }
            return best;
        }

        private List<MudVolume> volumes(BlockPos pos) {
            return volumesByBlock.computeIfAbsent(pos.asLong(), ignored -> loadVolumes(pos));
        }

        private List<MudVolume> loadVolumes(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
            if (medium == null || !MudMediumRuntime.enabled(level, pos, medium)) {
                return List.of();
            }
            return MudBlock.localShape(level, pos, state, medium).toAabbs().stream()
                    .map(shape -> new MudVolume(shape.move(pos), medium))
                    .toList();
        }
    }

    public record Span(
            Vec3 localUp,
            Vec3 surfaceLocalPosition,
            double initialDepth,
            double availableDepth,
            BlockPos surfacePos,
            SinkingMedium surfaceMedium) {

        public double depthAt(Vec3 localPosition) {
            return surfaceLocalPosition.subtract(localPosition).dot(localUp);
        }

        public Vec3 positionAtDepth(double depth) {
            return surfaceLocalPosition.subtract(localUp.scale(depth));
        }

        public Vec3 tangential(Vec3 localMotion) {
            return localMotion.subtract(localUp.scale(localMotion.dot(localUp)));
        }

        public boolean matches(SableCompat.RigidTransform transform) {
            if (transform == null) {
                return false;
            }
            Vec3 currentUp = transform.toLocalDirection(WORLD_UP);
            return currentUp != null && currentUp.lengthSqr() > 1.0E-8D
                    && currentUp.normalize().dot(localUp) >= AXIS_MATCH_DOT;
        }
    }
}
