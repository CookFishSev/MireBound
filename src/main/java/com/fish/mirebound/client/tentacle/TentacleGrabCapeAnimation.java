package com.fish.mirebound.client.tentacle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hangs a grabbed player's cape from the ragdoll's torso instead of from their movement.
 *
 * <p>Vanilla's {@code CapeLayer} derives the cape's swing entirely from entity motion: the
 * {@code xCloak}/{@code yCloak} trail, {@code yBodyRot}, {@code bob} and {@code walkDist}. None of
 * those know the player is being carried, and {@code PlayerModel.cloak} is parented to the model
 * root rather than to {@code body}, so it does not inherit the torso's pose either. The result was a
 * cape still swinging as if the player were running upright while their body hung inverted in a
 * tentacle's grip — the reported cape misalignment.
 *
 * <p>The cape hangs along the model's +Y from a pivot on the shoulder line, and model space is
 * Y-down relative to the world because vanilla scales by {@code -1, -1, 1} before rendering. Gravity
 * in that space therefore points along model +Y, and a cape at rest lies along it. This computes
 * that gravity direction inside the rotated torso's own frame and solves for the two rotations that
 * lay the cape back along it, which reproduces vanilla's rest pose for an upright player and keeps
 * the cape falling downward however the torso is turned.
 *
 * <p>Only the hang direction is simulated. A real cape also has its own inertia and can billow away
 * from gravity entirely, which would need per-frame cloth state; the sway term is a bounded stand-in
 * that keeps a thrashed cape reading as cloth without any of that state.
 */
public final class TentacleGrabCapeAnimation {
    /**
     * Vanilla's baseline cape tilt in degrees. Its rest pose is {@code 6 + f2/2 + f1} about X with
     * both swing terms at zero, so a stationary upright player's cape sits 6 degrees off the back.
     * Reusing it means an upright grabbed player's cape looks unchanged.
     */
    private static final float REST_TILT = 6.0F;
    /** How much physical sway to keep, so a thrashed cape still reads as cloth rather than board. */
    private static final float SWAY_STRENGTH = 260.0F;
    private static final float MAXIMUM_SWAY = 30.0F;
    private static final ThreadLocal<Base> BASE = new ThreadLocal<>();

    private TentacleGrabCapeAnimation() {
    }

    /** Captures the frame the cape transform is built relative to, before vanilla modifies it. */
    public static void begin(AbstractClientPlayer player, PoseStack poseStack) {
        if (ClientTentacleManager.grabForEntity(player.getId(), 1.0F) == null) {
            return;
        }
        BASE.set(new Base(player.getId(),
                new Matrix4f(poseStack.last().pose()),
                new Matrix3f(poseStack.last().normal())));
    }

    /**
     * Replaces vanilla's motion-driven cape transform with a gravity hang off the ragdoll torso.
     *
     * @return {@code true} when this took over the transform
     */
    public static boolean prepareCloak(AbstractClientPlayer player, PoseStack poseStack,
            float partialTick) {
        Base base = BASE.get();
        if (base == null || base.entityId != player.getId()) {
            return false;
        }
        ClientTentacleManager.GrabView grab = ClientTentacleManager.grabForEntity(
                player.getId(), partialTick);
        if (grab == null || grab.intensity() <= 0.001F) {
            return false;
        }

        // Discard everything vanilla just composed and rebuild from the captured frame.
        poseStack.last().pose().set(base.pose());
        poseStack.last().normal().set(base.normal());
        poseStack.translate(0.0F, 0.0F, 0.125F);

        Tilt tilt = tilt(grab.pose().bodyOrientation(),
                swayInTorsoFrame(player, grab.pose().bodyOrientation(), partialTick));
        poseStack.mulPose(Axis.XP.rotationDegrees(tilt.pitchDegrees()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tilt.rollDegrees()));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        return true;
    }

    /**
     * The cape's hang angles for a torso orientation and a sway vector, in degrees.
     *
     * <p>Separated from the {@link PoseStack} work so the geometry is testable without a render
     * context: everything that decides where the cape ends up lives here.
     */
    static Tilt tilt(Quaternionf bodyOrientation, Vec3 swayInTorsoFrame) {
        Tilt hang = hang(gravityInTorsoFrame(bodyOrientation));
        // Sway is subtracted from pitch and added to roll so the cape trails the drag rather than
        // leading it: the hang axis moves against whichever way the body is being hauled.
        return new Tilt(REST_TILT + hang.pitchDegrees() - clampSway((float) swayInTorsoFrame.z),
                hang.rollDegrees() + clampSway((float) swayInTorsoFrame.x));
    }

    /**
     * Solves for the two angles that lay the cape's hang axis along {@code down}.
     *
     * <p>Vanilla composes {@code XP(pitch)} then {@code ZP(roll)}, which sends the cape's rest axis
     * {@code +Y} to {@code (-sin roll, cos pitch cos roll, sin pitch cos roll)}. Inverting that gives
     * {@code roll = -asin(down.x)} and {@code pitch = atan2(down.z, down.y)} — an exact solution, not
     * a small-angle approximation. Taking {@code roll} from {@code asin} keeps it inside
     * {@code [-90, 90]} so {@code cos roll} stays non-negative, which is what makes the pair unique;
     * {@code atan2} then covers the full turn, so pitch stays continuous through a complete
     * inversion where a small-angle form would fold over.
     *
     * <p>Gravity pointing exactly along the torso's own lateral axis is the one direction this
     * parameterization cannot resolve — {@code down.y} and {@code down.z} both vanish and pitch
     * becomes arbitrary. Vanilla's cape has the same two degrees of freedom and the same blind spot,
     * and the roll limit means the cape is edge-on there anyway.
     */
    static Tilt hang(Vector3f down) {
        float roll = (float) -Math.toDegrees(Math.asin(Mth.clamp(down.x, -1.0F, 1.0F)));
        float pitch = (float) Math.toDegrees(Math.atan2(down.z, down.y));
        return new Tilt(pitch, roll);
    }

    /** Cape hang angles in degrees: {@code pitch} about model X, {@code roll} about model Z. */
    record Tilt(float pitchDegrees, float rollDegrees) {
    }

    public static void end(AbstractClientPlayer player) {
        Base base = BASE.get();
        if (base != null && base.entityId == player.getId()) {
            BASE.remove();
        }
    }

    /**
     * World-down expressed in the rotated torso's frame. The pose stack already carries the body
     * rotation, so the inverse of that rotation maps the world's gravity into this frame; model
     * space being Y-down is why the source vector is {@code +Y}.
     */
    static Vector3f gravityInTorsoFrame(Quaternionf bodyOrientation) {
        return new Quaternionf(bodyOrientation).conjugate()
                .transform(new Vector3f(0.0F, 1.0F, 0.0F));
    }

    /**
     * The player's per-tick displacement, mapped into the same torso frame, so the cape trails the
     * direction the tentacle is actually dragging the body rather than the direction they face.
     */
    static Vec3 swayInTorsoFrame(AbstractClientPlayer player, Quaternionf bodyOrientation,
            float partialTick) {
        return swayInTorsoFrame(new Vec3(
                Mth.lerp(partialTick, player.xOld, player.getX()) - player.xOld,
                Mth.lerp(partialTick, player.yOld, player.getY()) - player.yOld,
                Mth.lerp(partialTick, player.zOld, player.getZ()) - player.zOld),
                bodyOrientation);
    }

    /** The frame-mapping half of {@link #swayInTorsoFrame}, split out so it is testable. */
    static Vec3 swayInTorsoFrame(Vec3 velocity, Quaternionf bodyOrientation) {
        if (velocity.lengthSqr() <= 1.0E-10D) {
            return Vec3.ZERO;
        }
        Vector3f local = new Quaternionf(bodyOrientation).conjugate().transform(
                new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z));
        return new Vec3(local.x, local.y, local.z);
    }

    private static float clampSway(float component) {
        return Mth.clamp(component * SWAY_STRENGTH, -MAXIMUM_SWAY, MAXIMUM_SWAY);
    }

    private record Base(int entityId, Matrix4f pose, Matrix3f normal) {
    }
}
