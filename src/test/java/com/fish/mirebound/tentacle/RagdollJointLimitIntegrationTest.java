package com.fish.mirebound.tentacle;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.MudPhysicsProfiles;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Verifies the joint limits survive the full solver, not just direct calls.
 *
 * <p>{@link RagdollJointLimitsTest} covers the limiter in isolation. These tests run the real
 * {@link TentacleRagdollBody} update loop, where the limits compete with gravity, the grip, the
 * distance constraints and terrain — the place where a limit that is applied in the wrong order, or
 * whose correction the next constraint simply undoes, shows up as a broken pose.
 */
class RagdollJointLimitIntegrationTest {
    private static final double HEIGHT = 1.8D;
    private static final double WIDTH = 0.6D;

    @Test
    void elbowsAndKneesNeverHyperextendWhileBeingThrashedAround() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 torso = new Vec3(0.0D, HEIGHT * 0.08D, 0.0D);
        TentacleRagdollBody body = wholeBody(torso);
        Vec3 center = new Vec3(0.0D, 6.0D, 0.0D);

        // Whip the grip around hard enough that inertia alone would fold every joint backwards.
        for (int tick = 0; tick < 160; tick++) {
            double phase = tick * 0.45D;
            Vec3 tip = center.add(Math.sin(phase) * 1.60D, Math.cos(phase * 0.70D) * 0.90D,
                    Math.cos(phase) * 1.60D);
            Vec3 tipVelocity = new Vec3(Math.cos(phase) * 0.55D, 0.0D, -Math.sin(phase) * 0.55D);
            TentacleRagdollBody.Update update = body.update(Vec3.ZERO, center, tip, tipVelocity,
                    space(), grab, 1.0D, tip.subtract(center), 1.0D, 1.0D,
                    TentacleGrabMode.THRASH);
            center = center.add(update.velocity());

            assertHingeSides(body, tick);
        }
    }

    @Test
    void hipsStayWithinTheirConeUnderSustainedGravity() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 head = new Vec3(0.0D, HEIGHT * 0.43D, 0.0D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                HEIGHT, WIDTH, 0.0F, head, head, 0.10D,
                grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.HEAD, true);
        Vec3 center = new Vec3(0.0D, 6.0D, 0.0D);

        for (int tick = 0; tick < 200; tick++) {
            body.update(Vec3.ZERO, center, center.add(head), Vec3.ZERO,
                    space(), grab, 1.0D, new Vec3(0.0D, 1.0D, 0.0D), 1.0D);
        }

        // Hanging by the head, both legs must stay inside the hip cone rather than splaying or
        // crossing the midline: those are the two failure modes the limiter exists to prevent.
        RagdollJointLimits.Frame frame = frameOf(body);
        for (boolean left : new boolean[] {true, false}) {
            double lateral = lateralAngle(body, frame, left);
            double inward = left ? -RagdollJointLimits.HIP_INWARD_LIMIT
                    : -RagdollJointLimits.HIP_OUTWARD_LIMIT;
            double outward = left ? RagdollJointLimits.HIP_OUTWARD_LIMIT
                    : RagdollJointLimits.HIP_INWARD_LIMIT;
            boolean side = left;
            assertTrue(lateral >= inward - 1.0E-6D && lateral <= outward + 1.0E-6D,
                    () -> (side ? "left" : "right") + " hip left its cone: "
                            + Math.toDegrees(lateral) + " degrees");
        }
    }

    @Test
    void theHeadStaysInsideTheNeckConeInsteadOfDetaching() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 foot = new Vec3(WIDTH * 0.18D, -HEIGHT * 0.82D, 0.04D);
        TentacleRagdollBody body = new TentacleRagdollBody(
                HEIGHT, WIDTH, 0.0F, foot, foot, 0.10D,
                grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.LEFT_FOOT, true);
        Vec3 center = new Vec3(0.0D, 8.0D, 0.0D);

        // Held upside down by one foot: the head carries the whole torso's weight through the neck.
        for (int tick = 0; tick < 200; tick++) {
            TentacleRagdollBody.Update update = body.update(Vec3.ZERO, center,
                    center.add(foot), Vec3.ZERO, space(), grab, 1.0D,
                    new Vec3(0.0D, -1.0D, 0.0D), 1.0D);
            center = center.add(update.velocity());
        }

        RagdollJointLimits.Frame frame = frameOf(body);
        Vec3 neck = nodeDelta(body, "CHEST", "HEAD");
        Vec3 local = frame.toLocal(neck.normalize());
        double pitch = Math.asin(Math.max(-1.0D, Math.min(1.0D, local.z)));
        double roll = Math.atan2(local.x, local.y);

        assertTrue(Math.abs(pitch) <= RagdollJointLimits.NECK_PITCH_LIMIT + 1.0E-6D,
                () -> "neck pitch escaped: " + Math.toDegrees(pitch));
        assertTrue(Math.abs(roll) <= RagdollJointLimits.NECK_ROLL_LIMIT + 1.0E-6D,
                () -> "neck roll escaped: " + Math.toDegrees(roll));
    }

    @Test
    void limitsHoldWhileTheBodyIsPressedAgainstTerrain() {
        TentacleGrabProfile grab = grabProfile();
        Vec3 torso = new Vec3(0.0D, HEIGHT * 0.08D, 0.0D);
        TentacleRagdollBody body = wholeBody(torso);
        // A floor the ragdoll gets dragged along: terrain corrections run after the limits in each
        // iteration, so this is where an ordering mistake would surface.
        TentacleCollisionSpace floor = new AabbTentacleCollisionSpace(
                List.of(new AABB(-8.0D, -1.0D, -8.0D, 8.0D, 0.0D, 8.0D)), 0.002D);
        Vec3 center = new Vec3(0.0D, 0.85D, 0.0D);

        for (int tick = 0; tick < 160; tick++) {
            Vec3 tip = center.add(Math.sin(tick * 0.30D) * 1.20D, -0.35D, 0.0D);
            TentacleRagdollBody.Update update = body.update(Vec3.ZERO, center, tip, Vec3.ZERO,
                    floor, grab, 1.0D, tip.subtract(center), 1.0D, 1.0D,
                    TentacleGrabMode.WRAP);
            center = center.add(update.velocity());
            assertHingeSides(body, tick);
        }

        assertNotNull(body.pose());
    }

    /**
     * Asserts no elbow or knee has folded to its forbidden side. The unsigned flexion angle is not
     * enough on its own: a joint bent 20 degrees the wrong way looks identical to one bent 20
     * degrees correctly unless the side is checked.
     */
    private static void assertHingeSides(TentacleRagdollBody body, int tick) {
        RagdollJointLimits.Frame frame = frameOf(body);
        assertHingeSide(body, frame.forward(), "LEFT_SHOULDER", "LEFT_ELBOW", "LEFT_HAND",
                "left elbow", tick);
        assertHingeSide(body, frame.forward(), "RIGHT_SHOULDER", "RIGHT_ELBOW", "RIGHT_HAND",
                "right elbow", tick);
        assertHingeSide(body, frame.forward().scale(-1.0D), "LEFT_HIP", "LEFT_KNEE", "LEFT_FOOT",
                "left knee", tick);
        assertHingeSide(body, frame.forward().scale(-1.0D), "RIGHT_HIP", "RIGHT_KNEE", "RIGHT_FOOT",
                "right knee", tick);
    }

    private static void assertHingeSide(TentacleRagdollBody body, Vec3 bendReference,
            String root, String joint, String end, String name, int tick) {
        Vec3 upper = nodeDelta(body, root, joint);
        Vec3 lower = nodeDelta(body, joint, end);
        if (upper.lengthSqr() <= 1.0E-9D || lower.lengthSqr() <= 1.0E-9D) {
            return;
        }
        Vec3 upperDirection = upper.normalize();
        Vec3 axis = bendReference.subtract(upperDirection.scale(upperDirection.dot(bendReference)));
        if (axis.lengthSqr() <= 1.0E-9D) {
            return;
        }
        axis = axis.normalize();
        Vec3 lowerDirection = lower.normalize();
        Vec3 perpendicular = lowerDirection.subtract(
                upperDirection.scale(upperDirection.dot(lowerDirection)));
        if (perpendicular.lengthSqr() <= 1.0E-6D) {
            return;
        }
        double side = perpendicular.dot(axis);
        // The solver runs terrain and distance passes after the limit, so allow a small residual
        // rather than demanding an exactly non-negative projection.
        assertTrue(side > -0.06D,
                () -> name + " hyperextended at tick " + tick + ": side projection " + side);
    }

    private static RagdollJointLimits.Frame frameOf(TentacleRagdollBody body) {
        return RagdollJointLimits.Frame.of(
                nodeDelta(body, "RIGHT_SHOULDER", "LEFT_SHOULDER"),
                nodeDelta(body, "PELVIS", "CHEST"));
    }

    private static double lateralAngle(TentacleRagdollBody body,
            RagdollJointLimits.Frame frame, boolean left) {
        Vec3 leg = left ? nodeDelta(body, "LEFT_HIP", "LEFT_FOOT")
                : nodeDelta(body, "RIGHT_HIP", "RIGHT_FOOT");
        Vec3 local = frame.toLocal(leg);
        return Math.atan2(local.x, -local.y);
    }

    private static Vec3 nodeDelta(TentacleRagdollBody body, String from, String to) {
        return body.nodePosition(to).subtract(body.nodePosition(from));
    }

    private static TentacleRagdollBody wholeBody(Vec3 torso) {
        TentacleGrabProfile grab = grabProfile();
        return new TentacleRagdollBody(HEIGHT, WIDTH, 0.0F, torso, torso, 0.18D,
                grab.wholeBodyTipRatio(), grab.surfaceClearanceScale(),
                TentacleGrabTarget.WHOLE_BODY, true);
    }

    private static AabbTentacleCollisionSpace space() {
        return new AabbTentacleCollisionSpace(List.of(), 0.002D);
    }

    private static TentacleGrabProfile grabProfile() {
        return TentacleGrabProfile.fromValues(
                MudPhysicsProfiles.tentacleDefaultValues());
    }
}
