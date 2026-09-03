package com.fish.mirebound.client.tentacle;

import com.fish.mirebound.network.payload.TentacleStateSyncPayload;
import com.fish.mirebound.tentacle.TentacleGrabMode;
import com.fish.mirebound.tentacle.TentacleGrabTarget;
import com.fish.mirebound.tentacle.TentaclePhase;
import com.fish.mirebound.tentacle.TentacleRaycast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class ClientTentacleManager {
    private static final int STALE_TICKS = 60;
    private static final Map<Integer, ClientInstance> INSTANCES = new HashMap<>();
    private static Object activeLevel;

    private ClientTentacleManager() {
    }

    public static void accept(TentacleStateSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft)) {
            return;
        }
        if (payload.removed()) {
            INSTANCES.remove(payload.instanceId());
            return;
        }
        long tick = minecraft.level.getGameTime();
        ClientInstance instance = INSTANCES.get(payload.instanceId());
        if (instance == null) {
            INSTANCES.put(payload.instanceId(), new ClientInstance(payload, tick));
        } else {
            instance.accept(payload, tick);
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft)) {
            return;
        }
        long now = minecraft.level.getGameTime();
        INSTANCES.values().removeIf(instance -> now - instance.receivedTick > STALE_TICKS);
    }

    static List<View> views(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft) || INSTANCES.isEmpty()) {
            return List.of();
        }
        double now = minecraft.level.getGameTime() + partialTick;
        List<View> views = new ArrayList<>(INSTANCES.size());
        for (ClientInstance instance : INSTANCES.values()) {
            List<Vec3> points = instance.interpolate(now);
            if (points.size() >= 2) {
                views.add(new View(instance.id, instance.phase, instance.visualSeed, instance.visualAge(now),
                        instance.rootRadius, instance.tipRadius, instance.grabbedEntityId,
                        instance.grabMode, instance.grabIntensity, instance.interpolatePose(now),
                        points, bounds(points, instance.rootRadius)));
            }
        }
        return views;
    }

    public static void reset() {
        INSTANCES.clear();
        activeLevel = null;
    }

    public static TentacleTarget target(Vec3 origin, Vec3 direction,
            double maximumDistance, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft) || origin == null || direction == null
                || direction.lengthSqr() < 1.0E-8D) {
            return null;
        }
        double now = minecraft.level.getGameTime() + partialTick;
        TentacleTarget selected = null;
        double selectedDistance = maximumDistance + 1.0D;
        for (ClientInstance instance : INSTANCES.values()) {
            List<Vec3> points = instance.interpolate(now);
            if (points.isEmpty()) {
                continue;
            }
            Vec3 root = points.getFirst();
            TentacleRaycast.SphereHit hit = TentacleRaycast.raycastSphere(
                    origin, direction, maximumDistance, root,
                    instance.rootRadius + 0.20D);
            if (hit != null && hit.rayDistance() < selectedDistance) {
                selected = new TentacleTarget(
                        instance.id, root, root, hit.rayDistance());
                selectedDistance = hit.rayDistance();
            }
        }
        return selected;
    }

    static GrabView localGrab() {
        return localGrab(0.0F);
    }

    static GrabView localGrab(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        return grabForEntity(minecraft.player.getId(), partialTick);
    }

    static GrabView grabForEntity(int entityId, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureLevel(minecraft)) {
            return null;
        }
        double now = minecraft.level.getGameTime() + partialTick;
        ClientInstance selected = null;
        for (ClientInstance instance : INSTANCES.values()) {
            if (instance.grabbedEntityId != entityId || instance.grabIntensity <= 0.0F) {
                continue;
            }
            if (selected == null || instance.grabIntensity > selected.grabIntensity
                    || (instance.grabIntensity == selected.grabIntensity
                            && instance.id < selected.id)) {
                selected = instance;
            }
        }
        if (selected == null) {
            return null;
        }
        List<Vec3> points = selected.interpolate(now);
        return points.size() < 2 ? null
                : new GrabView(selected.id, selected.grabMode, selected.grabIntensity,
                        selected.visualAge(now), selected.rootRadius,
                        selected.interpolatePose(now), points);
    }

    private static boolean ensureLevel(Minecraft minecraft) {
        if (minecraft.level != activeLevel) {
            INSTANCES.clear();
            activeLevel = minecraft.level;
        }
        return minecraft.level != null;
    }

    private static AABB bounds(List<Vec3> points, float radius) {
        Vec3 first = points.getFirst();
        AABB bounds = new AABB(first, first);
        for (Vec3 point : points) {
            bounds = bounds.minmax(new AABB(point, point));
        }
        return bounds.inflate(radius + 0.08D);
    }

    record View(int id, TentaclePhase phase, long visualSeed, double age,
            float rootRadius, float tipRadius, int grabbedEntityId,
            TentacleGrabMode grabMode, float grabIntensity, RagdollPose grabPose,
            List<Vec3> points, AABB bounds) {
    }

    public record TentacleTarget(int instanceId, Vec3 position,
            Vec3 rootPosition, double distance) {
    }

    record GrabView(int id, TentacleGrabMode mode, float intensity,
            double age, float rootRadius, RagdollPose pose, List<Vec3> points) {
    }

    record RagdollPose(Quaternionf bodyOrientation, Quaternionf headOrientation,
            Quaternionf referenceOrientation,
            Vec3 headOffset, TentacleGrabTarget grabTarget, Vec3 gripOffset,
            Vec3 leftArmDirection, Vec3 rightArmDirection,
            Vec3 leftLegDirection, Vec3 rightLegDirection) {
    }

    private static final class ClientInstance {
        private final int id;
        private TentaclePhase phase;
        private long visualSeed;
        private float rootRadius;
        private float tipRadius;
        private int grabbedEntityId;
        private TentacleGrabMode grabMode;
        private float grabIntensity;
        private RagdollPose fromPose;
        private RagdollPose targetPose;
        private List<Vec3> fromPoints;
        private List<Vec3> targetPoints;
        private long receivedTick;
        private int interpolationTicks;
        private int serverAge;
        private double cachedPointsTime = Double.NaN;
        private List<Vec3> cachedPoints;
        private double cachedPoseTime = Double.NaN;
        private RagdollPose cachedPose;

        ClientInstance(TentacleStateSyncPayload payload, long tick) {
            id = payload.instanceId();
            phase = payload.phase();
            visualSeed = payload.visualSeed();
            serverAge = payload.age();
            rootRadius = payload.rootRadius();
            tipRadius = payload.tipRadius();
            grabbedEntityId = payload.grabbedEntityId();
            grabMode = payload.grabMode();
            grabIntensity = payload.grabIntensity();
            fromPose = pose(payload);
            targetPose = pose(payload);
            fromPoints = payload.points();
            targetPoints = payload.points();
            receivedTick = tick;
            interpolationTicks = Math.max(payload.syncIntervalTicks(), TentacleClientSettings.interpolationTicks());
        }

        void accept(TentacleStateSyncPayload payload, long tick) {
            fromPoints = interpolate(tick);
            fromPose = interpolatePose(tick);
            targetPoints = payload.points();
            targetPose = pose(payload);
            if (fromPoints.size() != targetPoints.size()) {
                fromPoints = targetPoints;
            }
            phase = payload.phase();
            visualSeed = payload.visualSeed();
            serverAge = payload.age();
            rootRadius = payload.rootRadius();
            tipRadius = payload.tipRadius();
            grabbedEntityId = payload.grabbedEntityId();
            grabMode = payload.grabMode();
            grabIntensity = payload.grabIntensity();
            receivedTick = tick;
            interpolationTicks = Math.max(payload.syncIntervalTicks(), TentacleClientSettings.interpolationTicks());
            invalidateInterpolationCache();
        }

        List<Vec3> interpolate(double now) {
            if (cachedPoints != null
                    && Double.doubleToLongBits(now) == Double.doubleToLongBits(cachedPointsTime)) {
                return cachedPoints;
            }
            if (fromPoints.size() != targetPoints.size()) {
                return targetPoints;
            }
            double amount = Math.max(0.0D, Math.min(1.0D,
                    (now - receivedTick) / Math.max(1, interpolationTicks)));
            List<Vec3> points = new ArrayList<>(targetPoints.size());
            for (int index = 0; index < targetPoints.size(); index++) {
                points.add(fromPoints.get(index).lerp(targetPoints.get(index), amount));
            }
            cachedPointsTime = now;
            cachedPoints = List.copyOf(points);
            return cachedPoints;
        }

        double visualAge(double now) {
            return serverAge + Math.max(0.0D, now - receivedTick);
        }

        RagdollPose interpolatePose(double now) {
            if (cachedPose != null
                    && Double.doubleToLongBits(now) == Double.doubleToLongBits(cachedPoseTime)) {
                return cachedPose;
            }
            double amount = Math.max(0.0D, Math.min(1.0D,
                    (now - receivedTick) / Math.max(1, interpolationTicks)));
            float alpha = (float) amount;
            Quaternionf bodyOrientation = new Quaternionf(fromPose.bodyOrientation()).slerp(
                    targetPose.bodyOrientation(), alpha).normalize();
            Quaternionf fromWorldBody = new Quaternionf(fromPose.referenceOrientation())
                    .mul(fromPose.bodyOrientation()).normalize();
            Quaternionf targetWorldBody = new Quaternionf(targetPose.referenceOrientation())
                    .mul(targetPose.bodyOrientation()).normalize();
            Quaternionf interpolatedWorldBody =
                    new Quaternionf(targetPose.referenceOrientation())
                            .mul(bodyOrientation).normalize();
            Quaternionf inverseInterpolatedWorldBody =
                    new Quaternionf(interpolatedWorldBody).conjugate();
            cachedPoseTime = now;
            cachedPose = new RagdollPose(
                    bodyOrientation,
                    new Quaternionf(fromPose.headOrientation()).slerp(
                            targetPose.headOrientation(), alpha).normalize(),
                    new Quaternionf(targetPose.referenceOrientation()),
                    fromPose.headOffset().lerp(targetPose.headOffset(), amount),
                    targetPose.grabTarget(),
                    fromPose.gripOffset().lerp(targetPose.gripOffset(), amount),
                    fromPose.leftArmDirection().lerp(targetPose.leftArmDirection(), amount).normalize(),
                    fromPose.rightArmDirection().lerp(targetPose.rightArmDirection(), amount).normalize(),
                    interpolateLocalDirection(fromWorldBody, targetWorldBody,
                            inverseInterpolatedWorldBody,
                            fromPose.leftLegDirection(), targetPose.leftLegDirection(), alpha),
                    interpolateLocalDirection(fromWorldBody, targetWorldBody,
                            inverseInterpolatedWorldBody,
                            fromPose.rightLegDirection(), targetPose.rightLegDirection(), alpha));
            return cachedPose;
        }

        private static RagdollPose pose(TentacleStateSyncPayload payload) {
            var pose = payload.grabPose();
            return new RagdollPose(
                    new Quaternionf((float) pose.bodyOrientation().x, (float) pose.bodyOrientation().y,
                            (float) pose.bodyOrientation().z, (float) pose.bodyOrientation().w).normalize(),
                    new Quaternionf((float) pose.headOrientation().x, (float) pose.headOrientation().y,
                            (float) pose.headOrientation().z, (float) pose.headOrientation().w).normalize(),
                    new Quaternionf((float) pose.referenceOrientation().x,
                            (float) pose.referenceOrientation().y,
                            (float) pose.referenceOrientation().z,
                            (float) pose.referenceOrientation().w).normalize(),
                    pose.headOffset(), pose.grabTarget(), pose.gripOffset(),
                    pose.leftArmDirection(), pose.rightArmDirection(),
                    pose.leftLegDirection(), pose.rightLegDirection());
        }

        private void invalidateInterpolationCache() {
            cachedPointsTime = Double.NaN;
            cachedPoints = null;
            cachedPoseTime = Double.NaN;
            cachedPose = null;
        }
    }

    /**
     * Interpolates a visible rigid limb in world space and then expresses it in
     * the already-interpolated body frame. Interpolating body rotation and a
     * body-local leg vector independently creates a small mid-frame phase error
     * during large simultaneous swings.
     */
    static Vec3 interpolateLocalDirection(
            Quaternionfc fromWorldBody, Quaternionfc targetWorldBody,
            Quaternionfc inverseInterpolatedWorldBody,
            Vec3 fromLocalDirection, Vec3 targetLocalDirection, float amount) {
        Vector3f fromWorld = fromWorldBody.transform(new Vector3f(
                (float) fromLocalDirection.x,
                (float) fromLocalDirection.y,
                (float) fromLocalDirection.z));
        Vector3f targetWorld = targetWorldBody.transform(new Vector3f(
                (float) targetLocalDirection.x,
                (float) targetLocalDirection.y,
                (float) targetLocalDirection.z));
        Vector3f interpolatedWorld = fromWorld.lerp(targetWorld, amount);
        if (!interpolatedWorld.isFinite() || interpolatedWorld.lengthSquared() <= 1.0E-10F) {
            return targetLocalDirection.lengthSqr() > 1.0E-10D
                    ? targetLocalDirection.normalize() : new Vec3(0.0D, -1.0D, 0.0D);
        }
        interpolatedWorld.normalize();
        inverseInterpolatedWorldBody.transform(interpolatedWorld);
        if (!interpolatedWorld.isFinite() || interpolatedWorld.lengthSquared() <= 1.0E-10F) {
            return new Vec3(0.0D, -1.0D, 0.0D);
        }
        interpolatedWorld.normalize();
        return new Vec3(interpolatedWorld.x, interpolatedWorld.y, interpolatedWorld.z);
    }
}
