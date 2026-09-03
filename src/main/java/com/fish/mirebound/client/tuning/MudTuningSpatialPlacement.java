package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared camera-relative placement target for wand-spawned world objects. */
public final class MudTuningSpatialPlacement {
    public static final double MINIMUM_DISTANCE = 1.0D;
    public static final double MAXIMUM_DISTANCE = 64.0D;
    public static final double DISTANCE_STEP = 0.5D;

    private MudTuningSpatialPlacement() {
    }

    public static boolean active(Minecraft minecraft) {
        boolean volumeSelection = minecraft != null
                && minecraft.screen instanceof TentacleVolumeSelectionScreen;
        return minecraft != null && (minecraft.screen == null || volumeSelection)
                && minecraft.player != null && minecraft.level != null
                && MudTuningInputController.heldWandHand(minecraft.player) != null
                && MudTuningClientState.mode().usesSpatialPlacement()
                && (volumeSelection || MudTuningTentacleTargeting.target(minecraft) == null);
    }

    public static Vec3 target(Minecraft minecraft) {
        if (!active(minecraft)) {
            return null;
        }
        if (minecraft.screen instanceof TentacleVolumeSelectionScreen selector) {
            return selector.placementTarget();
        }
        return cameraTarget(minecraft);
    }

    public static Vec3 cameraTarget(Minecraft minecraft) {
        PlacementRay ray = ray(minecraft);
        if (ray == null) {
            return null;
        }
        return ray.origin().add(ray.direction().scale(distance()));
    }

    public static Vec3 placementDirection(Minecraft minecraft) {
        PlacementRay ray = ray(minecraft);
        return ray == null ? null : ray.direction();
    }

    public static AABB bounds(Vec3 target) {
        return new AABB(target, target).inflate(0.5D);
    }

    public static double distance() {
        return clampDistance(MudTuningClientSettings.spatialPlacementDistance());
    }

    public static void adjustDistance(double scrollDelta) {
        MudTuningClientSettings.setSpatialPlacementDistance(
                adjustedDistance(distance(), scrollDelta));
    }

    static double adjustedDistance(double current, double scrollDelta) {
        if (Math.abs(scrollDelta) < 1.0E-6D) {
            return clampDistance(current);
        }
        return clampDistance(current + Math.copySign(DISTANCE_STEP, scrollDelta));
    }

    static PlacementRay ray(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return null;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        boolean cameraFollowsPlayer = camera.getEntity() == minecraft.player;
        boolean externalCamera = FreecamCompat.isExternalCameraActive(minecraft);
        Vec3 origin;
        Vec3 direction;
        if (useCameraRay(minecraft.options.getCameraType().isFirstPerson(),
                cameraFollowsPlayer, externalCamera)) {
            origin = camera.getPosition();
            direction = new Vec3(camera.getLookVector());
        } else {
            origin = minecraft.player.getEyePosition(partialTick);
            direction = minecraft.player.getViewVector(partialTick);
        }
        if (direction.lengthSqr() < 1.0E-8D) {
            return null;
        }
        return new PlacementRay(origin, direction.normalize(), partialTick);
    }

    static boolean useCameraRay(
            boolean firstPerson, boolean cameraFollowsPlayer, boolean externalCamera) {
        return firstPerson && cameraFollowsPlayer && !externalCamera;
    }

    private static double clampDistance(double value) {
        return Math.max(MINIMUM_DISTANCE, Math.min(MAXIMUM_DISTANCE, value));
    }

    record PlacementRay(Vec3 origin, Vec3 direction, float partialTick) {
    }
}
