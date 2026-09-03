package com.fish.mirebound.tentacle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Builds the physical chain guide from the collision-safe swept trail behind
 * the controller and the still-untravelled navigation route ahead of it.
 */
final class TentacleGuidePath {
    private static final double EPSILON = 1.0E-10D;

    private TentacleGuidePath() {
    }

    static List<Vec3> compose(List<Vec3> trail, List<Vec3> plannedPath,
            Vec3 controller, double controllerDistance,
            TentacleCollisionSpace collision, double radius, double maximumLength) {
        if (trail.isEmpty()) {
            return List.of();
        }
        double lengthLimit = Math.max(EPSILON, maximumLength);
        double trailLength = TentacleChainSolver.pathLength(trail);
        if (trailLength >= lengthLimit - EPSILON) {
            return TentacleChainSolver.trimPath(trail, lengthLimit);
        }
        if (plannedPath.size() < 2) {
            return trail;
        }

        double plannedLength = TentacleChainSolver.pathLength(plannedPath);
        if (plannedLength <= EPSILON) {
            return trail;
        }
        double distance = Mth.clamp(controllerDistance, 0.0D, plannedLength);
        Vec3 projected = TentacleChainSolver.sampleAtDistance(plannedPath, distance);
        Vec3 trailEnd = trail.getLast();
        if (distance >= plannedLength - EPSILON
                && trailEnd.distanceToSqr(projected) <= EPSILON) {
            return trail;
        }

        // A route rebind can leave the controller slightly off its projection.
        // Only bridge that seam when the cached collision corridor says it is safe.
        boolean trailBridgeBlocked = collision != null
                && trailEnd.distanceToSqr(controller) > EPSILON
                && !collision.clear(trailEnd, controller, radius);
        boolean planBridgeBlocked = collision != null
                && controller.distanceToSqr(projected) > EPSILON
                && !collision.clear(controller, projected, radius);
        if (trailBridgeBlocked || planBridgeBlocked) {
            return trail;
        }

        ArrayList<Vec3> result = new ArrayList<>(
                trail.size() + Math.min(plannedPath.size(), 64) + 1);
        result.addAll(trail);
        double composedLength = trailLength;
        composedLength = appendBounded(
                result, controller, composedLength, lengthLimit);
        if (composedLength < 0.0D) {
            return List.copyOf(result);
        }
        composedLength = appendBounded(result, projected, composedLength, lengthLimit);
        if (composedLength < 0.0D) {
            return List.copyOf(result);
        }

        double traversed = 0.0D;
        for (int index = 1; index < plannedPath.size(); index++) {
            Vec3 from = plannedPath.get(index - 1);
            Vec3 to = plannedPath.get(index);
            double segmentLength = from.distanceTo(to);
            double segmentEnd = traversed + segmentLength;
            if (segmentEnd > distance + EPSILON) {
                composedLength = appendBounded(
                        result, to, composedLength, lengthLimit);
                if (composedLength < 0.0D) {
                    break;
                }
            }
            traversed = segmentEnd;
        }
        return List.copyOf(result);
    }

    private static double appendBounded(List<Vec3> points, Vec3 point,
            double currentLength, double maximumLength) {
        Vec3 previous = points.getLast();
        double distance = previous.distanceTo(point);
        if (distance <= EPSILON) {
            return currentLength;
        }
        double remaining = maximumLength - currentLength;
        if (remaining <= EPSILON) {
            return -1.0D;
        }
        if (distance >= remaining - EPSILON) {
            points.add(distance <= remaining + EPSILON
                    ? point : previous.lerp(point, remaining / distance));
            return -1.0D;
        }
        points.add(point);
        return currentLength + distance;
    }
}
