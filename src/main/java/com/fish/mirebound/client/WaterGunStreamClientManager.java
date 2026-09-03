package com.fish.mirebound.client;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.network.payload.WaterGunProfileSyncPayload;
import com.fish.mirebound.network.payload.WaterGunStreamPayload;
import com.fish.mirebound.water.WaterGunBallistics;
import com.fish.mirebound.water.WaterGunItem;
import com.fish.mirebound.water.WaterGunProfile;
import com.fish.mirebound.water.WaterGunSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Maintains bounded ballistic snapshots used by water-gun particles and held-item animation. */
final class WaterGunStreamClientManager {
    private static final int REFILL_ANIMATION_TICKS = 10;
    private static final Map<Integer, Stream> STREAMS = new HashMap<>();
    private static ClientLevel level;
    private static WaterGunProfile visualProfile = WaterGunProfile.DEFAULT;
    private static boolean localHolding;
    private static boolean localFiring;
    private static boolean localServerStopped;
    private static float previousLocalRecoil;
    private static float localRecoil;
    private static float previousRefillDip;
    private static float refillDip;
    private static int refillAnimationTicks;
    private static List<SableCompat.SubLevelCollisionGeometry> sableGeometry = List.of();
    private static Vec3 sableOrigin = Vec3.ZERO;
    private static Vec3 sableDirection = Vec3.ZERO;
    private static int sableRefreshTicks;

    private WaterGunStreamClientManager() {
    }

    static void accept(WaterGunStreamPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ensureLevel(minecraft.level);
        boolean localShooter = minecraft.player != null
                && payload.shooterId() == minecraft.player.getId();
        if (!payload.active()) {
            STREAMS.remove(payload.shooterId());
            if (localShooter) {
                localServerStopped = true;
                localFiring = false;
            }
            return;
        }
        if (localShooter && localHolding) {
            return;
        }
        STREAMS.computeIfAbsent(payload.shooterId(), Stream::new)
                .accept(payload, minecraft.level);
    }

    static void acceptProfile(WaterGunProfileSyncPayload payload) {
        visualProfile = payload.applyTo(WaterGunProfile.DEFAULT);
        WaterGunItem.setDisplayCapacity(visualProfile.capacity());
    }

    static boolean hasUsableWater(int storedWater) {
        return storedWater >= Math.max(1, visualProfile.waterPerTick());
    }

    static boolean beginLocalFiring(boolean holding, int storedWater) {
        localServerStopped = false;
        return setLocalState(holding, hasUsableWater(storedWater), true);
    }

    static boolean setLocalState(boolean holding, boolean hasWater, boolean firingRequested) {
        if (!holding || !firingRequested) {
            localServerStopped = false;
        }
        localHolding = holding;
        localFiring = holding && hasWater && firingRequested && !localServerStopped;
        Minecraft minecraft = Minecraft.getInstance();
        if (!localFiring && minecraft.player != null) {
            STREAMS.remove(minecraft.player.getId());
        }
        return localFiring;
    }

    static void beginRefillAnimation() {
        refillAnimationTicks = REFILL_ANIMATION_TICKS;
        previousRefillDip = 0.0F;
        refillDip = 0.0F;
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            reset();
            return;
        }
        ensureLevel(minecraft.level);
        LocalPlayer player = minecraft.player;
        if (localFiring && player != null) {
            STREAMS.computeIfAbsent(player.getId(), Stream::new)
                    .predictLocal(player, minecraft);
        }

        previousLocalRecoil = localRecoil;
        localRecoil = Mth.clamp(localRecoil + (localFiring ? 0.24F : -0.16F), 0.0F, 1.0F);
        previousRefillDip = refillDip;
        if (refillAnimationTicks > 0) {
            float progress = (REFILL_ANIMATION_TICKS - refillAnimationTicks + 1.0F)
                    / REFILL_ANIMATION_TICKS;
            refillDip = Mth.sin(progress * Mth.PI);
            refillAnimationTicks--;
        } else {
            refillDip = 0.0F;
        }

        STREAMS.values().removeIf(stream -> stream.tick(minecraft.level));
    }

    static Iterable<Stream> streams() {
        return STREAMS.values();
    }

    static boolean isFiring(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && entityId == minecraft.player.getId()) {
            return localFiring;
        }
        return STREAMS.containsKey(entityId);
    }

    static float localRecoil(float partialTick) {
        return Mth.lerp(partialTick, previousLocalRecoil, localRecoil);
    }

    static float recoilDegrees() {
        return (float) visualProfile.recoilDegrees();
    }

    static float refillDip(float partialTick) {
        return Mth.lerp(partialTick, previousRefillDip, refillDip);
    }

    static void reset() {
        STREAMS.clear();
        level = null;
        visualProfile = WaterGunProfile.DEFAULT;
        WaterGunItem.resetDisplayCapacity();
        localHolding = false;
        localFiring = false;
        localServerStopped = false;
        previousLocalRecoil = 0.0F;
        localRecoil = 0.0F;
        previousRefillDip = 0.0F;
        refillDip = 0.0F;
        refillAnimationTicks = 0;
        sableGeometry = List.of();
        sableOrigin = Vec3.ZERO;
        sableDirection = Vec3.ZERO;
        sableRefreshTicks = 0;
        WaterGunNozzleFocus.reset();
    }

    private static void ensureLevel(ClientLevel current) {
        if (level != current) {
            reset();
            level = current;
        }
    }

    static final class Stream {
        final int shooterId;
        List<Vec3> previousPoints = List.of();
        List<Vec3> points = List.of();
        Vec3 targetOffset = Vec3.ZERO;
        int targetEntityId = -1;
        int staleTicks;
        long lastParticleTick = Long.MIN_VALUE;
        boolean impact;
        boolean burstPending = true;

        Stream(int shooterId) {
            this.shooterId = shooterId;
        }

        void accept(WaterGunStreamPayload payload, ClientLevel level) {
            previousPoints = remapPath(points, payload.points());
            points = payload.points();
            targetEntityId = payload.targetEntityId();
            Entity target = targetEntityId < 0 ? null : level.getEntity(targetEntityId);
            targetOffset = target == null || points.isEmpty()
                    ? Vec3.ZERO : points.getLast().subtract(target.position());
            impact = payload.washRadius() > 0.01F;
            staleTicks = 0;
        }

        void predictLocal(LocalPlayer player, Minecraft minecraft) {
            Vec3 look = player.getLookAngle().normalize();
            Vec3 origin = WaterGunSystem.nozzlePosition(player, look);
            List<Vec3> predicted = WaterGunBallistics.sample(origin, look, visualProfile);
            List<SableCompat.SubLevelCollisionGeometry> currentSable = sableGeometry(
                    minecraft.level, predicted, origin, look);
            PreviewHit hit = firstHit(minecraft, player, predicted, currentSable);
            List<Vec3> visible = hit == null
                    ? predicted : truncateAt(predicted, hit.segmentIndex(), hit.point());
            previousPoints = remapPath(points, visible);
            points = visible;
            impact = hit != null;
            targetEntityId = hit == null ? -1 : hit.targetEntityId();
            Entity target = targetEntityId < 0 ? null : minecraft.level.getEntity(targetEntityId);
            targetOffset = target == null || hit == null
                    ? Vec3.ZERO : hit.point().subtract(target.position());
            staleTicks = 0;
        }

        private static PreviewHit firstHit(
                Minecraft minecraft, LocalPlayer player, List<Vec3> predicted,
                List<SableCompat.SubLevelCollisionGeometry> currentSable) {
            AABB pathBounds = new AABB(predicted.getFirst(), predicted.getFirst());
            for (int index = 1; index < predicted.size(); index++) {
                pathBounds = pathBounds.minmax(new AABB(predicted.get(index), predicted.get(index)));
            }
            List<LivingEntity> candidates = minecraft.level.getEntitiesOfClass(
                    LivingEntity.class, pathBounds.inflate(0.08D),
                    candidate -> candidate != player && candidate.isAlive() && !candidate.isSpectator());
            for (int index = 0; index < predicted.size() - 1; index++) {
                Vec3 from = predicted.get(index);
                Vec3 to = predicted.get(index + 1);
                HitResult blockHit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(
                        from, to,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                PreviewHit best = null;
                double bestDistance = Double.POSITIVE_INFINITY;
                if (blockHit instanceof BlockHitResult hit
                        && blockHit.getType() == HitResult.Type.BLOCK) {
                    bestDistance = from.distanceToSqr(hit.getLocation());
                    best = new PreviewHit(index, hit.getLocation(), -1);
                }
                for (LivingEntity candidate : candidates) {
                    Optional<Vec3> clipped = candidate.getBoundingBox().inflate(0.08D).clip(from, to);
                    if (clipped.isEmpty()) {
                        continue;
                    }
                    double distance = from.distanceToSqr(clipped.get());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new PreviewHit(index, clipped.get(), candidate.getId());
                    }
                }
                for (SableCompat.SubLevelCollisionGeometry geometry : currentSable) {
                    Vec3 localFrom = geometry.transform().toLocal(from);
                    Vec3 localTo = geometry.transform().toLocal(to);
                    if (localFrom == null || localTo == null) {
                        continue;
                    }
                    for (AABB box : geometry.localBoxes()) {
                        Optional<Vec3> clipped = box.clip(localFrom, localTo);
                        if (clipped.isEmpty()) {
                            continue;
                        }
                        Vec3 world = geometry.transform().toWorld(clipped.get());
                        if (world == null) {
                            continue;
                        }
                        double distance = from.distanceToSqr(world);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = new PreviewHit(index, world, -1);
                        }
                    }
                }
                if (best != null) {
                    return best;
                }
            }
            return null;
        }

        private static List<SableCompat.SubLevelCollisionGeometry> sableGeometry(
                ClientLevel level, List<Vec3> predicted, Vec3 origin, Vec3 direction) {
            if (!SableCompat.isLoaded()) {
                return List.of();
            }
            boolean moved = sableOrigin.distanceToSqr(origin) > 0.25D;
            boolean turned = sableDirection.lengthSqr() <= 1.0E-8D
                    || sableDirection.dot(direction) < 0.985D;
            if (sableRefreshTicks-- <= 0 || moved || turned) {
                sableGeometry = SableCompat.collisionGeometry(
                        level,
                        List.of(predicted),
                        Math.max(0.20D, visualProfile.streamWidth() * 1.25D),
                        visualProfile.sableMaximumBlockSamples());
                sableOrigin = origin;
                sableDirection = direction;
                sableRefreshTicks = 2;
            }
            return sableGeometry;
        }

        private static List<Vec3> truncateAt(List<Vec3> predicted, int segmentIndex, Vec3 hit) {
            var result = new ArrayList<Vec3>(segmentIndex + 2);
            for (int index = 0; index <= segmentIndex; index++) {
                result.add(predicted.get(index));
            }
            if (result.getLast().distanceToSqr(hit) > 1.0E-8D) {
                result.add(hit);
            }
            return List.copyOf(result);
        }

        private static List<Vec3> remapPath(List<Vec3> source, List<Vec3> target) {
            if (source.isEmpty() || target.isEmpty()) {
                return target;
            }
            if (source.size() == target.size()) {
                return source;
            }
            var result = new ArrayList<Vec3>(target.size());
            if (target.size() == 1 || source.size() == 1) {
                Vec3 point = source.getFirst();
                for (int index = 0; index < target.size(); index++) {
                    result.add(point);
                }
                return List.copyOf(result);
            }
            for (int index = 0; index < target.size(); index++) {
                double position = index * (source.size() - 1.0D) / (target.size() - 1.0D);
                int lower = Mth.floor(position);
                int upper = Math.min(source.size() - 1, lower + 1);
                result.add(source.get(lower).lerp(source.get(upper), position - lower));
            }
            return List.copyOf(result);
        }

        boolean tick(ClientLevel level) {
            if (++staleTicks > 8) {
                return true;
            }
            if (targetEntityId >= 0 && !points.isEmpty()) {
                Entity target = level.getEntity(targetEntityId);
                if (target != null) {
                    var moved = new ArrayList<>(points);
                    moved.set(moved.size() - 1, target.position().add(targetOffset));
                    points = List.copyOf(moved);
                }
            }
            return false;
        }

        List<Vec3> interpolated(float partialTick) {
            if (previousPoints.size() != points.size() || points.isEmpty()) {
                return points;
            }
            var result = new ArrayList<Vec3>(points.size());
            for (int index = 0; index < points.size(); index++) {
                result.add(previousPoints.get(index).lerp(points.get(index), partialTick));
            }
            return result;
        }

        boolean beginParticleTick(long gameTick) {
            if (lastParticleTick == gameTick) {
                return false;
            }
            lastParticleTick = gameTick;
            return true;
        }

        boolean consumeBurst() {
            boolean result = burstPending;
            burstPending = false;
            return result;
        }

        private record PreviewHit(int segmentIndex, Vec3 point, int targetEntityId) {
        }
    }
}
