package com.fish.mirebound.generation;

import com.fish.mirebound.generation.natural.NaturalMudDepositShape;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Mutable progress state for one bounded generation or undo task. */
final class MudTerrainGenerationJob {
    final UUID playerId;
    final ResourceKey<Level> dimension;
    final Operation operation;
    final MudTerrainGenerationRequest request;
    final ResourceLocation sourceFilter;
    final ColumnCursor columns;
    final NaturalCursor naturalCells;
    final LakeCursor lakeCells;
    final List<Long> undoAdaptivePositions;
    final List<BlockSnapshot> undoDirectPositions;
    final List<Long> changedAdaptivePositions = new ArrayList<>();
    final List<BlockSnapshot> changedDirectPositions = new ArrayList<>();
    final List<Long> deferredAdaptivePositions = new ArrayList<>();
    final List<BlockSnapshot> deferredDirectPositions = new ArrayList<>();
    final ServerBossEvent bossBar;
    int adaptiveUndoIndex;
    int directUndoIndex;
    private int displayedPercent = -1;

    private MudTerrainGenerationJob(
            UUID playerId, ResourceKey<Level> dimension,
            Operation operation, MudTerrainGenerationRequest request,
            ResourceLocation sourceFilter, ColumnCursor columns,
            NaturalCursor naturalCells,
            LakeCursor lakeCells, List<Long> undoAdaptivePositions,
            List<BlockSnapshot> undoDirectPositions) {
        this.playerId = playerId;
        this.dimension = dimension;
        this.operation = operation;
        this.request = request;
        this.sourceFilter = sourceFilter;
        this.columns = columns;
        this.naturalCells = naturalCells;
        this.lakeCells = lakeCells;
        this.undoAdaptivePositions = undoAdaptivePositions;
        this.undoDirectPositions = undoDirectPositions;
        bossBar = new ServerBossEvent(progressName(0),
                operation == Operation.GENERATE
                        ? BossEvent.BossBarColor.GREEN
                        : BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10);
        bossBar.setProgress(0.0F);
    }

    static MudTerrainGenerationJob generate(
            UUID playerId, ResourceKey<Level> dimension,
            MudTerrainGenerationRequest request,
            ResourceLocation sourceFilter) {
        boolean surface = request.type()
                == MudTerrainGenerationType.SURFACE_DEPOSIT;
        boolean natural = request.type().isNaturalDeposit();
        return new MudTerrainGenerationJob(
                playerId, dimension, Operation.GENERATE, request, sourceFilter,
                surface ? new ColumnCursor(
                        request.center(), request.depositSettings().radius()) : null,
                natural ? new NaturalCursor(request) : null,
                surface || natural ? null : new LakeCursor(
                        MudTerrainLakeShape.build(request.lakeSettings()),
                        request.type()),
                List.of(), List.of());
    }

    static MudTerrainGenerationJob undo(UUID playerId, UndoRecord record) {
        return new MudTerrainGenerationJob(
                playerId, record.dimension, Operation.UNDO,
                null, null, null, null, null,
                record.adaptivePositions, record.directPositions);
    }

    void showTo(ServerPlayer player) {
        bossBar.addPlayer(player);
    }

    int changedCount() {
        return changedAdaptivePositions.size() + changedDirectPositions.size();
    }

    void updateProgress() {
        float progress;
        if (operation == Operation.UNDO) {
            int total = undoAdaptivePositions.size() + undoDirectPositions.size();
            progress = total == 0 ? 1.0F
                    : (float) (adaptiveUndoIndex + directUndoIndex) / total;
        } else if (request.type() == MudTerrainGenerationType.SURFACE_DEPOSIT) {
            progress = columns.progress();
        } else if (request.type().isNaturalDeposit()) {
            progress = naturalCells.progress();
        } else {
            progress = lakeCells.progress();
        }
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        bossBar.setProgress(progress);
        int percent = Mth.floor(progress * 100.0F);
        if (percent != displayedPercent) {
            displayedPercent = percent;
            bossBar.setName(progressName(percent));
        }
    }

    private Component progressName(int percent) {
        return Component.translatable(operation == Operation.GENERATE
                ? "bossbar.mirebound.generation.generating"
                : "bossbar.mirebound.generation.undoing", percent);
    }

    enum Operation {
        GENERATE,
        UNDO
    }

    static final class ColumnCursor {
        private final int minimumX;
        private final int maximumX;
        private final int maximumZ;
        private int x;
        private int z;
        private int processed;
        private final int total;

        ColumnCursor(BlockPos center, int radius) {
            minimumX = center.getX() - radius;
            maximumX = center.getX() + radius;
            x = minimumX;
            z = center.getZ() - radius;
            maximumZ = center.getZ() + radius;
            int diameter = radius * 2 + 1;
            total = diameter * diameter;
        }

        boolean hasNext() {
            return processed < total;
        }

        Column next() {
            Column result = new Column(x, z);
            processed++;
            if (x < maximumX) {
                x++;
            } else {
                x = minimumX;
                z++;
            }
            return result;
        }

        float progress() {
            return total == 0 ? 1.0F : (float) processed / total;
        }
    }

    record Column(int x, int z) {
    }

    static final class NaturalCursor {
        private final List<Cell> cells;
        private int index;

        NaturalCursor(MudTerrainGenerationRequest request) {
            cells = NaturalMudDepositShape.buildForWand(
                    request.type().naturalForm(),
                    request.depositSettings().seed(),
                    request.depositSettings().radius());
        }

        boolean hasNext() {
            return index < cells.size();
        }

        Cell next() {
            return cells.get(index++);
        }

        float progress() {
            return cells.isEmpty() ? 1.0F : (float) index / cells.size();
        }
    }

    enum LakeRole {
        INTERIOR,
        CAVITY,
        SHELL
    }

    record LakeCell(BlockPos offset, LakeRole role, boolean surface) {
    }

    static final class LakeCursor {
        private final List<LakeCell> cells;
        private int index;

        LakeCursor(
                MudTerrainLakeShape.Shape shape,
                MudTerrainGenerationType type) {
            List<LakeCell> built = new ArrayList<>(
                    shape.interior().size() + shape.cavity().size()
                            + shape.shell().size());
            Set<Long> surfaceInterior = MudTerrainLakeShape.surfaceInterior(shape);
            shape.cavity().forEach(pos -> built.add(
                    new LakeCell(pos, LakeRole.CAVITY, false)));
            shape.interior().forEach(pos -> built.add(
                    new LakeCell(pos, LakeRole.INTERIOR,
                            surfaceInterior.contains(pos.asLong()))));
            shape.shell().stream()
                    .filter(pos -> MudTerrainLakeShape.includesShell(type, pos))
                    .forEach(pos -> built.add(
                            new LakeCell(pos, LakeRole.SHELL, false)));
            cells = List.copyOf(built);
        }

        boolean hasNext() {
            return index < cells.size();
        }

        LakeCell next() {
            return cells.get(index++);
        }

        float progress() {
            return cells.isEmpty() ? 1.0F : (float) index / cells.size();
        }
    }

    record BlockSnapshot(BlockPos pos, BlockState before, BlockState after) {
    }

    record UndoRecord(
            ResourceKey<Level> dimension,
            List<Long> adaptivePositions,
            List<BlockSnapshot> directPositions) {
        boolean empty() {
            return adaptivePositions.isEmpty() && directPositions.isEmpty();
        }

        int size() {
            return adaptivePositions.size() + directPositions.size();
        }
    }
}
