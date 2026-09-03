package com.fish.mirebound.water;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Deterministic, bounded ballistic sampling shared by authoritative simulation and tests. */
public final class WaterGunBallistics {
    private static final int MAX_POINTS = 128;
    private static final double MAXIMUM_PATH_RANGE_MULTIPLIER = 3.0D;
    private static final double AIR_DRAG_PER_TICK = 0.08D;

    private WaterGunBallistics() {
    }

    public static List<Vec3> sample(Vec3 origin, Vec3 direction, WaterGunProfile profile) {
        if (direction.lengthSqr() <= 1.0E-8D || profile.pressure() <= 0.0D
                || profile.maximumRange() <= 0.0D) {
            return List.of(origin, origin);
        }
        double maximumRange = profile.maximumRange();
        double maximumPathLength = maximumRange * MAXIMUM_PATH_RANGE_MULTIPLIER;
        List<Vec3> points = new ArrayList<>(Math.min(MAX_POINTS,
                Mth.ceil(maximumPathLength / profile.segmentLength()) + 1));
        points.add(origin);
        Vec3 position = origin;
        Vec3 velocity = direction.normalize().scale(profile.pressure());
        double traveled = 0.0D;
        while (points.size() < MAX_POINTS && traveled < maximumPathLength) {
            double stepTime = Mth.clamp(
                    profile.segmentLength() / Math.max(0.05D, velocity.length()), 0.08D, 0.80D);
            Vec3 next = integratePosition(position, velocity, profile.gravity(), stepTime);
            double segment = position.distanceTo(next);
            if (segment > profile.segmentLength()) {
                double low = 0.0D;
                double high = stepTime;
                for (int iteration = 0; iteration < 12; iteration++) {
                    double middle = (low + high) * 0.5D;
                    Vec3 candidate = integratePosition(
                            position, velocity, profile.gravity(), middle);
                    if (position.distanceTo(candidate) > profile.segmentLength()) {
                        high = middle;
                    } else {
                        low = middle;
                    }
                }
                stepTime = low;
                next = integratePosition(position, velocity, profile.gravity(), stepTime);
                segment = position.distanceTo(next);
            }
            double remainingPath = maximumPathLength - traveled;
            if (segment > remainingPath) {
                next = position.lerp(next, remainingPath / segment);
                segment = remainingPath;
            }
            double exitFraction = sphereExitFraction(
                    position, next, origin, maximumRange);
            if (exitFraction >= 0.0D) {
                points.add(position.lerp(next, exitFraction));
                break;
            }
            points.add(next);
            traveled += segment;
            position = next;
            velocity = integrateVelocity(velocity, profile.gravity(), stepTime);
        }
        return List.copyOf(points);
    }

    private static Vec3 integratePosition(
            Vec3 position, Vec3 velocity, double gravity, double time) {
        double decay = Math.exp(-AIR_DRAG_PER_TICK * time);
        double velocityIntegral = (1.0D - decay) / AIR_DRAG_PER_TICK;
        double terminalSpeed = gravity / AIR_DRAG_PER_TICK;
        return position.add(
                velocity.x * velocityIntegral,
                (velocity.y + terminalSpeed) * velocityIntegral - terminalSpeed * time,
                velocity.z * velocityIntegral);
    }

    private static Vec3 integrateVelocity(Vec3 velocity, double gravity, double time) {
        double decay = Math.exp(-AIR_DRAG_PER_TICK * time);
        double terminalSpeed = gravity / AIR_DRAG_PER_TICK;
        return new Vec3(
                velocity.x * decay,
                (velocity.y + terminalSpeed) * decay - terminalSpeed,
                velocity.z * decay);
    }

    private static double sphereExitFraction(
            Vec3 from, Vec3 to, Vec3 center, double radius) {
        Vec3 start = from.subtract(center);
        Vec3 delta = to.subtract(from);
        double a = delta.lengthSqr();
        if (a <= 1.0E-12D) {
            return -1.0D;
        }
        double c = start.lengthSqr() - radius * radius;
        double endDistance = to.subtract(center).lengthSqr() - radius * radius;
        if (c > 1.0E-7D || endDistance < -1.0E-7D) {
            return -1.0D;
        }
        double b = 2.0D * start.dot(delta);
        double discriminant = b * b - 4.0D * a * c;
        if (discriminant < 0.0D) {
            return -1.0D;
        }
        double exit = (-b + Math.sqrt(discriminant)) / (2.0D * a);
        return exit >= 0.0D && exit <= 1.0D ? exit : -1.0D;
    }
}
