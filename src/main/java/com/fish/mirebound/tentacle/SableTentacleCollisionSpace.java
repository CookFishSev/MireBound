package com.fish.mirebound.tentacle;

import com.fish.mirebound.compat.sable.SableCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Combines world collision with live rigid transforms from nearby Sable sub-levels. */
final class SableTentacleCollisionSpace implements TentacleCollisionSpace {
    private static final double EPSILON = 1.0E-10D;
    private final TentacleCollisionSpace world;
    private final List<LocalSpace> subLevels;

    private SableTentacleCollisionSpace(TentacleCollisionSpace world, List<LocalSpace> subLevels) {
        this.world = world;
        this.subLevels = List.copyOf(subLevels);
    }

    static TentacleCollisionSpace capture(ServerLevel level, List<List<Vec3>> corridors,
            double padding, int maximumBlockSamples, TentacleCollisionSpace world) {
        if (!SableCompat.isLoaded()) {
            return world;
        }
        List<SableCompat.SubLevelCollisionGeometry> geometry =
                SableCompat.collisionGeometry(level, corridors, padding, maximumBlockSamples);
        if (geometry.isEmpty()) {
            return world;
        }
        List<LocalSpace> spaces = new ArrayList<>(geometry.size());
        for (SableCompat.SubLevelCollisionGeometry entry : geometry) {
            if (!entry.localBoxes().isEmpty()) {
                spaces.add(new LocalSpace(new SableTransform(entry.transform()),
                        new AabbTentacleCollisionSpace(entry.localBoxes(), 0.001D)));
            }
        }
        return combine(world, spaces);
    }

    static TentacleCollisionSpace combine(TentacleCollisionSpace world, List<LocalSpace> spaces) {
        return spaces.isEmpty() ? world : new SableTentacleCollisionSpace(world, spaces);
    }

    @Override
    public Vec3 move(Vec3 from, Vec3 desired, double radius) {
        Vec3 best = world.move(from, desired, radius);
        double bestProgress = progress(from, desired, best);
        for (LocalSpace local : subLevels) {
            Vec3 candidate = local.move(from, desired, radius);
            double progress = progress(from, desired, candidate);
            if (progress < bestProgress) {
                best = candidate;
                bestProgress = progress;
            }
        }
        return project(best, radius);
    }

    @Override
    public Vec3 project(Vec3 point, double radius) {
        Vec3 result = world.project(point, radius);
        for (int pass = 0; pass < 2; pass++) {
            for (LocalSpace local : subLevels) {
                result = local.project(result, radius);
            }
            result = world.project(result, radius);
        }
        return result;
    }

    @Override
    public boolean clear(Vec3 from, Vec3 to, double radius) {
        if (!world.clear(from, to, radius)) {
            return false;
        }
        for (LocalSpace local : subLevels) {
            if (!local.clear(from, to, radius)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean clear(Vec3 point, double radius) {
        if (!world.clear(point, radius)) {
            return false;
        }
        for (LocalSpace local : subLevels) {
            if (!local.clear(point, radius)) {
                return false;
            }
        }
        return true;
    }

    private static double progress(Vec3 from, Vec3 desired, Vec3 resolved) {
        Vec3 movement = desired.subtract(from);
        double lengthSquared = movement.lengthSqr();
        return lengthSquared <= EPSILON ? 0.0D
                : Math.max(0.0D, Math.min(1.0D,
                        resolved.subtract(from).dot(movement) / lengthSquared));
    }

    interface RigidTransform {
        Vec3 toLocal(Vec3 point);

        Vec3 toWorld(Vec3 point);
    }

    record LocalSpace(RigidTransform transform, AabbTentacleCollisionSpace collision) {
        Vec3 move(Vec3 from, Vec3 desired, double radius) {
            Vec3 localFrom = transform.toLocal(from);
            Vec3 localDesired = transform.toLocal(desired);
            if (localFrom == null || localDesired == null) {
                return desired;
            }
            Vec3 resolved = collision.move(localFrom, localDesired, radius);
            Vec3 world = transform.toWorld(resolved);
            return world == null ? desired : world;
        }

        Vec3 project(Vec3 point, double radius) {
            Vec3 localPoint = transform.toLocal(point);
            if (localPoint == null) {
                return point;
            }
            Vec3 resolved = collision.project(localPoint, radius);
            if (resolved.distanceToSqr(localPoint) <= EPSILON) {
                return point;
            }
            Vec3 world = transform.toWorld(resolved);
            return world == null ? point : world;
        }

        boolean clear(Vec3 from, Vec3 to, double radius) {
            Vec3 localFrom = transform.toLocal(from);
            Vec3 localTo = transform.toLocal(to);
            return localFrom == null || localTo == null || collision.clear(localFrom, localTo, radius);
        }

        boolean clear(Vec3 point, double radius) {
            Vec3 localPoint = transform.toLocal(point);
            return localPoint == null || collision.clear(localPoint, radius);
        }
    }

    private record SableTransform(SableCompat.RigidTransform delegate) implements RigidTransform {
        @Override
        public Vec3 toLocal(Vec3 point) {
            return delegate.toLocal(point);
        }

        @Override
        public Vec3 toWorld(Vec3 point) {
            return delegate.toWorld(point);
        }
    }
}
