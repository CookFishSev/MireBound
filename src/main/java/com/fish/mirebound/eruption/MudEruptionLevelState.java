package com.fish.mirebound.eruption;

import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.splash.MudSplashSystem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-dimension vent collection and emission schedule. */
final class MudEruptionLevelState {
    static final int SYSTEM_INTERVAL_TICKS = 10;

    private final List<MudEruptionVent> vents = new ArrayList<>();
    private final RandomSource random;
    private final MudEruptionSpawner spawner;

    MudEruptionLevelState(ServerLevel level) {
        random = RandomSource.create(level.getSeed()
                ^ level.dimension().location().hashCode() * 0x9E3779B97F4A7C15L);
        spawner = new MudEruptionSpawner(random);
    }

    void tick(ServerLevel level) {
        boolean spawnTick = level.getGameTime() % SYSTEM_INTERVAL_TICKS == 0L;
        if (vents.isEmpty()) {
            if (spawnTick) {
                spawner.trySpawnNearPlayers(
                        level, vents, MudEruptionPlayerIndex.captureSpawnPlayers(level));
            }
            return;
        }
        MudEruptionPlayerIndex players = MudEruptionPlayerIndex.capture(level);
        tickVents(level, players);
        if (spawnTick) {
            spawner.trySpawnNearPlayers(level, vents, players.spawnPlayers());
        }
    }

    void syncTo(ServerPlayer player) {
        for (MudEruptionVent vent : vents) {
            PacketDistributor.sendToPlayer(player, vent.payload(true, -1));
        }
    }

    private void tickVents(ServerLevel level, MudEruptionPlayerIndex players) {
        double renderDistance = MudPhysicsSettings.mudSplashProfile().renderDistance();
        Iterator<MudEruptionVent> iterator = vents.iterator();
        while (iterator.hasNext()) {
            MudEruptionVent vent = iterator.next();
            vent.ageTicks++;
            Vec3 worldOrigin = vent.worldOrigin();
            Vec3 worldNormal = vent.worldNormal();
            if (worldOrigin == null || worldNormal == null) {
                close(level, vent, -1);
                iterator.remove();
                continue;
            }
            if (!level.getChunkSource().hasChunk(
                    vent.surface.pos().getX() >> 4, vent.surface.pos().getZ() >> 4)) {
                close(level, vent, -1);
                iterator.remove();
                continue;
            }
            int mergeEntityId = players.crossingPlayer(vent);
            if (vent.ageTicks % 5 == 0) {
                vent.profile = com.fish.mirebound.mud.MudMediumRuntime.eruptionProfile(
                        level, vent.surface.pos(), vent.surface.medium());
            }
            boolean refreshSurface = vent.ageTicks % 20 == 0;
            boolean supportLost = refreshSurface
                    && !MudEruptionSurfaceSampler.stillSupports(level, vent.surface);
            if (mergeEntityId >= 0 || supportLost || vent.ageTicks >= vent.lifetimeTicks) {
                close(level, vent, mergeEntityId);
                iterator.remove();
                continue;
            }
            if (refreshSurface) {
                vent.visualPalette = MudEruptionSurfaceSampler.visualPalette(
                        level, vent.surface, vent.radiusPixels / 16.0D);
            }
            boolean observed = players.hasObserverNear(worldOrigin, renderDistance);
            updateContinuousFlow(vent);
            tickContinuousEmission(level, vent, worldOrigin, worldNormal, observed);
            tickSurgeEmission(level, vent, worldOrigin, worldNormal, observed);
        }
    }

    private void updateContinuousFlow(MudEruptionVent vent) {
        MudEruptionProfile.ContinuousSettings continuous = vent.profile.continuous();
        if (!continuous.enabled()) {
            return;
        }
        vent.continuousFlow += (vent.continuousTargetFlow - vent.continuousFlow) * 0.055D;
        if (--vent.continuousVariationCooldown <= 0) {
            vent.continuousTargetFlow = random.nextDouble();
            vent.continuousVariationCooldown = continuous.variationIntervalTicks();
        }
    }

    private void tickContinuousEmission(
            ServerLevel level, MudEruptionVent vent, Vec3 origin,
            Vec3 normal, boolean observed) {
        MudEruptionProfile.ContinuousSettings continuous = vent.profile.continuous();
        if (!continuous.enabled() || --vent.continuousCooldown > 0) {
            return;
        }
        MudEruptionDynamics.Burst flowBurst = MudEruptionDynamics.continuousBurst(
                vent.profile, vent.radiusPixels, vent.continuousFlow,
                random.nextDouble(), random.nextDouble());
        int accepted = observed ? emit(level, vent, origin, normal, flowBurst,
                continuous.jetCohesion(),
                continuous.coneScale(),
                continuous.breakupTriggerRatio(),
                continuous.breakupDurationTicks(),
                continuous.intervalTicks(),
                continuous.particleLifetimeTicks(),
                0xD1B54A32D192ED03L) : 0;
        if (accepted > 0 && vent.ageTicks >= vent.nextFlowSoundTick) {
            playEruptionSound(level, origin, flowBurst, 0.42F);
            vent.nextFlowSoundTick = vent.ageTicks
                    + Math.max(12, continuous.intervalTicks() * 4);
        }
        vent.continuousCooldown = continuous.intervalTicks();
    }

    private void tickSurgeEmission(ServerLevel level, MudEruptionVent vent,
            Vec3 origin, Vec3 normal, boolean observed) {
        MudEruptionProfile.SurgeSettings surges = vent.profile.surges();
        if (--vent.burstCooldown <= 0) {
            if (surges.enabled() && !vent.surgeActive()) {
                vent.startSurge(MudEruptionDynamics.burst(
                        vent.profile, vent.radiusPixels,
                        random.nextDouble(), random.nextDouble()));
            }
            vent.burstCooldown = randomBetween(
                    surges.minimumIntervalTicks(),
                    surges.maximumIntervalTicks());
        }
        MudEruptionDynamics.Burst surgeSlice = vent.nextSurgeSlice();
        if (!observed || surgeSlice == null) {
            return;
        }
        int accepted = emit(level, vent, origin, normal, surgeSlice,
                surges.jetCohesion(),
                surges.coneScale(),
                surges.breakupTriggerRatio(),
                surges.breakupDurationTicks(),
                1,
                MudPhysicsSettings.mudSplashProfile().lifetimeTicks(),
                0x9E3779B97F4A7C15L);
        if (accepted > 0 && vent.surgeSoundPending) {
            playEruptionSound(level, origin, vent.surgeSoundBurst(), 1.0F);
            vent.surgeSoundPending = false;
        }
    }

    private int emit(ServerLevel level, MudEruptionVent vent,
            Vec3 origin, Vec3 normal,
            MudEruptionDynamics.Burst burst, double jetCohesion,
            double coneScale, double breakupTriggerRatio,
            int breakupDurationTicks, int columnTrailTicks,
            int particleLifetimeTicks, long streamSalt) {
        double launchSpeed = MudEruptionDynamics.launchSpeed(
                burst.height(), MudPhysicsSettings.mudSplashProfile().gravity());
        return MudSplashSystem.spawnFountain(
                level, origin.add(normal.scale(0.018D)), normal, vent.visualPalette,
                vent.surface.medium(), burst.droplets(), launchSpeed,
                jetCohesion, coneScale, breakupTriggerRatio,
                breakupDurationTicks, columnTrailTicks, particleLifetimeTicks,
                vent.seed ^ (long) vent.ageTicks * streamSalt);
    }

    private void playEruptionSound(ServerLevel level, Vec3 origin,
            MudEruptionDynamics.Burst burst, float volumeScale) {
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.MUD_PLACE, SoundSource.BLOCKS,
                (0.16F + burst.droplets() * 0.006F) * volumeScale,
                0.72F + random.nextFloat() * 0.18F);
    }

    private static void close(ServerLevel level, MudEruptionVent vent, int mergeEntityId) {
        PacketDistributor.sendToPlayersInDimension(
                level, vent.payload(false, mergeEntityId));
    }

    private int randomBetween(int minimum, int maximum) {
        return minimum >= maximum ? minimum : minimum + random.nextInt(maximum - minimum + 1);
    }
}
