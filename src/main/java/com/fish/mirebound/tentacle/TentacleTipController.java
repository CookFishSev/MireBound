package com.fish.mirebound.tentacle;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Acceleration-limited three-dimensional pursuit along a navigation corridor. */
final class TentacleTipController {
    private static final double EPSILON = 1.0E-10D;
    private Vec3 position = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;
    private double distance;
    private boolean initialized;

    void reset() {
        position = Vec3.ZERO;
        velocity = Vec3.ZERO;
        distance = 0.0D;
        initialized = false;
    }

    void reset(Vec3 point) {
        position = point;
        velocity = Vec3.ZERO;
        distance = 0.0D;
        initialized = true;
    }

    void rebind(List<Vec3> path, Vec3 referenceTip, boolean snapToReference) {
        if (!initialized || snapToReference) {
            position = referenceTip;
            if (snapToReference) {
                velocity = Vec3.ZERO;
            }
            initialized = true;
        }
        distance = closestDistanceAlong(path, position);
        distance = Mth.clamp(distance, 0.0D, TentacleChainSolver.pathLength(path));
    }

    Vec3 advance(List<Vec3> path, double maximumSpeed, double acceleration,
            double lookaheadDistance, Vec3 physicalTip, double maximumLead,
            TentacleCollisionSpace collision, double radius) {
        double length = TentacleChainSolver.pathLength(path);
        if (path.size() < 2 || length <= EPSILON) {
            velocity = approach(velocity, Vec3.ZERO, Math.max(1.0E-4D, acceleration));
            return position;
        }
        if (!initialized) {
            reset(path.getFirst());
        }

        double safeAcceleration = Math.max(1.0E-4D, acceleration);
        double safeLookahead = Math.max(radius, lookaheadDistance);
        double searchWindow = safeLookahead * 2.0D + Math.max(0.0D, maximumSpeed) * 4.0D;
        distance = closestDistanceAlong(path, position,
                Math.max(0.0D, distance - searchWindow * 0.35D),
                Math.min(length, distance + searchWindow));

        double remaining = Math.max(0.0D, length - distance);
        double brakingSpeed = discreteBrakingSpeed(remaining, safeAcceleration);
        double desiredSpeed = Math.min(Math.max(0.0D, maximumSpeed), brakingSpeed);
        Vec3 pursuit = TentacleChainSolver.sampleAtDistance(
                path, Math.min(length, distance + safeLookahead));
        Vec3 direction = pursuit.subtract(position);
        if (direction.lengthSqr() <= EPSILON) {
            direction = path.getLast().subtract(position);
        }
        Vec3 desiredVelocity = direction.lengthSqr() <= EPSILON
                ? Vec3.ZERO : direction.normalize().scale(desiredSpeed);
        velocity = approach(velocity, desiredVelocity, safeAcceleration);

        Vec3 desired = position.add(velocity);
        double leadLimit = Math.max(radius, maximumLead);
        desired = limitLeadWithoutRetreat(position, desired, physicalTip, leadLimit);
        Vec3 resolved = collision.move(position, desired, radius);
        velocity = resolved.subtract(position);
        position = resolved;
        distance = closestDistanceAlong(path, position,
                Math.max(0.0D, distance - safeLookahead),
                Math.min(length, distance + safeLookahead + Math.max(maximumSpeed, velocity.length()) * 2.0D));
        return position;
    }

    /**
     * Caps newly-created controller lead without teleporting an already exposed
     * controller backwards when the physical chain temporarily retreats.
     */
    static Vec3 limitLeadWithoutRetreat(Vec3 current, Vec3 desired,
            Vec3 physicalTip, double maximumLead) {
        double limit = Math.max(0.0D, maximumLead);
        Vec3 desiredLead = desired.subtract(physicalTip);
        if (desiredLead.lengthSqr() <= limit * limit) {
            return desired;
        }

        Vec3 currentLead = current.subtract(physicalTip);
        double currentDistance = currentLead.length();
        if (currentDistance <= limit + EPSILON) {
            return desiredLead.lengthSqr() <= EPSILON
                    ? physicalTip
                    : physicalTip.add(desiredLead.normalize().scale(limit));
        }

        // The physical chain, not the controller, created this excessive
        // separation. Permit motion that closes it, but never turn the lead cap
        // into a spring that drags the controller and trail toward the root.
        Vec3 movement = desired.subtract(current);
        Vec3 away = currentLead.scale(1.0D / currentDistance);
        double outward = movement.dot(away);
        Vec3 limited = outward > 0.0D
                ? desired.subtract(away.scale(outward)) : desired;
        Vec3 limitedLead = limited.subtract(physicalTip);
        double limitedDistance = limitedLead.length();
        return limitedDistance > currentDistance && limitedDistance > EPSILON
                ? physicalTip.add(limitedLead.scale(currentDistance / limitedDistance))
                : limited;
    }

    Vec3 position() {
        return position;
    }

    Vec3 velocity() {
        return velocity;
    }

    double distance() {
        return distance;
    }

    double speed() {
        return velocity.length();
    }

    boolean initialized() {
        return initialized;
    }

    private static double discreteBrakingSpeed(double remaining, double acceleration) {
        return (Math.sqrt(acceleration * acceleration + 8.0D * acceleration * remaining)
                - acceleration) * 0.5D;
    }

    private static Vec3 approach(Vec3 value, Vec3 target, double maximumDelta) {
        Vec3 delta = target.subtract(value);
        double length = delta.length();
        return length <= maximumDelta || length <= EPSILON
                ? target : value.add(delta.scale(maximumDelta / length));
    }

    static double closestDistanceAlong(List<Vec3> path, Vec3 point) {
        return closestDistanceAlong(path, point, 0.0D, TentacleChainSolver.pathLength(path));
    }

    static double closestDistanceAlong(List<Vec3> path, Vec3 point,
            double minimumDistance, double maximumDistance) {
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        double bestPathDistance = Mth.clamp(minimumDistance, 0.0D, maximumDistance);
        double traversed = 0.0D;
        for (int index = 1; index < path.size(); index++) {
            Vec3 from = path.get(index - 1);
            Vec3 to = path.get(index);
            Vec3 segment = to.subtract(from);
            double lengthSquared = segment.lengthSqr();
            double segmentLength = Math.sqrt(lengthSquared);
            double segmentStart = traversed;
            double segmentEnd = traversed + segmentLength;
            traversed = segmentEnd;
            if (segmentEnd < minimumDistance || segmentStart > maximumDistance) {
                continue;
            }
            double minimumAmount = segmentLength <= EPSILON ? 0.0D
                    : Mth.clamp((minimumDistance - segmentStart) / segmentLength, 0.0D, 1.0D);
            double maximumAmount = segmentLength <= EPSILON ? 0.0D
                    : Mth.clamp((maximumDistance - segmentStart) / segmentLength, 0.0D, 1.0D);
            double amount = lengthSquared < EPSILON ? 0.0D
                    : Mth.clamp(point.subtract(from).dot(segment) / lengthSquared,
                            minimumAmount, maximumAmount);
            Vec3 projected = from.add(segment.scale(amount));
            double distanceSquared = projected.distanceToSqr(point);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestPathDistance = segmentStart + segmentLength * amount;
            }
        }
        return bestPathDistance;
    }
}
