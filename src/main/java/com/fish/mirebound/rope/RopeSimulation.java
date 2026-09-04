package com.fish.mirebound.rope;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Basic Verlet solver for fixed-length rope segments. */
public final class RopeSimulation {
    private static final double MIN_DISTANCE_SQUARED = 1.0E-12D;
    private static final double CONTACT_DEADBAND = 1.0E-4D;
    private static final double MAX_POINT_SPEED_PER_TICK = 1.5D;
    private static final double[] SEGMENT_CONTACT_SAMPLES = {0.25D, 0.5D, 0.75D};

    private final RopeProperties properties;
    private final int substeps;
    private final int constraintIterations;
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final double[] previousX;
    private final double[] previousY;
    private final double[] previousZ;
    private final boolean[] collisionContact;
    private final Vec3[] fixedTargets;
    private boolean hasFixedPoints;

    public RopeSimulation(RopeProperties properties, Vec3[] positions,
            Vec3[] velocitiesPerTick, int substeps, int constraintIterations) {
        this.properties = properties == null ? RopeProperties.DEFAULT : properties;
        if (positions == null || velocitiesPerTick == null
                || positions.length != this.properties.nodeCount()
                || velocitiesPerTick.length != positions.length) {
            throw new IllegalArgumentException("rope arrays do not match the rope properties");
        }
        if (substeps < 1 || constraintIterations < 1) {
            throw new IllegalArgumentException("rope solver passes must be positive");
        }
        this.substeps = substeps;
        this.constraintIterations = constraintIterations;
        int pointCount = positions.length;
        this.x = new double[pointCount];
        this.y = new double[pointCount];
        this.z = new double[pointCount];
        this.previousX = new double[pointCount];
        this.previousY = new double[pointCount];
        this.previousZ = new double[pointCount];
        this.collisionContact = new boolean[pointCount];
        this.fixedTargets = new Vec3[pointCount];

        for (int point = 0; point < pointCount; point++) {
            Vec3 position = finite(positions[point]) ? positions[point] : Vec3.ZERO;
            Vec3 velocity = finite(velocitiesPerTick[point])
                    ? limit(velocitiesPerTick[point], MAX_POINT_SPEED_PER_TICK)
                    : Vec3.ZERO;
            x[point] = position.x;
            y[point] = position.y;
            z[point] = position.z;
            previousX[point] = position.x - velocity.x / substeps;
            previousY[point] = position.y - velocity.y / substeps;
            previousZ[point] = position.z - velocity.z / substeps;
        }
    }

    public static RopeSimulation server(RopeProperties properties, Vec3[] positions,
            Vec3[] velocitiesPerTick) {
        return new RopeSimulation(properties, positions, velocitiesPerTick, 2, 8);
    }

    public void step(RopeCollisionWorld collision) {
        double timeStep = 1.0D / substeps;
        double velocityRetention = Math.pow(properties.velocityDamping(), timeStep);
        double gravityStep = properties.gravityPerTick() * timeStep * timeStep;
        boolean collisionActive = collision != null && !collision.isEmpty();
        pinFixedPoints();
        for (int substep = 0; substep < substeps; substep++) {
            Arrays.fill(collisionContact, false);
            pinFixedPoints();
            integrate(velocityRetention, gravityStep);

            for (int iteration = 0; iteration < constraintIterations; iteration++) {
                solveDistanceConstraints();
                if (collisionActive && ((iteration & 3) == 3
                        || iteration == constraintIterations - 1)) {
                    resolvePointCollisions(collision);
                    resolveSegmentCollisions(collision);
                }
                pinFixedPoints();
            }

            if (collisionActive && hasCollisionContact()) {
                for (int rigidityPass = 0; rigidityPass < 8; rigidityPass++) {
                    pinFixedPoints();
                    solveDistanceConstraints();
                }
                resolvePointCollisions(collision);
                resolveSegmentCollisions(collision);
            }
            pinFixedPoints();
            stabilizeCollisionContacts();
        }
    }

    private void integrate(double velocityRetention, double gravityStep) {
        for (int point = 0; point < x.length; point++) {
            if (fixedTargets[point] != null) {
                continue;
            }
            double currentX = x[point];
            double currentY = y[point];
            double currentZ = z[point];
            double velocityX = (currentX - previousX[point]) * velocityRetention;
            double velocityY = (currentY - previousY[point]) * velocityRetention;
            double velocityZ = (currentZ - previousZ[point]) * velocityRetention;
            previousX[point] = currentX;
            previousY[point] = currentY;
            previousZ[point] = currentZ;
            x[point] = currentX + velocityX;
            y[point] = currentY + velocityY - gravityStep;
            z[point] = currentZ + velocityZ;
        }
    }

    private void solveDistanceConstraints() {
        solveDistanceConstraints(0, x.length - 1);
    }

    private void solveDistanceConstraints(int firstSegment,
            int lastSegmentExclusive) {
        double targetLength = properties.segmentLength();
        int startSegment = Math.max(0, firstSegment);
        int endSegment = Math.min(x.length - 1, lastSegmentExclusive);
        for (int segment = startSegment; segment < endSegment; segment++) {
            int firstPoint = segment;
            int secondPoint = segment + 1;
            double dx = x[secondPoint] - x[firstPoint];
            double dy = y[secondPoint] - y[firstPoint];
            double dz = z[secondPoint] - z[firstPoint];
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= MIN_DISTANCE_SQUARED) {
                continue;
            }
            double firstWeight = fixedTargets[firstPoint] == null ? 1.0D : 0.0D;
            double secondWeight = fixedTargets[secondPoint] == null ? 1.0D : 0.0D;
            double denominator = firstWeight + secondWeight;
            if (denominator <= 0.0D) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            double correction = (distance - targetLength) / distance;
            double firstShare = firstWeight / denominator;
            double secondShare = secondWeight / denominator;
            x[firstPoint] += dx * correction * firstShare;
            y[firstPoint] += dy * correction * firstShare;
            z[firstPoint] += dz * correction * firstShare;
            x[secondPoint] -= dx * correction * secondShare;
            y[secondPoint] -= dy * correction * secondShare;
            z[secondPoint] -= dz * correction * secondShare;
        }
    }

    /** Repeats a bounded local solve for a rope tail that needs hard links. */
    public void enforceDistanceConstraints(int firstSegment,
            int segmentCount, int iterations) {
        int first = Math.max(0, firstSegment);
        int last = Math.min(x.length - 1, first + Math.max(0, segmentCount));
        int passes = Math.max(0, iterations);
        if (first >= last || passes == 0) {
            return;
        }
        for (int iteration = 0; iteration < passes; iteration++) {
            solveDistanceConstraints(first, last);
            pinFixedPoints();
        }
    }

    private void resolvePointCollisions(RopeCollisionWorld collision) {
        double radius = properties.collisionRadius();
        for (int point = 0; point < x.length; point++) {
            if (fixedTargets[point] != null) {
                continue;
            }
            Vec3 current = point(point);
            Vec3 previous = new Vec3(
                    previousX[point], previousY[point], previousZ[point]);
            Vec3 resolved = collision.sweep(previous, current, radius);
            if (resolved.distanceToSqr(current)
                    <= CONTACT_DEADBAND * CONTACT_DEADBAND) {
                continue;
            }
            setPoint(point, resolved);
            stopPoint(point);
            collisionContact[point] = true;
        }
    }

    private void resolveSegmentCollisions(RopeCollisionWorld collision) {
        double radius = properties.collisionRadius();
        for (int segment = 0; segment < x.length - 1; segment++) {
            Vec3 first = point(segment);
            Vec3 second = point(segment + 1);
            for (double fraction : SEGMENT_CONTACT_SAMPLES) {
                double inverseFraction = 1.0D - fraction;
                Vec3 sample = first.scale(inverseFraction).add(second.scale(fraction));
                Vec3 correction = collision.project(sample, radius).subtract(sample);
                double correctionLength = correction.length();
                if (correctionLength <= CONTACT_DEADBAND) {
                    continue;
                }
                if (correctionLength > radius) {
                    correction = correction.scale(radius / correctionLength);
                }

                double firstWeight = fixedTargets[segment] == null
                        ? inverseFraction : 0.0D;
                double secondWeight = fixedTargets[segment + 1] == null
                        ? fraction : 0.0D;
                double denominator = firstWeight * inverseFraction
                        + secondWeight * fraction;
                if (denominator <= 0.0D) {
                    continue;
                }
                applyCollisionCorrection(segment,
                        correction.scale(firstWeight / denominator));
                applyCollisionCorrection(segment + 1,
                        correction.scale(secondWeight / denominator));
            }
        }
    }

    private void applyCollisionCorrection(int point, Vec3 correction) {
        if (fixedTargets[point] != null || correction.lengthSqr() <= 0.0D) {
            return;
        }
        x[point] += correction.x;
        y[point] += correction.y;
        z[point] += correction.z;
        stopPoint(point);
        collisionContact[point] = true;
    }

    private void stabilizeCollisionContacts() {
        for (int point = 0; point < x.length; point++) {
            if (collisionContact[point] && fixedTargets[point] == null) {
                stopPoint(point);
            }
        }
    }

    public void dampFreeVelocities() {
        dampFreeVelocities(-1);
    }

    /** Damps the two free nodes directly attached to a grabbed segment. */
    public void dampFreeVelocities(int grabbedSegment) {
        dampFreeVelocities(grabbedSegment - 1, grabbedSegment + 2);
    }

    /** Damps the two free nodes directly attached to one rescue-held point. */
    public void dampFreeVelocitiesAroundPoint(int fixedPoint) {
        dampFreeVelocities(fixedPoint - 1, fixedPoint + 1);
    }

    private void dampFreeVelocities(int firstNeighbor, int secondNeighbor) {
        double damping = properties.dragVelocityDamping();
        for (int point = 0; point < x.length; point++) {
            if (fixedTargets[point] == null) {
                double pointDamping = point == firstNeighbor
                        || point == secondNeighbor
                        ? damping * 0.60D : damping;
                previousX[point] = x[point]
                        - (x[point] - previousX[point]) * pointDamping;
                previousY[point] = y[point]
                        - (y[point] - previousY[point]) * pointDamping;
                previousZ[point] = z[point]
                        - (z[point] - previousZ[point]) * pointDamping;
            }
        }
    }

    public void clearFixedPoints() {
        Arrays.fill(fixedTargets, null);
        hasFixedPoints = false;
    }

    public void fixPoint(int point, Vec3 target) {
        checkPoint(point);
        if (!finite(target)) {
            throw new IllegalArgumentException("fixed rope point must be finite");
        }
        fixedTargets[point] = target;
        hasFixedPoints = true;
        pinPoint(point, target);
    }

    private void pinFixedPoints() {
        if (!hasFixedPoints) {
            return;
        }
        for (int point = 0; point < fixedTargets.length; point++) {
            if (fixedTargets[point] != null) {
                pinPoint(point, fixedTargets[point]);
            }
        }
    }

    private void pinPoint(int point, Vec3 target) {
        setPoint(point, target);
        stopPoint(point);
    }

    public void resetVelocities() {
        for (int point = 0; point < x.length; point++) {
            stopPoint(point);
        }
    }

    public void resetVelocity(int point) {
        checkPoint(point);
        stopPoint(point);
    }

    public void setVelocity(int point, Vec3 velocity) {
        checkPoint(point);
        Vec3 used = finite(velocity) ? limit(velocity, MAX_POINT_SPEED_PER_TICK)
                : Vec3.ZERO;
        previousX[point] = x[point] - used.x / substeps;
        previousY[point] = y[point] - used.y / substeps;
        previousZ[point] = z[point] - used.z / substeps;
    }

    /** Removes only the velocity component moving into the supplied direction. */
    public void removeVelocityInto(int point, Vec3 direction) {
        checkPoint(point);
        if (fixedTargets[point] != null || !finite(direction)
                || direction.lengthSqr() <= MIN_DISTANCE_SQUARED) {
            return;
        }
        Vec3 axis = direction.normalize();
        Vec3 velocity = velocity(point);
        double into = velocity.dot(axis);
        if (into > 0.0D) {
            setVelocity(point, velocity.subtract(axis.scale(into)));
        }
    }

    public List<Vec3> positions() {
        Vec3[] points = positionArray();
        return List.copyOf(Arrays.asList(points));
    }

    public Vec3[] positionArray() {
        Vec3[] points = new Vec3[x.length];
        for (int point = 0; point < x.length; point++) {
            points[point] = point(point);
        }
        return points;
    }

    public Vec3[] velocityArrayPerTick() {
        Vec3[] velocities = new Vec3[x.length];
        for (int point = 0; point < x.length; point++) {
            velocities[point] = velocity(point);
        }
        return velocities;
    }

    public Vec3 point(int point) {
        checkPoint(point);
        return new Vec3(x[point], y[point], z[point]);
    }

    public Vec3 velocity(int point) {
        checkPoint(point);
        return new Vec3(
                (x[point] - previousX[point]) * substeps,
                (y[point] - previousY[point]) * substeps,
                (z[point] - previousZ[point]) * substeps);
    }

    public int pointCount() {
        return x.length;
    }

    public double maximumSegmentError() {
        double maximum = 0.0D;
        for (int segment = 0; segment < x.length - 1; segment++) {
            maximum = Math.max(maximum, Math.abs(
                    point(segment).distanceTo(point(segment + 1))
                            - properties.segmentLength()));
        }
        return maximum;
    }

    private void setPoint(int point, Vec3 value) {
        x[point] = value.x;
        y[point] = value.y;
        z[point] = value.z;
    }

    private void stopPoint(int point) {
        previousX[point] = x[point];
        previousY[point] = y[point];
        previousZ[point] = z[point];
    }

    private void checkPoint(int point) {
        if (point < 0 || point >= x.length) {
            throw new IndexOutOfBoundsException(point);
        }
    }

    private boolean hasCollisionContact() {
        for (boolean contact : collisionContact) {
            if (contact) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 limit(Vec3 value, double maximum) {
        double lengthSquared = value.lengthSqr();
        return lengthSquared > maximum * maximum
                ? value.scale(maximum / Math.sqrt(lengthSquared)) : value;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x)
                && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
