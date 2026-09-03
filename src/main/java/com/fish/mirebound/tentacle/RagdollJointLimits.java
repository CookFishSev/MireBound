package com.fish.mirebound.tentacle;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Angular limits for the held-player skeleton, expressed in a body-local frame.
 *
 * <p>These limits replace the former sample-based torso repulsion entirely. Repulsion pushed the
 * hand and elbow nodes directly, so the distance and grip constraints undid it within the same
 * solver iteration, and its correction was scaled by {@code 1/amount} — largest near the shoulder
 * and smallest at the fingertips, the opposite of what a cylinder test needs. A joint limit instead
 * constrains a bone's <em>direction</em>, which no distance constraint can cancel because it never
 * changes any bone's length.
 *
 * <p>Ranges are deliberately wider than human anatomy: this is a creature hauling a player around,
 * and readable exaggerated motion beats clinical accuracy. Two limits stay strict — elbows and knees
 * may not hyperextend, because a backwards joint is the most obvious tell that a pose is fake.
 */
final class RagdollJointLimits {
    private static final double EPSILON = 1.0E-9D;

    /**
     * Hard bound on the swing angle, and the reason one exists.
     *
     * <p>A bone direction is built as {@code planar = cos(swing)}, then the lateral angle is applied
     * within that plane. Past 90 degrees {@code cos(swing)} turns negative, which mirrors the
     * lateral axis: {@code (lateral 100, swing 150)} and {@code (lateral -80, swing 30)} are the
     * same direction. A limit set allowing swing beyond 90 therefore has no meaningful lateral
     * limit in that regime — a deep inward crossing can always be re-expressed as a legal-looking
     * outward angle plus a big swing, which is exactly the evasion the two-branch scoring below is
     * meant to stop.
     *
     * <p>Capping swing at 90 costs no reach. Straight overhead is {@code lateral 180, swing 0}, and
     * up-and-forward is a large lateral combined with a moderate swing, so the lateral range is what
     * carries the wide poses. Only the redundant second representation is removed.
     */
    private static final double MAXIMUM_SWING_LIMIT = Math.toRadians(90.0D);

    /** Shoulder travel away from the torso, in the frontal plane. Reaches past straight overhead. */
    static final double SHOULDER_OUTWARD_LIMIT = Math.toRadians(170.0D);
    /** How far the upper arm may cross the torso midline, at the default clearance scale. */
    static final double SHOULDER_INWARD_LIMIT = Math.toRadians(35.0D);
    /** Shoulder flexion and extension: the arm swinging forward and back. */
    static final double SHOULDER_SWING_LIMIT = Math.toRadians(90.0D);
    /** Elbow flexion. The minimum is above zero so a straight arm still has a defined bend side. */
    static final double ELBOW_MINIMUM_FLEXION = Math.toRadians(1.5D);
    static final double ELBOW_MAXIMUM_FLEXION = Math.toRadians(160.0D);
    /** Hip adduction: the leg crossing inward past the body midline. */
    static final double HIP_INWARD_LIMIT = Math.toRadians(15.0D);
    /** Hip abduction: the leg swinging outward. */
    static final double HIP_OUTWARD_LIMIT = Math.toRadians(110.0D);
    /** Hip flexion and extension: the leg swinging forward and back. */
    static final double HIP_SWING_LIMIT = Math.toRadians(85.0D);
    /** Knee flexion. Never negative: the knee may not bend backwards. */
    static final double KNEE_MINIMUM_FLEXION = Math.toRadians(1.5D);
    static final double KNEE_MAXIMUM_FLEXION = Math.toRadians(155.0D);
    /** Neck cone. Generous, so the head reads as reacting rather than welded to the chest. */
    static final double NECK_PITCH_LIMIT = Math.toRadians(70.0D);
    static final double NECK_ROLL_LIMIT = Math.toRadians(45.0D);

    private RagdollJointLimits() {
    }

    /**
     * The shoulder's inward limit for a configured arm-to-torso clearance scale.
     *
     * <p>The {@code tentacle_ragdoll_arm_torso_clearance_scale} tuning value used to scale a
     * positional repulsion that pushed the arms off the torso. That pass is gone, but the control it
     * exposed is still meaningful, so it now scales the angular version of the same thing: a larger
     * value permits less midline crossing and holds the arms further out. Dividing rather than
     * multiplying preserves the direction of the slider — turning "clearance" up still means "arms
     * further from the body".
     *
     * <p>The result is floored at zero because a scale large enough to drive it negative would flip
     * the inward bound into an outward one and force the arms permanently away from the torso.
     */
    static double shoulderInwardLimit(double armTorsoClearanceScale) {
        double scale = Double.isFinite(armTorsoClearanceScale)
                ? Mth.clamp(armTorsoClearanceScale, 0.50D, 2.0D) : 1.0D;
        return Math.max(0.0D, SHOULDER_INWARD_LIMIT / scale);
    }

    /**
     * A right-handed orthonormal body frame: {@code lateral} runs from the body's right side toward
     * its left, {@code up} from pelvis toward chest, {@code forward} out of the chest.
     */
    record Frame(Vec3 lateral, Vec3 up, Vec3 forward) {
        static final Frame IDENTITY = new Frame(
                new Vec3(1.0D, 0.0D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                new Vec3(0.0D, 0.0D, 1.0D));

        /**
         * Builds a frame from a measured lateral axis and a measured up axis. The up axis is
         * treated as authoritative for the torso's twist, so the lateral axis is the one
         * orthogonalized against it.
         */
        static Frame of(Vec3 lateralHint, Vec3 upHint) {
            Vec3 up = normalizeOr(upHint, IDENTITY.up());
            Vec3 lateral = lateralHint.subtract(up.scale(lateralHint.dot(up)));
            if (lateral.lengthSqr() <= EPSILON) {
                Vec3 fallback = Math.abs(up.x) < 0.90D
                        ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
                lateral = fallback.subtract(up.scale(fallback.dot(up)));
            }
            lateral = normalizeOr(lateral, IDENTITY.lateral());
            Vec3 forward = lateral.cross(up);
            if (forward.lengthSqr() <= EPSILON) {
                return IDENTITY;
            }
            return new Frame(lateral, up, forward.normalize());
        }

        Vec3 toLocal(Vec3 world) {
            return new Vec3(world.dot(lateral), world.dot(up), world.dot(forward));
        }

        Vec3 toWorld(Vec3 local) {
            return lateral.scale(local.x).add(up.scale(local.y)).add(forward.scale(local.z));
        }
    }

    /** A limited bone direction plus the angles it settled at, for next-tick continuity. */
    record Limited(Vec3 direction, double swingAngle, double lateralAngle) {
    }

    /**
     * Clamps a limb's root bone — a shoulder or a hip — into a cone described by two angles: the
     * lateral angle rotates away from straight down within the frontal plane, and the swing angle
     * rotates out of that plane. Positive lateral means toward the body's left; positive swing means
     * forward.
     *
     * <p>A spherical direction has two equivalent angle representations, and choosing between them
     * on continuity alone lets a genuine inward crossing be reinterpreted as "still outward, plus a
     * large forward kick", which slips past the inward limit. Both branches are therefore scored
     * against the real limits and the one needing less total clamping wins; continuity only breaks
     * ties. This is the behaviour the previous leg-only limiter had, now shared by arms and legs.
     */
    static Limited limitRootBone(Vec3 direction, Frame frame, boolean leftSide,
            double inwardLimit, double outwardLimit, double swingLimit,
            double previousSwingAngle, double previousLateralAngle) {
        double length = direction.length();
        if (!Double.isFinite(length) || length <= EPSILON) {
            return new Limited(direction, previousSwingAngle, previousLateralAngle);
        }
        // See MAXIMUM_SWING_LIMIT: a swing past 90 degrees mirrors the lateral axis and would make
        // the inward limit meaningless, so no caller can opt out of this bound.
        swingLimit = Math.min(Math.abs(swingLimit), MAXIMUM_SWING_LIMIT);
        Vec3 local = frame.toLocal(direction);
        double primarySwing = Math.asin(Mth.clamp(local.z / length, -1.0D, 1.0D));
        double primaryLateral = Math.atan2(local.x, -local.y);
        double alternateSwing = primarySwing >= 0.0D
                ? Math.PI - primarySwing : -Math.PI - primarySwing;
        double alternateLateral = normalizeAngle(primaryLateral + Math.PI);

        double minimumLateral = leftSide ? -inwardLimit : -outwardLimit;
        double maximumLateral = leftSide ? outwardLimit : inwardLimit;
        Candidate primary = candidate(primaryLateral, primarySwing,
                minimumLateral, maximumLateral, swingLimit, length, frame);
        Candidate alternate = candidate(alternateLateral, alternateSwing,
                minimumLateral, maximumLateral, swingLimit, length, frame);
        if (Math.abs(primary.violation() - alternate.violation()) > 1.0E-12D) {
            return primary.violation() < alternate.violation()
                    ? primary.limited() : alternate.limited();
        }
        double primaryDistance = angleDistance(primary.limited(),
                previousSwingAngle, previousLateralAngle);
        double alternateDistance = angleDistance(alternate.limited(),
                previousSwingAngle, previousLateralAngle);
        return primaryDistance <= alternateDistance
                ? primary.limited() : alternate.limited();
    }

    private static Candidate candidate(double lateralAngle, double swingAngle,
            double minimumLateral, double maximumLateral, double swingLimit,
            double length, Frame frame) {
        double clampedSwing = Mth.clamp(swingAngle, -swingLimit, swingLimit);
        double clampedLateral = Mth.clamp(lateralAngle, minimumLateral, maximumLateral);
        double lateralViolation = lateralAngle - clampedLateral;
        double swingViolation = swingAngle - clampedSwing;
        double violation = lateralViolation * lateralViolation + swingViolation * swingViolation;
        double planar = Math.cos(clampedSwing) * length;
        Vec3 limited = frame.toWorld(new Vec3(
                Math.sin(clampedLateral) * planar,
                -Math.cos(clampedLateral) * planar,
                Math.sin(clampedSwing) * length));
        return new Candidate(new Limited(limited, clampedSwing, clampedLateral), violation);
    }

    /**
     * Clamps a hinge such as an elbow or a knee, returning a corrected position for the bone's far
     * end. {@code bendReference} points the way the joint is allowed to fold; the component of it
     * perpendicular to the upper bone defines the hinge plane, so the plane keeps following the
     * limb as the whole thing swings instead of being baked in once.
     *
     * <p>A valid pose is returned untouched, including whatever out-of-plane play it has picked up:
     * snapping every tick would read as a rigid mannequin. Only a hyperextended or over-folded joint
     * is rebuilt, and rebuilding lands it in the hinge plane, which is where a real joint would be.
     */
    static Vec3 limitHinge(Vec3 root, Vec3 joint, Vec3 end,
            double minimumFlexion, double maximumFlexion, Vec3 bendReference) {
        Vec3 upper = joint.subtract(root);
        Vec3 lower = end.subtract(joint);
        double upperLength = upper.length();
        double lowerLength = lower.length();
        if (upperLength <= EPSILON || lowerLength <= EPSILON) {
            return end;
        }
        Vec3 upperDirection = upper.scale(1.0D / upperLength);
        Vec3 lowerDirection = lower.scale(1.0D / lowerLength);
        Vec3 bendAxis = hingePlaneAxis(upperDirection, bendReference);
        double flexion = Math.acos(Mth.clamp(upperDirection.dot(lowerDirection), -1.0D, 1.0D));
        Vec3 perpendicular = lowerDirection.subtract(
                upperDirection.scale(upperDirection.dot(lowerDirection)));
        // A negative projection onto the bend axis means the joint has folded to the wrong side:
        // that is hyperextension, no matter how small the unsigned flexion angle looks.
        boolean reversed = perpendicular.lengthSqr() > EPSILON
                && perpendicular.dot(bendAxis) < 0.0D;
        double target = Mth.clamp(flexion, minimumFlexion, maximumFlexion);
        if (!reversed && Math.abs(target - flexion) <= 1.0E-9D) {
            return end;
        }
        Vec3 corrected = upperDirection.scale(Math.cos(target))
                .add(bendAxis.scale(Math.sin(target)));
        return joint.add(corrected.scale(lowerLength));
    }

    private static Vec3 hingePlaneAxis(Vec3 upperDirection, Vec3 bendReference) {
        Vec3 axis = bendReference.subtract(
                upperDirection.scale(upperDirection.dot(bendReference)));
        if (axis.lengthSqr() > EPSILON) {
            return axis.normalize();
        }
        Vec3 fallback = Math.abs(upperDirection.y) < 0.90D
                ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        axis = fallback.subtract(upperDirection.scale(upperDirection.dot(fallback)));
        return axis.lengthSqr() <= EPSILON ? new Vec3(0.0D, 0.0D, 1.0D) : axis.normalize();
    }

    /**
     * Clamps the head's offset from the chest into the neck cone. Pitch tips the head forward and
     * back, roll tilts it toward a shoulder; yaw is a twist about the neck's own axis and so cannot
     * be expressed by an offset direction at all — it is limited where the head orientation is
     * built.
     */
    static Vec3 limitNeck(Vec3 direction, Frame frame) {
        double length = direction.length();
        if (!Double.isFinite(length) || length <= EPSILON) {
            return direction;
        }
        Vec3 local = frame.toLocal(direction.scale(1.0D / length));
        double pitch = Math.asin(Mth.clamp(local.z, -1.0D, 1.0D));
        double roll = Math.atan2(local.x, local.y);
        double clampedPitch = Mth.clamp(pitch, -NECK_PITCH_LIMIT, NECK_PITCH_LIMIT);
        double clampedRoll = Mth.clamp(roll, -NECK_ROLL_LIMIT, NECK_ROLL_LIMIT);
        if (Math.abs(clampedPitch - pitch) <= 1.0E-9D
                && Math.abs(clampedRoll - roll) <= 1.0E-9D) {
            return direction;
        }
        double planar = Math.cos(clampedPitch);
        return frame.toWorld(new Vec3(
                Math.sin(clampedRoll) * planar,
                Math.cos(clampedRoll) * planar,
                Math.sin(clampedPitch))).scale(length);
    }

    static double normalizeAngle(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    private static double angleDistance(Limited limited,
            double previousSwing, double previousLateral) {
        double swingDelta = unwrap(limited.swingAngle(), previousSwing) - previousSwing;
        double lateralDelta = unwrap(limited.lateralAngle(), previousLateral) - previousLateral;
        return swingDelta * swingDelta + lateralDelta * lateralDelta;
    }

    private static double unwrap(double angle, double reference) {
        double result = angle;
        while (result - reference > Math.PI) {
            result -= Math.PI * 2.0D;
        }
        while (result - reference < -Math.PI) {
            result += Math.PI * 2.0D;
        }
        return result;
    }

    private static Vec3 normalizeOr(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() <= EPSILON ? fallback : vector.normalize();
    }

    private record Candidate(Limited limited, double violation) {
    }
}
