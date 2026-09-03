package com.fish.mirebound.water;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.entitycoverage.EntityMudCoverageService;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.network.payload.WaterGunStreamPayload;
import com.fish.mirebound.network.payload.WaterGunProfileSyncPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.swarm.SwarmSystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative continuous pressure-water sessions and bounded wash collision. */
public final class WaterGunSystem {
    private static final int MAXIMUM_SYNC_POINTS = 48;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final double ENTITY_HIT_INFLATION = 0.08D;
    private static final ResourceLocation FIRING_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "water_gun_firing_speed");

    private WaterGunSystem() {
    }

    public static void handleInput(ServerPlayer player, boolean firing) {
        if (!firing) {
            stop(player, true);
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!validShooter(player, stack)) {
            stop(player, true);
            return;
        }
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
        session.inputTicksRemaining = MudPhysicsSettings.waterGunProfile().inputTimeoutTicks();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        var server = event.getServer();
        var iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || --session.inputTicksRemaining < 0
                    || !validShooter(player, player.getMainHandItem())) {
                if (player != null) {
                    removeMovementSlowdown(player);
                    sendStop(player);
                }
                iterator.remove();
                continue;
            }
            WaterGunProfile profile = MudPhysicsSettings.waterGunProfile();
            ItemStack stack = player.getMainHandItem();
            int stored = WaterGunItem.water(stack);
            if (stored < profile.waterPerTick()) {
                if (!session.playedEmptySound) {
                    player.serverLevel().playSound(null, player.blockPosition(),
                            SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.55F, 1.35F);
                    session.playedEmptySound = true;
                }
                removeMovementSlowdown(player);
                sendStop(player);
                iterator.remove();
                continue;
            }
            session.playedEmptySound = false;
            applyMovementSlowdown(player, profile);
            WaterGunItem.setWater(stack, stored - profile.waterPerTick());
            tickStream(player, session, profile);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeMovementSlowdown(player);
        }
        SESSIONS.remove(event.getEntity().getUUID());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncProfile(player);
        }
    }

    public static void syncProfile(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                WaterGunProfileSyncPayload.from(MudPhysicsSettings.waterGunProfile()));
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (UUID playerId : SESSIONS.keySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                removeMovementSlowdown(player);
            }
        }
        SESSIONS.clear();
    }

    private static void tickStream(ServerPlayer player, Session session, WaterGunProfile profile) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 origin = nozzlePosition(player, look);
        List<Vec3> ballistic = WaterGunBallistics.sample(origin, look, profile);
        List<SableCompat.SubLevelCollisionGeometry> sableGeometry = session.sableGeometry(
                level, ballistic, look, profile);
        StreamHit hit = trace(level, player, ballistic, sableGeometry);
        List<Vec3> visiblePath = hit == null
                ? ballistic
                : truncatePath(ballistic, hit.segmentIndex(), hit.worldPoint());
        float radius = hit == null ? 0.0F : profile.washRadius(origin.distanceTo(hit.worldPoint()));

        if (hit != null) {
            if (session.ageTicks % profile.washIntervalTicks() == 0) {
                float washAmount = profile.washAmountPerTick() * profile.washIntervalTicks();
                washEntities(level, hit.worldPoint(), radius, washAmount);
                if (hit.subLevel() == null) {
                    washWorldDecals(level, hit.worldPoint(), radius, washAmount);
                } else {
                    washSableDecals(level, hit, radius, washAmount);
                }
            }
            if ((player.tickCount & 3) == 0) {
                level.playSound(null, hit.worldPoint().x, hit.worldPoint().y, hit.worldPoint().z,
                        SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.18F,
                        1.50F + level.random.nextFloat() * 0.15F);
            }
        }
        if ((player.tickCount & 7) == 0) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.16F,
                    1.65F + level.random.nextFloat() * 0.10F);
        }

        if (session.ageTicks++ % profile.syncIntervalTicks() == 0 || !session.sentStart) {
            session.sentStart = true;
            int targetEntity = hit == null ? -1 : hit.targetEntityId();
            WaterGunStreamPayload payload = new WaterGunStreamPayload(
                    player.getId(), true, targetEntity, radius, syncPath(visiblePath));
            PacketDistributor.sendToPlayersNear(level, null,
                    origin.x, origin.y, origin.z, profile.renderDistance(), payload);
        }
    }

    private static StreamHit trace(ServerLevel level, ServerPlayer shooter,
            List<Vec3> points, List<SableCompat.SubLevelCollisionGeometry> sableGeometry) {
        AABB pathBounds = pathBounds(points).inflate(ENTITY_HIT_INFLATION);
        List<LivingEntity> entityCandidates = level.getEntitiesOfClass(
                LivingEntity.class, pathBounds,
                candidate -> candidate != shooter && candidate.isAlive() && !candidate.isSpectator());
        for (int segment = 0; segment < points.size() - 1; segment++) {
            Vec3 from = points.get(segment);
            Vec3 to = points.get(segment + 1);
            StreamHit best = worldBlockHit(level, shooter, from, to, segment);
            double bestDistance = best == null
                    ? Double.POSITIVE_INFINITY : from.distanceToSqr(best.worldPoint());

            StreamHit entityHit = nearestEntityHit(
                    entityCandidates, from, to, segment, bestDistance);
            if (entityHit != null) {
                best = entityHit;
                bestDistance = from.distanceToSqr(entityHit.worldPoint());
            }
            StreamHit sableHit = nearestSableHit(sableGeometry, from, to, segment, bestDistance);
            if (sableHit != null) {
                best = sableHit;
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static StreamHit worldBlockHit(ServerLevel level, ServerPlayer shooter,
            Vec3 from, Vec3 to, int segment) {
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return new StreamHit(segment, hit.getLocation(), -1, null, null);
    }

    private static StreamHit nearestEntityHit(List<LivingEntity> candidates,
            Vec3 from, Vec3 to, int segment, double maximumDistanceSqr) {
        StreamHit best = null;
        double bestDistance = maximumDistanceSqr;
        for (LivingEntity target : candidates) {
            Optional<Vec3> clipped = target.getBoundingBox()
                    .inflate(ENTITY_HIT_INFLATION).clip(from, to);
            if (clipped.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(clipped.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new StreamHit(segment, clipped.get(), target.getId(), null, null);
            }
        }
        return best;
    }

    private static StreamHit nearestSableHit(
            List<SableCompat.SubLevelCollisionGeometry> geometries,
            Vec3 from, Vec3 to, int segment, double maximumDistanceSqr) {
        StreamHit best = null;
        double bestDistance = maximumDistanceSqr;
        for (SableCompat.SubLevelCollisionGeometry geometry : geometries) {
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
                if (distance >= bestDistance) {
                    continue;
                }
                bestDistance = distance;
                best = new StreamHit(segment, world, -1,
                        geometry.subLevel(), clipped.get());
            }
        }
        return best;
    }

    private static void washEntities(ServerLevel level, Vec3 impact,
            float radius, float amount) {
        AABB bounds = new AABB(impact, impact).inflate(radius + 1.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                candidate -> candidate.isAlive() && !candidate.isSpectator())) {
            if (target.getBoundingBox().distanceToSqr(impact) > radius * radius) {
                continue;
            }
            if (target instanceof ServerPlayer player) {
                MudWashingSystem.washFromWaterGun(player, impact, radius, amount);
                SwarmSystem.wash(player, amount);
            } else {
                EntityMudCoverageService.wash(target, amount);
            }
        }
    }

    private static void washWorldDecals(ServerLevel level, Vec3 impact, float radius, float amount) {
        int reach = Mth.ceil(radius) + 1;
        BlockPos center = BlockPos.containing(impact);
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (level.getBlockEntity(pos) instanceof MudFootprintBlockEntity decal) {
                decal.washFromWaterGun(level, impact, radius, amount);
            }
        }
    }

    private static void washSableDecals(ServerLevel level, StreamHit hit, float radius, float amount) {
        if (hit.subLevel() == null || hit.localPoint() == null) {
            return;
        }
        int reach = Mth.ceil(radius) + 1;
        BlockPos center = BlockPos.containing(hit.localPoint());
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (SableCompat.subLevelBlockEntity(level, hit.subLevel(), pos)
                    instanceof MudFootprintBlockEntity decal) {
                decal.washFromWaterGun(level, hit.localPoint(), radius, amount);
            }
        }
    }

    private static List<Vec3> truncatePath(List<Vec3> points, int segment, Vec3 hit) {
        var result = new java.util.ArrayList<Vec3>(segment + 2);
        for (int index = 0; index <= segment; index++) {
            result.add(points.get(index));
        }
        if (result.getLast().distanceToSqr(hit) > 1.0E-8D) {
            result.add(hit);
        } else if (result.size() == 1) {
            result.add(hit);
        }
        return List.copyOf(result);
    }

    static List<Vec3> syncPath(List<Vec3> points) {
        if (points.size() <= MAXIMUM_SYNC_POINTS) {
            return points;
        }
        var result = new java.util.ArrayList<Vec3>(MAXIMUM_SYNC_POINTS);
        for (int index = 0; index < MAXIMUM_SYNC_POINTS; index++) {
            double sourcePosition = index * (points.size() - 1.0D)
                    / (MAXIMUM_SYNC_POINTS - 1.0D);
            int lower = Mth.floor(sourcePosition);
            int upper = Math.min(points.size() - 1, lower + 1);
            result.add(points.get(lower).lerp(
                    points.get(upper), sourcePosition - lower));
        }
        return List.copyOf(result);
    }

    private static AABB pathBounds(List<Vec3> points) {
        AABB bounds = new AABB(points.getFirst(), points.getFirst());
        for (int index = 1; index < points.size(); index++) {
            bounds = bounds.minmax(new AABB(points.get(index), points.get(index)));
        }
        return bounds;
    }

    public static Vec3 nozzlePosition(Player player, Vec3 look) {
        Vec3 forward = look.normalize();
        return player.getEyePosition().add(forward.scale(0.62D));
    }

    private static boolean validShooter(ServerPlayer player, ItemStack stack) {
        return player.isAlive() && !player.isSpectator()
                && stack.getItem() == ModBlocks.WATER_GUN.get();
    }

    private static void stop(ServerPlayer player, boolean send) {
        removeMovementSlowdown(player);
        Session removed = SESSIONS.remove(player.getUUID());
        if (send && removed != null && removed.sentStart) {
            sendStop(player);
        }
    }

    private static void sendStop(ServerPlayer player) {
        WaterGunProfile profile = MudPhysicsSettings.waterGunProfile();
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null,
                player.getX(), player.getY(), player.getZ(), profile.renderDistance(),
                WaterGunStreamPayload.stopped(player.getId()));
    }

    private static void applyMovementSlowdown(ServerPlayer player, WaterGunProfile profile) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }
        double amount = profile.firingMovementScale() - 1.0D;
        AttributeModifier current = movementSpeed.getModifier(FIRING_SPEED_MODIFIER);
        if (Math.abs(amount) <= 1.0E-6D) {
            if (current != null) {
                movementSpeed.removeModifier(FIRING_SPEED_MODIFIER);
            }
            return;
        }
        if (current != null && Math.abs(current.amount() - amount) <= 1.0E-6D
                && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            return;
        }
        movementSpeed.addOrUpdateTransientModifier(new AttributeModifier(
                FIRING_SPEED_MODIFIER, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeMovementSlowdown(ServerPlayer player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null && movementSpeed.getModifier(FIRING_SPEED_MODIFIER) != null) {
            movementSpeed.removeModifier(FIRING_SPEED_MODIFIER);
        }
    }

    private static final class Session {
        private int inputTicksRemaining;
        private int ageTicks;
        private boolean sentStart;
        private boolean playedEmptySound;
        private boolean hasSableSnapshot;
        private int sableSnapshotTicks;
        private Vec3 sableOrigin = Vec3.ZERO;
        private Vec3 sableDirection = Vec3.ZERO;
        private List<SableCompat.SubLevelCollisionGeometry> cachedSableGeometry = List.of();

        private List<SableCompat.SubLevelCollisionGeometry> sableGeometry(
                ServerLevel level, List<Vec3> points, Vec3 direction, WaterGunProfile profile) {
            Vec3 origin = points.getFirst();
            boolean moved = sableOrigin.distanceToSqr(origin) > 0.25D;
            boolean turned = sableDirection.lengthSqr() <= 1.0E-8D
                    || sableDirection.dot(direction) < 0.985D;
            if (!hasSableSnapshot || sableSnapshotTicks-- <= 0 || moved || turned) {
                cachedSableGeometry = SableCompat.isLoaded()
                        ? SableCompat.collisionGeometry(
                                level, List.of(points), Math.max(0.20D, profile.streamWidth() * 1.25D),
                                profile.sableMaximumBlockSamples())
                        : List.of();
                hasSableSnapshot = true;
                sableSnapshotTicks = profile.washIntervalTicks() - 1;
                sableOrigin = origin;
                sableDirection = direction;
            }
            return cachedSableGeometry;
        }
    }

    private record StreamHit(
            int segmentIndex,
            Vec3 worldPoint,
            int targetEntityId,
            Object subLevel,
            Vec3 localPoint) {
    }
}
