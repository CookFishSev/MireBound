package com.fish.mirebound.mud.tuning;

import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Incremental main-thread scanner for large tuning ranges. */
public final class MudTuningScanScheduler {
    private static final int BLOCK_BUDGET_PER_TICK = 16_384;
    private static final int REGION_BUDGET_PER_TICK = 4;
    private static final int CELL_SIZE = 16;
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final ArrayDeque<UUID> ORDER = new ArrayDeque<>();

    private MudTuningScanScheduler() {
    }

    public static boolean submit(ServerPlayer player, MudTuningScope scope,
            MudTuningAnchor first, MudTuningAnchor second) {
        if (player == null || scope == MudTuningScope.WORLD
                || JOBS.containsKey(player.getUUID())) {
            return false;
        }
        Bounds bounds = Bounds.of(first.pos(), second.pos());
        ArrayDeque<Region> regions = regions(bounds);
        if (regions.isEmpty()) {
            return false;
        }
        boolean sable = first.isSable();
        Job job = new Job(player.getUUID(), player.level().dimension(), scope,
                first, second, sable,
                MudTuningConversionSafety.isUnrestrictedEnabled(player),
                levelRevision(player), regions);
        JOBS.put(player.getUUID(), job);
        ORDER.addLast(player.getUUID());
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.mirebound.tuning.scanning"), true);
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int blocks = BLOCK_BUDGET_PER_TICK;
        int regions = REGION_BUDGET_PER_TICK;
        int jobs = ORDER.size();
        while (blocks > 0 && regions > 0 && jobs-- > 0 && !ORDER.isEmpty()) {
            UUID playerId = ORDER.removeFirst();
            Job job = JOBS.get(playerId);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = job == null ? null : server.getLevel(job.dimension);
            if (job == null) {
                continue;
            }
            if (player == null || level == null || player.level() != level
                    || !player.hasPermissions(2)
                    || levelRevision(player) != job.revision) {
                cancel(playerId);
                continue;
            }
            Region region = job.regions.peekFirst();
            if (region == null) {
                finish(player, job);
                continue;
            }
            int volume = (int) region.volume();
            if (volume > blocks) {
                ORDER.addLast(playerId);
                continue;
            }
            job.regions.removeFirst();
            job.scans.add(MudTuningObjectScanner.scan(level, region.minimum,
                    region.maximum, job.sable, MAX_HIGHLIGHT_PER_KIND,
                    job.forceAllBlocks));
            blocks -= volume;
            regions--;
            if (job.regions.isEmpty()) {
                finish(player, job);
            } else {
                ORDER.addLast(playerId);
            }
        }
    }

    private static void finish(ServerPlayer player, Job job) {
        JOBS.remove(job.playerId);
        ORDER.removeIf(job.playerId::equals);
        MudTuningObjectScanner.ScanResult scan = MudTuningObjectScanner.merge(
                job.scans, BlockPos.containing(player.position()),
                MAX_HIGHLIGHT_PER_KIND);
        MudTuningManager.completeDeferredScan(player, job.scope,
                job.first, job.second, scan);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        cancel(event.getEntity().getUUID());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
        ORDER.clear();
    }

    static boolean hasJob(UUID playerId) {
        return JOBS.containsKey(playerId);
    }

    private static void cancel(UUID playerId) {
        JOBS.remove(playerId);
        ORDER.removeIf(playerId::equals);
    }

    private static long levelRevision(ServerPlayer player) {
        return MudTuningManager.revision(player.serverLevel());
    }

    private static ArrayDeque<Region> regions(Bounds bounds) {
        ArrayDeque<Region> result = new ArrayDeque<>();
        for (long x = bounds.minimum.getX(); x <= bounds.maximum.getX(); x += CELL_SIZE) {
            int startX = (int) x;
            int maxX = (int) Math.min(bounds.maximum.getX(), x + CELL_SIZE - 1L);
            for (long z = bounds.minimum.getZ(); z <= bounds.maximum.getZ(); z += CELL_SIZE) {
                int startZ = (int) z;
                int maxZ = (int) Math.min(bounds.maximum.getZ(), z + CELL_SIZE - 1L);
                for (long y = bounds.minimum.getY(); y <= bounds.maximum.getY(); y += CELL_SIZE) {
                    int startY = (int) y;
                    int maxY = (int) Math.min(bounds.maximum.getY(), y + CELL_SIZE - 1L);
                    result.addLast(new Region(new BlockPos(startX, startY, startZ),
                            new BlockPos(maxX, maxY, maxZ)));
                }
            }
        }
        return result;
    }

    private record Job(UUID playerId, ResourceKey<Level> dimension,
            MudTuningScope scope, MudTuningAnchor first, MudTuningAnchor second,
            boolean sable, boolean forceAllBlocks, long revision,
            ArrayDeque<Region> regions, List<MudTuningObjectScanner.ScanResult> scans) {
        private Job(UUID playerId, ResourceKey<Level> dimension, MudTuningScope scope,
                MudTuningAnchor first, MudTuningAnchor second, boolean sable,
                boolean forceAllBlocks, long revision, ArrayDeque<Region> regions) {
            this(playerId, dimension, scope, first, second, sable, forceAllBlocks,
                    revision, regions, new ArrayList<>());
        }
    }

    private record Region(BlockPos minimum, BlockPos maximum) {
        private long volume() {
            return ((long) maximum.getX() - minimum.getX() + 1L)
                    * ((long) maximum.getY() - minimum.getY() + 1L)
                    * ((long) maximum.getZ() - minimum.getZ() + 1L);
        }
    }

    private record Bounds(BlockPos minimum, BlockPos maximum) {
        private static Bounds of(BlockPos first, BlockPos second) {
            return new Bounds(new BlockPos(Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ())),
                    new BlockPos(Math.max(first.getX(), second.getX()),
                            Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ())));
        }
    }

    private static final int MAX_HIGHLIGHT_PER_KIND = 1_024;
}
