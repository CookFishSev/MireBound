package com.fish.mirebound.generation;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import com.fish.mirebound.adaptive.AdaptiveMudService;
import com.fish.mirebound.adaptive.AdaptiveMudSourceStore;
import com.fish.mirebound.adaptive.AdaptiveMudTaskGate;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.tuning.MudTuningManager;
import com.fish.mirebound.generation.MudTerrainGenerationJob.BlockSnapshot;
import com.fish.mirebound.generation.MudTerrainGenerationJob.Column;
import com.fish.mirebound.generation.MudTerrainGenerationJob.LakeCell;
import com.fish.mirebound.generation.MudTerrainGenerationJob.LakeRole;
import com.fish.mirebound.generation.MudTerrainGenerationJob.NaturalCursor;
import com.fish.mirebound.generation.MudTerrainGenerationJob.Operation;
import com.fish.mirebound.generation.MudTerrainGenerationJob.UndoRecord;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape.Cell;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded server-tick execution for tuning-wand terrain generation. */
public final class MudTerrainGenerationScheduler {
    private static final int GENERATION_BUDGET_PER_TICK = 128;
    private static final int RESTORE_BUDGET_PER_TICK = 1_024;
    private static final Map<UUID, MudTerrainGenerationJob> JOBS = new HashMap<>();
    private static final Map<UUID, UndoRecord> RECENT = new HashMap<>();
    private static final ArrayDeque<UUID> ORDER = new ArrayDeque<>();

    private MudTerrainGenerationScheduler() {
    }

    public static boolean submit(
            ServerPlayer player, MudTerrainGenerationRequest request) {
        UUID playerId = player.getUUID();
        if (!AdaptiveMudTaskGate.tryAcquire(playerId)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        ResourceLocation source = null;
        if (request.type().usesDepositSettings()
                && (!request.type().isNaturalDeposit()
                        || request.lakeSettings().innerBlockId().equals(
                                MudTerrainLakeSettings.AIR))
                && request.depositSettings().sameSourceOnly()) {
            source = sourceId(level, request.center(), request.depositSettings());
            if (source == null) {
                AdaptiveMudTaskGate.release(playerId);
                return false;
            }
        }
        MudTerrainGenerationJob job = MudTerrainGenerationJob.generate(
                playerId, level.dimension(), request, source);
        JOBS.put(playerId, job);
        ORDER.addLast(playerId);
        job.showTo(player);
        return true;
    }

    public static boolean submitUndo(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UndoRecord record = RECENT.get(playerId);
        if (record == null || !record.dimension().equals(player.level().dimension())
                || record.empty() || !AdaptiveMudTaskGate.tryAcquire(playerId)) {
            return false;
        }
        MudTerrainGenerationJob job = MudTerrainGenerationJob.undo(playerId, record);
        JOBS.put(playerId, job);
        ORDER.addLast(playerId);
        job.showTo(player);
        return true;
    }

    public static boolean cancel(ServerPlayer player) {
        return cancel(player.getUUID());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int remainingGeneration = GENERATION_BUDGET_PER_TICK;
        int remainingRestores = RESTORE_BUDGET_PER_TICK;
        int jobsThisTick = ORDER.size();
        while (jobsThisTick-- > 0 && !ORDER.isEmpty()) {
            UUID playerId = ORDER.removeFirst();
            MudTerrainGenerationJob job = JOBS.get(playerId);
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
            int budget = job.operation == Operation.GENERATE
                    ? Math.min(32, remainingGeneration)
                    : Math.min(256, remainingRestores);
            if (budget <= 0) {
                ORDER.addLast(playerId);
                continue;
            }
            ProcessResult result = job.operation == Operation.GENERATE
                    ? processGeneration(level, job, budget)
                    : processUndo(level, job, budget);
            if (job.operation == Operation.GENERATE) {
                remainingGeneration -= result.consumed;
            } else {
                remainingRestores -= result.consumed;
            }
            if (result.complete) {
                complete(player, job);
            } else {
                job.updateProgress();
                ORDER.addLast(playerId);
            }
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        cancel(playerId);
        RECENT.remove(playerId);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        for (MudTerrainGenerationJob job : JOBS.values()) {
            job.bossBar.removeAllPlayers();
            AdaptiveMudTaskGate.release(job.playerId);
        }
        JOBS.clear();
        RECENT.clear();
        ORDER.clear();
    }

    private static ProcessResult processGeneration(
            ServerLevel level, MudTerrainGenerationJob job, int budget) {
        if (job.request.type() == MudTerrainGenerationType.SURFACE_DEPOSIT) {
            return processSurfaceDeposit(level, job, budget);
        }
        if (job.request.type().isNaturalDeposit()) {
            return processNaturalDeposit(level, job, budget);
        }
        return processLakePool(level, job, budget);
    }

    private static ProcessResult processSurfaceDeposit(
            ServerLevel level, MudTerrainGenerationJob job, int columnBudget) {
        MudTerrainGenerationSettings settings = job.request.depositSettings();
        AdaptiveMudSourceStore sources = AdaptiveMudSourceStore.get(level);
        List<BlockPos> positions = new ArrayList<>(
                columnBudget * settings.thickness());
        int scanned = 0;
        while (scanned < columnBudget && job.columns.hasNext()) {
            scanned++;
            Column column = job.columns.next();
            if (!MudTerrainDepositShape.contains(
                    job.request.center().getX(), job.request.center().getZ(),
                    column.x(), column.z(), settings)) {
                continue;
            }
            BlockPos borderProbe = new BlockPos(
                    column.x(), job.request.center().getY(), column.z());
            if (!level.getWorldBorder().isWithinBounds(borderProbe)) {
                continue;
            }
            int surfaceY = MudTerrainDepositPlanner.findSurfaceY(
                    level, job.request.center(), column.x(), column.z(), settings);
            if (surfaceY == Integer.MIN_VALUE) {
                continue;
            }
            int depth = MudTerrainDepositShape.depth(
                    job.request.center().getX(), job.request.center().getZ(),
                    column.x(), column.z(), settings);
            for (int offset = 0; offset < depth; offset++) {
                BlockPos pos = new BlockPos(
                        column.x(), surfaceY - offset, column.z());
                if (!level.isInWorldBounds(pos)) {
                    break;
                }
                BlockState state = level.getBlockState(pos);
                BlockState source = state.getBlock() instanceof AdaptiveMudBlock
                        ? sources.sourceState(pos) : state;
                if (!matchesSource(source, job.sourceFilter)) {
                    break;
                }
                if (state.getBlock() instanceof AdaptiveMudBlock) {
                    continue;
                }
                if (!AdaptiveMudEligibility.check(level, pos, state).supported()) {
                    break;
                }
                positions.add(pos);
            }
        }
        convert(level, positions, job);
        return new ProcessResult(!job.columns.hasNext(), scanned);
    }

    private static ProcessResult processNaturalDeposit(
            ServerLevel level, MudTerrainGenerationJob job, int columnBudget) {
        MudTerrainGenerationSettings settings = job.request.depositSettings();
        BlockState inner = BuiltInRegistries.BLOCK
                .get(job.request.lakeSettings().innerBlockId())
                .defaultBlockState();
        AdaptiveMudSourceStore sources = AdaptiveMudSourceStore.get(level);
        List<BlockPos> conversions = new ArrayList<>(
                columnBudget * settings.thickness());
        Set<Long> surfaceConversions = new HashSet<>();
        Direction surfaceDirection = job.request.rotation().localY();
        NaturalCursor cursor = job.naturalCells;
        int scanned = 0;
        boolean directChanged = false;
        while (scanned < columnBudget && cursor.hasNext()) {
            scanned++;
            Cell cell = cursor.next();
            int depth = NaturalMudDepositShape.columnDepth(
                    1, settings.thickness(), cell);
            for (int layer = 0; layer < depth; layer++) {
                BlockPos local = new BlockPos(cell.dx(), -layer, cell.dz());
                BlockPos pos = job.request.center().offset(
                        job.request.rotation().apply(local));
                if (!validLoadedPosition(level, pos)) {
                    continue;
                }
                boolean surface = layer == 0;
                if (inner.isAir()) {
                    BlockState state = level.getBlockState(pos);
                    BlockState source = state.getBlock() instanceof AdaptiveMudBlock
                            ? sources.sourceState(pos) : state;
                    if (!matchesSource(source, job.sourceFilter)) {
                        break;
                    }
                    if (state.getBlock() instanceof AdaptiveMudBlock) {
                        continue;
                    }
                    if (!AdaptiveMudEligibility.check(level, pos, state).supported()) {
                        break;
                    }
                    conversions.add(pos);
                    if (surface) {
                        surfaceConversions.add(pos.asLong());
                    }
                } else if (inner.getBlock() instanceof MudBlock) {
                    directChanged |= replaceDirect(level, pos,
                            lakeInteriorState(inner, surface, surfaceDirection,
                                    job.request.lakeSettings()
                                            .surfaceHeightPixels()), job);
                } else if (replaceDirect(level, pos, inner, job)) {
                    directChanged = true;
                    conversions.add(pos);
                    if (surface) {
                        surfaceConversions.add(pos.asLong());
                    }
                }
            }
        }
        convert(level, conversions, surfaceConversions, surfaceDirection,
                job.request.lakeSettings().surfaceHeightPixels(), job);
        if (directChanged) {
            MudTuningManager.markMudChanged(level);
        }
        return new ProcessResult(!cursor.hasNext(), scanned);
    }

    private static ProcessResult processLakePool(
            ServerLevel level, MudTerrainGenerationJob job, int positionBudget) {
        MudTerrainLakeSettings settings = job.request.lakeSettings();
        BlockState shell = BuiltInRegistries.BLOCK
                .get(settings.shellBlockId()).defaultBlockState();
        BlockState inner = BuiltInRegistries.BLOCK
                .get(settings.innerBlockId()).defaultBlockState();
        List<BlockPos> convert = new ArrayList<>(positionBudget);
        Set<Long> surfaceConversions = new HashSet<>();
        Direction surfaceDirection = job.request.rotation().localY();
        int scanned = 0;
        boolean directChanged = false;
        while (scanned < positionBudget && job.lakeCells.hasNext()) {
            scanned++;
            LakeCell cell = job.lakeCells.next();
            BlockPos pos = job.request.center().offset(
                    job.request.rotation().apply(cell.offset()));
            if (!validLoadedPosition(level, pos)) {
                continue;
            }
            if (cell.role() == LakeRole.CAVITY) {
                directChanged |= replaceDirect(
                        level, pos, Blocks.CAVE_AIR.defaultBlockState(), job);
            } else if (cell.role() == LakeRole.SHELL) {
                BlockState current = level.getBlockState(pos);
                if (shouldPlaceShell(shell.isAir(), current.is(
                        BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE))) {
                    directChanged |= replaceDirect(level, pos, shell, job);
                }
            } else if (inner.isAir()) {
                convert.add(pos);
                if (cell.surface()) {
                    surfaceConversions.add(pos.asLong());
                }
            } else if (inner.getBlock() instanceof MudBlock) {
                directChanged |= replaceDirect(level, pos,
                        lakeInteriorState(inner, cell.surface(), surfaceDirection,
                                settings.surfaceHeightPixels()), job);
            } else {
                if (replaceDirect(level, pos, inner, job)) {
                    directChanged = true;
                    convert.add(pos);
                    if (cell.surface()) {
                        surfaceConversions.add(pos.asLong());
                    }
                }
            }
        }
        convert(level, convert, surfaceConversions, surfaceDirection,
                settings.surfaceHeightPixels(), job);
        if (directChanged) {
            MudTuningManager.markMudChanged(level);
        }
        return new ProcessResult(!job.lakeCells.hasNext(), scanned);
    }

    static boolean shouldPlaceShell(
            boolean configuredShellIsAir, boolean currentIsProtected) {
        return !configuredShellIsAir && !currentIsProtected;
    }

    static BlockState lakeInteriorState(
            BlockState state, boolean surface, Direction surfaceDirection,
            int surfaceHeightPixels) {
        if (!surface || !(state.getBlock() instanceof MudBlock)
                || !state.hasProperty(MudBlock.VARIANT)
                || !state.hasProperty(MudBlock.HEIGHT)
                || !state.hasProperty(MudBlock.FACING)) {
            return state;
        }
        return state.setValue(MudBlock.VARIANT, MudBlockVariant.HEIGHT)
                .setValue(MudBlock.HEIGHT, surfaceHeightPixels)
                .setValue(MudBlock.FACING, surfaceDirection);
    }

    private static void convert(
            ServerLevel level, List<BlockPos> positions,
            MudTerrainGenerationJob job) {
        convert(level, positions, Set.of(), Direction.UP,
                MudTerrainLakeSettings.DEFAULT_SURFACE_HEIGHT_PIXELS, job);
    }

    private static void convert(
            ServerLevel level, List<BlockPos> positions,
            Set<Long> surfacePositions, Direction surfaceDirection,
            int surfaceHeightPixels, MudTerrainGenerationJob job) {
        if (positions.isEmpty()) {
            return;
        }
        AdaptiveMudService.convertPositions(
                level, positions, SinkingMedium.MUD, null, null,
                pos -> {
                    job.changedAdaptivePositions.add(pos.asLong());
                    if (!surfacePositions.contains(pos.asLong())) {
                        return;
                    }
                    BlockState current = level.getBlockState(pos);
                    BlockState surface = lakeInteriorState(
                            current, true, surfaceDirection, surfaceHeightPixels);
                    if (surface != current) {
                        level.setBlock(pos, surface, Block.UPDATE_ALL);
                    }
                });
    }

    private static boolean replaceDirect(
            ServerLevel level, BlockPos pos, BlockState replacement,
            MudTerrainGenerationJob job) {
        BlockState previous = level.getBlockState(pos);
        if (previous.equals(replacement)
                || previous.isAir() && replacement.isAir()
                || previous.getBlock() instanceof MudBlock
                || previous.hasBlockEntity()
                || level.getBlockEntity(pos) != null) {
            return false;
        }
        MudBlockProfileStore.get(level).removeAll(pos);
        if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
            return false;
        }
        job.changedDirectPositions.add(new BlockSnapshot(
                pos.immutable(), previous, level.getBlockState(pos)));
        return true;
    }

    private static ProcessResult processUndo(
            ServerLevel level, MudTerrainGenerationJob job, int restoreBudget) {
        int consumed = 0;
        List<BlockPos> adaptive = new ArrayList<>(restoreBudget);
        while (consumed < restoreBudget
                && job.adaptiveUndoIndex < job.undoAdaptivePositions.size()) {
            BlockPos pos = BlockPos.of(
                    job.undoAdaptivePositions.get(job.adaptiveUndoIndex++));
            consumed++;
            if (loaded(level, pos)) {
                adaptive.add(pos);
            } else {
                job.deferredAdaptivePositions.add(pos.asLong());
            }
        }
        if (!adaptive.isEmpty()) {
            AdaptiveMudService.restorePositions(level, adaptive, null, null);
        }

        boolean directChanged = false;
        while (consumed < restoreBudget
                && job.directUndoIndex < job.undoDirectPositions.size()) {
            BlockSnapshot snapshot = job.undoDirectPositions.get(job.directUndoIndex++);
            consumed++;
            if (!loaded(level, snapshot.pos())) {
                job.deferredDirectPositions.add(snapshot);
                continue;
            }
            if (!matchesGeneratedState(
                    level.getBlockState(snapshot.pos()), snapshot.after())) {
                continue;
            }
            MudBlockProfileStore.get(level).removeAll(snapshot.pos());
            directChanged |= level.setBlock(
                    snapshot.pos(), snapshot.before(), Block.UPDATE_ALL);
        }
        if (directChanged) {
            MudTuningManager.markMudChanged(level);
        }
        boolean complete = job.adaptiveUndoIndex >= job.undoAdaptivePositions.size()
                && job.directUndoIndex >= job.undoDirectPositions.size();
        return new ProcessResult(complete, consumed);
    }

    static boolean matchesGeneratedState(
            BlockState current, BlockState generated) {
        if (current.equals(generated)) {
            return true;
        }
        return generated.getBlock() instanceof MudBlock
                && current.getBlock() == generated.getBlock();
    }

    private static boolean matchesSource(
            BlockState source, ResourceLocation sourceFilter) {
        return source != null && !source.isAir()
                && (sourceFilter == null || sourceFilter.equals(
                        BuiltInRegistries.BLOCK.getKey(source.getBlock())));
    }

    private static ResourceLocation sourceId(
            ServerLevel level, BlockPos center,
            MudTerrainGenerationSettings settings) {
        BlockPos sourcePos = center;
        if (level.getBlockState(sourcePos).isAir()) {
            int surfaceY = MudTerrainDepositPlanner.findSurfaceY(
                    level, center, center.getX(), center.getZ(), settings);
            if (surfaceY == Integer.MIN_VALUE) {
                return null;
            }
            sourcePos = new BlockPos(center.getX(), surfaceY, center.getZ());
        }
        BlockState state = level.getBlockState(sourcePos);
        BlockState source = state.getBlock() instanceof AdaptiveMudBlock
                ? AdaptiveMudSourceStore.get(level).sourceState(sourcePos) : state;
        return source == null || source.isAir() ? null
                : BuiltInRegistries.BLOCK.getKey(source.getBlock());
    }

    private static boolean validLoadedPosition(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos)
                && level.getWorldBorder().isWithinBounds(pos)
                && loaded(level, pos);
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void complete(
            ServerPlayer player, MudTerrainGenerationJob job) {
        JOBS.remove(job.playerId);
        job.bossBar.removeAllPlayers();
        AdaptiveMudTaskGate.release(job.playerId);
        if (job.operation == Operation.GENERATE) {
            remember(job);
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.generation.completed",
                    job.changedCount()), true);
            return;
        }
        if (job.deferredAdaptivePositions.isEmpty()
                && job.deferredDirectPositions.isEmpty()) {
            RECENT.remove(job.playerId);
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.generation.undone"), true);
        } else {
            UndoRecord deferred = new UndoRecord(
                    job.dimension,
                    List.copyOf(job.deferredAdaptivePositions),
                    List.copyOf(job.deferredDirectPositions));
            RECENT.put(job.playerId, deferred);
            player.displayClientMessage(Component.translatable(
                    "message.mirebound.generation.undo_deferred",
                    deferred.size()), true);
        }
    }

    private static boolean cancel(UUID playerId) {
        MudTerrainGenerationJob job = JOBS.remove(playerId);
        if (job == null) {
            return false;
        }
        ORDER.removeIf(playerId::equals);
        job.bossBar.removeAllPlayers();
        AdaptiveMudTaskGate.release(playerId);
        if (job.operation == Operation.GENERATE) {
            remember(job);
        }
        return true;
    }

    private static void remember(MudTerrainGenerationJob job) {
        if (job.changedCount() > 0) {
            RECENT.put(job.playerId, new UndoRecord(
                    job.dimension,
                    List.copyOf(job.changedAdaptivePositions),
                    List.copyOf(job.changedDirectPositions)));
        }
    }

    private record ProcessResult(boolean complete, int consumed) {
    }
}
