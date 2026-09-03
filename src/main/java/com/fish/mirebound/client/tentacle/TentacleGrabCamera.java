package com.fish.mirebound.client.tentacle;

import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import com.fish.mirebound.mixin.client.tentacle.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import java.util.Locale;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TentacleGrabCamera {
    private static final ThreadLocal<InteractionRay> SCOPED_PICK_RAY = new ThreadLocal<>();
    private static float displayedIntensity;
    private static float referencePlayerYaw;
    private static float referencePlayerPitch;
    private static float currentInputYaw;
    private static float currentInputPitch;
    private static boolean interactionActive;
    private static int activeGrabId = -1;
    private static Quaternionf previousHeadOrientation;
    private static Quaternionf referenceBodyOrientation;
    private static Quaternionf referenceCameraOrientation;
    private static Quaternionf referenceInputOrientation;
    private static Vector3f previousCameraEuler;
    private static Quaternionf previousFinalCameraOrientation;
    private static Quaternionf currentFinalCameraOrientation;
    private static Quaternionf currentRelativeHeadOrientation;
    private static Vector3f currentCameraEuler;
    private static float currentAngularStep;
    private static Vec3 previousRagdollEye;
    private static long lastCameraTraceTick = Long.MIN_VALUE;
    private static long lastFreecamTraceTick = Long.MIN_VALUE;

    private TentacleGrabCamera() {
    }

    public static boolean hasCameraControl(Minecraft minecraft) {
        return minecraft.player != null
                && !FreecamCompat.isExternalCameraActive(minecraft)
                && TentacleClientSettings.grabCameraMode()
                        != TentacleClientSettings.GrabCameraMode.OFF
                && ClientTentacleManager.localGrab() != null;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (FreecamCompat.isExternalCameraActive(minecraft)) {
            displayedIntensity = 0.0F;
            clearCameraState();
            return;
        }
        ClientTentacleManager.GrabView grab = ClientTentacleManager.localGrab();
        float target = grab == null ? 0.0F : grab.intensity();
        displayedIntensity = Mth.lerp(target > displayedIntensity ? 0.32F : 0.18F,
                displayedIntensity, target);
        if (displayedIntensity < 0.001F && target <= 0.0F) {
            displayedIntensity = 0.0F;
        }
        if (grab == null || TentacleClientSettings.grabCameraMode() == TentacleClientSettings.GrabCameraMode.OFF) {
            clearCameraState();
        }
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        TentacleClientSettings.GrabCameraMode cameraMode = TentacleClientSettings.grabCameraMode();
        ClientTentacleManager.GrabView grab = ClientTentacleManager.localGrab((float) event.getPartialTick());
        boolean externalCamera = FreecamCompat.isExternalCameraActive(minecraft);
        if (externalCamera && grab != null) {
            traceFreecamBypass(minecraft, grab);
        }
        if (externalCamera
                || minecraft.player == null || minecraft.getCameraEntity() != minecraft.player
                || cameraMode == TentacleClientSettings.GrabCameraMode.OFF
                || grab == null || displayedIntensity <= 0.001F) {
            clearCameraState();
            return;
        }
        if (activeGrabId != grab.id()) {
            activeGrabId = grab.id();
            previousHeadOrientation = null;
            referenceBodyOrientation = new Quaternionf(
                    grab.pose().referenceOrientation()).normalize();
            referenceCameraOrientation = TentacleCameraRotation.fromCameraAngles(
                    event.getYaw(), event.getPitch(), event.getRoll(), new Quaternionf());
            referencePlayerYaw = minecraft.player.getViewYRot(
                    (float) event.getPartialTick());
            referencePlayerPitch = minecraft.player.getViewXRot(
                    (float) event.getPartialTick());
            referenceInputOrientation = TentacleCameraRotation.fromCameraAngles(
                    referencePlayerYaw, referencePlayerPitch, 0.0F, new Quaternionf());
            previousCameraEuler = null;
            previousFinalCameraOrientation = null;
            previousRagdollEye = null;
            lastCameraTraceTick = Long.MIN_VALUE;
        }
        float strength = cameraStrength(cameraMode);
        Quaternionf headOrientation = continuousHeadOrientation(grab.pose().headOrientation());
        Quaternionf drivenCamera = TentacleCameraRotation.composeRagdollCamera(
                referenceBodyOrientation, referenceCameraOrientation,
                headOrientation, strength, new Quaternionf());
        float playerYaw = minecraft.player.getViewYRot(
                (float) event.getPartialTick());
        float playerPitch = minecraft.player.getViewXRot(
                (float) event.getPartialTick());
        currentInputYaw = Mth.wrapDegrees(playerYaw - referencePlayerYaw);
        currentInputPitch = playerPitch - referencePlayerPitch;
        Quaternionf inputOrientation = TentacleCameraRotation.fromCameraAngles(
                playerYaw, playerPitch, 0.0F, new Quaternionf());
        Quaternionf cameraRotation = TentacleCameraRotation.composeViewInput(
                drivenCamera, referenceInputOrientation, inputOrientation, new Quaternionf());
        currentAngularStep = quaternionAngleDegrees(
                previousFinalCameraOrientation, cameraRotation);
        previousFinalCameraOrientation = new Quaternionf(cameraRotation);
        currentFinalCameraOrientation = new Quaternionf(cameraRotation);
        currentRelativeHeadOrientation = new Quaternionf(headOrientation);
        Vector3f angles = TentacleCameraRotation.closestEulerYxz(
                cameraRotation, previousCameraEuler, new Vector3f());
        previousCameraEuler = new Vector3f(angles);
        currentCameraEuler = new Vector3f(angles);
        float finalPitch = -(float) Math.toDegrees(angles.x);
        float finalYaw = 180.0F - (float) Math.toDegrees(angles.y);
        float finalRoll = -(float) Math.toDegrees(angles.z);
        event.setPitch(finalPitch);
        event.setYaw(finalYaw);
        event.setRoll(finalRoll);
        interactionActive = true;
    }

    public static void applyPosition(Camera camera, Entity cameraEntity, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        TentacleClientSettings.GrabCameraMode cameraMode = TentacleClientSettings.grabCameraMode();
        ClientTentacleManager.GrabView grab = ClientTentacleManager.localGrab(partialTick);
        if (minecraft.player == null || cameraEntity != minecraft.player
                || minecraft.getCameraEntity() != minecraft.player
                || FreecamCompat.isExternalCameraActive(minecraft)
                || cameraMode == TentacleClientSettings.GrabCameraMode.OFF
                || grab == null || displayedIntensity <= 0.001F) {
            return;
        }
        Vec3 vanillaEye = minecraft.player.getEyePosition(partialTick);
        Vec3 ragdollEye = ragdollEyePosition(minecraft, grab, partialTick);
        Vec3 headDelta = ragdollEye.subtract(vanillaEye);
        ((CameraAccessor) camera).mirebound$setPosition(camera.getPosition().add(headDelta));
        traceCameraFrame(minecraft, grab, vanillaEye, ragdollEye, headDelta);
    }

    private static Vec3 clampLength(Vec3 value, double maximum) {
        double length = value.length();
        return length > maximum && length > 1.0E-8D
                ? value.scale(maximum / length) : value;
    }

    private static float cameraStrength(TentacleClientSettings.GrabCameraMode cameraMode) {
        float modeScale = cameraMode == TentacleClientSettings.GrabCameraMode.IMMERSIVE ? 1.0F : 0.38F;
        return displayedIntensity * modeScale * (float) TentacleClientSettings.grabCameraStrength();
    }

    public static Vec3 interactionViewVector(Entity entity, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!hasInteractionDirection(entity, minecraft)) {
            return null;
        }
        Quaternionf camera = interactionCameraOrientation(entity, partialTick, minecraft);
        if (camera == null) {
            return null;
        }
        Vector3f forward = TentacleCameraRotation.cameraForward(camera, new Vector3f());
        return new Vec3(forward.x, forward.y, forward.z);
    }

    public static Vec3 interactionRayOrigin(Entity entity, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!hasInteractionDirection(entity, minecraft)) {
            return null;
        }
        ClientTentacleManager.GrabView grab = ClientTentacleManager.localGrab(partialTick);
        if (grab == null) {
            return null;
        }
        return ragdollEyePosition(minecraft, grab, partialTick);
    }

    private static boolean hasInteractionDirection(Entity entity, Minecraft minecraft) {
        return interactionActive && minecraft.player != null && entity == minecraft.player
                && minecraft.getCameraEntity() == entity
                && !FreecamCompat.isExternalCameraActive(minecraft);
    }

    private static Quaternionf interactionCameraOrientation(Entity entity,
            float partialTick, Minecraft minecraft) {
        ClientTentacleManager.GrabView grab = ClientTentacleManager.localGrab(partialTick);
        TentacleClientSettings.GrabCameraMode cameraMode =
                TentacleClientSettings.grabCameraMode();
        if (grab == null || referenceBodyOrientation == null
                || referenceCameraOrientation == null || referenceInputOrientation == null
                || cameraMode == TentacleClientSettings.GrabCameraMode.OFF) {
            return null;
        }
        Quaternionf drivenCamera = TentacleCameraRotation.composeRagdollCamera(
                referenceBodyOrientation, referenceCameraOrientation,
                new Quaternionf(grab.pose().headOrientation()).normalize(),
                cameraStrength(cameraMode), new Quaternionf());
        Quaternionf inputOrientation = TentacleCameraRotation.fromCameraAngles(
                entity.getViewYRot(partialTick), entity.getViewXRot(partialTick),
                0.0F, new Quaternionf());
        return TentacleCameraRotation.composeViewInput(
                drivenCamera, referenceInputOrientation, inputOrientation,
                new Quaternionf());
    }

    private static Vec3 ragdollEyePosition(Minecraft minecraft,
            ClientTentacleManager.GrabView grab, float partialTick) {
        double height = minecraft.player.getBbHeight();
        Vec3 vanillaEye = minecraft.player.getEyePosition(partialTick);
        Vec3 center = minecraft.player.getPosition(partialTick).add(0.0D, height * 0.5D, 0.0D);
        double eyeFromHeadNode = minecraft.player.getEyeHeight()
                - height * 0.5D - height * 0.43D;
        Vec3 desiredEye = center.add(grab.pose().headOffset())
                .add(TentaclePoseTransforms.headLocalOffset(
                        grab.pose(), 0.0D, eyeFromHeadNode, 0.0D));
        if (!finite(desiredEye)) {
            return vanillaEye;
        }
        Vec3 headDelta = clampLength(desiredEye.subtract(vanillaEye), height * 0.85D)
                .scale(Mth.clamp(cameraStrength(TentacleClientSettings.grabCameraMode()), 0.0F, 1.0F));
        Vec3 result = vanillaEye.add(headDelta);
        return finite(result) ? result : vanillaEye;
    }

    private static Quaternionf continuousHeadOrientation(Quaternionf current) {
        Quaternionf result = new Quaternionf(current).normalize();
        if (previousHeadOrientation != null
                && previousHeadOrientation.dot(result) < 0.0F) {
            result.set(-result.x, -result.y, -result.z, -result.w);
        }
        previousHeadOrientation = new Quaternionf(result);
        return result;
    }

    public static boolean beginScopedInteractionPick(Entity entity, float partialTick) {
        Vec3 origin = interactionRayOrigin(entity, partialTick);
        Vec3 direction = interactionViewVector(entity, partialTick);
        if (origin == null || direction == null || !finite(origin) || !finite(direction)) {
            return false;
        }
        SCOPED_PICK_RAY.set(new InteractionRay(entity, origin, direction));
        return true;
    }

    public static void endScopedInteractionPick() {
        SCOPED_PICK_RAY.remove();
    }

    public static Vec3 scopedPickOrigin(Entity entity) {
        InteractionRay ray = SCOPED_PICK_RAY.get();
        return ray != null && ray.entity() == entity ? ray.origin() : null;
    }

    public static Vec3 scopedPickDirection(Entity entity) {
        InteractionRay ray = SCOPED_PICK_RAY.get();
        return ray != null && ray.entity() == entity ? ray.direction() : null;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void traceCameraFrame(Minecraft minecraft,
            ClientTentacleManager.GrabView grab, Vec3 vanillaEye,
            Vec3 ragdollEye, Vec3 headDelta) {
        if (!TentacleCameraTraceLog.enabled() || minecraft.level == null
                || currentFinalCameraOrientation == null
                || currentRelativeHeadOrientation == null || currentCameraEuler == null) {
            previousRagdollEye = ragdollEye;
            return;
        }
        long tick = minecraft.level.getGameTime();
        double positionStep = previousRagdollEye == null
                ? 0.0D : ragdollEye.distanceTo(previousRagdollEye);
        previousRagdollEye = ragdollEye;
        boolean anomaly = currentAngularStep > 30.0F
                || positionStep > 0.45D || !finite(ragdollEye);
        if (tick == lastCameraTraceTick || (!anomaly && Math.floorMod(tick, 5L) != 0L)) {
            return;
        }
        lastCameraTraceTick = tick;
        float yaw = 180.0F - (float) Math.toDegrees(currentCameraEuler.y);
        float pitch = -(float) Math.toDegrees(currentCameraEuler.x);
        float roll = -(float) Math.toDegrees(currentCameraEuler.z);
        Quaternionf reference = referenceBodyOrientation;
        Quaternionf relative = currentRelativeHeadOrientation;
        Quaternionf camera = currentFinalCameraOrientation;
        TentacleCameraTraceLog.trace(String.format(Locale.ROOT,
                "tick=%d id=%d anomaly=%s mode=%s strength=%.4f "
                        + "angularStep=%.3f positionStep=%.5f "
                        + "angles=(yaw:%.3f,pitch:%.3f,roll:%.3f) "
                        + "input=(yaw:%.3f,pitch:%.3f) "
                        + "referenceQ=(%.5f,%.5f,%.5f,%.5f) "
                        + "headQ=(%.5f,%.5f,%.5f,%.5f) "
                        + "cameraQ=(%.5f,%.5f,%.5f,%.5f) "
                        + "vanillaEye=(%.4f,%.4f,%.4f) ragdollEye=(%.4f,%.4f,%.4f) "
                        + "headDelta=(%.4f,%.4f,%.4f)",
                tick, grab.id(), anomaly, grab.mode(),
                cameraStrength(TentacleClientSettings.grabCameraMode()),
                currentAngularStep, positionStep, yaw, pitch, roll,
                currentInputYaw, currentInputPitch,
                reference.x, reference.y, reference.z, reference.w,
                relative.x, relative.y, relative.z, relative.w,
                camera.x, camera.y, camera.z, camera.w,
                vanillaEye.x, vanillaEye.y, vanillaEye.z,
                ragdollEye.x, ragdollEye.y, ragdollEye.z,
                headDelta.x, headDelta.y, headDelta.z));
    }

    private static void traceFreecamBypass(Minecraft minecraft,
            ClientTentacleManager.GrabView grab) {
        if (!TentacleCameraTraceLog.enabled() || minecraft.level == null) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        if (tick == lastFreecamTraceTick || Math.floorMod(tick, 20L) != 0L) {
            return;
        }
        lastFreecamTraceTick = tick;
        TentacleCameraTraceLog.trace("tick=" + tick + " id=" + grab.id()
                + " event=freecam_bypass camera_transform_applied=false");
    }

    private static float quaternionAngleDegrees(Quaternionf first, Quaternionf second) {
        if (first == null || second == null) {
            return 0.0F;
        }
        float dot = Math.abs(first.dot(second));
        return (float) Math.toDegrees(
                2.0D * Math.acos(Mth.clamp(dot, 0.0F, 1.0F)));
    }

    private static void clearCameraState() {
        interactionActive = false;
        referencePlayerYaw = 0.0F;
        referencePlayerPitch = 0.0F;
        currentInputYaw = 0.0F;
        currentInputPitch = 0.0F;
        activeGrabId = -1;
        previousHeadOrientation = null;
        referenceBodyOrientation = null;
        referenceCameraOrientation = null;
        referenceInputOrientation = null;
        previousCameraEuler = null;
        previousFinalCameraOrientation = null;
        currentFinalCameraOrientation = null;
        currentRelativeHeadOrientation = null;
        currentCameraEuler = null;
        currentAngularStep = 0.0F;
        previousRagdollEye = null;
        lastCameraTraceTick = Long.MIN_VALUE;
        SCOPED_PICK_RAY.remove();
    }

    public static void reset() {
        displayedIntensity = 0.0F;
        lastFreecamTraceTick = Long.MIN_VALUE;
        clearCameraState();
    }

    private record InteractionRay(Entity entity, Vec3 origin, Vec3 direction) {
    }
}
