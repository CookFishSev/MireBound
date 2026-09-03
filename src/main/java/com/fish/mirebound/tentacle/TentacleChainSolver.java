package com.fish.mirebound.tentacle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class TentacleChainSolver {
    private static final double EPSILON = 1.0E-10D;
    /**
     * Pursuit may retract the active rest length so unused body length does not
     * have to coil beside the root. Keeping a lower bound preserves enough
     * segment spacing for the thick-body collision solver at very short range.
     */
    static final double MINIMUM_TRACKING_LENGTH_SCALE = 0.55D;
    /**
     * How many leading segments are held along the route's initial tangent, and how firmly. The
     * span is deliberately short: it must cover the joint the renderer reads for the root cap
     * without flattening the body's organic curve.
     */
    private static final int ROOT_ORIENTATION_SEGMENTS = 3;
    private static final double ROOT_ORIENTATION_STRENGTH = 0.45D;
    /**
     * Largest guide dead zone relative to the segment it governs. Beyond roughly half a segment the
     * dead zone starts suppressing corrections the segment could genuinely make.
     */
    private static final double MAXIMUM_DEAD_ZONE_RATIO = 0.55D;
    private final Vec3[] points;
    private final Vec3[] previous;
    private final Vec3[] stepStart;
    private final double[] stretchLambdas;
    private final double[] bendLambdas;
    private final Vec3[] curvatureCorrections;
    private Vec3 previousGuideDirection;
    private Vec3 previousGuideLateral;
    private boolean tipTerrainContact;
    private boolean stepRejected;
    private double guideCorrection;
    private double tipOrientationCorrection;
    private double rootOrientationCorrection;
    private double lengthCorrection;
    private double bendCorrection;
    private double curvatureCorrection;
    private double selfCollisionCorrection;
    private double terrainCorrection;
    private double stepLimitCorrection;
    private int terrainContacts;
    private int selfCollisionContacts;
    private double lastRestLength;
    private double lastGuideLength;
    private double lastCurveAmplitude;
    private double lastMuscleScale;
    private boolean diagnosticsEnabled;

    TentacleChainSolver(int pointCount, Vec3 root) {
        points = new Vec3[pointCount];
        previous = new Vec3[pointCount];
        stepStart = new Vec3[pointCount];
        stretchLambdas = new double[pointCount - 1];
        bendLambdas = new double[Math.max(0, pointCount - 2)];
        curvatureCorrections = new Vec3[pointCount];
        for (int index = 0; index < pointCount; index++) {
            Vec3 point = root.add(0.0D, index * 0.0125D, 0.0D);
            points[index] = point;
            previous[index] = point;
            stepStart[index] = point;
            curvatureCorrections[index] = Vec3.ZERO;
        }
    }

    int pointCount() {
        return points.length;
    }

    Vec3 point(int index) {
        return points[index];
    }

    AABB bounds(double padding) {
        double minX = points[0].x;
        double minY = points[0].y;
        double minZ = points[0].z;
        double maxX = minX;
        double maxY = minY;
        double maxZ = minZ;
        for (int index = 1; index < points.length; index++) {
            Vec3 point = points[index];
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(Math.max(0.0D, padding));
    }

    void displaceSegment(int segment, Vec3 displacement, double amount) {
        if (segment < 0 || segment >= points.length - 1 || amount <= 0.0D) {
            return;
        }
        Vec3 correction = displacement.scale(amount);
        if (segment > 0) {
            applyPositionCorrection(segment, correction.scale(0.45D));
        }
        applyPositionCorrection(segment + 1, correction.scale(segment == 0 ? 1.0D : 0.55D));
    }

    void displaceContact(int segment, double amount, Vec3 correction, boolean pinTip) {
        if (segment < 0 || segment >= points.length - 1
                || correction.lengthSqr() < EPSILON) {
            return;
        }
        double clamped = Mth.clamp(amount, 0.0D, 1.0D);
        int first = segment;
        int second = segment + 1;
        double firstWeight = movableForExternalContact(first, pinTip) ? 1.0D - clamped : 0.0D;
        double secondWeight = movableForExternalContact(second, pinTip) ? clamped : 0.0D;
        double totalWeight = firstWeight + secondWeight;
        if (totalWeight <= EPSILON) {
            return;
        }
        applyPositionCorrection(first, correction.scale(firstWeight / totalWeight));
        applyPositionCorrection(second, correction.scale(secondWeight / totalWeight));
    }

    double externalContactMobility(int segment, double amount, boolean pinTip) {
        if (segment < 0 || segment >= points.length - 1) {
            return 0.0D;
        }
        double clamped = Mth.clamp(amount, 0.0D, 1.0D);
        double first = movableForExternalContact(segment, pinTip) ? 1.0D - clamped : 0.0D;
        double second = movableForExternalContact(segment + 1, pinTip) ? clamped : 0.0D;
        return first + second;
    }

    void reprojectExternalCorrection(TentaclePhysicsProfile profile,
            TentacleCollisionSpace collision) {
        if (collision == null) {
            return;
        }
        solveCollisions(profile, collision);
    }

    private boolean movableForExternalContact(int index, boolean pinTip) {
        return index > 0 && (!pinTip || index < points.length - 1);
    }

    List<Vec3> snapshot() {
        return List.copyOf(Arrays.asList(points.clone()));
    }

    List<Vec3> previousSnapshot() {
        return List.copyOf(Arrays.asList(previous.clone()));
    }

    boolean restoreState(List<Vec3> savedPoints, List<Vec3> savedPrevious) {
        if (savedPoints == null || savedPrevious == null
                || savedPoints.size() != points.length
                || savedPrevious.size() != points.length) {
            return false;
        }
        for (int index = 0; index < points.length; index++) {
            Vec3 point = savedPoints.get(index);
            Vec3 prior = savedPrevious.get(index);
            if (!finite(point) || !finite(prior)) {
                return false;
            }
            points[index] = point;
            previous[index] = prior;
            stepStart[index] = point;
        }
        Arrays.fill(stretchLambdas, 0.0D);
        Arrays.fill(bendLambdas, 0.0D);
        Arrays.fill(curvatureCorrections, Vec3.ZERO);
        previousGuideDirection = null;
        previousGuideLateral = null;
        return true;
    }

    boolean tipTerrainContact() {
        return tipTerrainContact;
    }

    boolean stepRejected() {
        return stepRejected;
    }

    void setDiagnosticsEnabled(boolean enabled) {
        diagnosticsEnabled = enabled;
    }

    StepDiagnostics diagnostics() {
        double maximumStretchRatio = 0.0D;
        double arcLength = 0.0D;
        for (int index = 1; index < points.length; index++) {
            double length = points[index - 1].distanceTo(points[index]);
            arcLength += length;
            if (lastRestLength > EPSILON) {
                maximumStretchRatio = Math.max(maximumStretchRatio, length / lastRestLength);
            }
        }
        return new StepDiagnostics(
                guideCorrection, tipOrientationCorrection, rootOrientationCorrection,
                lengthCorrection, bendCorrection, curvatureCorrection,
                selfCollisionCorrection, terrainCorrection, stepLimitCorrection,
                terrainContacts, selfCollisionContacts,
                lastRestLength, lastGuideLength, lastCurveAmplitude, lastMuscleScale,
                maximumStretchRatio, arcLength, tipTerrainContact, stepRejected);
    }

    void initializeAlongPath(TentaclePhysicsProfile profile, List<Vec3> path,
            TentacleCollisionSpace collision, double visibleLength) {
        if (path.size() < 2) {
            return;
        }
        double length = Math.max(0.001D, visibleLength);
        for (int index = 0; index < points.length; index++) {
            double distance = length * index / Math.max(1.0D, points.length - 1.0D);
            Vec3 point = sampleAtDistance(path, distance);
            if (index > 0) {
                point = collision.project(point, radiusAt(profile, index));
            }
            points[index] = point;
            previous[index] = point;
            stepStart[index] = point;
        }
        Arrays.fill(stretchLambdas, 0.0D);
        Arrays.fill(bendLambdas, 0.0D);
        previousGuideDirection = null;
        previousGuideLateral = null;
    }

    void step(TentaclePhysicsProfile profile, Vec3 root, double extension, double lengthScale,
            List<Vec3> guidePath, TentacleCollisionSpace collision, double motionTime, long motionSeed) {
        step(profile, root, extension, lengthScale, guidePath,
                profile.tipAdvanceSpeed(), collision, motionTime, motionSeed, Vec3.ZERO);
    }

    void step(TentaclePhysicsProfile profile, Vec3 root, double extension, double lengthScale,
            List<Vec3> guidePath, double actuatorSpeed,
            TentacleCollisionSpace collision, double motionTime, long motionSeed) {
        step(profile, root, extension, lengthScale, guidePath, actuatorSpeed,
                collision, motionTime, motionSeed, Vec3.ZERO);
    }

    void step(TentaclePhysicsProfile profile, Vec3 root, double extension, double lengthScale,
            List<Vec3> guidePath, double actuatorSpeed,
            TentacleCollisionSpace collision, double motionTime, long motionSeed,
            Vec3 terminalDirection) {
        step(profile, root, extension, lengthScale, guidePath, actuatorSpeed,
                collision, motionTime, motionSeed, terminalDirection, 1.0D);
    }

    void step(TentaclePhysicsProfile profile, Vec3 root, double extension, double lengthScale,
            List<Vec3> guidePath, double actuatorSpeed,
            TentacleCollisionSpace collision, double motionTime, long motionSeed,
            Vec3 terminalDirection, double gravityScale) {
        step(profile, root, extension, lengthScale, guidePath, actuatorSpeed,
                collision, motionTime, motionSeed, terminalDirection, gravityScale, 0.0D);
    }

    void step(TentaclePhysicsProfile profile, Vec3 root, double extension, double lengthScale,
            List<Vec3> guidePath, double actuatorSpeed,
            TentacleCollisionSpace collision, double motionTime, long motionSeed,
            Vec3 terminalDirection, double gravityScale, double trackingGuideScale) {
        moveRoot(root);
        System.arraycopy(points, 0, stepStart, 0, points.length);
        tipTerrainContact = false;
        stepRejected = false;
        resetDiagnostics();
        double smoothExtension = Mth.clamp(extension, 0.0D, 1.0D);
        double smoothLengthScale = Mth.clamp(lengthScale, MINIMUM_TRACKING_LENGTH_SCALE,
                Math.max(1.0D, profile.trackingMaximumStretch()));
        double restLength = Math.max(0.0125D,
                profile.maximumLength() * smoothExtension * smoothLengthScale
                        / Math.max(1, points.length - 1));
        lastRestLength = restLength;
        int substeps = Math.max(1, profile.substeps());
        double timeStep = 1.0D / substeps;

        for (int substep = 0; substep < substeps; substep++) {
            predict(profile, collision, timeStep, gravityScale);
            guide(profile, guidePath, collision, restLength,
                    motionTime + substep * timeStep, motionSeed,
                    Math.max(0.001D, actuatorSpeed) / substeps,
                    trackingGuideScale);
            Arrays.fill(stretchLambdas, 0.0D);
            Arrays.fill(bendLambdas, 0.0D);
            for (int iteration = 0; iteration < profile.iterations(); iteration++) {
                solveBends(profile, restLength, timeStep);
                solveCurvature(profile);
                solveLengths(profile, restLength, timeStep);
                solveRootOrientation(profile, guidePath, collision, restLength,
                        Math.max(0.001D, actuatorSpeed) / (substeps * profile.iterations()));
                solveTipOrientation(profile, guidePath, collision, restLength,
                        Math.max(0.001D, actuatorSpeed) / (substeps * profile.iterations()),
                        terminalDirection);
                solveSelfCollisions(profile);
                solveCollisions(profile, collision);
                points[0] = root;
            }
        }
        limitStepDisplacement(profile, collision, Math.max(0.001D, actuatorSpeed));
        stabilizeExcessStretch(profile, collision, restLength, 1.0D);
        if (!finiteState()) {
            rejectStep();
        }
    }

    private void resetDiagnostics() {
        guideCorrection = 0.0D;
        tipOrientationCorrection = 0.0D;
        rootOrientationCorrection = 0.0D;
        lengthCorrection = 0.0D;
        bendCorrection = 0.0D;
        curvatureCorrection = 0.0D;
        selfCollisionCorrection = 0.0D;
        terrainCorrection = 0.0D;
        stepLimitCorrection = 0.0D;
        terrainContacts = 0;
        selfCollisionContacts = 0;
        lastGuideLength = 0.0D;
        lastCurveAmplitude = 0.0D;
        lastMuscleScale = 0.0D;
    }

    private void stabilizeExcessStretch(TentaclePhysicsProfile profile,
            TentacleCollisionSpace collision, double restLength, double stretchLimit) {
        double maximumSegmentLength = restLength * Math.max(1.0D, stretchLimit);
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 1; index < points.length; index++) {
                Vec3 parent = points[index - 1];
                Vec3 offset = points[index].subtract(parent);
                double distance = offset.length();
                if (!Double.isFinite(distance)) {
                    return;
                }
                if (distance <= maximumSegmentLength || distance <= EPSILON) {
                    continue;
                }
                Vec3 desired = parent.add(offset.scale(maximumSegmentLength / distance));
                Vec3 resolved = collision.move(points[index], desired, radiusAt(profile, index));
                recordTerrainCorrection(desired, resolved);
                if (!finite(resolved)) {
                    return;
                }
                if (parent.distanceToSqr(resolved) + EPSILON >= distance * distance) {
                    continue;
                }
                applyCollisionCorrection(index, resolved.subtract(points[index]));
            }
            solveCollisions(profile, collision);
        }
        Arrays.fill(stretchLambdas, 0.0D);
        Arrays.fill(bendLambdas, 0.0D);
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private boolean finiteState() {
        for (Vec3 point : points) {
            if (!finite(point)) {
                return false;
            }
        }
        return true;
    }

    private void rejectStep() {
        stepRejected = true;
        tipTerrainContact = true;
        for (int index = 0; index < points.length; index++) {
            points[index] = stepStart[index];
            previous[index] = stepStart[index];
        }
        Arrays.fill(stretchLambdas, 0.0D);
        Arrays.fill(bendLambdas, 0.0D);
    }

    private void moveRoot(Vec3 root) {
        Vec3 shift = root.subtract(points[0]);
        if (shift.lengthSqr() < EPSILON) {
            points[0] = root;
            previous[0] = root;
            return;
        }
        for (int index = 0; index < points.length; index++) {
            points[index] = points[index].add(shift);
            previous[index] = previous[index].add(shift);
        }
        points[0] = root;
        previous[0] = root;
    }

    private void predict(TentaclePhysicsProfile profile, TentacleCollisionSpace collision,
            double timeStep, double gravityScale) {
        previous[0] = points[0];
        double gravity = Mth.clamp(gravityScale, 0.0D, 1.0D);
        double substepDamping = substepDamping(
                profile.damping(), profile.substeps());
        for (int index = 1; index < points.length; index++) {
            Vec3 current = points[index];
            Vec3 velocity = current.subtract(previous[index]).scale(substepDamping);
            previous[index] = current;
            Vec3 desired = current.add(velocity).add(0.0D,
                    -profile.gravity() * gravity * timeStep * timeStep, 0.0D);
            Vec3 resolved = collision.move(current, desired, radiusAt(profile, index));
            recordTerrainCorrection(desired, resolved);
            points[index] = resolved;
            previous[index] = previous[index].add(resolved.subtract(desired));
            if (index == points.length - 1 && resolved.distanceToSqr(desired) > EPSILON) {
                tipTerrainContact = true;
            }
        }
    }

    private void guide(TentaclePhysicsProfile profile, List<Vec3> path,
            TentacleCollisionSpace collision, double restLength,
            double motionTime, long motionSeed, double maximumStep,
            double trackingGuideScale) {
        if (path.size() < 2 || profile.guideStrength() <= 0.0D) {
            return;
        }
        // Guide actuation runs once per substep, outside the XPBD constraint-iteration loop.
        // Normalize against substeps so changing solver iterations affects convergence only,
        // not the visible route-following strength.
        double perSubstep = substepGuideStrength(
                profile.guideStrength(), profile.substeps());
        double seedAngle = (motionSeed & 0xFFFFL) * (Math.PI * 2.0D / 65536.0D);
        double restingLength = restLength * Math.max(1, points.length - 1);
        double guideLength = Math.min(pathLength(path), restingLength);
        lastGuideLength = guideLength;
        Vec3 guideEnd = sampleAtDistance(path, guideLength);
        Vec3 guideDirection = guideEnd.subtract(path.getFirst());
        Vec3 normalizedGuide = guideDirection.lengthSqr() < EPSILON
                ? new Vec3(0.0D, 1.0D, 0.0D) : guideDirection.normalize();
        Vec3 lateral = continuousPerpendicular(normalizedGuide, seedAngle);
        Vec3 binormal = normalizedGuide.cross(lateral).normalize();
        double unusedLength = Math.sqrt(Math.max(0.0D,
                restingLength * restingLength - guideLength * guideLength));
        double tracking = Mth.clamp(trackingGuideScale, 0.0D, 1.0D);
        // The path itself already carries all collision-required bends. During
        // pursuit, adding another large lateral "slack" curve turns unused rest
        // length into an artificial root loop and prevents the chain from looking
        // taut even in open space. Idle and grab modes keep their organic curve.
        double curveAmplitude = unusedLength * profile.slackCurve() * (1.0D - tracking);
        lastCurveAmplitude = curveAmplitude;
        double linearSlackRatio = restingLength <= EPSILON ? 0.0D
                : Math.max(0.0D, restingLength - guideLength) / restingLength;
        // A taut tentacle must be allowed to become straight. Keeping the decorative
        // muscle wave at full strength near maximum reach makes the guide and the
        // constraints fight forever, which looks like a root-driven orbit. During
        // pursuit allow reduced muscle motion (20%) to preserve liveliness while
        // maintaining tip accuracy.
        double muscleScale = Mth.clamp(linearSlackRatio / 0.08D, 0.0D, 1.0D)
                * Mth.lerp(tracking, 1.0D, 0.20D);
        lastMuscleScale = muscleScale;
        for (int index = 1; index < points.length; index++) {
            double fraction = index / (double) (points.length - 1);
            // Distribute the whole body across the available route. Packing the
            // unused leading segments at distance zero makes many thick root
            // capsules share one point, so self-collision and length constraints
            // manufacture a persistent sideways force.
            double guideDistance = guideLength * fraction;
            Vec3 curve = Vec3.ZERO;
            if (curveAmplitude > EPSILON) {
                curve = lateral.scale(Math.sin(Math.PI * fraction) * curveAmplitude);
                double detailAmplitude = curveAmplitude;
                for (int harmonic = 2; harmonic <= profile.curveWaves(); harmonic++) {
                    detailAmplitude *= profile.curveDetail();
                    Vec3 axis = (harmonic & 1) == 0 ? binormal : lateral;
                    double sign = ((motionSeed >>> (harmonic + 7)) & 1L) == 0L ? 1.0D : -1.0D;
                    curve = curve.add(axis.scale(
                            Math.sin(Math.PI * harmonic * fraction) * detailAmplitude * sign));
                }
            }
            Vec3 pathTarget = sampleAtDistance(path, guideDistance);
            Vec3 target = pathTarget.add(curve);
            if (muscleScale > EPSILON && profile.muscleAmplitude() > EPSILON) {
                // Idle motion is strongest near the tip, which reads as a searching
                // sweep. Pursuit needs the opposite bias: the wave belongs in the
                // mid-body so the body undulates without dragging the tip off the
                // route it is trying to hit.
                double tipBias = Mth.lerp(tracking, 0.30D + fraction * 0.70D,
                        1.0D - fraction * 0.72D);
                double envelope = Math.sin(Math.PI * fraction) * tipBias;
                double wave = motionTime * profile.muscleSpeed()
                        + fraction * Math.PI * 3.30D + seedAngle;
                target = target
                        .add(lateral.scale(Math.sin(wave) * profile.muscleAmplitude()
                                * envelope * muscleScale))
                        .add(binormal.scale(Math.cos(wave * 0.63D)
                                * profile.muscleAmplitude() * envelope
                                * profile.curveDetail() * muscleScale));
            }
            double radius = radiusAt(profile, index);
            if (!collision.clear(pathTarget, target, radius)) {
                target = pathTarget;
            }
            double rootResponseLocal = Mth.clamp(fraction / 0.24D, 0.0D, 1.0D);
            double trackingRootResponse = tracking
                    * (1.0D - rootResponseLocal);
            double strengthEnvelope = Math.max(
                    0.20D + 0.80D * fraction * fraction,
                    trackingRootResponse * 0.58D);
            double strength = perSubstep * strengthEnvelope;
            if (index == points.length - 1) {
                strength = Math.min(0.85D, strength * 1.8D);
            }
            Vec3 projected = collision.project(target, radius);
            Vec3 offset = projected.subtract(points[index]);
            double distance = offset.length();
            double deadZone = radiusAt(profile, index) * profile.guideDeadZoneScale();
            // A radius-sized dead zone can be longer than the segment it governs on a thick
            // tentacle: at the default profile the root's is 0.239 against a 0.27 rest length,
            // 88% of the segment. Past that point the guide cannot express any direction the
            // segment could actually adopt, so inertia and the length constraint own it instead.
            // Bound every dead zone by its own segment regardless of mode; tracking then tightens
            // it further below. This is a positional bound, not an angular constraint.
            deadZone = Math.min(deadZone, restLength * MAXIMUM_DEAD_ZONE_RATIO);
            if (trackingRootResponse > 0.0D) {
                double trackingCap = restLength
                        * Mth.lerp(rootResponseLocal, 0.10D, 0.72D);
                deadZone = Mth.lerp(trackingRootResponse,
                        deadZone, Math.min(deadZone, trackingCap));
            }
            if (distance <= deadZone || distance <= EPSILON) {
                continue;
            }
            Vec3 outsideDeadZone = points[index].add(offset.scale((distance - deadZone) / distance));
            Vec3 guided = moveToward(points[index], outsideDeadZone, strength, maximumStep);
            Vec3 before = diagnosticsEnabled ? points[index] : null;
            applySweptActuation(profile, collision, index, guided, restLength);
            if (diagnosticsEnabled) {
                guideCorrection += before.distanceTo(points[index]);
            }
        }
    }

    private Vec3 continuousPerpendicular(Vec3 direction, double seedAngle) {
        Vec3 normalized = direction.lengthSqr() < EPSILON
                ? new Vec3(0.0D, 1.0D, 0.0D) : direction.normalize();
        Vec3 lateral;
        if (previousGuideDirection == null || previousGuideLateral == null) {
            lateral = perpendicular(normalized, seedAngle);
        } else {
            lateral = parallelTransport(previousGuideLateral, previousGuideDirection, normalized);
            lateral = lateral.subtract(normalized.scale(lateral.dot(normalized)));
            if (lateral.lengthSqr() < EPSILON) {
                lateral = perpendicular(normalized, seedAngle);
            } else {
                lateral = lateral.normalize();
            }
        }
        previousGuideDirection = normalized;
        previousGuideLateral = lateral;
        return lateral;
    }

    static Vec3 parallelTransport(Vec3 vector, Vec3 fromDirection, Vec3 toDirection) {
        Vec3 from = fromDirection.lengthSqr() < EPSILON
                ? new Vec3(0.0D, 1.0D, 0.0D) : fromDirection.normalize();
        Vec3 to = toDirection.lengthSqr() < EPSILON
                ? from : toDirection.normalize();
        Vec3 axis = from.cross(to);
        double cosine = Mth.clamp(from.dot(to), -1.0D, 1.0D);
        if (cosine <= -1.0D + 1.0E-8D) {
            return vector;
        }
        Vec3 firstOrder = axis.cross(vector);
        Vec3 secondOrder = axis.cross(firstOrder).scale(1.0D / (1.0D + cosine));
        return vector.add(firstOrder).add(secondOrder);
    }

    private static Vec3 perpendicular(Vec3 direction, double seedAngle) {
        Vec3 normalized = direction.lengthSqr() < EPSILON
                ? new Vec3(0.0D, 1.0D, 0.0D) : direction.normalize();
        Vec3 reference = new Vec3(Math.cos(seedAngle), 0.65D, Math.sin(seedAngle)).normalize();
        Vec3 perpendicular = reference.subtract(normalized.scale(reference.dot(normalized)));
        if (perpendicular.lengthSqr() < EPSILON) {
            reference = Math.abs(normalized.y) < 0.90D
                    ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
            perpendicular = reference.subtract(normalized.scale(reference.dot(normalized)));
        }
        return perpendicular.normalize();
    }

    private void solveLengths(TentaclePhysicsProfile profile, double restLength, double timeStep) {
        for (int index = 0; index < stretchLambdas.length; index++) {
            Vec3 firstBefore = diagnosticsEnabled ? points[index] : null;
            Vec3 secondBefore = diagnosticsEnabled ? points[index + 1] : null;
            stretchLambdas[index] = solveDistance(index, index + 1, restLength,
                    profile.stretchCompliance(), stretchLambdas[index], timeStep);
            if (diagnosticsEnabled) {
                lengthCorrection += firstBefore.distanceTo(points[index])
                        + secondBefore.distanceTo(points[index + 1]);
            }
        }
    }

    private void solveBends(TentaclePhysicsProfile profile, double restLength, double timeStep) {
        double minimumBendDistance = restLength * 2.0D * profile.bendRestRatio();
        for (int index = 0; index < bendLambdas.length; index++) {
            double fraction = (index + 1.0D) / Math.max(1.0D, bendLambdas.length);
            double flexibility = Mth.lerp(fraction * fraction,
                    1.0D, profile.tipBendFlexibility());
            Vec3 firstBefore = diagnosticsEnabled ? points[index] : null;
            Vec3 secondBefore = diagnosticsEnabled ? points[index + 2] : null;
            bendLambdas[index] = solveMinimumDistance(index, index + 2, minimumBendDistance,
                    profile.bendCompliance() * flexibility, bendLambdas[index], timeStep);
            if (diagnosticsEnabled) {
                bendCorrection += firstBefore.distanceTo(points[index])
                        + secondBefore.distanceTo(points[index + 2]);
            }
        }
    }

    /**
     * One-sided XPBD bend guard. The two-hop chord may be straighter than the
     * configured ratio; only an over-folded joint is corrected.
     */
    private double solveMinimumDistance(int first, int second, double minimumDistance,
            double compliance, double lambda, double timeStep) {
        if (points[first].distanceToSqr(points[second])
                >= minimumDistance * minimumDistance) {
            return 0.0D;
        }
        return solveDistance(first, second, minimumDistance,
                compliance, Math.max(0.0D, lambda), timeStep);
    }

    /**
     * Owns the direction of the first few segments, the way {@link #solveTipOrientation} owns the
     * last few.
     *
     * <p>Every other constraint leaves the root direction undetermined. {@code points[0]} is pinned
     * to the anchor by position only; gravity, the guide, collision and self-collision all skip
     * index 0; and the bend guard is a one-sided <em>minimum</em> two-hop distance, so it resists
     * over-folding but never prefers one direction over another. That is tolerable while the route
     * is at least as long as the chain, because the guide then spaces the body at roughly its rest
     * length and the first segment has nowhere to go but along the route.
     *
     * <p>It breaks as soon as the route is shorter than the chain. The guide places body point
     * {@code i} at {@code guideLength * i/(n-1)} along the route while the length constraint holds
     * every segment at {@code restLength}, so the surplus length has to buckle somewhere. With no
     * constraint expressing a preference, the buckle lands on the very first joint and the root
     * segment settles pointing away from the route — in practice steeply downward. Because
     * {@code ProceduralTentacleRenderer} builds the root cap along {@code -(points[1] - points[0])},
     * an inverted first segment is directly visible as a root cap aimed at the ground regardless of
     * where the tentacle is reaching.
     *
     * <p>Anchoring the leading segments to the route's own initial tangent leaves the surplus to
     * buckle in the middle of the body, which is both what a real tentacle does and what the
     * renderer can display correctly.
     */
    private void solveRootOrientation(TentaclePhysicsProfile profile, List<Vec3> path,
            TentacleCollisionSpace collision, double restLength, double maximumStep) {
        if (points.length < 3 || path.size() < 2) {
            return;
        }
        int segmentCount = Math.min(ROOT_ORIENTATION_SEGMENTS, points.length - 1);
        double restingLength = restLength * Math.max(1, points.length - 1);
        double routeLength = Math.min(pathLength(path), restingLength);
        double lookahead = Math.min(routeLength, restLength * segmentCount);
        if (lookahead <= EPSILON) {
            return;
        }
        Vec3 tangent = sampleAtDistance(path, lookahead).subtract(points[0]);
        if (tangent.lengthSqr() < EPSILON) {
            return;
        }
        tangent = tangent.normalize();
        double baseStrength = 1.0D - Math.pow(1.0D - ROOT_ORIENTATION_STRENGTH,
                1.0D / Math.max(1, profile.iterations()));
        for (int index = 1; index <= segmentCount; index++) {
            // Strongest at the joint the renderer reads, fading out along the body so the
            // organic curve further up is untouched.
            double fromRoot = (index - 1.0D) / segmentCount;
            double falloff = (1.0D - fromRoot) * (1.0D - fromRoot);
            Vec3 target = points[0].add(tangent.scale(restLength * index));
            target = collision.project(target, radiusAt(profile, index));
            Vec3 oriented = moveToward(points[index], target, baseStrength * falloff,
                    maximumStep * (0.45D + 0.55D * falloff));
            Vec3 before = diagnosticsEnabled ? points[index] : null;
            applySweptActuation(profile, collision, index, oriented, restLength);
            if (diagnosticsEnabled) {
                rootOrientationCorrection += before.distanceTo(points[index]);
            }
        }
    }

    private void solveCurvature(TentaclePhysicsProfile profile) {
        if (points.length < 3 || profile.curvatureSmoothing() <= 0.0D) {
            return;
        }
        Arrays.fill(curvatureCorrections, Vec3.ZERO);
        double passStrength = 1.0D - Math.pow(1.0D - profile.curvatureSmoothing(),
                1.0D / Math.max(1, profile.substeps() * profile.iterations()));
        for (int center = 1; center < points.length - 1; center++) {
            int before = center - 1;
            int after = center + 1;
            double beforeWeight = before == 0 ? 0.0D : 1.0D;
            double denominator = beforeWeight + 5.0D;
            Vec3 curvature = points[before].subtract(points[center].scale(2.0D)).add(points[after]);
            Vec3 lambda = curvature.scale(passStrength / denominator);
            if (beforeWeight > 0.0D) {
                curvatureCorrections[before] = curvatureCorrections[before].subtract(lambda);
            }
            curvatureCorrections[center] = curvatureCorrections[center].add(lambda.scale(2.0D));
            curvatureCorrections[after] = curvatureCorrections[after].subtract(lambda);
        }
        for (int index = 1; index < points.length; index++) {
            if (diagnosticsEnabled) {
                curvatureCorrection += curvatureCorrections[index].length();
            }
            applyPositionCorrection(index, curvatureCorrections[index]);
        }
    }

    private void solveTipOrientation(TentaclePhysicsProfile profile, List<Vec3> path,
            TentacleCollisionSpace collision, double restLength, double maximumStep,
            Vec3 terminalDirection) {
        if (points.length < 3 || path.size() < 2 || profile.tipOrientationStrength() <= 0.0D) {
            return;
        }
        double restingLength = restLength * Math.max(1, points.length - 1);
        double pathLength = Math.min(pathLength(path), restingLength);
        Vec3 pathEnd = sampleAtDistance(path, pathLength);
        int segmentCount = Math.min(profile.tipOrientationSegments(), points.length - 1);
        double lookback = Math.min(pathLength, restLength * segmentCount);
        Vec3 tangent = pathEnd.subtract(sampleAtDistance(path, pathLength - lookback));
        if (tangent.lengthSqr() < EPSILON) {
            return;
        }
        tangent = tangent.normalize();
        double terminalStrength = 0.0D;
        if (terminalDirection != null && terminalDirection.lengthSqr() > EPSILON) {
            terminalStrength = Mth.clamp(terminalDirection.length(), 0.0D, 1.0D);
            Vec3 constrained = terminalDirection.normalize();
            Vec3 blended = tangent.scale(1.0D - terminalStrength)
                    .add(constrained.scale(terminalStrength));
            if (blended.lengthSqr() > EPSILON) {
                tangent = blended.normalize();
            }
        }
        int tip = points.length - 1;
        int first = Math.max(1, tip - segmentCount + 1);
        double baseStrength = 1.0D - Math.pow(1.0D - profile.tipOrientationStrength(),
                1.0D / Math.max(1, profile.iterations()));
        for (int index = first; index <= tip; index++) {
            double fromTip = tip - index;
            double offset = restLength * fromTip;
            Vec3 pathTarget = sampleAtDistance(path, Math.max(0.0D, pathLength - offset));
            Vec3 tangentTarget = pathEnd.subtract(tangent.scale(offset));
            double tailFraction = (index - first + 1.0D) / (tip - first + 1.0D);
            double tangentBlend = Mth.lerp(terminalStrength,
                    0.35D + 0.50D * tailFraction, 1.0D);
            Vec3 target = pathTarget.lerp(tangentTarget, tangentBlend);
            target = collision.project(target, radiusAt(profile, index));
            double strength = Mth.lerp(terminalStrength,
                    baseStrength * tailFraction * tailFraction, 1.0D);
            Vec3 oriented = moveToward(points[index], target, strength,
                    maximumStep * (0.45D + 0.55D * tailFraction));
            Vec3 before = diagnosticsEnabled ? points[index] : null;
            applySweptActuation(profile, collision, index, oriented, restLength);
            if (diagnosticsEnabled) {
                tipOrientationCorrection += before.distanceTo(points[index]);
            }
        }
    }

    private void applySweptActuation(TentaclePhysicsProfile profile,
            TentacleCollisionSpace collision, int index, Vec3 desired, double restLength) {
        Vec3 current = points[index];
        double maximumSegmentLength = restLength * Math.max(1.0D, profile.solverStretchLimit());
        Vec3 strainLimited = preventIncreasingStrain(index, desired, maximumSegmentLength);
        Vec3 resolved = collision.move(current, strainLimited, radiusAt(profile, index));
        recordTerrainCorrection(strainLimited, resolved);
        if (finite(resolved)) {
            applyActuationCorrection(profile, index, resolved.subtract(current));
        }
    }

    private Vec3 preventIncreasingStrain(int index, Vec3 desired, double maximumSegmentLength) {
        Vec3 limited = preventIncreasingNeighborStrain(
                points[index], desired, points[index - 1], maximumSegmentLength);
        if (index + 1 < points.length) {
            limited = preventIncreasingNeighborStrain(
                    points[index], limited, points[index + 1], maximumSegmentLength);
        }
        return limited;
    }

    private static Vec3 preventIncreasingNeighborStrain(Vec3 current, Vec3 desired,
            Vec3 neighbor, double maximumSegmentLength) {
        Vec3 fromNeighbor = current.subtract(neighbor);
        double currentDistance = fromNeighbor.length();
        if (currentDistance < maximumSegmentLength || currentDistance <= EPSILON) {
            return desired;
        }
        Vec3 movement = desired.subtract(current);
        Vec3 away = fromNeighbor.scale(1.0D / currentDistance);
        double outwardMovement = movement.dot(away);
        return outwardMovement > 0.0D
                ? desired.subtract(away.scale(outwardMovement))
                : desired;
    }

    private void solveSelfCollisions(TentaclePhysicsProfile profile) {
        if (!profile.selfCollisionEnabled() || profile.selfCollisionResponse() <= 0.0D) {
            return;
        }
        for (int first = 0; first < points.length - 1; first++) {
            for (int second = first + 3; second < points.length - 1; second++) {
                double firstRadius = (radiusAt(profile, first) + radiusAt(profile, first + 1)) * 0.5D;
                double secondRadius = (radiusAt(profile, second) + radiusAt(profile, second + 1)) * 0.5D;
                double minimum = (firstRadius + secondRadius) * profile.selfCollisionRadiusScale();
                double alongBodyGap = (second - first - 1)
                        * Math.max(EPSILON, lastRestLength);
                if (alongBodyGap <= minimum + profile.collisionSlop()) {
                    continue;
                }
                TentacleEntityCollider.SegmentPair pair = TentacleEntityCollider.closestPoints(
                        points[first], points[first + 1], points[second], points[second + 1]);
                Vec3 delta = pair.second().subtract(pair.first());
                double distance = delta.length();
                if (distance >= minimum) {
                    continue;
                }
                Vec3 normal;
                if (distance > EPSILON) {
                    normal = delta.scale(1.0D / distance);
                } else {
                    normal = fallbackSelfCollisionNormal(first, second);
                }
                double correction = (minimum - distance) * profile.selfCollisionResponse();
                if (diagnosticsEnabled) {
                    selfCollisionCorrection += correction;
                    selfCollisionContacts++;
                }
                if (first == 0) {
                    displaceSelfCollisionSegment(second, normal.scale(correction));
                } else {
                    Vec3 half = normal.scale(correction * 0.5D);
                    displaceSelfCollisionSegment(first, half.scale(-1.0D));
                    displaceSelfCollisionSegment(second, half);
                }
            }
        }
    }

    private void displaceSelfCollisionSegment(int segment, Vec3 correction) {
        if (segment > 0) {
            applyPositionCorrection(segment, correction);
        }
        applyPositionCorrection(segment + 1, correction);
    }

    private Vec3 fallbackSelfCollisionNormal(int first, int second) {
        Vec3 firstTangent = localTangent(first);
        Vec3 secondTangent = localTangent(second);
        Vec3 normal = firstTangent.cross(secondTangent);
        if (normal.lengthSqr() < EPSILON) {
            normal = perpendicular(firstTangent, (first * 31L + second * 17L) * 0.37D);
        }
        return normal.normalize();
    }

    private Vec3 localTangent(int index) {
        Vec3 before = points[Math.max(0, index - 1)];
        Vec3 after = points[Math.min(points.length - 1, index + 1)];
        Vec3 tangent = after.subtract(before);
        return tangent.lengthSqr() < EPSILON ? new Vec3(0.0D, 1.0D, 0.0D) : tangent.normalize();
    }

    private static Vec3 moveToward(Vec3 current, Vec3 target,
            double strength, double maximumDistance) {
        Vec3 movement = target.subtract(current).scale(Mth.clamp(strength, 0.0D, 1.0D));
        double length = movement.length();
        return length > maximumDistance && length > EPSILON
                ? current.add(movement.scale(maximumDistance / length))
                : current.add(movement);
    }

    static double substepDamping(double damping, int substeps) {
        return Math.pow(Mth.clamp(damping, 0.0D, 1.0D),
                1.0D / Math.max(1, substeps));
    }

    static double substepGuideStrength(double strength, int substeps) {
        return 1.0D - Math.pow(1.0D - Mth.clamp(strength, 0.0D, 1.0D),
                1.0D / Math.max(1, substeps));
    }

    private double solveDistance(int first, int second, double restLength,
            double compliance, double lambda, double timeStep) {
        Vec3 delta = points[second].subtract(points[first]);
        double distance = delta.length();
        if (distance < EPSILON) {
            return lambda;
        }
        double firstWeight = first == 0 ? 0.0D : 1.0D;
        double secondWeight = 1.0D;
        double alpha = compliance / (timeStep * timeStep);
        double deltaLambda = (-(distance - restLength) - alpha * lambda)
                / (firstWeight + secondWeight + alpha);
        Vec3 direction = delta.scale(1.0D / distance);
        if (firstWeight > 0.0D) {
            applyPositionCorrection(first, direction.scale(-firstWeight * deltaLambda));
        }
        applyPositionCorrection(second, direction.scale(secondWeight * deltaLambda));
        return lambda + deltaLambda;
    }

    private void solveCollisions(TentaclePhysicsProfile profile, TentacleCollisionSpace collision) {
        for (int index = 1; index < points.length; index++) {
            Vec3 before = points[index];
            Vec3 resolved = collision.project(before, radiusAt(profile, index));
            recordTerrainCorrection(before, resolved);
            applyCollisionCorrection(index, resolved.subtract(before));
        }
        for (int index = 0; index < points.length - 1; index++) {
            double radius = Math.max(radiusAt(profile, index), radiusAt(profile, index + 1));
            if (!collision.clear(points[index], points[index + 1], radius)) {
                Vec3 before = points[index + 1];
                Vec3 resolved = collision.move(points[index], before, radius);
                recordTerrainCorrection(before, resolved);
                applyCollisionCorrection(index + 1, resolved.subtract(before));
            }
        }
    }

    private void applyPositionCorrection(int index, Vec3 correction) {
        if (correction.lengthSqr() < EPSILON) {
            return;
        }
        points[index] = points[index].add(correction);
        previous[index] = previous[index].add(correction);
    }

    private void applyActuationCorrection(TentaclePhysicsProfile profile,
            int index, Vec3 correction) {
        if (correction.lengthSqr() < EPSILON) {
            return;
        }
        points[index] = points[index].add(correction);
        double fraction = index / (double) Math.max(1, points.length - 1);
        double rootAbsorption = fraction * fraction * (3.0D - 2.0D * fraction);
        double inertiaTransfer = profile.guideInertiaTransfer() * rootAbsorption;
        previous[index] = previous[index].add(
                correction.scale(1.0D - inertiaTransfer));
    }

    private void applyCollisionCorrection(int index, Vec3 correction) {
        if (index == points.length - 1 && correction.lengthSqr() >= EPSILON) {
            tipTerrainContact = true;
        }
        applyPositionCorrection(index, correction);
    }

    private void limitStepDisplacement(TentaclePhysicsProfile profile,
            TentacleCollisionSpace collision, double maximumDistance) {
        for (int index = 1; index < points.length; index++) {
            Vec3 movement = points[index].subtract(stepStart[index]);
            double distance = movement.length();
            if (distance > maximumDistance && distance > EPSILON) {
                Vec3 limited = stepStart[index].add(movement.scale(maximumDistance / distance));
                Vec3 resolved = collision.move(points[index], limited, radiusAt(profile, index));
                if (diagnosticsEnabled) {
                    stepLimitCorrection += points[index].distanceTo(limited);
                }
                recordTerrainCorrection(limited, resolved);
                applyCollisionCorrection(index, resolved.subtract(points[index]));
            }
        }
        solveCollisions(profile, collision);
        points[0] = stepStart[0];
        previous[0] = stepStart[0];
    }

    private void recordTerrainCorrection(Vec3 desired, Vec3 resolved) {
        if (!diagnosticsEnabled) {
            return;
        }
        double correction = desired.distanceTo(resolved);
        if (correction <= EPSILON) {
            return;
        }
        terrainCorrection += correction;
        terrainContacts++;
    }

    double radiusAt(TentaclePhysicsProfile profile, int index) {
        return profile.radiusAt(index / (double) (points.length - 1));
    }

    static double pathLength(List<Vec3> path) {
        double length = 0.0D;
        for (int index = 1; index < path.size(); index++) {
            length += path.get(index - 1).distanceTo(path.get(index));
        }
        return length;
    }

    static List<Vec3> trimPath(List<Vec3> path, double maximumLength) {
        if (path.size() < 2 || maximumLength <= 0.0D) {
            return path;
        }
        List<Vec3> result = new ArrayList<>();
        result.add(path.getFirst());
        double remaining = maximumLength;
        for (int index = 1; index < path.size(); index++) {
            Vec3 from = path.get(index - 1);
            Vec3 to = path.get(index);
            double length = from.distanceTo(to);
            if (length <= remaining) {
                result.add(to);
                remaining -= length;
                continue;
            }
            if (remaining > 1.0E-6D && length > 1.0E-9D) {
                result.add(from.lerp(to, remaining / length));
            }
            break;
        }
        return List.copyOf(result);
    }

    static Vec3 sampleAtDistance(List<Vec3> path, double distance) {
        double remaining = Math.max(0.0D, distance);
        for (int index = 1; index < path.size(); index++) {
            Vec3 from = path.get(index - 1);
            Vec3 to = path.get(index);
            double length = from.distanceTo(to);
            if (remaining <= length && length > EPSILON) {
                return from.lerp(to, remaining / length);
            }
            remaining -= length;
        }
        return path.getLast();
    }

    record StepDiagnostics(
            double guideCorrection,
            double tipOrientationCorrection,
            double rootOrientationCorrection,
            double lengthCorrection,
            double bendCorrection,
            double curvatureCorrection,
            double selfCollisionCorrection,
            double terrainCorrection,
            double stepLimitCorrection,
            int terrainContacts,
            int selfCollisionContacts,
            double restLength,
            double guideLength,
            double curveAmplitude,
            double muscleScale,
            double maximumStretchRatio,
            double arcLength,
            boolean tipTerrainContact,
            boolean stepRejected) {
    }

}
