package com.fish.mirebound.adaptive;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs large adaptive-mud mutations in bounded, round-robin server-tick work units. */
public final class AdaptiveMudConversionScheduler {
    private static final int BLOCK_BUDGET_PER_TICK = 4_096;
    private static final int REGION_BUDGET_PER_TICK = 8;
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final ArrayDeque<UUID> ORDER = new ArrayDeque<>();

    private AdaptiveMudConversionScheduler() {
    }

    public static boolean submit(ServerPlayer player, ServerLevel level, UUID subLevelId,
            BlockPos minimum, BlockPos maximum, Operation operation,
            CompletionHandler completionHandler) {
        return submit(player, level, subLevelId, minimum, maximum, operation,
                null, completionHandler);
    }

    public static boolean submit(ServerPlayer player, ServerLevel level, UUID subLevelId,
            BlockPos minimum, BlockPos maximum, Operation operation,
            ResourceLocation sourceFilter, CompletionHandler completionHandler) {
        return submit(player, level, subLevelId, minimum, maximum, operation,
                sourceFilter, false, completionHandler);
    }

    public static boolean submit(ServerPlayer player, ServerLevel level, UUID subLevelId,
            BlockPos minimum, BlockPos maximum, Operation operation,
            ResourceLocation sourceFilter, boolean forceAllBlocks,
            CompletionHandler completionHandler) {
        UUID playerId = player.getUUID();
        if (JOBS.containsKey(playerId) || !AdaptiveMudTaskGate.tryAcquire(playerId)) {
            return false;
        }
        boolean queued = false;
        try {
            RegionCursor regions = partition(minimum, maximum);
            Region firstRegion = regions.peek();
            if (firstRegion != null && firstRegion.volume() == regions.totalVolume()) {
                Object subLevel = resolveSubLevel(level, subLevelId);
                if (subLevelId != null && subLevel == null) {
                    return false;
                }
                AdaptiveMudService.MutationResult result = allChunksLoaded(
                        firstRegion, level.getChunkSource()::hasChunk)
                                ? mutate(level, subLevel, firstRegion, operation,
                                        sourceFilter, forceAllBlocks)
                                : AdaptiveMudService.MutationResult.EMPTY;
                completionHandler.complete(player, Result.of(result));
                return true;
            }

            Job job = new Job(playerId, level.dimension(), subLevelId, operation,
                    sourceFilter, forceAllBlocks, regions, completionHandler);
            JOBS.put(playerId, job);
            ORDER.addLast(playerId);
            job.showTo(player);
            queued = true;
            return true;
        } finally {
            if (!queued) {
                AdaptiveMudTaskGate.release(playerId);
            }
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int remainingBudget = BLOCK_BUDGET_PER_TICK;
        int remainingRegions = REGION_BUDGET_PER_TICK;
        int stalledJobs = 0;
        while (remainingBudget > 0 && remainingRegions > 0 && !ORDER.isEmpty()) {
            UUID playerId = ORDER.removeFirst();
            Job job = JOBS.get(playerId);
            if (job == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.getLevel(job.dimension);
            if (player == null || level == null || !player.hasPermissions(2)
                    || !player.level().dimension().equals(job.dimension)) {
                cancel(playerId);
                continue;
            }
            Region region = job.regions.peek();
            if (region == null) {
                complete(player, job);
                continue;
            }
            if (region.volume() > remainingBudget) {
                ORDER.addLast(playerId);
                stalledJobs++;
                if (stalledJobs >= ORDER.size()) {
                    break;
                }
                continue;
            }
            Object subLevel = resolveSubLevel(level, job.subLevelId);
            if (job.subLevelId != null && subLevel == null) {
                cancel(playerId);
                continue;
            }
            job.regions.advance();
            if (allChunksLoaded(region, level.getChunkSource()::hasChunk)) {
                job.add(mutate(level, subLevel, region, job.operation,
                                job.sourceFilter, job.forceAllBlocks),
                        region.volume());
            } else {
                job.skip(region.volume());
            }
            remainingBudget -= region.volume();
            remainingRegions--;
            stalledJobs = 0;
            if (job.regions.peek() == null) {
                complete(player, job);
            } else {
                job.updateProgress();
                ORDER.addLast(playerId);
            }
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        cancel(event.getEntity().getUUID());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (Job job : JOBS.values()) {
            job.bossBar.removeAllPlayers();
            AdaptiveMudTaskGate.release(job.playerId);
        }
        JOBS.clear();
        ORDER.clear();
    }

    static RegionCursor partition(BlockPos first, BlockPos second) {
        return new RegionCursor(first, second);
    }

    static boolean allChunksLoaded(Region region,
            BiPredicate<Integer, Integer> chunkLoaded) {
        int minimumChunkX = region.minimum.getX() >> 4;
        int maximumChunkX = region.maximum.getX() >> 4;
        int minimumChunkZ = region.minimum.getZ() >> 4;
        int maximumChunkZ = region.maximum.getZ() >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!chunkLoaded.test(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static AdaptiveMudService.MutationResult mutate(ServerLevel level, Object subLevel,
            Region region, Operation operation, ResourceLocation sourceFilter,
            boolean forceAllBlocks) {
        return operation == Operation.CONVERT
                ? AdaptiveMudService.convert(level, region.minimum, region.maximum,
                        SinkingMedium.MUD, subLevel, sourceFilter, forceAllBlocks)
                : AdaptiveMudService.restore(level, region.minimum, region.maximum,
                        subLevel, sourceFilter);
    }

    private static Object resolveSubLevel(ServerLevel level, UUID subLevelId) {
        return subLevelId == null ? null : SableCompat.subLevelById(level, subLevelId);
    }

    private static void complete(ServerPlayer player, Job job) {
        JOBS.remove(job.playerId);
        job.bossBar.removeAllPlayers();
        AdaptiveMudTaskGate.release(job.playerId);
        job.completionHandler.complete(player,
                new Result(job.changed, job.rejected));
    }

    private static void cancel(UUID playerId) {
        Job job = JOBS.remove(playerId);
        if (job != null) {
            job.bossBar.removeAllPlayers();
            ORDER.removeIf(playerId::equals);
            AdaptiveMudTaskGate.release(playerId);
        }
    }

    public enum Operation {
        CONVERT,
        RESTORE
    }

    @FunctionalInterface
    public interface CompletionHandler {
        void complete(ServerPlayer player, Result result);
    }

    static record Region(BlockPos minimum, BlockPos maximum) {
        int volume() {
            return (maximum.getX() - minimum.getX() + 1)
                    * (maximum.getY() - minimum.getY() + 1)
                    * (maximum.getZ() - minimum.getZ() + 1);
        }
    }

    public record Result(long changed, long rejected) {
        private static Result of(AdaptiveMudService.MutationResult result) {
            return new Result(result.changed(), result.rejected());
        }
    }

    /** Lazily traverses one bounded chunk section at a time without preallocating regions. */
    static final class RegionCursor {
        private final BlockPos minimum;
        private final BlockPos maximum;
        private final long totalVolume;
        private int x;
        private int y;
        private int z;
        private boolean finished;

        private RegionCursor(BlockPos first, BlockPos second) {
            minimum = new BlockPos(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()));
            maximum = new BlockPos(
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ()));
            totalVolume = volume(minimum, maximum);
            x = minimum.getX();
            y = minimum.getY();
            z = minimum.getZ();
        }

        Region peek() {
            if (finished) {
                return null;
            }
            return new Region(new BlockPos(x, y, z), new BlockPos(
                    Math.min(maximum.getX(), sectionEnd(x)),
                    Math.min(maximum.getY(), sectionEnd(y)),
                    Math.min(maximum.getZ(), sectionEnd(z))));
        }

        void advance() {
            Region current = peek();
            if (current == null) {
                return;
            }
            if (current.maximum.getY() < maximum.getY()) {
                y = current.maximum.getY() + 1;
                return;
            }
            y = minimum.getY();
            if (current.maximum.getZ() < maximum.getZ()) {
                z = current.maximum.getZ() + 1;
                return;
            }
            z = minimum.getZ();
            if (current.maximum.getX() < maximum.getX()) {
                x = current.maximum.getX() + 1;
                return;
            }
            finished = true;
        }

        long totalVolume() {
            return totalVolume;
        }

        private static int sectionEnd(int coordinate) {
            return (coordinate >> 4 << 4) + 15;
        }

        private static long volume(BlockPos minimum, BlockPos maximum) {
            long sizeX = (long) maximum.getX() - minimum.getX() + 1L;
            long sizeY = (long) maximum.getY() - minimum.getY() + 1L;
            long sizeZ = (long) maximum.getZ() - minimum.getZ() + 1L;
            if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L
                    || sizeX > Long.MAX_VALUE / sizeY) {
                return Long.MAX_VALUE;
            }
            long xy = sizeX * sizeY;
            return xy > Long.MAX_VALUE / sizeZ ? Long.MAX_VALUE : xy * sizeZ;
        }
    }

    private static final class Job {
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final UUID subLevelId;
        private final Operation operation;
        private final ResourceLocation sourceFilter;
        private final boolean forceAllBlocks;
        private final RegionCursor regions;
        private final CompletionHandler completionHandler;
        private final ServerBossEvent bossBar;
        private final long totalVolume;
        private long processedVolume;
        private long changed;
        private long rejected;
        private int displayedPercent = -1;

        private Job(UUID playerId, ResourceKey<Level> dimension, UUID subLevelId,
                Operation operation, ResourceLocation sourceFilter, boolean forceAllBlocks,
                RegionCursor regions, CompletionHandler completionHandler) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.subLevelId = subLevelId;
            this.operation = operation;
            this.sourceFilter = sourceFilter;
            this.forceAllBlocks = forceAllBlocks;
            this.regions = regions;
            this.completionHandler = completionHandler;
            this.totalVolume = regions.totalVolume();
            this.bossBar = new ServerBossEvent(progressName(0), BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            this.bossBar.setProgress(0.0F);
        }

        private void showTo(ServerPlayer player) {
            bossBar.addPlayer(player);
        }

        private void add(AdaptiveMudService.MutationResult result, int processed) {
            changed += result.changed();
            rejected += result.rejected();
            processedVolume += processed;
        }

        private void skip(int processed) {
            processedVolume += processed;
        }

        private void updateProgress() {
            float progress = (float) Mth.clamp(
                    (double) processedVolume / totalVolume, 0.0D, 1.0D);
            bossBar.setProgress(progress);
            int percent = Mth.floor(progress * 100.0F);
            if (percent != displayedPercent) {
                displayedPercent = percent;
                bossBar.setName(progressName(percent));
            }
        }

        private Component progressName(int percent) {
            return Component.translatable(operation == Operation.CONVERT
                    ? "bossbar.mirebound.adaptive.converting"
                    : "bossbar.mirebound.adaptive.restoring", percent);
        }
    }
}
