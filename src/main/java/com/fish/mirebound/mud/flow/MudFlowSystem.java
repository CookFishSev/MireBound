package com.fish.mirebound.mud.flow;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudLocalProfileSync;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudVolumeState;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.tuning.MudTuningManager;
import com.fish.mirebound.network.payload.MudFlowVisualPayload;
import com.fish.mirebound.stain.MudFootprintBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative finite-volume mud flow for ordinary world levels. */
public final class MudFlowSystem {
    private static final int HARD_MAXIMUM_UPDATES_PER_LEVEL_TICK = 512;
    private static final int MAXIMUM_QUEUE_POLLS_PER_LEVEL_TICK = 2048;
    private static final int MAXIMUM_ACTIVE_TICKETS = 131072;
    private static final int MAXIMUM_DISTANCE_MEMORIES = 131072;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Map<ServerLevel, LevelState> STATES = new IdentityHashMap<>();
    private static final ThreadLocal<Integer> MUTATION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private MudFlowSystem() {
    }

    public static void wake(ServerLevel level, BlockPos pos) {
        if (!isOrdinaryFlowCell(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MudBlock mudBlock)
                || mudBlock instanceof AdaptiveMudBlock) {
            return;
        }
        MudFlowProfile profile = MudMediumRuntime.flowProfile(level, pos, mudBlock.medium());
        if (profile.enabled()) {
            LevelState levelState = state(level);
            levelState.schedule(level, pos, mudBlock.medium(), profile,
                    levelState.distance(pos, mudBlock.medium()),
                    level.getGameTime() + profile.intervalTicks());
        }
    }

    public static void wakeNow(ServerLevel level, BlockPos pos) {
        if (!isOrdinaryFlowCell(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MudBlock mudBlock)
                || mudBlock instanceof AdaptiveMudBlock) {
            return;
        }
        MudFlowProfile profile = MudMediumRuntime.flowProfile(level, pos, mudBlock.medium());
        if (profile.enabled()) {
            LevelState levelState = state(level);
            levelState.schedule(level, pos, mudBlock.medium(), profile,
                    levelState.distance(pos, mudBlock.medium()),
                    level.getGameTime());
        }
    }

    public static void wakeAll(ServerLevel level, Iterable<BlockPos> positions) {
        if (!isOrdinaryLevel(level)) {
            return;
        }
        for (BlockPos pos : positions) {
            wakeNow(level, pos);
        }
    }

    public static void wakeNeighbors(ServerLevel level, BlockPos pos) {
        wakeNow(level, pos);
        wakeNow(level, pos.above());
        for (Direction direction : HORIZONTAL) {
            wakeNow(level, pos.relative(direction));
        }
    }

    /** Invalidates captured profiles after any global or local tuning change. */
    public static void invalidate(ServerLevel level, SinkingMedium medium) {
        LevelState state = STATES.get(level);
        if (state != null) {
            state.invalidate(medium);
        }
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        LevelState state = STATES.get(level);
        if (state != null) {
            state.forget(pos);
        }
    }

    public static boolean mutationActive() {
        return MUTATION_DEPTH.get() > 0;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelState state = STATES.get(level);
            if (state != null) {
                state.tick(level);
            }
        }
        STATES.keySet().removeIf(level -> level.getServer() != event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : STATES.keySet()) {
            MudLocalProfileSync.clear(level);
        }
        STATES.clear();
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MudLocalProfileSync.clear(level);
            STATES.remove(level);
        }
    }

    private static boolean isOrdinaryLevel(ServerLevel level) {
        return level.getServer().getLevel(level.dimension()) == level;
    }

    private static boolean isOrdinaryFlowCell(ServerLevel level, BlockPos pos) {
        return isOrdinaryLevel(level)
                && hasLoadedChunk(level, pos)
                && SableCompat.subLevelAtStorage(level, pos) == null;
    }

    private static LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new LevelState());
    }

    private static final class LevelState {
        private final Map<Long, Ticket> active = new HashMap<>();
        private final PriorityQueue<Ticket> due = new PriorityQueue<>(
                Comparator.comparingLong(Ticket::dueTick).thenComparingLong(Ticket::sequence));
        private final Map<Long, DistanceMemory> distances = new LinkedHashMap<>();
        private long nextSequence;
        private int visualBudget;

        private void schedule(ServerLevel level, BlockPos pos, SinkingMedium medium,
                MudFlowProfile profile, int horizontalDistance, long dueTick) {
            MudFlowProfile localProfile = MudMediumRuntime.flowProfile(level, pos, medium);
            if (!localProfile.enabled()
                    || horizontalDistance > localProfile.maximumSpreadDistance()
                    || pos.getY() < level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight()
                    || !hasLoadedChunk(level, pos)) {
                return;
            }
            BlockState current = level.getBlockState(pos);
            if (!(current.getBlock() instanceof MudBlock mudBlock)
                    || mudBlock instanceof AdaptiveMudBlock
                    || mudBlock.medium() != medium) {
                return;
            }
            long key = pos.asLong();
            Ticket previous = active.get(key);
            if (previous == null && active.size() >= MAXIMUM_ACTIVE_TICKETS) {
                return;
            }
            if (previous != null
                    && previous.dueTick() <= dueTick
                    && previous.horizontalDistance() <= horizontalDistance
                    && previous.medium() == medium) {
                return;
            }
            Ticket ticket = new Ticket(key, medium, horizontalDistance,
                    dueTick, nextSequence++);
            active.put(key, ticket);
            due.add(ticket);
            compactQueueIfNeeded();
        }

        private void tick(ServerLevel level) {
            visualBudget = 24;
            long now = level.getGameTime();
            EnumMap<SinkingMedium, Integer> updatesByMedium = new EnumMap<>(SinkingMedium.class);
            int updates = 0;
            int polls = 0;
            while (updates < HARD_MAXIMUM_UPDATES_PER_LEVEL_TICK
                    && polls++ < MAXIMUM_QUEUE_POLLS_PER_LEVEL_TICK) {
                Ticket ticket = due.peek();
                if (ticket == null || ticket.dueTick() > now) {
                    break;
                }
                due.poll();
                if (active.get(ticket.position()) != ticket) {
                    continue;
                }
                active.remove(ticket.position());
                BlockPos pos = BlockPos.of(ticket.position());
                MudFlowProfile profile = currentProfile(level, pos, ticket.medium());
                if (!profile.enabled()) {
                    continue;
                }
                int mediumUpdates = updatesByMedium.getOrDefault(ticket.medium(), 0);
                if (mediumUpdates >= profile.maximumUpdatesPerTick()) {
                    schedule(level, pos, ticket.medium(), profile,
                            ticket.horizontalDistance(), now + 1L);
                    continue;
                }
                updatesByMedium.put(ticket.medium(), mediumUpdates + 1);
                updates++;
                process(level, ticket, profile, now);
            }
            MudLocalProfileSync.flush(level);
            compactQueueIfNeeded();
        }

        private void process(ServerLevel level, Ticket ticket,
                MudFlowProfile profile, long now) {
            BlockPos pos = BlockPos.of(ticket.position());
            if (!hasLoadedChunk(level, pos)) {
                return;
            }
            BlockState sourceState = level.getBlockState(pos);
            if (!(sourceState.getBlock() instanceof MudBlock sourceBlock)
                    || sourceBlock instanceof AdaptiveMudBlock
                    || sourceBlock.medium() != ticket.medium()
                    || MudBlock.surfaceDirection(sourceState, ticket.medium()) != Direction.UP) {
                return;
            }
            int sourcePixels = MudVolumeState.pixels(sourceState);
            if (sourcePixels <= 0) {
                return;
            }

            BlockPos below = pos.below();
            boolean belowInBounds = below.getY() >= level.getMinBuildHeight()
                    && below.getY() < level.getMaxBuildHeight();
            if (belowInBounds && MudFlowStepRules.needsVerticalResolution(
                    hasLoadedChunk(level, below), false, 16)) {
                schedule(level, pos, ticket.medium(), profile,
                        ticket.horizontalDistance(), now + profile.intervalTicks());
                return;
            }
            if (belowInBounds) {
                Cell belowCell = cell(level.getBlockState(below),
                        sourceState.getBlock(), ticket.medium());
                if (MudFlowStepRules.needsVerticalResolution(
                        true, belowCell.accepts(), belowCell.pixels())) {
                    int transfer = MudFlowTransfer.downward(sourcePixels, belowCell.pixels(),
                            profile.pixelsPerTransfer());
                    if (transfer > 0 && applyTransfer(level, pos, sourceState, below,
                            belowCell.state(), sourcePixels, belowCell.pixels(), transfer)) {
                        emitVisual(level, pos, below, ticket.medium(), sourcePixels - transfer,
                                belowCell.pixels() + transfer, transfer, profile);
                        rememberAfterTransfer(level, pos, below, ticket.medium(),
                                ticket.horizontalDistance(), ticket.horizontalDistance());
                        rescheduleChanged(level, ticket, profile, now, pos, below, false);
                    } else {
                        schedule(level, pos, ticket.medium(), profile,
                                ticket.horizontalDistance(), now + profile.intervalTicks());
                    }
                    return;
                }
            }

            if (ticket.horizontalDistance() >= profile.maximumSpreadDistance()) {
                return;
            }
            int start = Math.floorMod(Long.hashCode(ticket.position())
                    + (int) (now / profile.intervalTicks()), HORIZONTAL.length);
            boolean changed = false;
            boolean retryAfterChunkLoad = false;
            List<BlockPos> changedTargets = new ArrayList<>(HORIZONTAL.length);
            for (int offset = 0; offset < HORIZONTAL.length && sourcePixels > 0; offset++) {
                Direction direction = HORIZONTAL[(start + offset) % HORIZONTAL.length];
                BlockPos targetPos = pos.relative(direction);
                if (!canRead(level, targetPos)) {
                    retryAfterChunkLoad = true;
                    continue;
                }
                BlockState latestSource = level.getBlockState(pos);
                if (latestSource.getBlock() != sourceState.getBlock()) {
                    break;
                }
                sourcePixels = MudVolumeState.pixels(latestSource);
                Cell target = cell(level.getBlockState(targetPos),
                        sourceState.getBlock(), ticket.medium());
                int transfer = target.accepts()
                        ? MudFlowTransfer.horizontal(sourcePixels, target.pixels(), profile)
                        : 0;
                if (transfer > 0 && applyTransfer(level, pos, latestSource, targetPos,
                        target.state(), sourcePixels, target.pixels(), transfer)) {
                    emitVisual(level, pos, targetPos, ticket.medium(), sourcePixels - transfer,
                            target.pixels() + transfer, transfer, profile);
                    changed = true;
                    changedTargets.add(targetPos.immutable());
                    rememberAfterTransfer(level, pos, targetPos, ticket.medium(),
                            ticket.horizontalDistance(), ticket.horizontalDistance() + 1);
                    sourcePixels -= transfer;
                }
            }
            if (changed || retryAfterChunkLoad) {
                schedule(level, pos, ticket.medium(), profile,
                        ticket.horizontalDistance(), now + profile.intervalTicks());
                for (BlockPos targetPos : changedTargets) {
                    schedule(level, targetPos, ticket.medium(), profile,
                            ticket.horizontalDistance() + 1,
                            now + profile.intervalTicks());
                    scheduleAdjacent(level, targetPos, ticket, profile, now,
                            ticket.horizontalDistance() + 1);
                }
            }
        }

        private void rescheduleChanged(ServerLevel level, Ticket ticket,
                MudFlowProfile profile, long now, BlockPos source,
                BlockPos target, boolean horizontal) {
            int distance = ticket.horizontalDistance() + (horizontal ? 1 : 0);
            schedule(level, source, ticket.medium(), profile,
                    ticket.horizontalDistance(), now + profile.intervalTicks());
            schedule(level, target, ticket.medium(), profile, distance,
                    now + profile.intervalTicks());
            scheduleAdjacent(level, target, ticket, profile, now, distance);
        }

        private void scheduleAdjacent(ServerLevel level, BlockPos center, Ticket ticket,
                MudFlowProfile profile, long now, int distance) {
            schedule(level, center.above(), ticket.medium(), profile, distance,
                    now + profile.intervalTicks());
            for (Direction direction : HORIZONTAL) {
                schedule(level, center.relative(direction), ticket.medium(), profile,
                        distance, now + profile.intervalTicks());
            }
        }

        private MudFlowProfile currentProfile(
                ServerLevel level, BlockPos pos, SinkingMedium medium) {
            return MudMediumRuntime.flowProfile(level, pos, medium);
        }

        private void emitVisual(ServerLevel level, BlockPos source, BlockPos target,
                SinkingMedium medium, int sourceAfter, int targetAfter, int transferred,
                MudFlowProfile profile) {
            if (visualBudget-- <= 0) {
                return;
            }
            int duration = Math.max(4, Math.min(12, profile.intervalTicks() + 2));
            PacketDistributor.sendToPlayersNear(level, null,
                    target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                    64.0D, new MudFlowVisualPayload(
                            source.asLong(), target.asLong(), medium,
                            sourceAfter, targetAfter, transferred, duration));
        }

        private void invalidate(SinkingMedium medium) {
            active.entrySet().removeIf(entry -> entry.getValue().medium() == medium);
            distances.entrySet().removeIf(entry -> entry.getValue().medium() == medium);
            compactQueue();
        }

        private int distance(BlockPos pos, SinkingMedium medium) {
            DistanceMemory memory = distances.get(pos.asLong());
            return memory != null && memory.medium() == medium ? memory.distance() : 0;
        }

        private void rememberAfterTransfer(ServerLevel level, BlockPos source,
                BlockPos target, SinkingMedium medium, int sourceDistance, int targetDistance) {
            remember(target, medium, targetDistance);
            BlockState sourceState = level.getBlockState(source);
            if (sourceState.getBlock() instanceof MudBlock mudBlock
                    && mudBlock.medium() == medium) {
                remember(source, medium, sourceDistance);
            } else {
                forget(source);
            }
        }

        private void remember(BlockPos pos, SinkingMedium medium, int distance) {
            long key = pos.asLong();
            DistanceMemory previous = distances.get(key);
            int resolved = previous != null && previous.medium() == medium
                    ? Math.min(previous.distance(), distance)
                    : distance;
            if (resolved <= 0) {
                distances.remove(key);
            } else {
                distances.put(key, new DistanceMemory(medium, resolved));
                while (distances.size() > MAXIMUM_DISTANCE_MEMORIES) {
                    var oldest = distances.keySet().iterator();
                    if (!oldest.hasNext()) {
                        break;
                    }
                    oldest.next();
                    oldest.remove();
                }
            }
        }

        private void forget(BlockPos pos) {
            long key = pos.asLong();
            active.remove(key);
            distances.remove(key);
        }

        private void compactQueueIfNeeded() {
            if (due.size() > active.size() * 2 + 1024) {
                compactQueue();
            }
        }

        private void compactQueue() {
            due.clear();
            due.addAll(active.values());
        }
    }

    private static boolean canRead(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight()
                && pos.getY() < level.getMaxBuildHeight()
                && hasLoadedChunk(level, pos);
    }

    private static boolean hasLoadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static Cell cell(
            BlockState state, Block sourceBlock, SinkingMedium medium) {
        if (state.isAir()) {
            return new Cell(state, 0, true);
        }
        if (state.getBlock() == sourceBlock
                && state.getBlock() instanceof MudBlock
                && MudBlock.surfaceDirection(state, medium) == Direction.UP) {
            return new Cell(state, MudVolumeState.pixels(state), true);
        }
        if (canDisplace(state)) {
            return new Cell(state, 0, true);
        }
        return new Cell(state, 0, false);
    }

    private static boolean canDisplace(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof MudFootprintBlock) {
            return true;
        }
        boolean redstoneComponent = state.isSignalSource()
                || block instanceof RedStoneWireBlock
                || block instanceof BaseRailBlock
                || block instanceof TripWireBlock;
        boolean fragileDecoration = block instanceof BushBlock
                || block instanceof BaseTorchBlock
                || block instanceof AbstractCandleBlock;
        return MudFlowStepRules.canDisplaceDecoration(
                redstoneComponent, state.hasBlockEntity(),
                !state.getFluidState().isEmpty(), state.canBeReplaced(), fragileDecoration);
    }

    private static boolean applyTransfer(ServerLevel level, BlockPos sourcePos,
            BlockState sourceState, BlockPos targetPos, BlockState targetState,
            int sourcePixels, int targetPixels, int transfer) {
        MudBlock sourceBlock = (MudBlock) sourceState.getBlock();
        SinkingMedium medium = sourceBlock.medium();
        MudBlockProfileStore store = MudBlockProfileStore.get(level);
        // Capture the source profile before either placement callback can refresh or remove it.
        MudBlockProfileStore.Profile sourceProfile = store.profile(level, sourcePos, medium);
        int sourceAfter = sourcePixels - transfer;
        int targetAfter = targetPixels + transfer;
        boolean displaced = !targetState.isAir() && targetPixels == 0;
        BlockState nextTarget = MudVolumeState.withPixels(targetPixels == 0
                ? sourceBlock.defaultBlockState()
                : targetState, targetAfter, Direction.UP);
        beginMutation();
        try {
            if (!level.setBlock(targetPos, nextTarget, Block.UPDATE_ALL)) {
                return false;
            }
            BlockState nextSource = sourceAfter <= 0
                    ? sourceState.getFluidState().createLegacyBlock()
                    : MudVolumeState.withPixels(sourceState, sourceAfter, Direction.UP);
            if (level.setBlock(sourcePos, nextSource, Block.UPDATE_ALL)) {
                if (store.copyIfAbsent(level, sourceProfile, targetPos, medium)) {
                    MudLocalProfileSync.queueChunk(
                        level, new ChunkPos(targetPos.getX() >> 4, targetPos.getZ() >> 4));
                }
                if (displaced && !(targetState.getBlock() instanceof MudFootprintBlock)) {
                    Block.dropResources(targetState, level, targetPos);
                }
                MudTuningManager.markMudChanged(level);
                return true;
            }
            level.setBlock(targetPos, targetState, Block.UPDATE_ALL);
            return false;
        } finally {
            endMutation();
        }
    }

    private static void beginMutation() {
        MUTATION_DEPTH.set(MUTATION_DEPTH.get() + 1);
    }

    private static void endMutation() {
        int remaining = MUTATION_DEPTH.get() - 1;
        if (remaining <= 0) {
            MUTATION_DEPTH.remove();
        } else {
            MUTATION_DEPTH.set(remaining);
        }
    }

    private record Cell(BlockState state, int pixels, boolean accepts) {
    }

    private record Ticket(long position, SinkingMedium medium,
            int horizontalDistance, long dueTick, long sequence) {
    }

    private record DistanceMemory(SinkingMedium medium, int distance) {
    }
}
