package com.fish.mirebound.entitycoverage;

import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.EntityMudCoveragePayload;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server authority for low-cost mud coverage on non-player living entities. */
public final class EntityMudCoverageService {
    private static final String PERSISTENT_KEY = "mirebound:entity_mud_coverage";
    private static final int CONTACT_INTERVAL_TICKS = 5;
    private static final int WASH_INTERVAL_TICKS = 10;
    private static final int MINIMUM_FULL_SNAPSHOT_CHANGES = 8;
    private static final Map<LivingEntity, EntityMudCoverageState> STATES =
            new IdentityHashMap<>();

    private EntityMudCoverageService() {
    }

    public static void applyContact(LivingEntity entity, Level level, BlockPos pos,
            BlockState blockState, SinkingMedium medium) {
        if (level.isClientSide() || entity instanceof Player || !entity.isAlive()
                || !MudPhysicsSettings.entityCoverageEnabled()) {
            return;
        }
        EntityMudCoverageState state = state(entity, true);
        long gameTime = level.getGameTime();
        long visualSource = MudVisualSource.capture(level, pos);
        if (!state.contactUpdateDue(
                medium, visualSource, pos.asLong(), gameTime,
                CONTACT_INTERVAL_TICKS)) {
            return;
        }
        boolean changed = state.refreshAutomaticFade(gameTime);

        double immersedShare = 0.35D;
        if (MudBlock.surfaceDirection(blockState, medium) == Direction.UP) {
            double surfaceY = pos.getY() + MudBlock.surfaceHeight(blockState, medium);
            immersedShare = Mth.clamp(
                    (surfaceY - entity.getBoundingBox().minY)
                            / Math.max(0.25D, entity.getBbHeight()),
                    0.0D, 1.0D);
        }
        float gain = 0.010F + (float) immersedShare * 0.030F;
        changed |= state.add(medium, visualSource, gain, false);
        changed |= addContactSpots(state, entity, pos,
                MudBlock.surfaceDirection(blockState, medium),
                (float) immersedShare, medium, visualSource);
        mutate(state, changed);
    }

    public static void applySplash(LivingEntity entity, SinkingMedium medium,
            long visualSource, Vec3 impact, float radius,
            float strength, boolean mudClod) {
        if (entity.level().isClientSide() || entity instanceof Player
                || !entity.isAlive() || impact == null
                || !MudPhysicsSettings.entityCoverageEnabled()) {
            return;
        }
        float normalized = Mth.clamp(strength, 0.0F, 1.5F);
        float gain = mudClod
                ? Mth.clamp(0.075F + normalized * 0.055F, 0.075F, 0.155F)
                : Mth.clamp(0.005F + normalized * 0.007F, 0.005F, 0.015F);
        EntityMudCoverageState state = state(entity, true);
        boolean changed = state.refreshAutomaticFade(
                entity.level().getGameTime());
        LocalSpot local = localSpot(entity, impact, radius);
        changed |= state.add(medium, visualSource, gain, true);
        changed |= state.addSpot(
                local.x(), local.y(), local.z(), local.radius(),
                mudClod
                        ? Mth.clamp(0.82F + normalized * 0.12F, 0.82F, 1.0F)
                        : Mth.clamp(0.38F + normalized * 0.16F, 0.38F, 0.68F),
                medium, visualSource, true);
        mutate(state, changed);
    }

    public static void wash(LivingEntity entity, float amount) {
        if (entity.level().isClientSide() || entity instanceof Player
                || !entity.isAlive() || !Float.isFinite(amount) || amount <= 0.0F) {
            return;
        }
        EntityMudCoverageState state = state(entity, false);
        if (state == null) {
            return;
        }
        mutate(state, state.washVisible(amount));
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof LivingEntity entity
                && !(entity instanceof Player)) {
            state(entity, false);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (STATES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, EntityMudCoverageState>> iterator =
                STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, EntityMudCoverageState> stored = iterator.next();
            LivingEntity entity = stored.getKey();
            EntityMudCoverageState state = stored.getValue();
            if (entity.isRemoved() || !entity.isAlive()
                    || entity.level().isClientSide()) {
                iterator.remove();
                continue;
            }
            if (state.dirty() && Math.floorMod(
                    entity.tickCount + entity.getId(), WASH_INTERVAL_TICKS) == 0) {
                int fadeTicks = MudPhysicsSettings.entityCoverageAutomaticFadeTicks();
                boolean automaticFadeChanged = state.advanceAutomaticFade(
                        entity.level().getGameTime(), fadeTicks);
                if (automaticFadeChanged) {
                    state.markSynchronizationPending();
                    if (!state.dirty() || fadeTicks <= 0) {
                        state.markPersistencePending();
                    }
                }
                boolean changed = false;
                float wash = entity.isInWaterOrBubble()
                        ? 0.035F
                        : entity.level().isRainingAt(entity.blockPosition())
                                ? 0.008F : 0.0F;
                if (wash > 0.0F) {
                    changed |= state.washVisible(wash);
                }
                mutate(state, changed);
            }
            flush(entity, state);
            if (!state.dirty() && !state.persistencePending()
                    && !state.synchronizationPending()) {
                iterator.remove();
            }
        }
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)
                || !(event.getTarget() instanceof LivingEntity target)
                || target instanceof Player) {
            return;
        }
        EntityMudCoverageState state = state(target, false);
        if (state != null && state.dirty()) {
            EntityMudCoverageState.Snapshot snapshot = state.snapshot();
            PacketDistributor.sendToPlayer(observer, payload(
                    target, snapshot, true, snapshot.spots(), List.of()));
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        STATES.clear();
    }

    private static void mutate(EntityMudCoverageState state, boolean changed) {
        if (!changed) {
            return;
        }
        state.markSynchronizationPending();
        state.markPersistencePending();
    }

    private static void flush(LivingEntity entity, EntityMudCoverageState state) {
        if (state.persistencePending()) {
            state.clearPersistencePending();
            save(entity, state);
        }
        if (!state.synchronizationPending()) {
            return;
        }
        state.clearSynchronizationPending();
        long signature = state.synchronizationSignature();
        if (signature == state.lastBroadcastSignature()) {
            return;
        }
        EntityMudCoverageState.Snapshot snapshot = state.snapshot();
        EntityMudCoverageSyncTracker.Delta delta =
                state.synchronizationDelta();
        boolean fullSnapshot = state.lastBroadcastSignature() == Long.MIN_VALUE
                || delta.size() > Math.max(
                        MINIMUM_FULL_SNAPSHOT_CHANGES,
                        snapshot.spots().size() / 2);
        List<EntityMudCoverageSpot> transmittedSpots = fullSnapshot
                ? snapshot.spots() : delta.changed();
        List<Integer> removedSpotIds = fullSnapshot
                ? List.of() : delta.removedIds();
        state.markBroadcast(signature);
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload(
                entity, snapshot, fullSnapshot,
                transmittedSpots, removedSpotIds));
    }

    private static EntityMudCoverageState state(LivingEntity entity, boolean create) {
        EntityMudCoverageState cached = STATES.get(entity);
        if (cached != null) {
            return cached;
        }
        CompoundTag persistent = entity.getPersistentData();
        EntityMudCoverageState loaded = null;
        if (persistent.contains(PERSISTENT_KEY, Tag.TAG_COMPOUND)) {
            loaded = EntityMudCoverageState.load(
                    persistent.getCompound(PERSISTENT_KEY), patternSeed(entity));
        } else if (create) {
            loaded = new EntityMudCoverageState(patternSeed(entity));
        }
        if (loaded != null) {
            STATES.put(entity, loaded);
        }
        return loaded;
    }

    private static void save(LivingEntity entity, EntityMudCoverageState state) {
        if (state.dirty()) {
            entity.getPersistentData().put(PERSISTENT_KEY, state.save());
        } else {
            entity.getPersistentData().remove(PERSISTENT_KEY);
        }
    }

    private static EntityMudCoveragePayload payload(
            LivingEntity entity, EntityMudCoverageState.Snapshot snapshot,
            boolean fullSnapshot, List<EntityMudCoverageSpot> transmittedSpots,
            List<Integer> removedSpotIds) {
        List<EntityMudCoveragePayload.Spot> spots = new ArrayList<>(
                transmittedSpots.size());
        for (EntityMudCoverageSpot spot : transmittedSpots) {
            spots.add(new EntityMudCoveragePayload.Spot(
                    spot.id(),
                    spot.shape().ordinal(),
                    EntityMudCoverageEncoding.signed(spot.localX()),
                    EntityMudCoverageEncoding.unit(spot.localY()),
                    EntityMudCoverageEncoding.signed(spot.localZ()),
                    EntityMudCoverageEncoding.unit(spot.radius()),
                    EntityMudCoverageEncoding.unit(spot.strength()),
                    EntityMudCoverageEncoding.mediumId(spot.medium()),
                    spot.visualSource()));
        }
        return new EntityMudCoveragePayload(
                entity.getId(), entity.getUUID(), snapshot.revision(),
                snapshot.patternSeed(),
                EntityMudCoverageEncoding.unit(snapshot.primaryStrength()),
                EntityMudCoverageEncoding.mediumId(snapshot.primaryMedium()),
                snapshot.primaryVisualSource(),
                EntityMudCoverageEncoding.unit(snapshot.secondaryStrength()),
                EntityMudCoverageEncoding.mediumId(snapshot.secondaryMedium()),
                snapshot.secondaryVisualSource(),
                EntityMudCoverageEncoding.unit(snapshot.automaticFadeScale()),
                fullSnapshot, spots, removedSpotIds);
    }

    private static LocalSpot localSpot(
            LivingEntity entity, Vec3 impact, float worldRadius) {
        AABB bounds = entity.getBoundingBox();
        double halfWidth = Math.max(0.125D, entity.getBbWidth() * 0.5D);
        double dx = impact.x - entity.getX();
        double dz = impact.z - entity.getZ();
        double yaw = Math.toRadians(entity.yBodyRot);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        float localX = (float) Mth.clamp(
                (-dx * cos - dz * sin) / halfWidth, -1.0D, 1.0D);
        float localZ = (float) Mth.clamp(
                (-dx * sin + dz * cos) / halfWidth, -1.0D, 1.0D);
        float localY = (float) Mth.clamp(
                (impact.y - bounds.minY) / Math.max(0.25D, bounds.getYsize()),
                0.0D, 1.0D);
        float radius = Mth.clamp(
                worldRadius / Math.max(0.25F, entity.getBbHeight()),
                0.035F, 0.42F);
        return new LocalSpot(localX, localY, localZ, radius);
    }

    private static boolean addContactSpots(
            EntityMudCoverageState state, LivingEntity entity, BlockPos pos,
            Direction surface, float immersedShare,
            SinkingMedium medium, long visualSource) {
        float radius = 0.11F + immersedShare * 0.09F;
        float strength = 0.48F + immersedShare * 0.30F;
        if (surface.getAxis().isVertical()) {
            float boundary = surface == Direction.DOWN
                    ? 1.0F - immersedShare : immersedShare;
            AABB bounds = entity.getBoundingBox();
            double x = Mth.clamp(pos.getX() + 0.5D, bounds.minX, bounds.maxX);
            double z = Mth.clamp(pos.getZ() + 0.5D, bounds.minZ, bounds.maxZ);
            float contactRadius = 0.24F + immersedShare * 0.16F;
            LocalSpot local = localSpot(entity,
                    new Vec3(x, pos.getY() + 0.5D, z),
                    entity.getBbHeight() * contactRadius);
            return state.addContactVolume(
                    local.x(), boundary, local.z(), local.radius(),
                    surface == Direction.UP, strength, medium, visualSource);
        }

        AABB bounds = entity.getBoundingBox();
        double x = Mth.clamp(pos.getX() + 0.5D, bounds.minX, bounds.maxX);
        double y = Mth.clamp(pos.getY() + 0.5D, bounds.minY, bounds.maxY);
        double z = Mth.clamp(pos.getZ() + 0.5D, bounds.minZ, bounds.maxZ);
        if (surface == Direction.EAST || surface == Direction.WEST) {
            x = pos.getX() + (surface == Direction.EAST ? 1.0D : 0.0D);
        } else {
            z = pos.getZ() + (surface == Direction.SOUTH ? 1.0D : 0.0D);
        }
        LocalSpot local = localSpot(entity, new Vec3(x, y, z),
                entity.getBbHeight() * radius);
        return state.addSpot(local.x(), local.y(), local.z(), local.radius(),
                strength, medium, visualSource, false);
    }

    private static int patternSeed(LivingEntity entity) {
        long bits = entity.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(entity.getUUID().getLeastSignificantBits(), 23);
        bits ^= bits >>> 33;
        bits *= 0xff51afd7ed558ccdL;
        bits ^= bits >>> 33;
        return (int) (bits ^ bits >>> 32);
    }

    private record LocalSpot(float x, float y, float z, float radius) {
    }
}
