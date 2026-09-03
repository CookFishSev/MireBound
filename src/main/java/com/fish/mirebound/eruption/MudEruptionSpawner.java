package com.fish.mirebound.eruption;

import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Low-frequency discovery, spacing and dimension-wide instance budgets for eruption vents. */
final class MudEruptionSpawner {
    static final int HARD_MAXIMUM_ACTIVE_PER_LEVEL = 96;
    private static final int BASE_PROBES_PER_PLAYER = 2;
    private static final int VERTICAL_PROBE_ABOVE = 4;
    private static final int VERTICAL_PROBE_BELOW = 7;

    private final RandomSource random;

    MudEruptionSpawner(RandomSource random) {
        this.random = random;
    }

    void trySpawnNearPlayers(ServerLevel level, List<MudEruptionVent> vents,
            List<ServerPlayer> players) {
        if (!hasCapacity(vents.size(), MudPhysicsSettings.eruptionMaximumActivePerLevel())) {
            return;
        }
        double broadRadius = broadSearchRadius();
        int probeBudget = broadSpawnAttempts();
        for (ServerPlayer player : players) {
            trySpawnLocalProfile(level, vents, player,
                    MudPhysicsParameter.ERUPTION_SEARCH_RADIUS.maximum());
            if (!hasCapacity(vents.size(), MudPhysicsSettings.eruptionMaximumActivePerLevel())) {
                return;
            }
            for (int probe = 0; probe < probeBudget; probe++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = Math.sqrt(random.nextDouble()) * broadRadius;
                int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                List<MudEruptionSurfaceSampler.Surface> surfaces =
                        MudEruptionSurfaceSampler.findSurfaces(
                                level, player, x, z,
                                Mth.floor(player.getY()) + VERTICAL_PROBE_ABOVE,
                                Mth.floor(player.getY()) - VERTICAL_PROBE_BELOW);
                MudEruptionSurfaceSampler.Surface surface = chooseSpawnSurface(
                        level, vents, player, surfaces, probe);
                if (surface == null) {
                    continue;
                }
                MudEruptionProfile profile = MudMediumRuntime.eruptionProfile(
                        level, surface.pos(), surface.medium());
                MudEruptionProfile.SpawnSettings spawning = profile.spawning();
                double adjustedChance = spawning.spawnChance()
                        * MudEruptionLevelState.SYSTEM_INTERVAL_TICKS
                        / spawning.spawnIntervalTicks();
                if (random.nextDouble() > Mth.clamp(adjustedChance, 0.0D, 1.0D)) {
                    continue;
                }
                spawn(level, vents, surface.withRandomOrigin(random), profile);
                if (!hasCapacity(vents.size(), MudPhysicsSettings.eruptionMaximumActivePerLevel())) {
                    return;
                }
            }
        }
    }

    private void trySpawnLocalProfile(ServerLevel level, List<MudEruptionVent> vents,
            ServerPlayer player, double broadRadius) {
        MudBlockProfileStore.EruptionCandidate candidate =
                MudBlockProfileStore.get(level).randomEruptionCandidate(
                        level, player.getX(), player.getZ(), broadRadius, random);
        if (candidate == null) {
            return;
        }
        MudEruptionProfile profile = candidate.profile();
        MudEruptionProfile.SpawnSettings spawning = profile.spawning();
        if (!hasCapacity(vents.size(), MudPhysicsSettings.eruptionMaximumActivePerLevel())) {
            return;
        }
        List<MudEruptionSurfaceSampler.Surface> surfaces =
                MudEruptionSurfaceSampler.exposedSurfaces(level, candidate.pos());
        MudEruptionSurfaceSampler.Surface surface = chooseConfiguredSurface(
                surfaces, candidate.medium(), spawning);
        if (surface == null) {
            return;
        }
        double adjustedChance = spawning.spawnChance()
                * MudEruptionLevelState.SYSTEM_INTERVAL_TICKS / spawning.spawnIntervalTicks();
        if (random.nextDouble() <= MudEruptionDynamics.combinedAttemptChance(
                adjustedChance, spawning.spawnAttempts())) {
            spawn(level, vents, surface.withRandomOrigin(random), profile);
        }
    }

    private MudEruptionSurfaceSampler.Surface chooseSpawnSurface(
            ServerLevel level, List<MudEruptionVent> vents, ServerPlayer player,
            List<MudEruptionSurfaceSampler.Surface> surfaces, int probe) {
        if (surfaces.isEmpty()) {
            return null;
        }
        int start = random.nextInt(surfaces.size());
        for (int offset = 0; offset < surfaces.size(); offset++) {
            MudEruptionSurfaceSampler.Surface surface =
                    surfaces.get((start + offset) % surfaces.size());
            MudEruptionProfile.SpawnSettings spawning = MudMediumRuntime.eruptionProfile(
                    level, surface.pos(), surface.medium()).spawning();
            if (!spawning.enabled() || !spawning.allows(surface.face())
                    || probe >= spawning.spawnAttempts()
                    || !hasCapacity(vents.size(),
                            MudPhysicsSettings.eruptionMaximumActivePerLevel())) {
                continue;
            }
            Vec3 worldOrigin = surface.worldOrigin();
            if (worldOrigin == null || player.position().distanceTo(worldOrigin)
                    > spawning.searchRadius()) {
                continue;
            }
            return surface;
        }
        return null;
    }

    private MudEruptionSurfaceSampler.Surface chooseConfiguredSurface(
            List<MudEruptionSurfaceSampler.Surface> surfaces, SinkingMedium medium,
            MudEruptionProfile.SpawnSettings spawning) {
        if (surfaces.isEmpty()) {
            return null;
        }
        int start = random.nextInt(surfaces.size());
        for (int offset = 0; offset < surfaces.size(); offset++) {
            MudEruptionSurfaceSampler.Surface surface =
                    surfaces.get((start + offset) % surfaces.size());
            if (surface.medium() == medium && spawning.allows(surface.face())) {
                return surface;
            }
        }
        return null;
    }

    private void spawn(ServerLevel level, List<MudEruptionVent> vents,
            MudEruptionSurfaceSampler.Surface surface, MudEruptionProfile profile) {
        MudEruptionProfile.SpawnSettings spawning = profile.spawning();
        MudEruptionProfile.ContinuousSettings continuous = profile.continuous();
        MudEruptionProfile.SurgeSettings surges = profile.surges();
        if (!hasCapacity(vents.size(), MudPhysicsSettings.eruptionMaximumActivePerLevel())) {
            return;
        }
        Vec3 origin = surface.worldOrigin();
        if (origin == null) {
            return;
        }
        if (tooClose(vents, origin, spawning.minimumSpacing())) {
            return;
        }
        double radius = randomBetween(
                spawning.minimumRadiusPixels(), spawning.maximumRadiusPixels());
        int lifetime = randomBetween(
                spawning.minimumLifetimeTicks(), spawning.maximumLifetimeTicks());
        long seed = random.nextLong();
        var visualPalette = MudEruptionSurfaceSampler.visualPalette(
                level, surface, radius / 16.0D);
        MudEruptionVent vent = new MudEruptionVent(
                MudEruptionSystem.nextId(), level.dimension().location(), surface, profile,
                radius, lifetime, seed, visualPalette, random.nextDouble(),
                randomBetween(1, continuous.intervalTicks()),
                randomBetween(1, continuous.variationIntervalTicks()),
                randomBetween(surges.minimumIntervalTicks(),
                        surges.maximumIntervalTicks()));
        vents.add(vent);
        PacketDistributor.sendToPlayersInDimension(level, vent.payload(true, -1));
    }

    static boolean hasCapacity(int totalActive, int configuredMaximum) {
        return totalActive < HARD_MAXIMUM_ACTIVE_PER_LEVEL
                && configuredMaximum > 0
                && totalActive < Math.min(HARD_MAXIMUM_ACTIVE_PER_LEVEL, configuredMaximum);
    }

    private static boolean tooClose(List<MudEruptionVent> vents, Vec3 origin, double spacing) {
        double distanceSquared = spacing * spacing;
        for (MudEruptionVent vent : vents) {
            Vec3 currentOrigin = vent.worldOrigin();
            if (currentOrigin != null && currentOrigin.distanceToSqr(origin) < distanceSquared) {
                return true;
            }
        }
        return false;
    }

    private static double broadSearchRadius() {
        double radius = 10.0D;
        for (SinkingMedium medium : SinkingMedium.values()) {
            MudEruptionProfile profile = MudPhysicsSettings.eruptionProfile(medium);
            if (profile != null && profile.spawning().enabled()) {
                radius = Math.max(radius, profile.spawning().searchRadius());
            }
        }
        return Math.min(32.0D, radius);
    }

    private static int broadSpawnAttempts() {
        int attempts = BASE_PROBES_PER_PLAYER;
        for (SinkingMedium medium : SinkingMedium.values()) {
            MudEruptionProfile profile = MudPhysicsSettings.eruptionProfile(medium);
            if (profile != null && profile.spawning().enabled()) {
                attempts = Math.max(attempts, profile.spawning().spawnAttempts());
            }
        }
        return Math.min(8, attempts);
    }

    private int randomBetween(int minimum, int maximum) {
        return minimum >= maximum ? minimum : minimum + random.nextInt(maximum - minimum + 1);
    }

    private double randomBetween(double minimum, double maximum) {
        return minimum >= maximum ? minimum : Mth.lerp(random.nextDouble(), minimum, maximum);
    }
}
