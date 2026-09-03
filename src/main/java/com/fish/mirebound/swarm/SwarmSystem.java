package com.fish.mirebound.swarm;

import com.fish.mirebound.mud.MudBehaviorContext;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.SwarmStateSyncPayload;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.compat.sable.SableCompat.SinkingSample;
import com.fish.mirebound.compat.sable.SableCompat.SinkingVolumeProbe;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative insect intensity, independent from persistent mud skin coverage. */
public final class SwarmSystem {
    private static final int PROBE_INTERVAL = 5;
    private static final int HORIZONTAL_SAMPLES = 3;
    private static final int VERTICAL_SAMPLES = 16;
    private static final double SAMPLE_INSET = 0.035D;
    private static final double CONTACT_TOLERANCE = 0.002D;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private SwarmSystem() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        if (MudPhysics.isPollutionSuppressed(player)) {
            state.immersion = 0.0F;
            state.profilePos = null;
            state.rotationInitialized = false;
            if (state.strength > 0.0F) {
                set(player, state, 0.0F, true);
            }
            return;
        }
        if (player.isDeadOrDying()) {
            if (enabled(player, state, MudPhysicsParameter.SWARM_CLEAR_ON_DEATH)) {
                set(player, state, 0.0F, true);
            }
            return;
        }

        if (player.tickCount % PROBE_INTERVAL == Math.floorMod(player.getId(), PROBE_INTERVAL)) {
            ImmersionSample sample = bodyImmersion(player);
            state.immersion = sample.amount();
            if (sample.profilePos() != null || sample.amount() > 0.0F) {
                state.profilePos = sample.profilePos();
                state.profileMedium = sample.medium();
            }
        }
        float previous = state.strength;
        float shakeRemoval = updateShakeRemoval(player, state);
        if (player.isInWaterOrBubble()) {
            state.strength -= value(player, state, MudPhysicsParameter.SWARM_WATER_DECAY);
        } else if (state.immersion > 0.0F) {
            state.strength = Math.max(state.strength, state.immersion);
            state.strength += value(player, state, MudPhysicsParameter.SWARM_BUILDUP) * state.immersion;
        } else {
            state.strength -= value(player, state, MudPhysicsParameter.SWARM_DECAY);
        }
        if (state.strength > 0.0F) {
            state.strength -= shakeRemoval;
        }
        state.strength = Mth.clamp(state.strength, 0.0F, 1.0F);

        if (state.strength > 0.001F) {
            float movementScale = Mth.lerp(state.strength, 1.0F,
                    value(player, state, MudPhysicsParameter.SWARM_MOVE_SCALE));
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * movementScale, motion.y, motion.z * movementScale);
        }
        if (Math.abs(previous - state.strength) >= 0.001F) {
            set(player, state, state.strength, false);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        State state = STATES.get(player.getUUID());
        if (enabled(player, state, MudPhysicsParameter.SWARM_CLEAR_ON_RECONNECT)) {
            STATES.remove(player.getUUID());
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
            if (enabled(player, state, MudPhysicsParameter.SWARM_CLEAR_ON_RECONNECT)) {
                state.strength = 0.0F;
            }
            state.rotationInitialized = false;
            sync(player, state, true);
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        STATES.clear();
    }

    public static void onStruggle(ServerPlayer player, float charge) {
        State state = STATES.get(player.getUUID());
        if (state == null || state.strength <= 0.0F) {
            return;
        }
        float effectiveness = value(player, state, MudPhysicsParameter.SWARM_STRUGGLE_SCALE);
        set(player, state, state.strength - charge * (1.0F - effectiveness + 0.08F), true);
    }

    public static void wash(ServerPlayer player, float amount) {
        State state = STATES.get(player.getUUID());
        if (state != null) {
            set(player, state, state.strength
                    - amount * value(player, state, MudPhysicsParameter.SWARM_WATER_DECAY) * 4.0F, true);
        }
    }

    private static ImmersionSample bodyImmersion(ServerPlayer player) {
        AABB box = player.getBoundingBox();
        double minX = Math.min(box.getCenter().x, box.minX + SAMPLE_INSET);
        double maxX = Math.max(box.getCenter().x, box.maxX - SAMPLE_INSET);
        double minY = box.minY + SAMPLE_INSET;
        double maxY = Math.max(minY, box.maxY - SAMPLE_INSET);
        double minZ = Math.min(box.getCenter().z, box.minZ + SAMPLE_INSET);
        double maxZ = Math.max(box.getCenter().z, box.maxZ - SAMPLE_INSET);
        SinkingVolumeProbe sableProbe = SableCompat.sinkingVolumeProbe(
                player.level(), box.inflate(0.05D), player);
        int submerged = 0;
        Map<SourceKey, Integer> sourceCounts = new HashMap<>();
        int total = HORIZONTAL_SAMPLES * HORIZONTAL_SAMPLES * VERTICAL_SAMPLES;

        for (int yIndex = 0; yIndex < VERTICAL_SAMPLES; yIndex++) {
            double y = Mth.lerp(
                    (yIndex + 0.5D) / VERTICAL_SAMPLES,
                    minY,
                    maxY);
            for (int xIndex = 0; xIndex < HORIZONTAL_SAMPLES; xIndex++) {
                double x = Mth.lerp(
                        (xIndex + 0.5D) / HORIZONTAL_SAMPLES,
                        minX,
                        maxX);
                for (int zIndex = 0; zIndex < HORIZONTAL_SAMPLES; zIndex++) {
                    double z = Mth.lerp(
                            (zIndex + 0.5D) / HORIZONTAL_SAMPLES,
                            minZ,
                            maxZ);
                    MoundSample sample = moundSample(player, new Vec3(x, y, z), sableProbe);
                    if (sample.inside()) {
                        submerged++;
                        SourceKey source = new SourceKey(
                                sample.profilePos(), sample.medium());
                        sourceCounts.merge(source, 1, Integer::sum);
                    }
                }
            }
        }
        BlockPos profilePos = null;
        SinkingMedium profileMedium = SinkingMedium.INSECT_MOUND;
        int strongestCount = 0;
        for (Map.Entry<SourceKey, Integer> entry : sourceCounts.entrySet()) {
            if (entry.getValue() > strongestCount) {
                profilePos = entry.getKey().profilePos();
                profileMedium = entry.getKey().medium();
                strongestCount = entry.getValue();
            }
        }
        return new ImmersionSample(
                submerged / (float) total, profilePos, profileMedium);
    }

    private static MoundSample moundSample(ServerPlayer player, Vec3 point,
            SinkingVolumeProbe sableProbe) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = player.level().getBlockState(pos);
        SinkingMedium medium = ModBlocks.mediumOf(state.getBlock());
        if (medium != null
                && MudMediumRuntime.enabled(player.level(), pos, medium)
                && MudBehaviorContext.swarm(player.level(), pos, medium)
                && MudBlock.containsLocalPoint(
                        player.level(), pos, state, medium,
                        point.subtract(Vec3.atLowerCornerOf(pos)),
                        CONTACT_TOLERANCE)) {
            return new MoundSample(true, pos.immutable(), medium);
        }

        SinkingSample sample = sableProbe.sample(point);
        boolean insideSable = sample != null
                && MudMediumRuntime.enabled(
                        player.level(), sample.pos(), sample.medium())
                && MudBehaviorContext.swarm(
                        player.level(), sample.pos(), sample.medium());
        return insideSable
                ? new MoundSample(true, sample.pos().immutable(), sample.medium())
                : new MoundSample(false, null, SinkingMedium.INSECT_MOUND);
    }

    private static float updateShakeRemoval(ServerPlayer player, State state) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (!state.rotationInitialized) {
            state.rotationInitialized = true;
            state.lastYaw = yaw;
            state.lastPitch = pitch;
            return 0.0F;
        }

        float yawDelta = Mth.wrapDegrees(yaw - state.lastYaw);
        float pitchDelta = Mth.wrapDegrees(pitch - state.lastPitch);
        float yawAcceleration = Mth.wrapDegrees(yawDelta - state.lastYawDelta);
        float pitchAcceleration = Mth.wrapDegrees(pitchDelta - state.lastPitchDelta);
        state.lastYaw = yaw;
        state.lastPitch = pitch;
        state.lastYawDelta = yawDelta;
        state.lastPitchDelta = pitchDelta;

        float angularJolt = Mth.sqrt(
                yawAcceleration * yawAcceleration
                        + pitchAcceleration * pitchAcceleration);
        float excess = Math.max(
                0.0F,
                angularJolt - value(player, state, MudPhysicsParameter.SWARM_SHAKE_THRESHOLD));
        return excess * value(player, state, MudPhysicsParameter.SWARM_SHAKE_REMOVAL);
    }

    private static void set(ServerPlayer player, State state, float strength, boolean force) {
        state.strength = Mth.clamp(strength, 0.0F, 1.0F);
        sync(player, state, force);
    }

    private static void sync(ServerPlayer player, State state, boolean force) {
        int packed = Mth.clamp(Math.round(state.strength * 1000.0F), 0, 1000);
        if (force || packed == 0 || Math.abs(packed - state.lastSynced) >= 8 || player.tickCount - state.lastSyncTick >= 10) {
            BlockPos profilePos = state.profilePos;
            PacketDistributor.sendToPlayer(player, new SwarmStateSyncPayload(
                    packed, profilePos != null, profilePos == null ? 0L : profilePos.asLong(),
                    state.profileMedium.id()));
            state.lastSynced = packed;
            state.lastSyncTick = player.tickCount;
        }
    }

    private static float value(ServerPlayer player, State state, MudPhysicsParameter parameter) {
        return (float) MudMediumRuntime.value(
                player.level(), state == null ? null : state.profilePos,
                state == null ? SinkingMedium.INSECT_MOUND : state.profileMedium, parameter);
    }

    private static boolean enabled(ServerPlayer player, State state, MudPhysicsParameter parameter) {
        return value(player, state, parameter) >= 0.5F;
    }

    private record ImmersionSample(
            float amount, BlockPos profilePos, SinkingMedium medium) {
    }

    private record MoundSample(
            boolean inside, BlockPos profilePos, SinkingMedium medium) {
    }

    private record SourceKey(BlockPos profilePos, SinkingMedium medium) {
    }

    private static final class State {
        private float strength;
        private float immersion;
        private BlockPos profilePos;
        private SinkingMedium profileMedium = SinkingMedium.INSECT_MOUND;
        private boolean rotationInitialized;
        private float lastYaw;
        private float lastPitch;
        private float lastYawDelta;
        private float lastPitchDelta;
        private int lastSynced = -1;
        private int lastSyncTick = Integer.MIN_VALUE;
    }
}
