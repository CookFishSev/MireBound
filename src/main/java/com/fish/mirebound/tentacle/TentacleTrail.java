package com.fish.mirebound.tentacle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Persistent follow-the-leader centerline built from the tip's actual swept motion. */
final class TentacleTrail {
    private final List<Vec3> points = new ArrayList<>();
    private List<Vec3> cachedPath;
    private int unwrapClearTicks;
    private int rootUnwrapClearTicks;

    void reset(Vec3 root) {
        points.clear();
        points.add(root);
        cachedPath = null;
        unwrapClearTicks = 0;
        rootUnwrapClearTicks = 0;
    }

    void resetTo(List<Vec3> path, Vec3 root, Vec3 tip) {
        points.clear();
        if (path.size() >= 2) {
            points.addAll(path);
            points.set(0, root);
            points.set(points.size() - 1, tip);
        } else {
            points.add(root);
            if (tip.distanceToSqr(root) > 1.0E-10D) {
                points.add(tip);
            }
        }
        cachedPath = null;
        unwrapClearTicks = 0;
        rootUnwrapClearTicks = 0;
    }

    boolean resetToBounded(List<Vec3> path, Vec3 root, Vec3 tip,
            TentacleCollisionSpace collision, double radius, int maximumPoints) {
        List<Vec3> previousPoints = List.copyOf(points);
        List<Vec3> previousCachedPath = cachedPath;
        int previousUnwrapTicks = unwrapClearTicks;
        int previousRootUnwrapTicks = rootUnwrapClearTicks;
        resetTo(path, root, tip);
        compact(collision, radius, Math.max(3, maximumPoints));
        if (points.size() <= Math.max(3, maximumPoints)) {
            return true;
        }
        points.clear();
        points.addAll(previousPoints);
        cachedPath = previousCachedPath;
        unwrapClearTicks = previousUnwrapTicks;
        rootUnwrapClearTicks = previousRootUnwrapTicks;
        return false;
    }

    boolean record(Vec3 root, Vec3 tip, TentacleCollisionSpace collision,
            double radius, double sampleDistance, int unwrapTicks, int maximumPoints) {
        int pointLimit = Math.max(3, maximumPoints);
        if (points.isEmpty()) {
            reset(root);
        } else {
            points.set(0, root);
            cachedPath = null;
        }
        if (points.size() == 1) {
            if (tip.distanceToSqr(root) > 1.0E-10D) {
                points.add(tip);
            }
            return true;
        }

        int last = points.size() - 1;
        Vec3 stable = points.get(Math.max(0, last - 1));
        double spacing = Math.max(0.01D, sampleDistance);
        boolean canReplaceTail = points.get(last).distanceToSqr(tip) < spacing * spacing
                && collision.clear(stable, tip, radius);
        if (canReplaceTail) {
            points.set(last, tip);
        } else if (points.get(last).distanceToSqr(tip) > 1.0E-10D) {
            if (points.size() >= pointLimit) {
                compact(collision, radius, pointLimit - 1);
                last = points.size() - 1;
                stable = points.get(Math.max(0, last - 1));
                if (points.size() >= pointLimit) {
                    // A collision-safe tail replacement is preferable to growing without bound.
                    // If even that is impossible, let the caller rebind to the authoritative
                    // physical chain and request a low-frequency route refresh.
                    if (collision.clear(stable, tip, radius)) {
                        points.set(last, tip);
                        return true;
                    }
                    return false;
                }
            }
            points.add(tip);
        }

        if (points.size() >= 3) {
            int middle = points.size() - 2;
            Vec3 before = points.get(middle - 1);
            if (collision.clear(before, tip, radius)) {
                if (++unwrapClearTicks >= Math.max(1, unwrapTicks)) {
                    points.remove(middle);
                    points.set(points.size() - 1, tip);
                    unwrapClearTicks = 0;
                }
            } else {
                unwrapClearTicks = 0;
            }
        }
        // The same release at the root end. Without it the trail only ever forgets history next to
        // the tip, so a target that keeps circling an obstacle grows the wrap past the length the
        // body can span: the guide then gets truncated at its goal end and the tentacle follows a
        // route that stops in mid-air instead of continuing the pursuit.
        //
        // Line of sight from the root is what separates a stale wrap from a live one. While the
        // obstacle genuinely stands between the root and the rest of the trail this never fires and
        // the wrap is preserved in full; it only collapses the part of the detour that has already
        // become slack, which is what lets the body slide around the obstacle rather than
        // accumulate every lap.
        if (points.size() >= 3
                && collision.clear(points.getFirst(), points.get(2), radius)) {
            if (++rootUnwrapClearTicks >= Math.max(1, unwrapTicks)) {
                points.remove(1);
                rootUnwrapClearTicks = 0;
            }
        } else {
            rootUnwrapClearTicks = 0;
        }
        compact(collision, radius, pointLimit);
        return points.size() <= pointLimit;
    }

    List<Vec3> path() {
        if (cachedPath == null) {
            cachedPath = List.copyOf(points);
        }
        return cachedPath;
    }

    double length() {
        return TentacleChainSolver.pathLength(points);
    }

    int size() {
        return points.size();
    }

    private void compact(TentacleCollisionSpace collision, double radius, int maximumPoints) {
        while (points.size() > maximumPoints) {
            int candidate = -1;
            double bestSpan = Double.POSITIVE_INFINITY;
            for (int index = 1; index < points.size() - 1; index++) {
                Vec3 before = points.get(index - 1);
                Vec3 after = points.get(index + 1);
                double span = before.distanceTo(after);
                if (span < bestSpan && collision.clear(before, after, radius)) {
                    candidate = index;
                    bestSpan = span;
                }
            }
            if (candidate < 0) {
                return;
            }
            points.remove(candidate);
        }
    }
}
