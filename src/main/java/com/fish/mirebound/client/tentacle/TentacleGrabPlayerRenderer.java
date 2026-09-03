package com.fish.mirebound.client.tentacle;

import com.fish.mirebound.mixin.client.tentacle.WalkAnimationStateAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Applies the synchronized ragdoll after vanilla setupAnim and restores shared models afterward. */
public final class TentacleGrabPlayerRenderer {
    private static final ThreadLocal<Deque<RenderState>> STATES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private TentacleGrabPlayerRenderer() {
    }

    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        ClientTentacleManager.GrabView grab = ClientTentacleManager.grabForEntity(
                event.getEntity().getId(), event.getPartialTick());
        if (grab == null || grab.intensity() <= 0.001F) {
            return;
        }
        RenderState state = new RenderState(event.getEntity().getId(), grab);
        state.savedWalkAnimation = WalkState.capture(event.getEntity().walkAnimation);
        state.savedWalkAnimation.clear(event.getEntity().walkAnimation);
        STATES.get().push(state);
    }

    /**
     * Applies the ragdoll's whole-body rotation.
     *
     * <p>Called from {@code LivingEntityRenderer.setupRotations}, which runs inside the
     * {@code PoseStack} scope vanilla pops before drawing the name tag — so unlike the
     * {@code RenderPlayerEvent.Pre} transform this replaces, it cannot tilt the player's name.
     * {@code PlayerRenderer} overrides {@code setupRotations} but opens every branch with a
     * {@code super} call, so injecting at the parent's head still lands outermost, keeping the
     * ragdoll rotation outside vanilla's {@code 180 - yBodyRot} yaw exactly as before.
     *
     * <p>This runs for every living entity, so the no-grab case returns before touching the stack.
     */
    public static void applyBodyRotation(Entity entity, PoseStack poseStack, float scale) {
        Deque<RenderState> stack = STATES.get();
        if (stack.isEmpty() || stack.peek().entityId != entity.getId()) {
            return;
        }
        rotateAboutCentre(poseStack, stack.peek().grab.pose().bodyOrientation(),
                entity.getBbHeight(), scale);
    }

    /**
     * Rotates about the entity's own centre rather than its feet, so a spun body stays where the
     * tentacle is holding it instead of orbiting the block position.
     *
     * <p>{@code scale} divides the pivot because vanilla has already applied the entity scale to the
     * stack by the time {@code setupRotations} runs; a raw block offset would otherwise be scaled
     * twice on a non-standard-sized player and the body would rotate about the wrong point.
     */
    static void rotateAboutCentre(PoseStack poseStack, Quaternionf rotation,
            float height, float scale) {
        float pivot = height * 0.5F / Math.max(1.0E-4F, scale);
        poseStack.translate(0.0F, pivot, 0.0F);
        poseStack.mulPose(rotation);
        poseStack.translate(0.0F, -pivot, 0.0F);
    }

    /** Called by the renderer mixin immediately after vanilla has produced its final animated pose. */
    public static boolean prepareBeforeSetup(AbstractClientPlayer player,
            PlayerModel<AbstractClientPlayer> model, float netHeadYaw, float headPitch) {
        Deque<RenderState> stack = STATES.get();
        if (stack.isEmpty() || stack.peek().entityId != player.getId()) {
            return false;
        }
        RenderState state = stack.peek();
        // Vanilla's own head rotation is discarded along with the rest of the animated pose, so the
        // look angles are captured here and recomposed onto the neck in applyAfterSetup.
        state.headYaw = netHeadYaw;
        state.headPitch = headPitch;
        if (state.savedAnimationState == null) {
            state.savedAnimationState = AnimationState.capture(model);
        }
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0.0F;
        model.swimAmount = 0.0F;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        return true;
    }

    /** Called by the renderer mixin immediately after vanilla has produced its final animated pose. */
    public static void applyAfterSetup(AbstractClientPlayer player,
            PlayerModel<AbstractClientPlayer> model) {
        Deque<RenderState> stack = STATES.get();
        if (stack.isEmpty() || stack.peek().entityId != player.getId()) {
            return;
        }
        RenderState state = stack.peek();
        if (state.savedPose != null) {
            return;
        }
        state.savedPose = SavedPose.capture(model);
        ClientTentacleManager.RagdollPose pose = state.grab.pose();
        model.body.xRot = 0.0F;
        model.body.yRot = 0.0F;
        model.body.zRot = 0.0F;
        applyHeadRotation(model, pose, state.headYaw, state.headPitch);
        model.hat.copyFrom(model.head);
        applyArmDirection(model.leftArm, pose.leftArmDirection());
        applyArmDirection(model.rightArm, pose.rightArmDirection());
        applyLegDirection(model.leftLeg, pose.leftLegDirection());
        applyLegDirection(model.rightLeg, pose.rightLegDirection());
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    /**
     * Composes the neck's simulated tilt with the player's own look direction.
     *
     * <p>The head used to be driven purely from the ragdoll: vanilla's {@code netHeadYaw} and
     * {@code headPitch} were forced to zero while grabbed, and the head rotation was rebuilt from
     * the ragdoll's neck bone alone. The neck bone has no yaw information at all — a bone direction
     * cannot express rotation about its own axis — so the head could only ever pitch and roll with
     * the torso. It read as welded in place: a player being carried could not look around.
     *
     * <p>Now the neck supplies the ragdoll's physical tilt and the look angles supply yaw and pitch
     * on top, which is also the order a real neck works in.
     *
     * <p>The head translation the previous implementation applied is gone deliberately. It tried to
     * reconcile the ragdoll head node's world offset against the rigid model's head socket and push
     * the difference into {@code model.head}'s pivot, but the model's head is not free-floating —
     * moving its pivot detaches it from the neck opening in the torso cuboid, which is exactly the
     * misalignment reported. The neck cone in {@code RagdollJointLimits} now bounds the tilt
     * instead, so the head stays on its socket and the offset never needs correcting.
     */
    private static void applyHeadRotation(PlayerModel<AbstractClientPlayer> model,
            ClientTentacleManager.RagdollPose pose, float headYaw, float headPitch) {
        Quaternionf neck = new Quaternionf(pose.bodyOrientation()).conjugate()
                .mul(pose.headOrientation()).normalize();
        Quaternionf look = new Quaternionf()
                .rotateY(headYaw * ((float) Math.PI / 180.0F))
                .rotateX(headPitch * ((float) Math.PI / 180.0F));
        applyQuaternion(model.head, neck.mul(look).normalize());
    }

    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Deque<RenderState> stack = STATES.get();
        if (stack.isEmpty() || stack.peek().entityId != event.getEntity().getId()) {
            return;
        }
        RenderState state = stack.pop();
        if (state.savedPose != null) {
            state.savedPose.restore();
        }
        if (state.savedAnimationState != null) {
            state.savedAnimationState.restore(event.getRenderer().getModel());
        }
        if (state.savedWalkAnimation != null) {
            state.savedWalkAnimation.restore(event.getEntity().walkAnimation);
        }
        if (stack.isEmpty()) {
            STATES.remove();
        }
    }

    /** Used by client-only entity mixins to hide action state during this render call. */
    public static boolean suppressesAnimation(Entity entity) {
        if (!(entity instanceof AbstractClientPlayer)) {
            return false;
        }
        for (RenderState state : STATES.get()) {
            if (state.entityId == entity.getId()) {
                return true;
            }
        }
        return false;
    }

    static void applyArmDirection(ModelPart part, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vector3f angles = TentacleModelPartRotations.armEuler(direction);
        part.xRot = angles.x;
        part.yRot = angles.y;
        part.zRot = angles.z;
    }

    static void applyLegDirection(ModelPart part, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vector3f angles = TentacleModelPartRotations.legEuler(direction);
        part.xRot = angles.x;
        part.yRot = angles.y;
        part.zRot = angles.z;
    }

    private static void applyQuaternion(ModelPart part, Quaternionf rotation) {
        Vector3f angles = rotation.getEulerAnglesXYZ(new Vector3f());
        part.xRot = angles.x;
        part.yRot = angles.y;
        part.zRot = angles.z;
    }

    private static final class RenderState {
        private final int entityId;
        private final ClientTentacleManager.GrabView grab;
        private SavedPose savedPose;
        private AnimationState savedAnimationState;
        private WalkState savedWalkAnimation;
        private float headYaw;
        private float headPitch;

        private RenderState(int entityId, ClientTentacleManager.GrabView grab) {
            this.entityId = entityId;
            this.grab = grab;
        }
    }

    private record WalkState(float speedOld, float speed, float position) {
        static WalkState capture(WalkAnimationState animation) {
            WalkAnimationStateAccessor accessor = (WalkAnimationStateAccessor) animation;
            return new WalkState(accessor.mirebound$getSpeedOld(),
                    accessor.mirebound$getSpeed(), accessor.mirebound$getPosition());
        }

        void clear(WalkAnimationState animation) {
            WalkAnimationStateAccessor accessor = (WalkAnimationStateAccessor) animation;
            accessor.mirebound$setSpeedOld(0.0F);
            accessor.mirebound$setSpeed(0.0F);
            accessor.mirebound$setPosition(0.0F);
        }

        void restore(WalkAnimationState animation) {
            WalkAnimationStateAccessor accessor = (WalkAnimationStateAccessor) animation;
            accessor.mirebound$setSpeedOld(speedOld);
            accessor.mirebound$setSpeed(speed);
            accessor.mirebound$setPosition(position);
        }
    }

    private record PartPose(ModelPart part, float x, float y, float z,
            float xRot, float yRot, float zRot) {
        static PartPose capture(ModelPart part) {
            return new PartPose(part, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot);
        }

        void restore() {
            part.x = x;
            part.y = y;
            part.z = z;
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
        }
    }

    private record SavedPose(PartPose head, PartPose hat, PartPose body, PartPose jacket,
            PartPose leftArm, PartPose leftSleeve, PartPose rightArm, PartPose rightSleeve,
            PartPose leftLeg, PartPose leftPants, PartPose rightLeg, PartPose rightPants) {
        static SavedPose capture(PlayerModel<AbstractClientPlayer> model) {
            return new SavedPose(PartPose.capture(model.head), PartPose.capture(model.hat),
                    PartPose.capture(model.body), PartPose.capture(model.jacket),
                    PartPose.capture(model.leftArm), PartPose.capture(model.leftSleeve),
                    PartPose.capture(model.rightArm), PartPose.capture(model.rightSleeve),
                    PartPose.capture(model.leftLeg), PartPose.capture(model.leftPants),
                    PartPose.capture(model.rightLeg), PartPose.capture(model.rightPants));
        }

        void restore() {
            head.restore();
            hat.restore();
            body.restore();
            jacket.restore();
            leftArm.restore();
            leftSleeve.restore();
            rightArm.restore();
            rightSleeve.restore();
            leftLeg.restore();
            leftPants.restore();
            rightLeg.restore();
            rightPants.restore();
        }
    }

    private record AnimationState(boolean crouching, boolean riding, float attackTime,
            float swimAmount, HumanoidModel.ArmPose leftArmPose,
            HumanoidModel.ArmPose rightArmPose) {
        static AnimationState capture(PlayerModel<AbstractClientPlayer> model) {
            return new AnimationState(model.crouching, model.riding, model.attackTime,
                    model.swimAmount, model.leftArmPose, model.rightArmPose);
        }

        void restore(PlayerModel<?> model) {
            model.crouching = crouching;
            model.riding = riding;
            model.attackTime = attackTime;
            model.swimAmount = swimAmount;
            model.leftArmPose = leftArmPose;
            model.rightArmPose = rightArmPose;
        }
    }
}
