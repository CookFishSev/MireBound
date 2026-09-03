package com.fish.mirebound.splash;

import com.fish.mirebound.adaptive.MudVisualSource;
import com.fish.mirebound.adaptive.MudVisualPalette;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.entitycoverage.EntityMudCoverageService;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPhysics;
import com.fish.mirebound.stain.MudWallStainSystem;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.network.payload.MudSplashPayload;
import com.fish.mirebound.network.payload.MudSurfaceImpactPayload;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative ballistic fragments without per-fragment entities. Each
 * level owns a bounded, reusable array and only active droplets are touched.
 */
public final class MudSplashSystem {
    private static final Map<ServerLevel, Pool> POOLS = new IdentityHashMap<>();
    private static final double MAXIMUM_STEP_LENGTH = 0.18D;
    private static final int MAXIMUM_NEARBY_NON_PLAYER_ENTITIES = 64;

    private MudSplashSystem() {
    }

    /** Converts one visible mud-ball impact into a bounded outward fragment fan. */
    public static int spawnClodImpact(
            ServerPlayer player, Vec3 origin, Vec3 impactNormal,
            Vec3 incomingVelocity, int ignoredEntityId,
            int requestedFragments,
            float chargePower, SinkingMedium medium) {
        MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
        int count = Mth.clamp(requestedFragments, 1, 8);
        if (!profile.enabled()) {
            return 0;
        }
        Pool pool = POOLS.computeIfAbsent(player.serverLevel(), ignored -> new Pool());
        count = Math.min(count, pool.remainingCapacity(profile));
        if (count <= 0) {
            return 0;
        }
        float power = Mth.clamp(chargePower, 0.0F, 1.0F);
        long seed = player.getUUID().getLeastSignificantBits()
                ^ player.serverLevel().getGameTime() * 0x9E3779B97F4A7C15L
                ^ Float.floatToIntBits(power);
        RandomSource random = RandomSource.create(seed);
        List<Spawn> spawns = new ArrayList<>(count);
        Vec3 normal = safeNormal(impactNormal,
                incomingVelocity.lengthSqr() > 1.0E-8D
                        ? incomingVelocity.normalize().scale(-1.0D)
                        : new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 carried = incomingVelocity.scale(0.055D);
        float baseStrength = (float) Mth.clamp(
                profile.stainStrength() * (0.34D + power * 0.18D),
                0.03D, 0.72D);
        for (int index = 0; index < count; index++) {
            Vec3 direction = new Vec3(
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.32D,
                    random.nextDouble() - 0.5D);
            double inward = direction.dot(normal);
            if (inward < 0.04D) {
                direction = direction.add(normal.scale(0.04D - inward));
            }
            direction = safeNormal(direction, normal);
            double speed = 0.16D + power * 0.12D
                    + random.nextDouble() * 0.16D;
            Vec3 fragmentVelocity = direction.scale(speed).add(carried);
            spawns.add(new Spawn(
                    medium == null ? SinkingMedium.MUD : medium,
                    MudVisualSource.NONE,
                    origin, fragmentVelocity,
                    0.032F + random.nextFloat() * 0.035F,
                    baseStrength * (0.72F + random.nextFloat() * 0.28F),
                    true));
        }
        int accepted = pool.spawn(ignoredEntityId, spawns, profile);
        sendVisualBatch(player.serverLevel(), origin,
                ignoredEntityId, seed, spawns, accepted,
                profile, profile.lifetimeTicks());
        return accepted;
    }

    /**
     * Emits one rejection burst into the same bounded ballistic pool as impact
     * splashes. Client visuals and server wall/player stains therefore follow
     * exactly the same trajectories.
     */
    public static int spawnPurge(ServerPlayer player, SinkingMedium medium,
            int dropletCount, float launchSpeed) {
        float[] mediumWeights = new float[SinkingMedium.COUNT];
        if (medium != null) {
            mediumWeights[medium.id()] = 1.0F;
        }
        return spawnPurge(player, mediumWeights, medium, dropletCount, launchSpeed);
    }

    /**
     * Emits one bounded burst containing the complete assimilation mixture. Each
     * droplet still owns one concrete medium so its texture and eventual stains
     * remain exact instead of collapsing the palette into one averaged color.
     */
    public static int spawnPurge(ServerPlayer player, float[] mediumWeights,
            SinkingMedium fallback, int dropletCount, float launchSpeed) {
        MudVisualPalette palette = new MudVisualPalette();
        int length = Math.min(mediumWeights == null ? 0 : mediumWeights.length,
                SinkingMedium.COUNT);
        for (int mediumId = 0; mediumId < length; mediumId++) {
            if (mediumWeights[mediumId] > 0.0F) {
                palette.add(SinkingMedium.byId(mediumId), MudVisualSource.NONE,
                        mediumWeights[mediumId]);
            }
        }
        return spawnPurge(player, palette, fallback, dropletCount, launchSpeed);
    }

    public static int spawnPurge(ServerPlayer player, MudVisualPalette visualPalette,
            SinkingMedium fallback, int dropletCount, float launchSpeed) {
        MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
        int count = Mth.clamp(dropletCount, 0, 24);
        if (count == 0 || !profile.enabled()) {
            return 0;
        }
        MudVisualPalette palette = visualPalette == null
                ? new MudVisualPalette() : visualPalette;
        if (palette.isEmpty()) {
            palette = new MudVisualPalette();
            palette.add(fallback == null ? SinkingMedium.ASSIMILATION_SLIME : fallback,
                    MudVisualSource.NONE, 1.0F);
        }
        List<MudVisualPalette.Entry> assignments = interleaveVisualEntries(
                palette, allocateVisualCounts(count, palette));
        if (assignments.isEmpty()) {
            return 0;
        }
        long seed = player.getUUID().getMostSignificantBits()
                ^ player.serverLevel().getGameTime() * 0x9E3779B97F4A7C15L;
        RandomSource random = RandomSource.create(seed);
        Vec3 origin = player.position().add(0.0D, player.getBbHeight() * 0.48D, 0.0D);
        List<Spawn> spawns = new ArrayList<>(count);
        for (int index = 0; index < assignments.size(); index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double horizontal = launchSpeed * (0.45D + random.nextDouble() * 0.55D);
            double upward = launchSpeed * (0.25D + random.nextDouble() * 0.65D);
            Vec3 velocity = new Vec3(
                    Math.cos(angle) * horizontal,
                    upward,
                    Math.sin(angle) * horizontal);
            float size = 0.025F + random.nextFloat() * 0.020F;
            float strength = (float) Mth.clamp(
                    profile.stainStrength()
                            * (0.72D + random.nextDouble() * 0.28D),
                    0.05D, 1.0D);
            spawns.add(new Spawn(
                    assignments.get(index).medium(), assignments.get(index).visualSource(),
                    origin, velocity, size, strength, true));
        }
        Pool pool = POOLS.computeIfAbsent(player.serverLevel(), ignored -> new Pool());
        int accepted = pool.spawn(player.getId(), spawns, profile);
        if (accepted <= 0) {
            return 0;
        }
        Map<VisualKey, List<MudSplashPayload.Droplet>> visualByMedium =
                new LinkedHashMap<>();
        for (int index = 0; index < accepted; index++) {
            Spawn spawn = spawns.get(index);
            visualByMedium.computeIfAbsent(
                    new VisualKey(spawn.medium(), spawn.visualSource()),
                    ignored -> new ArrayList<>())
                    .add(new MudSplashPayload.Droplet(
                            (float) spawn.velocity().x,
                            (float) spawn.velocity().y,
                            (float) spawn.velocity().z,
                            spawn.size()));
        }
        for (Map.Entry<VisualKey, List<MudSplashPayload.Droplet>> entry
                : visualByMedium.entrySet()) {
            PacketDistributor.sendToPlayersNear(
                    player.serverLevel(), null, origin.x, origin.y, origin.z,
                    profile.renderDistance(),
                    new MudSplashPayload(
                            origin.x, origin.y, origin.z, player.getId(),
                            entry.getKey().medium(), entry.getKey().visualSource(),
                            profile.playerHitRadius(),
                            (float) profile.gravity(),
                            (float) profile.drag(),
                            profile.lifetimeTicks(),
                            seed ^ entry.getKey().medium().id() * 0x9E3779B97F4A7C15L
                                    ^ entry.getKey().visualSource(),
                            entry.getValue()));
        }
        return accepted;
    }

    /**
     * Emits a column-first fountain into the existing global droplet pool. Vent logic
     * supplies only a bounded palette and launch envelope; collision, staining,
     * Sable support and network presentation remain owned here.
     */
    public static int spawnFountain(ServerLevel level, Vec3 origin,
            MudVisualPalette visualPalette, SinkingMedium fallback, int requestedDroplets,
            double launchSpeed, double jetCohesion, double topSpreadScale,
            double spreadTriggerRatio, int spreadDurationTicks,
            int columnTrailTicks, int particleLifetimeTicks, long seed) {
        return spawnFountain(level, origin, new Vec3(0.0D, 1.0D, 0.0D),
                visualPalette, fallback, requestedDroplets, launchSpeed,
                jetCohesion, topSpreadScale, spreadTriggerRatio,
                spreadDurationTicks, columnTrailTicks, particleLifetimeTicks, seed);
    }

    public static int spawnFountain(ServerLevel level, Vec3 origin, Vec3 launchNormal,
            MudVisualPalette visualPalette, SinkingMedium fallback, int requestedDroplets,
            double launchSpeed, double jetCohesion, double topSpreadScale,
            double spreadTriggerRatio, int spreadDurationTicks,
            int columnTrailTicks, int particleLifetimeTicks, long seed) {
        MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
        int count = Mth.clamp(requestedDroplets, 0, 64);
        if (!profile.enabled() || count == 0) {
            return 0;
        }
        Pool pool = POOLS.computeIfAbsent(level, ignored -> new Pool());
        count = Math.min(count, pool.remainingCapacity(profile));
        if (count == 0) {
            return 0;
        }
        MudVisualPalette palette = visualPalette == null
                ? new MudVisualPalette() : visualPalette;
        if (palette.isEmpty()) {
            palette = new MudVisualPalette();
            palette.add(fallback == null ? SinkingMedium.MUD : fallback,
                    MudVisualSource.NONE, 1.0F);
        }
        List<MudVisualPalette.Entry> assignments = interleaveVisualEntries(
                palette, allocateVisualCounts(count, palette));
        RandomSource random = RandomSource.create(seed);
        List<Spawn> spawns = new ArrayList<>(assignments.size());
        Vec3 normal = launchNormal == null || launchNormal.lengthSqr() <= 1.0E-8D
                ? new Vec3(0.0D, 1.0D, 0.0D) : launchNormal.normalize();
        Vec3 tangentU = orthogonalAxis(Vec3.ZERO, normal);
        Vec3 tangentV = normal.cross(tangentU).normalize();
        for (MudVisualPalette.Entry assignment : assignments) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double cohesion = Mth.clamp(jetCohesion, 0.0D, 1.0D);
            boolean coreBranch = random.nextDouble() < cohesion;
            double radial = MudFountainMotion.radialSpeed(
                    launchSpeed, cohesion, topSpreadScale,
                    coreBranch, random.nextDouble());
            double upward = MudFountainMotion.upwardSpeed(
                    launchSpeed, coreBranch, random.nextDouble());
            Vec3 velocity = normal.scale(upward)
                    .add(tangentU.scale(Math.cos(angle) * radial))
                    .add(tangentV.scale(Math.sin(angle) * radial));
            float breakupTriggerVelocity = (float) (launchSpeed
                    * Mth.clamp(spreadTriggerRatio, 0.0D, 0.80D)
                    * (0.90D + random.nextDouble() * 0.20D));
            float size = 0.034F + random.nextFloat() * 0.034F;
            float strength = (float) Mth.clamp(
                    profile.stainStrength() * (0.72D + random.nextDouble() * 0.28D),
                    0.05D, 1.0D);
            spawns.add(new Spawn(
                    assignment.medium(), assignment.visualSource(),
                    origin, velocity, size, strength, false,
                    true, breakupTriggerVelocity,
                    Mth.clamp(spreadDurationTicks, 1, 12),
                    Mth.clamp(columnTrailTicks, 1, 40)));
        }
        int lifetime = Mth.clamp(particleLifetimeTicks, 1, 255);
        int accepted = pool.spawn(-1, spawns, profile, lifetime);
        sendVisualBatch(level, origin, -1, seed, spawns, accepted, profile, lifetime);
        return accepted;
    }

    public static boolean tryImpact(ServerPlayer player, MudPlayerData data,
            SinkingMedium medium, Vec3 surfacePoint, Vec3 surfaceNormal,
            Vec3 surfaceAxisX, Vec3 surfaceAxisZ, Vec3 incomingVelocity,
            double mudVolumeFraction, boolean createSurfacePile) {
        MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
        if (!profile.enabled()
                || MudPhysics.isPollutionSuppressed(player)
                || !impactCooldownElapsed(
                        player.tickCount,
                        data.lastMudSplashTick,
                        profile.impactCooldownTicks())) {
            return false;
        }
        Vec3 normal = safeNormal(surfaceNormal, new Vec3(0.0D, 1.0D, 0.0D));
        double inwardSpeed = Math.max(0.0D, -incomingVelocity.dot(normal));
        double minimum = profile.minimumImpactSpeed();
        if (inwardSpeed < minimum) {
            return false;
        }

        double maximum = profile.maximumImpactSpeed();
        double speedFactor = qualifiedImpactStrength(
                inwardSpeed, minimum, maximum);
        double surfaceImpactStrength = speedFactor;
        double volumeFactor = Mth.clamp(0.20D + mudVolumeFraction * 0.80D, 0.20D, 1.0D);
        int dropletCount = dropletCount(
                profile.baseDroplets(),
                profile.maximumDropletsPerImpact(),
                speedFactor,
                volumeFactor);
        long seed = player.getUUID().getLeastSignificantBits()
                ^ player.serverLevel().getGameTime() * 0x9e3779b97f4a7c15L
                ^ Double.doubleToLongBits(surfacePoint.x + surfacePoint.y * 7.0D + surfacePoint.z * 31.0D);
        RandomSource random = RandomSource.create(seed);
        Vec3 axisX = orthogonalAxis(surfaceAxisX, normal);
        Vec3 axisZ = orthogonalAxis(surfaceAxisZ, normal);
        if (axisX.cross(axisZ).dot(normal) < 0.0D) {
            axisZ = axisZ.scale(-1.0D);
        }
        Vec3 tangentialCarry = incomingVelocity.subtract(normal.scale(incomingVelocity.dot(normal))).scale(0.18D);
        double launchScale = profile.launchSpeed()
                * (0.48D + speedFactor * 0.92D)
                * Math.sqrt(volumeFactor);
        Vec3 origin = surfacePoint.add(normal.scale(0.025D));
        List<MediumShare> mediumShares = impactMediumShares(
                player, medium, surfacePoint, normal, axisX, axisZ);
        int[] weights = new int[mediumShares.size()];
        for (int index = 0; index < weights.length; index++) {
            weights[index] = mediumShares.get(index).weight();
        }
        int[] mediumCounts = allocateMediumCounts(
                dropletCount, weights);
        List<MediumShare> assignments =
                interleaveMediums(mediumShares, mediumCounts);
        List<Spawn> spawns = new ArrayList<>(dropletCount);
        for (int index = 0; index < assignments.size(); index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radial = 0.24D + random.nextDouble() * 0.78D;
            double lift = 0.52D + random.nextDouble() * 0.70D;
            Vec3 direction = normal.scale(lift)
                    .add(axisX.scale(Math.cos(angle) * radial))
                    .add(axisZ.scale(Math.sin(angle) * radial))
                    .normalize();
            double speed = launchScale * (0.58D + random.nextDouble() * 0.72D);
            Vec3 velocity = direction.scale(speed).add(tangentialCarry);
            float size = (float) ((0.040D + random.nextDouble() * 0.035D)
                    * (0.72D + volumeFactor * 0.38D));
            float strength = (float) Mth.clamp(
                    profile.stainStrength()
                            * (0.70D + random.nextDouble() * 0.30D)
                            * (0.70D + speedFactor * 0.30D),
                    0.05D,
                    1.0D);
            spawns.add(new Spawn(
                    assignments.get(index).medium(),
                    assignments.get(index).visualSource(),
                    origin,
                    velocity,
                    size,
                    strength,
                    false));
        }

        Pool pool = POOLS.computeIfAbsent(player.serverLevel(), ignored -> new Pool());
        int accepted = pool.spawn(player.getId(), spawns, profile);
        boolean surfacePileCreated = createSurfacePile && surfaceImpactStrength > 0.0D;
        if (!impactHasFeedback(accepted, surfacePileCreated)) {
            return false;
        }
        data.lastMudSplashTick = player.tickCount;
        Map<VisualKey, List<MudSplashPayload.Droplet>> visualByMedium =
                new LinkedHashMap<>();
        for (int index = 0; index < accepted; index++) {
            Spawn spawn = spawns.get(index);
            visualByMedium.computeIfAbsent(
                    new VisualKey(spawn.medium(), spawn.visualSource()),
                    ignored -> new ArrayList<>())
                    .add(new MudSplashPayload.Droplet(
                            (float) spawn.velocity().x,
                            (float) spawn.velocity().y,
                            (float) spawn.velocity().z,
                            spawn.size()));
        }
        for (Map.Entry<VisualKey,
                List<MudSplashPayload.Droplet>> entry
                : visualByMedium.entrySet()) {
            PacketDistributor.sendToPlayersNear(
                    player.serverLevel(),
                    null,
                    origin.x,
                    origin.y,
                    origin.z,
                    profile.renderDistance(),
                    new MudSplashPayload(
                            origin.x,
                            origin.y,
                            origin.z,
                            player.getId(),
                            entry.getKey().medium(),
                            entry.getKey().visualSource(),
                            profile.playerHitRadius(),
                            (float) profile.gravity(),
                            (float) profile.drag(),
                            profile.lifetimeTicks(),
                            seed ^ entry.getKey().medium().id()
                                    * 0x9e3779b97f4a7c15L,
                            entry.getValue()));
        }
        if (surfacePileCreated) {
            PacketDistributor.sendToPlayersNear(
                    player.serverLevel(),
                    null,
                    origin.x,
                    origin.y,
                    origin.z,
                    profile.renderDistance(),
                    new MudSurfaceImpactPayload(
                            player.getId(),
                            medium,
                            surfacePoint.x,
                            surfacePoint.y,
                            surfacePoint.z,
                            (float) surfaceImpactStrength,
                            (float) volumeFactor));
        }
        player.serverLevel().playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.MUD_BREAK, SoundSource.BLOCKS,
                (float) (0.30D + speedFactor * 0.38D),
                (float) (0.82D + random.nextDouble() * 0.24D));
        return true;
    }

    static boolean impactHasFeedback(int acceptedDroplets, boolean createSurfacePile) {
        return acceptedDroplets > 0 || createSurfacePile;
    }

    private static List<MediumShare> impactMediumShares(
            ServerPlayer player,
            SinkingMedium fallback,
            Vec3 surfacePoint,
            Vec3 normal,
            Vec3 axisX,
            Vec3 axisZ) {
        double radius = Mth.clamp(
                player.getBbWidth() * 0.72D, 0.34D, 0.58D);
        AABB sampleBounds = new AABB(surfacePoint, surfacePoint)
                .inflate(radius + 0.12D);
        SableCompat.MudVolumeProbe sableProbe =
                SableCompat.isLoaded()
                        ? SableCompat.mudVolumeProbe(
                                player.level(), sampleBounds, player)
                        : null;
        Map<VisualKey, Integer> weights = new LinkedHashMap<>();
        for (int row = -1; row <= 1; row++) {
            for (int column = -1; column <= 1; column++) {
                Vec3 sample = surfacePoint
                        .add(axisX.scale(column * radius))
                        .add(axisZ.scale(row * radius))
                        .add(normal.scale(-0.035D));
                MediumShare sampled = ordinaryMediumAt(
                        player.serverLevel(), sample);
                if (sampled == null
                        && sableProbe != null
                        && !sableProbe.isEmpty()) {
                    SableCompat.MudVolumeSample sable =
                            sableProbe.sample(sample, 0.025D);
                    sampled = sable == null ? null
                            : new MediumShare(
                                    sable.medium(), sable.visualSource(), 1);
                }
                if (sampled != null) {
                    weights.merge(new VisualKey(
                            sampled.medium(), sampled.visualSource()),
                            1, Integer::sum);
                }
            }
        }
        if (weights.isEmpty()) {
            return List.of(new MediumShare(
                    fallback, MudVisualSource.NONE, 1));
        }
        List<MediumShare> result =
                new ArrayList<>(weights.size());
        for (Map.Entry<VisualKey, Integer> entry
                : weights.entrySet()) {
            result.add(new MediumShare(
                    entry.getKey().medium(), entry.getKey().visualSource(),
                    entry.getValue()));
        }
        return List.copyOf(result);
    }

    private static MediumShare ordinaryMediumAt(
            ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        var state = level.getBlockState(pos);
        SinkingMedium medium =
                ModBlocks.mediumOf(state.getBlock());
        if (medium == null
                || !MudMediumRuntime.enabled(level, pos, medium)) {
            return null;
        }
        Vec3 local = point.subtract(
                pos.getX(), pos.getY(), pos.getZ());
        return MudBlock.containsLocalPoint(
                level, pos, state, medium, local, 0.025D)
                ? new MediumShare(medium,
                        MudVisualSource.capture(level, pos), 1)
                : null;
    }

    static int[] allocateMediumCounts(
            int total, int[] weights) {
        int[] counts = new int[weights.length];
        if (total <= 0 || weights.length == 0) {
            return counts;
        }
        int positive = 0;
        long weightSum = 0L;
        for (int weight : weights) {
            if (weight > 0) {
                positive++;
                weightSum += weight;
            }
        }
        if (positive == 0) {
            counts[0] = total;
            return counts;
        }
        if (total < positive) {
            for (int assigned = 0;
                    assigned < total;
                    assigned++) {
                int best = -1;
                for (int index = 0;
                        index < weights.length;
                        index++) {
                    if (counts[index] == 0
                            && weights[index] > 0
                            && (best < 0
                                    || weights[index]
                                            > weights[best])) {
                        best = index;
                    }
                }
                counts[best] = 1;
            }
            return counts;
        }

        int remaining = total;
        for (int index = 0;
                index < weights.length;
                index++) {
            if (weights[index] > 0) {
                counts[index] = 1;
                remaining--;
            }
        }
        long[] remainders = new long[weights.length];
        int distributed = 0;
        for (int index = 0;
                index < weights.length;
                index++) {
            if (weights[index] <= 0) {
                continue;
            }
            long weighted = (long) remaining
                    * weights[index];
            int share = (int) (weighted / weightSum);
            counts[index] += share;
            distributed += share;
            remainders[index] = weighted % weightSum;
        }
        int leftovers = remaining - distributed;
        while (leftovers-- > 0) {
            int best = -1;
            for (int index = 0;
                    index < remainders.length;
                    index++) {
                if (weights[index] > 0
                        && (best < 0
                                || remainders[index]
                                        > remainders[best])) {
                    best = index;
                }
            }
            counts[best]++;
            remainders[best] = -1L;
        }
        return counts;
    }

    static int[] allocatePurgeMediumCounts(
            int total, float[] mediumWeights, SinkingMedium fallback) {
        int[] weights = new int[SinkingMedium.COUNT];
        int length = Math.min(
                mediumWeights == null ? 0 : mediumWeights.length,
                SinkingMedium.COUNT);
        boolean populated = false;
        for (int mediumId = 0; mediumId < length; mediumId++) {
            float weight = mediumWeights[mediumId];
            if (!Float.isFinite(weight) || weight <= 0.0001F) {
                continue;
            }
            weights[mediumId] = Math.max(1, Math.round(weight * 10_000.0F));
            populated = true;
        }
        if (!populated) {
            SinkingMedium selected = fallback == null
                    ? SinkingMedium.ASSIMILATION_SLIME : fallback;
            weights[selected.id()] = 1;
        }
        return allocateMediumCounts(total, weights);
    }

    static int[] allocateVisualCounts(int total, MudVisualPalette palette) {
        if (palette == null || palette.isEmpty()) {
            return new int[0];
        }
        int[] weights = new int[palette.size()];
        for (int index = 0; index < weights.length; index++) {
            weights[index] = Math.max(1,
                    Math.round(palette.weightAt(index) * 10_000.0F));
        }
        return allocateMediumCounts(total, weights);
    }

    private static List<MudVisualPalette.Entry> interleaveVisualEntries(
            MudVisualPalette palette, int[] counts) {
        int total = 0;
        int[] remaining = counts.clone();
        for (int count : counts) {
            total += count;
        }
        List<MudVisualPalette.Entry> result = new ArrayList<>(total);
        while (result.size() < total) {
            for (int index = 0; index < remaining.length; index++) {
                if (remaining[index] > 0) {
                    result.add(new MudVisualPalette.Entry(
                            palette.mediumAt(index), palette.visualSourceAt(index),
                            palette.weightAt(index)));
                    remaining[index]--;
                }
            }
        }
        return result;
    }

    private static List<SinkingMedium> interleaveMediumIds(int[] counts) {
        int total = 0;
        int[] remaining = counts.clone();
        for (int count : counts) {
            total += count;
        }
        List<SinkingMedium> result = new ArrayList<>(total);
        while (result.size() < total) {
            for (int mediumId = 0; mediumId < remaining.length; mediumId++) {
                if (remaining[mediumId] > 0) {
                    result.add(SinkingMedium.byId(mediumId));
                    remaining[mediumId]--;
                }
            }
        }
        return result;
    }

    private static List<MediumShare> interleaveMediums(
            List<MediumShare> shares, int[] counts) {
        int total = 0;
        int[] remaining = counts.clone();
        for (int count : counts) {
            total += count;
        }
        List<MediumShare> result =
                new ArrayList<>(total);
        while (result.size() < total) {
            for (int index = 0;
                    index < remaining.length;
                    index++) {
                if (remaining[index] > 0) {
                    result.add(shares.get(index));
                    remaining[index]--;
                }
            }
        }
        return result;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MudSplashProfile profile = MudPhysicsSettings.mudSplashProfile();
        POOLS.entrySet().removeIf(entry -> {
            ServerLevel level = entry.getKey();
            if (level.getServer() != event.getServer()) {
                return true;
            }
            entry.getValue().tick(level, profile);
            return false;
        });
    }

    private static void sendVisualBatch(ServerLevel level, Vec3 origin,
            int sourceEntityId, long seed, List<Spawn> spawns, int accepted,
            MudSplashProfile profile, int lifetimeTicks) {
        Map<VisualKey, List<MudSplashPayload.Droplet>> visualByMedium =
                new LinkedHashMap<>();
        for (int index = 0; index < accepted; index++) {
            Spawn spawn = spawns.get(index);
            visualByMedium.computeIfAbsent(
                    new VisualKey(spawn.medium(), spawn.visualSource()),
                    ignored -> new ArrayList<>())
                    .add(new MudSplashPayload.Droplet(
                            (float) spawn.velocity().x,
                            (float) spawn.velocity().y,
                            (float) spawn.velocity().z,
                            spawn.size(),
                            spawn.fountain(),
                            spawn.breakupTriggerVelocityY(),
                            spawn.breakupDurationTicks(),
                            spawn.columnTrailTicks()));
        }
        for (Map.Entry<VisualKey, List<MudSplashPayload.Droplet>> entry
                : visualByMedium.entrySet()) {
            PacketDistributor.sendToPlayersNear(
                    level, null, origin.x, origin.y, origin.z, profile.renderDistance(),
                    new MudSplashPayload(
                            origin.x, origin.y, origin.z, sourceEntityId,
                            entry.getKey().medium(), entry.getKey().visualSource(),
                            profile.playerHitRadius(), (float) profile.gravity(),
                            (float) profile.drag(), lifetimeTicks,
                            seed ^ entry.getKey().medium().id()
                                    * 0x9E3779B97F4A7C15L
                                    ^ entry.getKey().visualSource(),
                            entry.getValue()));
        }
    }

    public static void clear() {
        POOLS.clear();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            POOLS.remove(level);
        }
    }

    static double normalizedImpact(double inwardSpeed, double minimum, double maximum) {
        if (maximum <= minimum) {
            return inwardSpeed >= minimum ? 1.0D : 0.0D;
        }
        return Mth.clamp((inwardSpeed - minimum) / (maximum - minimum), 0.0D, 1.0D);
    }

    static double qualifiedImpactStrength(
            double inwardSpeed, double minimum, double maximum) {
        if (inwardSpeed < minimum) {
            return 0.0D;
        }
        return 0.12D + normalizedImpact(
                inwardSpeed, minimum, maximum) * 0.88D;
    }

    public static boolean impactCooldownElapsed(
            int currentTick, int lastImpactTick, int cooldownTicks) {
        if (lastImpactTick == Integer.MIN_VALUE) {
            return true;
        }
        return (long) currentTick - (long) lastImpactTick >= Math.max(0, cooldownTicks);
    }

    static int dropletCount(int base, int maximum, double speedFactor, double volumeFactor) {
        return Mth.clamp(
                (int) Math.round(Math.max(1, base)
                        * (0.70D + Mth.clamp(speedFactor, 0.0D, 1.0D) * 4.30D)
                        * Mth.clamp(volumeFactor, 0.20D, 1.0D)),
                1,
                Math.max(1, maximum));
    }

    private static Vec3 safeNormal(Vec3 candidate, Vec3 fallback) {
        return candidate != null && candidate.lengthSqr() > 1.0E-8D
                ? candidate.normalize() : fallback;
    }

    private static Vec3 orthogonalAxis(Vec3 candidate, Vec3 normal) {
        Vec3 projected = candidate == null ? Vec3.ZERO
                : candidate.subtract(normal.scale(candidate.dot(normal)));
        if (projected.lengthSqr() < 1.0E-8D) {
            projected = Math.abs(normal.y) < 0.92D
                    ? normal.cross(new Vec3(0.0D, 1.0D, 0.0D))
                    : normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        return projected.normalize();
    }

    private record MediumShare(
            SinkingMedium medium, long visualSource, int weight) {
    }

    private record VisualKey(SinkingMedium medium, long visualSource) {
    }

    private record Spawn(
            SinkingMedium medium,
            long visualSource,
            Vec3 position,
            Vec3 velocity,
            float size,
            float strength,
            boolean forcePlayerStain,
            boolean fountain,
            float breakupTriggerVelocityY,
            int breakupDurationTicks,
            int columnTrailTicks) {
        private Spawn(SinkingMedium medium, long visualSource,
                Vec3 position, Vec3 velocity,
                float size, float strength, boolean forcePlayerStain) {
            this(medium, visualSource, position, velocity,
                    size, strength, forcePlayerStain,
                    false, -Float.MAX_VALUE, 1, 1);
        }
    }

    private static final class Pool {
        private Droplet[] droplets = createPool(MudSplashProfile.DEFAULT.maximumActiveDroplets());
        private int activeCount;
        private int nextSearchIndex;

        private int remainingCapacity(MudSplashProfile profile) {
            resize(profile.maximumActiveDroplets());
            return droplets.length - activeCount;
        }

        private int spawn(int sourceEntityId, List<Spawn> spawns, MudSplashProfile profile) {
            return spawn(sourceEntityId, spawns, profile, profile.lifetimeTicks());
        }

        private int spawn(int sourceEntityId, List<Spawn> spawns,
                MudSplashProfile profile, int lifetimeTicks) {
            resize(profile.maximumActiveDroplets());
            int accepted = 0;
            for (Spawn spawn : spawns) {
                Droplet droplet = acquire();
                if (droplet == null) {
                    break;
                }
                droplet.active = true;
                activeCount++;
                droplet.sourceEntityId = sourceEntityId;
                droplet.medium = spawn.medium();
                droplet.visualSource = spawn.visualSource();
                droplet.position = spawn.position();
                droplet.velocity = spawn.velocity();
                droplet.size = spawn.size();
                droplet.strength = spawn.strength();
                droplet.forcePlayerStain = spawn.forcePlayerStain();
                droplet.lifetimeTicks = Mth.clamp(lifetimeTicks, 1, 255);
                droplet.ageTicks = 0;
                accepted++;
            }
            return accepted;
        }

        private void tick(ServerLevel level, MudSplashProfile profile) {
            resize(profile.maximumActiveDroplets());
            AABB activeBounds = activeBounds();
            if (activeBounds == null) {
                return;
            }
            SableCompat.SurfaceProbe sableProbe = SableCompat.isLoaded()
                    ? SableCompat.surfaceProbe(level, activeBounds.inflate(0.28D))
                    : null;
            AABB playerSearch = activeBounds.inflate(
                    profile.playerHitRadius() + 0.25D);
            List<ServerPlayer> nearbyPlayers = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive() || MudPhysics.isPollutionSuppressed(player)) {
                    continue;
                }
                Vec3 motion = playerMotion(player);
                AABB sweptBounds = player.getBoundingBox().minmax(
                        player.getBoundingBox().move(-motion.x, -motion.y, -motion.z));
                if (playerSearch.intersects(sweptBounds)) {
                    nearbyPlayers.add(player);
                }
            }
            List<LivingEntity> nearbyEntities = new ArrayList<>(
                    MAXIMUM_NEARBY_NON_PLAYER_ENTITIES);
            level.getEntities(
                    EntityTypeTest.forClass(LivingEntity.class),
                    playerSearch,
                    entity -> !(entity instanceof Player) && entity.isAlive(),
                    nearbyEntities,
                    MAXIMUM_NEARBY_NON_PLAYER_ENTITIES);
            for (Droplet droplet : droplets) {
                if (droplet.active) {
                    tickDroplet(level, droplet, sableProbe,
                            nearbyPlayers, nearbyEntities, profile);
                }
            }
        }

        private void tickDroplet(ServerLevel level, Droplet droplet,
                SableCompat.SurfaceProbe sableProbe, List<ServerPlayer> nearbyPlayers,
                List<LivingEntity> nearbyEntities,
                MudSplashProfile profile) {
            Vec3 from = droplet.position;
            Vec3 to = from.add(droplet.velocity);
            Entity source = level.getEntity(droplet.sourceEntityId);
            SurfaceImpact surface = ordinaryImpact(level, source, from, to);
            SurfaceImpact sable = sableImpact(sableProbe, from, to, droplet.velocity);
            if (sable != null && (surface == null
                    || from.distanceToSqr(sable.point()) < from.distanceToSqr(surface.point()))) {
                surface = sable;
            }
            double maximumDistance = surface == null
                    ? from.distanceToSqr(to)
                    : from.distanceToSqr(surface.point());
            LivingImpact livingImpact = livingImpact(
                    nearbyPlayers, nearbyEntities,
                    droplet.sourceEntityId, from, to,
                    maximumDistance);
            if (livingImpact != null) {
                if (livingImpact.entity() instanceof ServerPlayer player) {
                    MudPhysics.applyMudSplashToPlayer(
                            player,
                            livingImpact.point(),
                            Math.max(profile.playerHitRadius(),
                                    droplet.size * 2.2F),
                            profile.playerStainStrength() * droplet.strength,
                            droplet.medium,
                            droplet.visualSource,
                            droplet.forcePlayerStain);
                } else {
                    EntityMudCoverageService.applySplash(
                            livingImpact.entity(), droplet.medium,
                            droplet.visualSource,
                            livingImpact.point(),
                            Math.max(profile.playerHitRadius(),
                                    droplet.size * 2.2F),
                            profile.playerStainStrength() * droplet.strength,
                            false);
                }
                release(droplet);
                return;
            }
            if (surface != null) {
                MudWallStainSystem.placeMudSplashStain(
                        level,
                        surface.subLevel(),
                        surface.supportPos(),
                        surface.containerPos(),
                        surface.face(),
                        surface.localPoint(),
                        surface.point(),
                        Math.max(profile.stainRadius(), droplet.size * 2.0F),
                        droplet.strength,
                        droplet.medium,
                        droplet.visualSource);
                release(droplet);
                return;
            }
            droplet.position = to;
            droplet.velocity = droplet.velocity
                    .add(0.0D, -profile.gravity(), 0.0D)
                    .scale(profile.drag());
            droplet.ageTicks++;
            if (droplet.ageTicks >= droplet.lifetimeTicks
                    || droplet.position.y < level.getMinBuildHeight() - 8.0D) {
                release(droplet);
            }
        }

        private static SurfaceImpact ordinaryImpact(
                ServerLevel level, Entity source, Vec3 from, Vec3 to) {
            ClipContext context = source == null
                    ? new ClipContext(from, to, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            net.minecraft.world.phys.shapes.CollisionContext.empty())
                    : new ClipContext(from, to, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, source);
            BlockHitResult hit = level.clip(context);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return null;
            }
            Vec3 point = hit.getLocation();
            var supportPos = hit.getBlockPos();
            var face = hit.getDirection();
            var containerPos = supportPos.relative(face);
            return new SurfaceImpact(
                    null,
                    supportPos,
                    containerPos,
                    face,
                    point.subtract(containerPos.getX(), containerPos.getY(), containerPos.getZ()),
                    point);
        }

        private static SurfaceImpact sableImpact(SableCompat.SurfaceProbe probe,
                Vec3 from, Vec3 to, Vec3 velocity) {
            if (probe == null || probe.isEmpty() || velocity.lengthSqr() < 1.0E-8D) {
                return null;
            }
            double distance = from.distanceTo(to);
            int steps = Mth.clamp((int) Math.ceil(distance / MAXIMUM_STEP_LENGTH), 1, 16);
            Vec3 preferredNormal = velocity.normalize().scale(-1.0D);
            for (int step = 1; step <= steps; step++) {
                Vec3 sample = from.lerp(to, step / (double) steps);
                SableCompat.SurfaceContact contact = SableCompat.findSurface(
                        probe, sample, preferredNormal,
                        MAXIMUM_STEP_LENGTH * 0.70D, 0.02D);
                if (contact != null) {
                    return new SurfaceImpact(
                            contact.subLevel(),
                            contact.supportPos(),
                            contact.containerPos(),
                            contact.face(),
                            contact.localPoint(),
                            contact.worldPoint());
                }
            }
            return null;
        }

        private static LivingImpact livingImpact(List<ServerPlayer> players,
                List<LivingEntity> entities, int sourceEntityId, Vec3 from, Vec3 to,
                double maximumDistanceSqr) {
            LivingImpact best = null;
            double bestDistance = maximumDistanceSqr;
            for (ServerPlayer player : players) {
                LivingImpact hit = closerImpact(
                        player, sourceEntityId, from, to, bestDistance);
                if (hit != null) {
                    best = hit;
                    bestDistance = from.distanceToSqr(hit.trajectoryPoint());
                }
            }
            for (LivingEntity entity : entities) {
                LivingImpact hit = closerImpact(
                        entity, sourceEntityId, from, to, bestDistance);
                if (hit != null) {
                    best = hit;
                    bestDistance = from.distanceToSqr(hit.trajectoryPoint());
                }
            }
            return best;
        }

        private static LivingImpact closerImpact(LivingEntity entity,
                int sourceEntityId, Vec3 from, Vec3 to, double maximumDistanceSqr) {
            if (entity.getId() == sourceEntityId) {
                return null;
            }
            MudSplashCollision.SweptHit hit = MudSplashCollision.sweepEntity(
                    entity.getBoundingBox(), entityMotion(entity), from, to);
            if (hit == null
                    || from.distanceToSqr(hit.trajectoryPoint()) >= maximumDistanceSqr) {
                return null;
            }
            return new LivingImpact(entity, hit.trajectoryPoint(), hit.surfacePoint());
        }

        private static Vec3 playerMotion(ServerPlayer player) {
            return entityMotion(player);
        }

        private static Vec3 entityMotion(LivingEntity entity) {
            return new Vec3(
                    entity.getX() - entity.xo,
                    entity.getY() - entity.yo,
                    entity.getZ() - entity.zo);
        }

        private AABB activeBounds() {
            if (activeCount <= 0) {
                return null;
            }
            double minimumX = Double.POSITIVE_INFINITY;
            double minimumY = Double.POSITIVE_INFINITY;
            double minimumZ = Double.POSITIVE_INFINITY;
            double maximumX = Double.NEGATIVE_INFINITY;
            double maximumY = Double.NEGATIVE_INFINITY;
            double maximumZ = Double.NEGATIVE_INFINITY;
            for (Droplet droplet : droplets) {
                if (!droplet.active) {
                    continue;
                }
                double nextX = droplet.position.x + droplet.velocity.x;
                double nextY = droplet.position.y + droplet.velocity.y;
                double nextZ = droplet.position.z + droplet.velocity.z;
                minimumX = Math.min(minimumX, Math.min(droplet.position.x, nextX) - 0.08D);
                minimumY = Math.min(minimumY, Math.min(droplet.position.y, nextY) - 0.08D);
                minimumZ = Math.min(minimumZ, Math.min(droplet.position.z, nextZ) - 0.08D);
                maximumX = Math.max(maximumX, Math.max(droplet.position.x, nextX) + 0.08D);
                maximumY = Math.max(maximumY, Math.max(droplet.position.y, nextY) + 0.08D);
                maximumZ = Math.max(maximumZ, Math.max(droplet.position.z, nextZ) + 0.08D);
            }
            return new AABB(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
        }

        private Droplet acquire() {
            if (activeCount >= droplets.length) {
                return null;
            }
            for (int offset = 0; offset < droplets.length; offset++) {
                int index = (nextSearchIndex + offset) % droplets.length;
                Droplet droplet = droplets[index];
                if (!droplet.active) {
                    nextSearchIndex = (index + 1) % droplets.length;
                    return droplet;
                }
            }
            return null;
        }

        private void release(Droplet droplet) {
            if (!droplet.active) {
                return;
            }
            droplet.active = false;
            activeCount = Math.max(0, activeCount - 1);
        }

        private void resize(int configured) {
            if (configured == droplets.length) {
                return;
            }
            Droplet[] resized = createPool(configured);
            int target = 0;
            for (Droplet droplet : droplets) {
                if (droplet.active && target < resized.length) {
                    resized[target++] = droplet;
                }
            }
            droplets = resized;
            activeCount = target;
            nextSearchIndex = target < droplets.length ? target : 0;
        }

        private static Droplet[] createPool(int size) {
            Droplet[] result = new Droplet[size];
            for (int index = 0; index < size; index++) {
                result[index] = new Droplet();
            }
            return result;
        }
    }

    private static final class Droplet {
        boolean active;
        int sourceEntityId;
        SinkingMedium medium = SinkingMedium.MUD;
        long visualSource;
        Vec3 position = Vec3.ZERO;
        Vec3 velocity = Vec3.ZERO;
        float size;
        float strength;
        boolean forcePlayerStain;
        int lifetimeTicks;
        int ageTicks;
    }

    private record SurfaceImpact(Object subLevel,
            net.minecraft.core.BlockPos supportPos,
            net.minecraft.core.BlockPos containerPos,
            net.minecraft.core.Direction face,
            Vec3 localPoint,
            Vec3 point) {
    }

    private record LivingImpact(
            LivingEntity entity, Vec3 trajectoryPoint, Vec3 point) {
    }
}
