package com.fish.mirebound.client.tentacle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Covers the cape hang geometry, which is the whole of the reported cape misalignment.
 *
 * <p>Rendering needs a {@code PoseStack} and a live player, so the angle solve is split out of
 * {@code prepareCloak} and driven directly here. The central assertion is a round trip: rebuild the
 * rotation the renderer would compose from the returned angles, apply it to the cape's rest axis, and
 * check the result actually points along gravity. That checks the solve against the transform it
 * feeds rather than against a restatement of its own formula.
 */
class TentacleGrabCapeAnimationTest {
    private static final float EPSILON = 1.0E-4F;
    /** The cape's rest axis in model space: {@code PlayerModel.cloak} hangs along +Y. */
    private static final Vector3f REST_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);

    @Test
    void anUprightTorsoKeepsVanillasRestingTilt() {
        TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.tilt(
                new Quaternionf(), Vec3.ZERO);

        // Vanilla's rest pose is 6 degrees about X with both swing terms at zero, so an upright
        // grabbed player must look exactly as they did before the grab.
        assertEquals(6.0F, tilt.pitchDegrees(), EPSILON);
        assertEquals(0.0F, tilt.rollDegrees(), EPSILON);
    }

    @Test
    void theSolvedAnglesActuallyPointTheCapeAlongGravity() {
        for (float[] axisAngle : new float[][] {
                {1.0F, 0.0F, 0.0F, 25.0F}, {1.0F, 0.0F, 0.0F, 155.0F},
                {0.0F, 0.0F, 1.0F, 40.0F}, {0.0F, 0.0F, 1.0F, -85.0F},
                {1.0F, 0.0F, 1.0F, 110.0F}, {0.60F, 0.30F, 0.74F, -140.0F},
                {0.20F, 0.90F, 0.39F, 70.0F}}) {
            Quaternionf body = new Quaternionf().rotateAxis(
                    (float) Math.toRadians(axisAngle[3]),
                    axisAngle[0], axisAngle[1], axisAngle[2]);
            Vector3f gravity = TentacleGrabCapeAnimation.gravityInTorsoFrame(body);
            if (Math.abs(gravity.x) > 0.999F) {
                // Gravity along the torso's lateral axis is the parameterization's blind spot, and
                // vanilla's cape has the same one. Covered by its own test below.
                continue;
            }

            TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.hang(gravity);
            Vector3f hung = composeAsRenderer(tilt).transform(new Vector3f(REST_AXIS));

            assertEquals(0.0F, hung.distance(gravity), 1.0E-4F,
                    "cape did not settle along gravity for " + gravity + ", got " + hung);
        }
    }

    @Test
    void anInvertedTorsoFlipsTheCapeSoItStillFallsDownward() {
        // Hanging by the feet: the torso is rotated 180 degrees about X, so model +Y — the direction
        // the cape hangs along — now points at the sky and the cape must fold the other way.
        Quaternionf inverted = new Quaternionf().rotateX((float) Math.PI);
        TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.tilt(inverted, Vec3.ZERO);

        assertEquals(180.0F, Math.abs(tilt.pitchDegrees() - 6.0F), 1.0E-3F);
        Vector3f hung = composeAsRenderer(TentacleGrabCapeAnimation.hang(
                TentacleGrabCapeAnimation.gravityInTorsoFrame(inverted)))
                .transform(new Vector3f(REST_AXIS));
        assertEquals(-1.0F, hung.y, 1.0E-4F, "inverted cape did not flip");
    }

    @Test
    void aTorsoPitchedForwardTiltsTheCapeByTheSameAngle() {
        for (double degrees : new double[] {-150.0D, -70.0D, -20.0D, 25.0D, 80.0D, 140.0D}) {
            TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.tilt(
                    new Quaternionf().rotateX((float) Math.toRadians(degrees)), Vec3.ZERO);

            // Gravity in the torso frame rotates opposite to the torso, and the cape follows gravity.
            assertEquals(6.0D - degrees, tilt.pitchDegrees(), 1.0E-3D,
                    () -> "torso pitched " + degrees + " degrees");
            assertEquals(0.0F, tilt.rollDegrees(), EPSILON);
        }
    }

    @Test
    void theHangAngleIsContinuousThroughAFullInversion() {
        // A small-angle form folds over near the poles. atan2 does not, and a cape that snaps
        // 180 degrees between two frames is the visible symptom of getting this wrong.
        float previousPitch = Float.NaN;
        for (int step = 0; step <= 720; step++) {
            float degrees = -180.0F + step * 0.5F;
            TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.tilt(
                    new Quaternionf().rotateX((float) Math.toRadians(degrees)), Vec3.ZERO);
            if (!Float.isNaN(previousPitch)) {
                float delta = Math.abs(tilt.pitchDegrees() - previousPitch);
                float wrapped = Math.min(delta, 360.0F - delta);
                assertTrue(wrapped < 2.0F,
                        "cape pitch jumped " + wrapped + " degrees at torso pitch " + degrees);
            }
            previousPitch = tilt.pitchDegrees();
        }
    }

    @Test
    void rollStaysWithinTheHalfTurnThatMakesTheSolveUnique() {
        // roll comes from asin so cos(roll) never goes negative. That is what keeps the (pitch, roll)
        // pair unique; letting roll past 90 would give two representations of every direction.
        for (int step = 0; step <= 360; step++) {
            float degrees = -180.0F + step;
            Quaternionf body = new Quaternionf().rotateZ((float) Math.toRadians(degrees));
            TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.hang(
                    TentacleGrabCapeAnimation.gravityInTorsoFrame(body));

            assertTrue(Math.abs(tilt.rollDegrees()) <= 90.0F + EPSILON,
                    "roll escaped its half turn: " + tilt.rollDegrees());
        }
    }

    @Test
    void gravityAlongTheLateralAxisStillProducesFiniteAngles() {
        // The degenerate direction: down.y and down.z both vanish. It must not yield NaN, which
        // would propagate into the pose matrix and make the cape vanish.
        TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.hang(
                new Vector3f(1.0F, 0.0F, 0.0F));

        assertTrue(Float.isFinite(tilt.pitchDegrees()), "pitch was not finite");
        assertEquals(-90.0F, tilt.rollDegrees(), 1.0E-3F);
    }

    @Test
    void gravityIsExpressedInTheTorsosOwnFrame() {
        // Rolled 90 degrees about Z. Gravity rotates opposite to the torso, so world down lands on
        // the torso's local +X and the cape must swing sideways rather than lying flat on the back.
        Vector3f down = TentacleGrabCapeAnimation.gravityInTorsoFrame(
                new Quaternionf().rotateZ((float) (Math.PI / 2.0)));

        assertEquals(1.0F, down.length(), EPSILON);
        assertEquals(1.0F, down.x, 1.0E-4F);
        assertEquals(0.0F, down.y, 1.0E-4F);
        assertEquals(-90.0F, TentacleGrabCapeAnimation.hang(down).rollDegrees(), 1.0E-3F);
        // And the composed transform really does put the cape there.
        Vector3f hung = composeAsRenderer(TentacleGrabCapeAnimation.hang(down))
                .transform(new Vector3f(REST_AXIS));
        assertEquals(0.0F, hung.distance(down), 1.0E-4F);
    }

    @Test
    void swayIsMappedIntoTheTorsoFrameAndBounded() {
        Quaternionf upright = new Quaternionf();
        Vec3 dragged = TentacleGrabCapeAnimation.swayInTorsoFrame(
                new Vec3(0.0D, 0.0D, 0.40D), upright);

        assertEquals(0.40D, dragged.z, 1.0E-6D);

        // A violent drag must not spin the cape through the body: the clamp is what keeps it cloth.
        TentacleGrabCapeAnimation.Tilt extreme = TentacleGrabCapeAnimation.tilt(
                upright, new Vec3(9.0D, 0.0D, 9.0D));
        assertEquals(6.0F - 30.0F, extreme.pitchDegrees(), EPSILON);
        assertEquals(30.0F, extreme.rollDegrees(), EPSILON);

        TentacleGrabCapeAnimation.Tilt reversed = TentacleGrabCapeAnimation.tilt(
                upright, new Vec3(-9.0D, 0.0D, -9.0D));
        assertEquals(6.0F + 30.0F, reversed.pitchDegrees(), EPSILON);
        assertEquals(-30.0F, reversed.rollDegrees(), EPSILON);
    }

    @Test
    void swayTrailsTheDragInsteadOfLeadingIt() {
        // Dragged forward along the torso's own +Z: the cape must swing backward, away from the
        // direction of travel, which is negative pitch relative to rest.
        TentacleGrabCapeAnimation.Tilt tilt = TentacleGrabCapeAnimation.tilt(
                new Quaternionf(), new Vec3(0.0D, 0.0D, 0.05D));

        assertTrue(tilt.pitchDegrees() < 6.0F,
                "cape led the drag instead of trailing it: " + tilt.pitchDegrees());
    }

    @Test
    void aStationaryPlayerHasNoSway() {
        assertEquals(Vec3.ZERO, TentacleGrabCapeAnimation.swayInTorsoFrame(
                Vec3.ZERO, new Quaternionf()));
    }

    @Test
    void swayFollowsTheDragDirectionRatherThanTheFacing() {
        // Torso turned to face world +X. A drag along world +Z is now a sideways drag in the
        // torso's own frame, so it must produce roll, not pitch.
        Quaternionf turned = new Quaternionf().rotateY((float) (Math.PI / 2.0));
        Vec3 local = TentacleGrabCapeAnimation.swayInTorsoFrame(
                new Vec3(0.0D, 0.0D, 0.25D), turned);

        assertEquals(0.25D, Math.abs(local.x), 1.0E-6D);
        assertEquals(0.0D, local.z, 1.0E-6D);
    }

    /** The rotation {@code prepareCloak} composes, minus the 180-degree yaw that only faces it out. */
    private static Quaternionf composeAsRenderer(TentacleGrabCapeAnimation.Tilt tilt) {
        return new Quaternionf()
                .mul(Axis.XP.rotationDegrees(tilt.pitchDegrees()))
                .mul(Axis.ZP.rotationDegrees(tilt.rollDegrees()));
    }
}
