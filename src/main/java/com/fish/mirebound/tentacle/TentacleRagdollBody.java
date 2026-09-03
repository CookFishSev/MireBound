package com.fish.mirebound.tentacle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Server-authoritative articulated player pose, allocated only for an active grab. */
final class TentacleRagdollBody {
    private static final double EPSILON = 1.0E-9D;
    private static final int PELVIS = 0;
    private static final int CHEST = 1;
    private static final int HEAD = 2;
    private static final int LEFT_SHOULDER = 3;
    private static final int LEFT_ELBOW = 4;
    private static final int LEFT_HAND = 5;
    private static final int RIGHT_SHOULDER = 6;
    private static final int RIGHT_ELBOW = 7;
    private static final int RIGHT_HAND = 8;
    private static final int LEFT_HIP = 9;
    private static final int LEFT_KNEE = 10;
    private static final int LEFT_FOOT = 11;
    private static final int RIGHT_HIP = 12;
    private static final int RIGHT_KNEE = 13;
    private static final int RIGHT_FOOT = 14;
    private static final int NODE_COUNT = 15;
    private static final int[][] COLLISION_BONES = {
            {PELVIS, CHEST}, {CHEST, HEAD},
            {CHEST, LEFT_SHOULDER}, {LEFT_SHOULDER, LEFT_ELBOW}, {LEFT_ELBOW, LEFT_HAND},
            {CHEST, RIGHT_SHOULDER}, {RIGHT_SHOULDER, RIGHT_ELBOW}, {RIGHT_ELBOW, RIGHT_HAND},
            {PELVIS, LEFT_HIP}, {LEFT_HIP, LEFT_KNEE}, {LEFT_KNEE, LEFT_FOOT},
            {PELVIS, RIGHT_HIP}, {RIGHT_HIP, RIGHT_KNEE}, {RIGHT_KNEE, RIGHT_FOOT}
    };
    private static final double[] INVERSE_MASS = {
            0.45D, 0.38D, 0.70D,
            0.60D, 1.0D, 1.30D, 0.60D, 1.0D, 1.30D,
            0.60D, 0.92D, 1.15D, 0.60D, 0.92D, 1.15D
    };
    /**
     * Root bones carry two angular limits each plus the previous-tick angles the limiter needs to
     * break representation ties, so they are indexed separately from the node array.
     */
    private static final int LEFT_SHOULDER_JOINT = 0;
    private static final int RIGHT_SHOULDER_JOINT = 1;
    private static final int LEFT_HIP_JOINT = 2;
    private static final int RIGHT_HIP_JOINT = 3;
    private static final int ROOT_JOINT_COUNT = 4;

    private final Vec3[] positions = new Vec3[NODE_COUNT];
    private final Vec3[] previous = new Vec3[NODE_COUNT];
    private final List<Constraint> constraints = new ArrayList<>(24);
    private final boolean[][] linkedNodes = new boolean[NODE_COUNT][NODE_COUNT];
    private final GripBinding grip;
    private final double gripOrientationLength;
    private final Vec3 fallbackGripNormal;
    private final double gripClearance;
    private final Quaterniond referenceOrientation;
    private final double[] rootJointSwing = new double[ROOT_JOINT_COUNT];
    private final double[] rootJointLateral = new double[ROOT_JOINT_COUNT];
    private TentacleRagdollPose pose = TentacleRagdollPose.IDENTITY;

    TentacleRagdollBody(ServerPlayer player, Vec3 surfaceContact, Vec3 tentaclePoint,
            double tipRadius, TentacleGrabProfile profile, TentacleGrabTarget preferredTarget,
            boolean forcePreferredTarget) {
        this(player.getBbHeight(), player.getBbWidth(), player.getYRot(),
                surfaceContact.subtract(player.getBoundingBox().getCenter()),
                tentaclePoint.subtract(player.getBoundingBox().getCenter()), tipRadius,
                profile.wholeBodyTipRatio(), profile.surfaceClearanceScale(), preferredTarget,
                forcePreferredTarget);
    }

    TentacleRagdollBody(double height, double width, float yaw, Vec3 contactOffset) {
        this(height, width, yaw, contactOffset, contactOffset, 0.0D,
                Double.POSITIVE_INFINITY, 1.0D, TentacleGrabTarget.NONE);
    }

    TentacleRagdollBody(double height, double width, float yaw, Vec3 contactOffset,
            Vec3 tentacleOffset, double tipRadius, double wholeBodyTipRatio,
            double surfaceClearanceScale) {
        this(height, width, yaw, contactOffset, tentacleOffset, tipRadius,
                wholeBodyTipRatio, surfaceClearanceScale, TentacleGrabTarget.NONE);
    }

    TentacleRagdollBody(double height, double width, float yaw, Vec3 contactOffset,
            Vec3 tentacleOffset, double tipRadius, double wholeBodyTipRatio,
            double surfaceClearanceScale, TentacleGrabTarget preferredTarget) {
        this(height, width, yaw, contactOffset, tentacleOffset, tipRadius,
                wholeBodyTipRatio, surfaceClearanceScale, preferredTarget, false);
    }

    TentacleRagdollBody(double height, double width, float yaw, Vec3 contactOffset,
            Vec3 tentacleOffset, double tipRadius, double wholeBodyTipRatio,
            double surfaceClearanceScale, TentacleGrabTarget preferredTarget,
            boolean forcePreferredTarget) {
        set(PELVIS, 0.0D, -height * 0.18D, 0.0D);
        set(CHEST, 0.0D, height * 0.18D, 0.0D);
        set(HEAD, 0.0D, height * 0.43D, 0.0D);
        set(LEFT_SHOULDER, width * 0.48D, height * 0.20D, 0.0D);
        set(LEFT_ELBOW, width * 0.78D, -height * 0.01D, 0.0D);
        set(LEFT_HAND, width * 0.82D, -height * 0.25D, 0.0D);
        set(RIGHT_SHOULDER, -width * 0.48D, height * 0.20D, 0.0D);
        set(RIGHT_ELBOW, -width * 0.78D, -height * 0.01D, 0.0D);
        set(RIGHT_HAND, -width * 0.82D, -height * 0.25D, 0.0D);
        set(LEFT_HIP, width * 0.20D, -height * 0.20D, 0.0D);
        set(LEFT_KNEE, width * 0.18D, -height * 0.50D, 0.0D);
        set(LEFT_FOOT, width * 0.18D, -height * 0.82D, 0.04D);
        set(RIGHT_HIP, -width * 0.20D, -height * 0.20D, 0.0D);
        set(RIGHT_KNEE, -width * 0.18D, -height * 0.50D, 0.0D);
        set(RIGHT_FOOT, -width * 0.18D, -height * 0.82D, 0.04D);
        rotateInitialPose(Math.toRadians(-yaw));
        addConstraints();
        referenceOrientation = worldBodyOrientation(TentacleRagdollPose.IDENTITY.bodyOrientation());
        boolean wholeBody = !forcePreferredTarget
                && tipRadius / Math.max(0.05D, width) >= wholeBodyTipRatio;
        grip = wholeBody
                ? binding(PELVIS, CHEST, TentacleGrabTarget.WHOLE_BODY, contactOffset)
                : preferredTarget != null && preferredTarget != TentacleGrabTarget.NONE
                        ? bindingForTarget(preferredTarget, tentacleOffset)
                        : nearestBinding(tentacleOffset);
        gripOrientationLength = positions[grip.first()].distanceTo(positions[grip.second()]);
        Vec3 initialSeparation = gripPoint().subtract(tentacleOffset);
        if (initialSeparation.lengthSqr() <= EPSILON) {
            initialSeparation = contactOffset.subtract(tentacleOffset);
        }
        if (initialSeparation.lengthSqr() <= EPSILON) {
            initialSeparation = new Vec3(0.0D, 0.0D, 1.0D);
        }
        fallbackGripNormal = initialSeparation.normalize();
        double bodyRadius = targetRadius(width, grip.target());
        // A distributed whole-body wrap is centered on the ragdoll's pelvis/chest
        // axis. Retaining the ordinary surface clearance here keeps the physical
        // tentacle centerline beside that axis while the rendered coils are centered
        // on it, producing a persistent lateral mismatch. Limb grabs still need
        // surface clearance so the tip does not pull a hand, foot or limb into itself.
        gripClearance = grip.target() == TentacleGrabTarget.WHOLE_BODY
                ? 0.0D
                : Math.max(initialSeparation.length(), tipRadius + bodyRadius)
                        * Mth.clamp(surfaceClearanceScale, 0.50D, 2.0D);
        pose = buildPose();
    }

    static double targetRadius(double width, TentacleGrabTarget target) {
        return width * switch (target) {
            case HEAD -> 0.46D;
            case TORSO, WHOLE_BODY -> 0.42D;
            case LEFT_ARM, RIGHT_ARM, LEFT_HAND, RIGHT_HAND,
                    LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT -> 0.19D;
            default -> 0.0D;
        };
    }

    Update update(Vec3 currentVelocity, Vec3 bodyCenter, Vec3 tip, Vec3 tipVelocity,
            TentacleCollisionSpace collision, TentacleGrabProfile profile, double sizeScale) {
        return update(currentVelocity, bodyCenter, tip, tipVelocity,
                collision, profile, sizeScale, tip.subtract(bodyCenter), 1.0D);
    }

    Update update(Vec3 currentVelocity, Vec3 bodyCenter, Vec3 tip, Vec3 tipVelocity,
            TentacleCollisionSpace collision, TentacleGrabProfile profile, double sizeScale,
            double controlScale) {
        return update(currentVelocity, bodyCenter, tip, tipVelocity,
                collision, profile, sizeScale, tip.subtract(bodyCenter), controlScale);
    }

    Update update(Vec3 currentVelocity, Vec3 bodyCenter, Vec3 tip, Vec3 tipVelocity,
            TentacleCollisionSpace collision, TentacleGrabProfile profile, double sizeScale,
            Vec3 tipDirection, double controlScale) {
        return update(currentVelocity, bodyCenter, tip, tipVelocity, collision, profile,
                sizeScale, tipDirection, controlScale, 1.0D);
    }

    Update update(Vec3 currentVelocity, Vec3 bodyCenter, Vec3 tip, Vec3 tipVelocity,
            TentacleCollisionSpace collision, TentacleGrabProfile profile, double sizeScale,
            Vec3 tipDirection, double controlScale, double attachmentProgress) {
        return update(currentVelocity, bodyCenter, tip, tipVelocity, collision, profile,
                sizeScale, tipDirection, controlScale, attachmentProgress,
                TentacleGrabMode.THRASH);
    }

    Update update(Vec3 currentVelocity, Vec3 bodyCenter, Vec3 tip, Vec3 tipVelocity,
            TentacleCollisionSpace collision, TentacleGrabProfile profile, double sizeScale,
            Vec3 tipDirection, double controlScale, double attachmentProgress,
            TentacleGrabMode grabMode) {
        double attachment = Mth.clamp(attachmentProgress, 0.0D, 1.0D);
        Vec3 currentGrip = bodyCenter.add(gripPoint());
        Vec3 outward = currentGrip.subtract(tip);
        outward = outward.lengthSqr() <= EPSILON ? fallbackGripNormal : outward.normalize();
        Vec3 desiredGripWorld = tip.add(outward.scale(gripClearance));
        Vec3 linearVelocity = TentacleGrabController.constrainedVelocity(
                currentVelocity, currentGrip, desiredGripWorld,
                tipVelocity, profile, sizeScale, controlScale);
        double forceScale = Math.pow(Mth.clamp(sizeScale, 0.25D, 8.0D), 0.35D);
        Vec3 desiredGrip = desiredGripWorld.subtract(bodyCenter);
        integrate(profile, forceScale,
                inertialForce(grabMode, currentVelocity, tipVelocity, profile));

        int iterations = Math.max(1, profile.ragdollSolverIterations());
        double orientationStiffness = profile.ragdollGripOrientationStiffness() * attachment;
        double orientationStep = 1.0D - Math.pow(
                1.0D - Mth.clamp(orientationStiffness, 0.0D, 1.0D),
                1.0D / iterations);
        for (int iteration = 0; iteration < iterations; iteration++) {
            double gripStiffness = profile.ragdollGripStiffness() * (0.20D + attachment * 0.80D);
            double gripStep = 1.0D - Math.pow(1.0D - gripStiffness,
                    1.0D / iterations);
            solveGrip(desiredGrip, gripStep);
            solveWholeBodyOrientation(tipDirection, orientationStep, grabMode);
            solveGripOrientation(tipDirection, orientationStep);
            for (Constraint constraint : constraints) {
                solve(constraint, profile.ragdollJointStiffness());
            }
            resolveSelfCollisions(profile.ragdollCollisionRadius());
            if (profile.ragdollCollisionEnabled() && collision != null) {
                resolveCollisions(bodyCenter, collision, profile.ragdollCollisionRadius());
                resolveBoneCollisions(bodyCenter, collision, profile.ragdollCollisionRadius());
            }
            solveJointLimits(profile.ragdollArmTorsoClearanceScale());
        }

        Vec3 centerDrift = positions[PELVIS].add(positions[CHEST]).scale(0.5D);
        if (centerDrift.lengthSqr() > EPSILON) {
            double correctionScale = Mth.clamp(profile.ragdollGripStiffness() * 0.32D, 0.05D, 0.35D);
            linearVelocity = linearVelocity.add(clampLength(centerDrift.scale(correctionScale),
                    profile.maximumAcceleration() * forceScale));
            recenter(centerDrift);
        }
        double response = Mth.clamp(controlScale, 0.10D, 1.0D);
        Vec3 boundedChange = clampLength(linearVelocity.subtract(currentVelocity),
                profile.maximumAcceleration() * forceScale * response);
        linearVelocity = clampLength(currentVelocity.add(boundedChange),
                profile.maximumSpeed() * Math.sqrt(forceScale) * response);
        pose = buildPose();
        return new Update(linearVelocity, pose);
    }

    TentacleRagdollPose pose() {
        return pose;
    }

    double gripClearance() {
        return gripClearance;
    }

    /**
     * Body-local position of one skeleton node, by name. Lets tests assert on joint angles the
     * rendered pose cannot express — a bone direction carries no information about which side a
     * hinge folded to, so hyperextension is only visible from the node positions themselves.
     */
    Vec3 nodePosition(String name) {
        return positions[switch (name) {
            case "PELVIS" -> PELVIS;
            case "CHEST" -> CHEST;
            case "HEAD" -> HEAD;
            case "LEFT_SHOULDER" -> LEFT_SHOULDER;
            case "LEFT_ELBOW" -> LEFT_ELBOW;
            case "LEFT_HAND" -> LEFT_HAND;
            case "RIGHT_SHOULDER" -> RIGHT_SHOULDER;
            case "RIGHT_ELBOW" -> RIGHT_ELBOW;
            case "RIGHT_HAND" -> RIGHT_HAND;
            case "LEFT_HIP" -> LEFT_HIP;
            case "LEFT_KNEE" -> LEFT_KNEE;
            case "LEFT_FOOT" -> LEFT_FOOT;
            case "RIGHT_HIP" -> RIGHT_HIP;
            case "RIGHT_KNEE" -> RIGHT_KNEE;
            case "RIGHT_FOOT" -> RIGHT_FOOT;
            default -> throw new IllegalArgumentException("Unknown ragdoll node: " + name);
        }];
    }

    List<Vec3> collisionSamplePoints(Vec3 bodyCenter) {
        List<Vec3> result = new ArrayList<>(positions.length + COLLISION_BONES.length);
        for (Vec3 position : positions) {
            result.add(bodyCenter.add(position));
        }
        for (int[] bone : COLLISION_BONES) {
            result.add(bodyCenter.add(positions[bone[0]].add(positions[bone[1]]).scale(0.5D)));
        }
        return List.copyOf(result);
    }

    /**
     * Smallest distance between either rendered hip-to-foot bone and the torso axis. The renderer
     * draws each leg as one rigid cuboid, so this measures what a player actually sees rather than
     * the solver's intermediate knee node.
     */
    double minimumRenderedLegTorsoDistance() {
        Vec3 torsoAxis = positions[CHEST].subtract(positions[PELVIS]);
        double minimum = Double.POSITIVE_INFINITY;
        int[][] legs = {
                {LEFT_HIP, LEFT_FOOT},
                {RIGHT_HIP, RIGHT_FOOT}
        };
        for (int[] leg : legs) {
            for (double amount : new double[] {0.32D, 0.52D, 0.72D, 0.88D, 1.0D}) {
                Vec3 sample = positions[leg[0]].lerp(positions[leg[1]], amount);
                Vec3 closest = closestPointOnSegment(
                        sample, positions[PELVIS], positions[CHEST], torsoAxis);
                minimum = Math.min(minimum, sample.distanceTo(closest));
            }
        }
        return minimum;
    }

    static Vec3 inertialForce(TentacleGrabMode mode, Vec3 bodyVelocity,
            Vec3 tipVelocity, TentacleGrabProfile profile) {
        if (mode == null || mode == TentacleGrabMode.HOLD || profile.ragdollInertia() <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 relativeVelocity = bodyVelocity.subtract(tipVelocity)
                .scale(profile.ragdollInertia());
        return clampLength(relativeVelocity, profile.ragdollGravity() * 3.5D);
    }

    private void integrate(TentacleGrabProfile profile, double forceScale, Vec3 inertialForce) {
        double maximumStep = profile.ragdollMaximumNodeSpeed() * Math.sqrt(forceScale);
        Vec3 gravity = new Vec3(0.0D, -profile.ragdollGravity(), 0.0D);
        for (int node = 0; node < NODE_COUNT; node++) {
            Vec3 current = positions[node];
            Vec3 velocity = current.subtract(previous[node]).scale(profile.ragdollDamping());
            double mobility = 0.30D + INVERSE_MASS[node] * 0.70D;
            Vec3 force = gravity.scale(mobility * armGravityScale(node, profile))
                    .add(inertialForce.scale(mobility));
            Vec3 desired = current.add(clampLength(velocity.add(force), maximumStep));
            previous[node] = current;
            positions[node] = desired;
        }
    }

    private static double armGravityScale(int node, TentacleGrabProfile profile) {
        double scale = profile.ragdollArmGravityScale();
        return switch (node) {
            case LEFT_ELBOW, RIGHT_ELBOW -> Mth.lerp(0.58D, 1.0D, scale);
            case LEFT_HAND, RIGHT_HAND -> scale;
            default -> 1.0D;
        };
    }

    private void solve(Constraint constraint, double stiffness) {
        int first = constraint.first();
        int second = constraint.second();
        Vec3 delta = positions[second].subtract(positions[first]);
        double length = delta.length();
        if (length <= EPSILON) {
            return;
        }
        double firstWeight = INVERSE_MASS[first];
        double secondWeight = INVERSE_MASS[second];
        double correction = (length - constraint.length()) / length * stiffness;
        Vec3 offset = delta.scale(correction / (firstWeight + secondWeight));
        correctNode(first, offset.scale(firstWeight));
        correctNode(second, offset.scale(-secondWeight));
    }

    private void resolveSelfCollisions(double radius) {
        double minimumDistance = Math.max(0.02D, radius * 2.0D);
        double minimumDistanceSquared = minimumDistance * minimumDistance;
        for (int first = 0; first < NODE_COUNT; first++) {
            for (int second = first + 1; second < NODE_COUNT; second++) {
                if (linkedNodes[first][second]) {
                    continue;
                }
                Vec3 separation = positions[second].subtract(positions[first]);
                double distanceSquared = separation.lengthSqr();
                if (distanceSquared >= minimumDistanceSquared) {
                    continue;
                }
                double distance = Math.sqrt(Math.max(EPSILON, distanceSquared));
                Vec3 normal = distanceSquared <= EPSILON
                        ? fallbackSelfNormal(first, second) : separation.scale(1.0D / distance);
                double firstWeight = grip.contains(first) ? 0.0D : INVERSE_MASS[first];
                double secondWeight = grip.contains(second) ? 0.0D : INVERSE_MASS[second];
                double totalWeight = firstWeight + secondWeight;
                if (totalWeight <= EPSILON) {
                    continue;
                }
                Vec3 correction = normal.scale(minimumDistance - distance);
                correctNode(first, correction.scale(-firstWeight / totalWeight));
                correctNode(second, correction.scale(secondWeight / totalWeight));
            }
        }
    }

    /**
     * Every angular limit in one pass, in the body's own frame.
     *
     * <p>This replaces the former sample-based torso repulsion. Repulsion pushed the hand and elbow
     * nodes directly, so the distance and grip constraints undid it inside the same iteration, and
     * its correction was divided by the sample position along the limb — largest at the shoulder,
     * smallest at the fingertips, the opposite of what a cylinder test needs. A joint limit instead
     * constrains a bone's <em>direction</em>, which no distance constraint can cancel because it
     * never changes a bone's length.
     *
     * <p>Order matters: root bones first, because an elbow's hinge plane is derived from the upper
     * arm that the shoulder limit has just placed.
     *
     * @param armTorsoClearanceScale the synchronized arm-to-torso clearance tuning value, which now
     *     scales the shoulder's inward limit — the angular form of the same "keep the arms off the
     *     torso" control the removed repulsion pass used it for
     */
    private void solveJointLimits(double armTorsoClearanceScale) {
        RagdollJointLimits.Frame frame = bodyFrame();
        double shoulderInward = RagdollJointLimits.shoulderInwardLimit(armTorsoClearanceScale);
        limitRootBone(LEFT_SHOULDER_JOINT, LEFT_SHOULDER, LEFT_ELBOW, LEFT_HAND, frame, true,
                shoulderInward,
                RagdollJointLimits.SHOULDER_OUTWARD_LIMIT,
                RagdollJointLimits.SHOULDER_SWING_LIMIT);
        limitRootBone(RIGHT_SHOULDER_JOINT, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_HAND, frame, false,
                shoulderInward,
                RagdollJointLimits.SHOULDER_OUTWARD_LIMIT,
                RagdollJointLimits.SHOULDER_SWING_LIMIT);
        limitRootBone(LEFT_HIP_JOINT, LEFT_HIP, LEFT_KNEE, LEFT_FOOT, frame, true,
                RagdollJointLimits.HIP_INWARD_LIMIT,
                RagdollJointLimits.HIP_OUTWARD_LIMIT,
                RagdollJointLimits.HIP_SWING_LIMIT);
        limitRootBone(RIGHT_HIP_JOINT, RIGHT_HIP, RIGHT_KNEE, RIGHT_FOOT, frame, false,
                RagdollJointLimits.HIP_INWARD_LIMIT,
                RagdollJointLimits.HIP_OUTWARD_LIMIT,
                RagdollJointLimits.HIP_SWING_LIMIT);

        // Elbows fold forward, knees fold backward: the bend reference is the body's own forward
        // axis, negated for the knee. Deriving the hinge plane from it per call keeps the plane
        // valid however far the whole limb has swung.
        Vec3 forward = frame.forward();
        Vec3 backward = forward.scale(-1.0D);
        limitHinge(LEFT_SHOULDER, LEFT_ELBOW, LEFT_HAND,
                RagdollJointLimits.ELBOW_MINIMUM_FLEXION,
                RagdollJointLimits.ELBOW_MAXIMUM_FLEXION, forward);
        limitHinge(RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_HAND,
                RagdollJointLimits.ELBOW_MINIMUM_FLEXION,
                RagdollJointLimits.ELBOW_MAXIMUM_FLEXION, forward);
        limitHinge(LEFT_HIP, LEFT_KNEE, LEFT_FOOT,
                RagdollJointLimits.KNEE_MINIMUM_FLEXION,
                RagdollJointLimits.KNEE_MAXIMUM_FLEXION, backward);
        limitHinge(RIGHT_HIP, RIGHT_KNEE, RIGHT_FOOT,
                RagdollJointLimits.KNEE_MINIMUM_FLEXION,
                RagdollJointLimits.KNEE_MAXIMUM_FLEXION, backward);

        limitNeck(frame);
    }

    /**
     * The body frame the limits are expressed in. Shoulder lateral is authoritative for the
     * torso's twist; the pelvis-to-chest axis supplies up.
     */
    private RagdollJointLimits.Frame bodyFrame() {
        return RagdollJointLimits.Frame.of(
                positions[LEFT_SHOULDER].subtract(positions[RIGHT_SHOULDER]),
                positions[CHEST].subtract(positions[PELVIS]));
    }

    /**
     * Clamps a shoulder or hip. The limited vector is the root-to-endpoint direction, because that
     * is the span the renderer draws as one rigid cuboid; the middle node follows at half weight so
     * the bone does not shear.
     */
    private void limitRootBone(int joint, int root, int middle, int end,
            RagdollJointLimits.Frame frame, boolean leftSide,
            double inwardLimit, double outwardLimit, double swingLimit) {
        Vec3 current = positions[end].subtract(positions[root]);
        RagdollJointLimits.Limited limited = RagdollJointLimits.limitRootBone(
                current, frame, leftSide, inwardLimit, outwardLimit, swingLimit,
                rootJointSwing[joint], rootJointLateral[joint]);
        rootJointSwing[joint] = limited.swingAngle();
        rootJointLateral[joint] = limited.lateralAngle();
        Vec3 correction = limited.direction().subtract(current);
        if (correction.lengthSqr() > EPSILON) {
            correctNode(end, correction);
            correctNode(middle, correction.scale(0.50D));
        }
    }

    /** Clamps an elbow or knee, moving only the far end so the upper bone keeps its direction. */
    private void limitHinge(int root, int joint, int end,
            double minimumFlexion, double maximumFlexion, Vec3 bendReference) {
        Vec3 limited = RagdollJointLimits.limitHinge(
                positions[root], positions[joint], positions[end],
                minimumFlexion, maximumFlexion, bendReference);
        Vec3 correction = limited.subtract(positions[end]);
        if (correction.lengthSqr() > EPSILON) {
            correctNode(end, correction);
        }
    }

    /** Clamps the head into the neck cone. */
    private void limitNeck(RagdollJointLimits.Frame frame) {
        Vec3 current = positions[HEAD].subtract(positions[CHEST]);
        Vec3 limited = RagdollJointLimits.limitNeck(current, frame);
        Vec3 correction = limited.subtract(current);
        if (correction.lengthSqr() > EPSILON) {
            correctNode(HEAD, correction);
        }
    }

    private static Vec3 closestPointOnSegment(Vec3 point, Vec3 start, Vec3 end, Vec3 segment) {
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= EPSILON) {
            return start;
        }
        double amount = Mth.clamp(point.subtract(start).dot(segment) / lengthSquared, 0.0D, 1.0D);
        return start.lerp(end, amount);
    }

    private void resolveCollisions(Vec3 bodyCenter, TentacleCollisionSpace collision, double radius) {
        for (int node = 0; node < NODE_COUNT; node++) {
            Vec3 oldWorld = bodyCenter.add(previous[node]);
            Vec3 desiredWorld = bodyCenter.add(positions[node]);
            Vec3 resolved = collision.move(oldWorld, desiredWorld, radius).subtract(bodyCenter);
            correctNode(node, resolved.subtract(positions[node]));
        }
    }

    private void resolveBoneCollisions(Vec3 bodyCenter,
            TentacleCollisionSpace collision, double radius) {
        for (int[] bone : COLLISION_BONES) {
            int first = bone[0];
            int second = bone[1];
            Vec3 midpoint = positions[first].add(positions[second]).scale(0.5D);
            Vec3 worldMidpoint = bodyCenter.add(midpoint);
            Vec3 resolved = collision.project(worldMidpoint, radius);
            Vec3 correction = resolved.subtract(worldMidpoint);
            if (correction.lengthSqr() <= EPSILON) {
                continue;
            }
            double firstWeight = INVERSE_MASS[first] * (1.0D - grip.influence(first));
            double secondWeight = INVERSE_MASS[second] * (1.0D - grip.influence(second));
            double totalWeight = firstWeight + secondWeight;
            if (totalWeight <= EPSILON) {
                continue;
            }
            correctNode(first, correction.scale(2.0D * firstWeight / totalWeight));
            correctNode(second, correction.scale(2.0D * secondWeight / totalWeight));
        }
    }

    private TentacleRagdollPose buildPose() {
        Vec3 lateral = positions[LEFT_SHOULDER].subtract(positions[RIGHT_SHOULDER]);
        Vec3 torsoUp = positions[CHEST].subtract(positions[PELVIS]);
        Quaterniond previousWorld = new Quaterniond(referenceOrientation).mul(pose.bodyOrientation());
        Quaterniond worldBody = orientation(lateral, torsoUp, previousWorld);
        Vec3 headUp = positions[HEAD].subtract(positions[CHEST]);
        Quaterniond previousWorldHead = new Quaterniond(referenceOrientation)
                .mul(pose.headOrientation());
        Quaterniond worldHead = orientation(lateral, headUp, previousWorldHead);
        Quaterniond inverseReference = new Quaterniond(referenceOrientation).conjugate();
        Quaterniond body = new Quaterniond(inverseReference).mul(worldBody).normalize();
        Quaterniond head = new Quaterniond(inverseReference).mul(worldHead).normalize();
        Quaterniond inverseBody = new Quaterniond(worldBody).conjugate();
        return new TentacleRagdollPose(body, head, referenceOrientation, positions[HEAD],
                grip.target(), gripPoint(),
                toLocalDirection(positions[LEFT_HAND].subtract(positions[LEFT_SHOULDER]), inverseBody),
                toLocalDirection(positions[RIGHT_HAND].subtract(positions[RIGHT_SHOULDER]), inverseBody),
                toLocalDirection(positions[LEFT_FOOT].subtract(positions[LEFT_HIP]), inverseBody),
                toLocalDirection(positions[RIGHT_FOOT].subtract(positions[RIGHT_HIP]), inverseBody));
    }

    private Quaterniond worldBodyOrientation(Quaterniond fallback) {
        return orientation(positions[LEFT_SHOULDER].subtract(positions[RIGHT_SHOULDER]),
                positions[CHEST].subtract(positions[PELVIS]), fallback);
    }

    private static Quaterniond orientation(Vec3 lateral, Vec3 up, Quaterniond fallback) {
        if (lateral.lengthSqr() <= EPSILON || up.lengthSqr() <= EPSILON) {
            return new Quaterniond(fallback);
        }
        Vec3 x = lateral.normalize();
        Vec3 y = up.subtract(x.scale(up.dot(x)));
        double minimumProjectedUp = up.lengthSqr() * 1.0E-5D;
        if (y.lengthSqr() <= Math.max(EPSILON, minimumProjectedUp)) {
            Vector3d fallbackUp = new Quaterniond(fallback)
                    .transform(new Vector3d(0.0D, 1.0D, 0.0D));
            Vec3 stableUp = new Vec3(fallbackUp.x, fallbackUp.y, fallbackUp.z);
            y = stableUp.subtract(x.scale(stableUp.dot(x)));
            if (y.lengthSqr() <= EPSILON) {
                return new Quaterniond(fallback);
            }
        }
        y = y.normalize();
        Vec3 z = x.cross(y).normalize();
        y = z.cross(x).normalize();
        Matrix3d matrix = new Matrix3d(
                x.x, x.y, x.z,
                y.x, y.y, y.z,
                z.x, z.y, z.z);
        Quaterniond result = new Quaterniond().setFromNormalized(matrix).normalize();
        if (result.dot(fallback) < 0.0D) {
            result.set(-result.x, -result.y, -result.z, -result.w);
        }
        return result;
    }

    private static Vec3 toLocalDirection(Vec3 direction, Quaterniond inverseBody) {
        if (direction.lengthSqr() <= EPSILON) {
            return new Vec3(0.0D, -1.0D, 0.0D);
        }
        Vector3d transformed = inverseBody.transform(new Vector3d(
                direction.x, direction.y, direction.z)).normalize();
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    private void recenter(Vec3 offset) {
        for (int node = 0; node < NODE_COUNT; node++) {
            positions[node] = positions[node].subtract(offset);
            previous[node] = previous[node].subtract(offset);
        }
    }

    private void rotateInitialPose(double yaw) {
        double sine = Math.sin(yaw);
        double cosine = Math.cos(yaw);
        for (int node = 0; node < NODE_COUNT; node++) {
            Vec3 point = positions[node];
            Vec3 rotated = new Vec3(
                    point.x * cosine - point.z * sine,
                    point.y,
                    point.x * sine + point.z * cosine);
            positions[node] = rotated;
            previous[node] = rotated;
        }
    }

    static Vec3 targetOffset(double height, double width, float yaw,
            TentacleGrabTarget target) {
        Vec3 local = switch (target) {
            case HEAD -> new Vec3(0.0D, height * 0.43D, 0.0D);
            case TORSO, WHOLE_BODY, NONE -> new Vec3(0.0D, height * 0.08D, 0.0D);
            case LEFT_ARM -> new Vec3(width * 0.64D, height * 0.09D, 0.0D);
            case RIGHT_ARM -> new Vec3(-width * 0.64D, height * 0.09D, 0.0D);
            case LEFT_HAND -> new Vec3(width * 0.82D, -height * 0.25D, 0.0D);
            case RIGHT_HAND -> new Vec3(-width * 0.82D, -height * 0.25D, 0.0D);
            case LEFT_LEG -> new Vec3(width * 0.19D, -height * 0.42D, 0.0D);
            case RIGHT_LEG -> new Vec3(-width * 0.19D, -height * 0.42D, 0.0D);
            case LEFT_FOOT -> new Vec3(width * 0.18D, -height * 0.82D, 0.04D);
            case RIGHT_FOOT -> new Vec3(-width * 0.18D, -height * 0.82D, 0.04D);
        };
        double angle = Math.toRadians(-yaw);
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        return new Vec3(local.x * cosine - local.z * sine, local.y,
                local.x * sine + local.z * cosine);
    }

    private GripBinding nearestBinding(Vec3 point) {
        GripBinding[] candidates = {
                binding(CHEST, HEAD, TentacleGrabTarget.HEAD, point),
                binding(PELVIS, CHEST, TentacleGrabTarget.TORSO, point),
                binding(LEFT_SHOULDER, LEFT_ELBOW, TentacleGrabTarget.LEFT_ARM, point),
                binding(LEFT_SHOULDER, LEFT_HAND, TentacleGrabTarget.LEFT_HAND, point),
                binding(RIGHT_SHOULDER, RIGHT_ELBOW, TentacleGrabTarget.RIGHT_ARM, point),
                binding(RIGHT_SHOULDER, RIGHT_HAND, TentacleGrabTarget.RIGHT_HAND, point),
                binding(LEFT_HIP, LEFT_KNEE, TentacleGrabTarget.LEFT_LEG, point),
                binding(LEFT_HIP, LEFT_FOOT, TentacleGrabTarget.LEFT_FOOT, point),
                binding(RIGHT_HIP, RIGHT_KNEE, TentacleGrabTarget.RIGHT_LEG, point),
                binding(RIGHT_HIP, RIGHT_FOOT, TentacleGrabTarget.RIGHT_FOOT, point)
        };
        GripBinding nearest = candidates[0];
        double distance = Double.POSITIVE_INFINITY;
        for (GripBinding candidate : candidates) {
            double candidateDistance = candidate.point(positions).distanceToSqr(point);
            if (candidateDistance < distance) {
                distance = candidateDistance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private GripBinding bindingForTarget(TentacleGrabTarget target, Vec3 point) {
        return switch (target) {
            case HEAD -> endpointBinding(CHEST, HEAD, target);
            case TORSO -> binding(PELVIS, CHEST, target, point);
            case LEFT_ARM -> binding(LEFT_SHOULDER, LEFT_ELBOW, target, point);
            case RIGHT_ARM -> binding(RIGHT_SHOULDER, RIGHT_ELBOW, target, point);
            case LEFT_HAND -> endpointBinding(LEFT_SHOULDER, LEFT_HAND, target);
            case RIGHT_HAND -> endpointBinding(RIGHT_SHOULDER, RIGHT_HAND, target);
            case LEFT_LEG -> binding(LEFT_HIP, LEFT_KNEE, target, point);
            case RIGHT_LEG -> binding(RIGHT_HIP, RIGHT_KNEE, target, point);
            case LEFT_FOOT -> endpointBinding(LEFT_HIP, LEFT_FOOT, target);
            case RIGHT_FOOT -> endpointBinding(RIGHT_HIP, RIGHT_FOOT, target);
            case WHOLE_BODY -> binding(PELVIS, CHEST, target, point);
            case NONE -> nearestBinding(point);
        };
    }

    private static GripBinding endpointBinding(int first, int second,
            TentacleGrabTarget target) {
        return new GripBinding(first, second, 1.0D, target);
    }

    private GripBinding binding(int first, int second, TentacleGrabTarget target, Vec3 point) {
        Vec3 from = positions[first];
        Vec3 segment = positions[second].subtract(from);
        double amount = segment.lengthSqr() <= EPSILON ? 0.0D
                : Mth.clamp(point.subtract(from).dot(segment) / segment.lengthSqr(), 0.0D, 1.0D);
        return new GripBinding(first, second, amount, target);
    }

    private Vec3 gripPoint() {
        return grip.point(positions);
    }

    private void solveGrip(Vec3 desired, double stiffness) {
        if (grip.target() == TentacleGrabTarget.WHOLE_BODY) {
            Vec3 correction = desired.subtract(gripPoint()).scale(stiffness);
            int[] torsoNodes = {PELVIS, CHEST, LEFT_SHOULDER, RIGHT_SHOULDER,
                    LEFT_HIP, RIGHT_HIP};
            for (int node : torsoNodes) {
                correctNode(node, correction);
            }
            return;
        }
        if (grip.first() == grip.second()) {
            correctNode(grip.first(), desired.subtract(positions[grip.first()]).scale(stiffness));
            return;
        }
        double firstAmount = 1.0D - grip.amount();
        double secondAmount = grip.amount();
        double denominator = firstAmount * firstAmount + secondAmount * secondAmount;
        Vec3 correction = desired.subtract(gripPoint()).scale(stiffness / Math.max(EPSILON, denominator));
        correctNode(grip.first(), correction.scale(firstAmount));
        correctNode(grip.second(), correction.scale(secondAmount));
    }

    private void solveWholeBodyOrientation(Vec3 incomingTipDirection,
            double stiffness, TentacleGrabMode grabMode) {
        if (grabMode == null || grabMode == TentacleGrabMode.HOLD
                || grip.target() != TentacleGrabTarget.WHOLE_BODY
                || stiffness <= 0.0D || incomingTipDirection.lengthSqr() <= EPSILON) {
            return;
        }
        Vec3 currentAxis = positions[CHEST].subtract(positions[PELVIS]);
        double torsoLength = currentAxis.length();
        if (torsoLength <= EPSILON) {
            return;
        }

        // WRAP and THRASH are driven by the real terminal chain direction. Rotate
        // only the torso axis here; ordinary joint constraints then carry the
        // shoulders, hips and loose limbs without aiming those limbs at the tip.
        Vec3 desiredAxis = incomingTipDirection.normalize().scale(torsoLength);
        Vec3 halfCorrection = desiredAxis.subtract(currentAxis)
                .scale(Mth.clamp(stiffness, 0.0D, 1.0D) * 0.5D);
        correctNode(PELVIS, halfCorrection.scale(-1.0D));
        correctNode(CHEST, halfCorrection);
    }

    private void solveGripOrientation(Vec3 incomingTipDirection, double stiffness) {
        if (stiffness <= 0.0D || gripOrientationLength <= EPSILON
                || incomingTipDirection.lengthSqr() <= EPSILON
                || !isEndpointTarget(grip.target())) {
            return;
        }
        // Endpoint bones approach the attachment from the body side, opposite the incoming
        // terminal tangent. Keep the endpoint itself on the grip and rotate the proximal node;
        // midpoint bindings remain unconstrained ball joints.
        Vec3 desiredBoneDirection = incomingTipDirection.normalize().scale(-1.0D);
        Vec3 desiredFirst = positions[grip.second()]
                .subtract(desiredBoneDirection.scale(gripOrientationLength));
        correctNode(grip.first(),
                desiredFirst.subtract(positions[grip.first()]).scale(stiffness));
    }

    private static boolean isEndpointTarget(TentacleGrabTarget target) {
        return target == TentacleGrabTarget.HEAD
                || target == TentacleGrabTarget.LEFT_HAND
                || target == TentacleGrabTarget.RIGHT_HAND
                || target == TentacleGrabTarget.LEFT_FOOT
                || target == TentacleGrabTarget.RIGHT_FOOT;
    }

    private void addConstraints() {
        link(PELVIS, CHEST);
        link(CHEST, HEAD);
        link(CHEST, LEFT_SHOULDER);
        link(CHEST, RIGHT_SHOULDER);
        link(LEFT_SHOULDER, RIGHT_SHOULDER);
        link(PELVIS, LEFT_HIP);
        link(PELVIS, RIGHT_HIP);
        link(LEFT_HIP, RIGHT_HIP);
        link(LEFT_SHOULDER, LEFT_ELBOW);
        link(LEFT_ELBOW, LEFT_HAND);
        link(RIGHT_SHOULDER, RIGHT_ELBOW);
        link(RIGHT_ELBOW, RIGHT_HAND);
        link(LEFT_HIP, LEFT_KNEE);
        link(LEFT_KNEE, LEFT_FOOT);
        link(RIGHT_HIP, RIGHT_KNEE);
        link(RIGHT_KNEE, RIGHT_FOOT);
        link(LEFT_SHOULDER, RIGHT_HIP);
        link(RIGHT_SHOULDER, LEFT_HIP);
        link(LEFT_SHOULDER, LEFT_HIP);
        link(RIGHT_SHOULDER, RIGHT_HIP);
    }

    private void link(int first, int second) {
        constraints.add(new Constraint(first, second, positions[first].distanceTo(positions[second])));
        linkedNodes[first][second] = true;
        linkedNodes[second][first] = true;
    }

    private void correctNode(int node, Vec3 correction) {
        positions[node] = positions[node].add(correction);
        previous[node] = previous[node].add(correction);
    }

    private static Vec3 fallbackSelfNormal(int first, int second) {
        int axis = Math.floorMod(first * 31 + second * 17, 3);
        return axis == 0 ? new Vec3(1.0D, 0.0D, 0.0D)
                : axis == 1 ? new Vec3(0.0D, 1.0D, 0.0D)
                        : new Vec3(0.0D, 0.0D, 1.0D);
    }

    private void set(int node, double x, double y, double z) {
        positions[node] = new Vec3(x, y, z);
        previous[node] = positions[node];
    }

    private static Vec3 clampLength(Vec3 value, double maximum) {
        double length = value.length();
        return length > maximum && length > EPSILON ? value.scale(maximum / length) : value;
    }

    private record Constraint(int first, int second, double length) {
    }

    private record GripBinding(int first, int second, double amount, TentacleGrabTarget target) {
        Vec3 point(Vec3[] positions) {
            return positions[first].lerp(positions[second], amount);
        }

        boolean contains(int node) {
            if (target == TentacleGrabTarget.WHOLE_BODY) {
                return node == PELVIS || node == CHEST
                        || node == LEFT_SHOULDER || node == RIGHT_SHOULDER
                        || node == LEFT_HIP || node == RIGHT_HIP;
            }
            return node == first || node == second;
        }

        double influence(int node) {
            if (target == TentacleGrabTarget.WHOLE_BODY) {
                return contains(node) ? 1.0D : 0.0D;
            }
            if (node == first) {
                return 1.0D - amount;
            }
            return node == second ? amount : 0.0D;
        }
    }

    record Update(Vec3 velocity, TentacleRagdollPose pose) {
    }
}
