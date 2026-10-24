package com.fish.mirebound.client;

import com.fish.mirebound.assimilation.AssimilationProfile;
import com.fish.mirebound.assimilation.AssimilationSoulMotion;
import com.fish.mirebound.assimilation.AssimilationStage;
import com.fish.mirebound.mixin.client.tentacle.CameraAccessor;
import com.fish.mirebound.network.payload.AssimilationSoulPositionPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owner-only collisionless soul camera. The authoritative player remains at the body. */
public final class AssimilationSoulCamera {
    private static Vec3 relativePosition;
    private static Vec3 previousRelativePosition;
    private static Vec3 restoreStart;
    private static Vec3 velocity = Vec3.ZERO;
    private static boolean stasisActiveLastTick;
    private static boolean restoringLastTick;
    private static float yaw;
    private static float pitch;
    private static int lockedHotbarSlot = -1;
    private static double floatPhase;
    private static int positionSyncTicks;

    private AssimilationSoulCamera() {
    }

    public static void tick(Minecraft minecraft) {
        if (!ClientAssimilationState.localStasisActive(minecraft) || minecraft.player == null) {
            relativePosition = null;
            previousRelativePosition = null;
            restoreStart = null;
            velocity = Vec3.ZERO;
            stasisActiveLastTick = false;
            restoringLastTick = false;
            lockedHotbarSlot = -1;
            floatPhase = 0.0D;
            positionSyncTicks = 0;
            return;
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(minecraft.player.getId());
        if (view == null) {
            return;
        }
        if (!stasisActiveLastTick || relativePosition == null) {
            relativePosition = bodyEyeOffset(minecraft);
            previousRelativePosition = relativePosition;
            velocity = Vec3.ZERO;
            yaw = minecraft.player.getYRot();
            pitch = minecraft.player.getXRot();
            lockedHotbarSlot = minecraft.player.getInventory().selected;
            stasisActiveLastTick = true;
        }
        if (lockedHotbarSlot >= 0) {
            minecraft.player.getInventory().selected = lockedHotbarSlot;
        }
        previousRelativePosition = relativePosition;
        if (view.stage() == AssimilationStage.SEALED
                && view.soulTransitionTicks() > 0) {
            restoringLastTick = false;
            Vec3 currentBodyEye = bodyEyeOffset(minecraft);
            Vec3 emergenceTarget = emergenceTarget(view, currentBodyEye);
            float total = Math.max(1.0F, view.profile().soulTransitionTicks());
            float progress = 1.0F - Mth.clamp(view.soulTransitionTicks() / total, 0.0F, 1.0F);
            double eased = AssimilationSoulMotion.smoothTransition(progress);
            relativePosition = currentBodyEye.lerp(emergenceTarget, eased);
            velocity = Vec3.ZERO;
            syncPosition();
            return;
        }
        if (view.stage() == AssimilationStage.RESTORING) {
            if (!restoringLastTick || restoreStart == null) {
                restoreStart = relativePosition;
                restoringLastTick = true;
            }
            float total = Math.max(1.0F, view.profile().restoreTicks());
            float elapsed = 1.0F - Mth.clamp(view.restoringTicks() / total, 0.0F, 1.0F);
            double returnProgress = AssimilationSoulMotion.restoreReturnProgress(elapsed);
            relativePosition = restoreStart.lerp(bodyEyeOffset(minecraft), returnProgress);
            velocity = Vec3.ZERO;
            syncPosition();
            return;
        }
        restoringLastTick = false;
        restoreStart = null;
        Vec3 input = minecraft.screen == null ? movementInput(minecraft) : Vec3.ZERO;
        AssimilationProfile profile = view.profile();
        if (!AssimilationQteClient.stabilizeCamera(view)) {
            floatPhase += profile.soulFloatSpeed();
        }
        double speed = profile.soulMoveSpeed();
        if (minecraft.screen == null && minecraft.options.keySprint.isDown()) {
            speed *= profile.soulSprintMultiplier();
        }
        velocity = AssimilationSoulMotion.updateVelocity(velocity, input, speed,
                profile.soulAcceleration(), profile.soulDrag());
        Vec3 center = new Vec3(0.0D, minecraft.player.getEyeHeight() * 0.5D, 0.0D);
        AssimilationSoulMotion.Step step = AssimilationSoulMotion.advance(
                relativePosition, velocity, center, profile.soulRadius(), profile.soulBoundarySoftness());
        relativePosition = step.position();
        velocity = step.velocity();
        syncPosition();
    }

    public static void applyPosition(Camera camera, Entity cameraEntity, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (relativePosition != null && minecraft.player != null && cameraEntity == minecraft.player
                && ClientAssimilationState.localStasisActive(minecraft)) {
            ClientAssimilationState.View view = ClientAssimilationState.view(minecraft.player.getId());
            Vec3 rendered = renderPosition(partialTick);
            if (view == null || rendered == null) {
                return;
            }
            if (ClientAssimilationState.localSoulActive(minecraft)) {
                double renderPhase = AssimilationQteClient.stabilizeCamera(view)
                        ? floatPhase : floatPhase + partialTick * view.profile().soulFloatSpeed();
                double bob = Math.sin(renderPhase)
                        * view.profile().soulFloatAmplitude();
                rendered = rendered.add(0.0D, bob, 0.0D);
            }
            CameraAccessor accessor = (CameraAccessor) camera;
            accessor.mirebound$setPosition(rendered);
            accessor.mirebound$setRotation(yaw, pitch, 0.0F);
        }
    }

    public static void turnYaw(double input) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureAngles(minecraft);
        yaw += (float) input * 0.15F;
    }

    public static void turnPitch(double input) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureAngles(minecraft);
        pitch = Mth.clamp(pitch + (float) input * 0.15F, -90.0F, 90.0F);
    }

    public static Vec3 position() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientAssimilationState.View view = minecraft.player == null ? null
                : ClientAssimilationState.view(minecraft.player.getId());
        return view == null || relativePosition == null ? null
                : view.anchor().add(relativePosition);
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    static Vec3 renderPosition(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientAssimilationState.View view = minecraft.player == null ? null
                : ClientAssimilationState.view(minecraft.player.getId());
        if (relativePosition == null || view == null) {
            return null;
        }
        Vec3 from = previousRelativePosition == null ? relativePosition : previousRelativePosition;
        return AssimilationSoulMotion.relativeRenderPosition(
                view.renderAnchor(partialTick), from, relativePosition, partialTick);
    }

    public static void reset() {
        relativePosition = null;
        previousRelativePosition = null;
        restoreStart = null;
        velocity = Vec3.ZERO;
        stasisActiveLastTick = false;
        restoringLastTick = false;
        yaw = 0.0F;
        pitch = 0.0F;
        lockedHotbarSlot = -1;
        floatPhase = 0.0D;
        positionSyncTicks = 0;
    }

    private static void ensureAngles(Minecraft minecraft) {
        if (!stasisActiveLastTick && minecraft.player != null) {
            yaw = minecraft.player.getYRot();
            pitch = minecraft.player.getXRot();
        }
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1.0D : 0.0D) - (negative ? 1.0D : 0.0D);
    }

    private static Vec3 movementInput(Minecraft minecraft) {
        double forward = axis(minecraft.options.keyUp.isDown(), minecraft.options.keyDown.isDown());
        double strafe = axis(minecraft.options.keyLeft.isDown(), minecraft.options.keyRight.isDown());
        double vertical = AssimilationSoulMotion.verticalInput(
                minecraft.options.keyJump.isDown(), minecraft.options.keyShift.isDown());
        return AssimilationSoulMotion.inputDirection(yaw, forward, strafe, vertical);
    }

    private static Vec3 bodyEyeOffset(Minecraft minecraft) {
        return new Vec3(0.0D, minecraft.player.getEyeHeight(), 0.0D);
    }

    private static Vec3 emergenceTarget(ClientAssimilationState.View view, Vec3 bodyEye) {
        return AssimilationSoulMotion.emergenceTarget(
                bodyEye, view.frozenYaw(), view.profile().soulEmergenceBackOffset(),
                view.profile().soulEmergenceUpOffset());
    }

    private static void syncPosition() {
        Vec3 position = position();
        if (position != null && positionSyncTicks-- <= 0) {
            PacketDistributor.sendToServer(new AssimilationSoulPositionPayload(
                    (float) position.x, (float) position.y, (float) position.z));
            positionSyncTicks = 3;
        }
    }
}
