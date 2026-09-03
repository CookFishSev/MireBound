package com.fish.mirebound.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MudTerrainGenerationSchedulerTest {
    @Test
    void columnCursorTraversesTheBoundedSquareOnce() {
        MudTerrainGenerationJob.ColumnCursor cursor =
                new MudTerrainGenerationJob.ColumnCursor(
                        new BlockPos(10, 70, -4), 2);
        Set<String> visited = new HashSet<>();

        while (cursor.hasNext()) {
            MudTerrainGenerationJob.Column column = cursor.next();
            assertTrue(visited.add(column.x() + ":" + column.z()));
        }

        assertEquals(25, visited.size());
        assertTrue(visited.contains("8:-6"));
        assertTrue(visited.contains("12:-2"));
        assertFalse(cursor.hasNext());
        assertEquals(1.0F, cursor.progress());
    }

    @Test
    void lakeCursorCarriesFillCavityAndShellRolesOnce() {
        MudTerrainLakeShape.Shape shape = new MudTerrainLakeShape.Shape(
                java.util.List.of(BlockPos.ZERO),
                java.util.List.of(BlockPos.ZERO.above()),
                java.util.List.of(BlockPos.ZERO.below()));
        MudTerrainGenerationJob.LakeCursor cursor =
                new MudTerrainGenerationJob.LakeCursor(
                        shape, MudTerrainGenerationType.LAKE_POOL);
        Set<MudTerrainGenerationJob.LakeRole> roles = new HashSet<>();

        while (cursor.hasNext()) {
            roles.add(cursor.next().role());
        }

        assertEquals(Set.of(
                MudTerrainGenerationJob.LakeRole.INTERIOR,
                MudTerrainGenerationJob.LakeRole.CAVITY,
                MudTerrainGenerationJob.LakeRole.SHELL), roles);
        assertEquals(1.0F, cursor.progress());
    }

    @Test
    void surfaceLakeCursorDropsOnlyTheUpperShell() {
        MudTerrainLakeShape.Shape shape = new MudTerrainLakeShape.Shape(
                java.util.List.of(BlockPos.ZERO),
                java.util.List.of(BlockPos.ZERO.above()),
                java.util.List.of(BlockPos.ZERO.below(), BlockPos.ZERO.above()));
        MudTerrainGenerationJob.LakeCursor cursor =
                new MudTerrainGenerationJob.LakeCursor(
                        shape, MudTerrainGenerationType.LAKE_SURFACE);
        Set<BlockPos> shell = new HashSet<>();

        while (cursor.hasNext()) {
            MudTerrainGenerationJob.LakeCell cell = cursor.next();
            if (cell.role() == MudTerrainGenerationJob.LakeRole.SHELL) {
                shell.add(cell.offset());
            }
        }

        assertEquals(Set.of(BlockPos.ZERO.below()), shell);
    }

    @Test
    void configuredShellCanFillAirCells() {
        assertTrue(MudTerrainGenerationScheduler.shouldPlaceShell(
                false, false));
        assertFalse(MudTerrainGenerationScheduler.shouldPlaceShell(
                true, false));
        assertFalse(MudTerrainGenerationScheduler.shouldPlaceShell(
                false, true));
    }

    @Test
    void lakeCursorMarksOnlyTheTopInteriorCellsAsSurface() {
        MudTerrainLakeShape.Shape shape = new MudTerrainLakeShape.Shape(
                java.util.List.of(BlockPos.ZERO.below(), BlockPos.ZERO),
                java.util.List.of(), java.util.List.of());
        MudTerrainGenerationJob.LakeCursor cursor =
                new MudTerrainGenerationJob.LakeCursor(
                        shape, MudTerrainGenerationType.LAKE_SURFACE);
        Set<BlockPos> surface = new HashSet<>();

        while (cursor.hasNext()) {
            MudTerrainGenerationJob.LakeCell cell = cursor.next();
            if (cell.surface()) {
                surface.add(cell.offset());
            }
        }

        assertEquals(Set.of(BlockPos.ZERO), surface);
    }

    @Test
    void naturalCursorTraversesSharedWandShapeOnce() {
        MudTerrainGenerationRequest request = new MudTerrainGenerationRequest(
                MudTerrainGenerationType.MARSH_MOSAIC, BlockPos.ZERO, true,
                new MudTerrainGenerationSettings(14, 4, 0.5D, 6, 173, false),
                new MudTerrainLakeSettings(8, 4, 173,
                        MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR));
        MudTerrainGenerationJob.NaturalCursor cursor =
                new MudTerrainGenerationJob.NaturalCursor(request);
        Set<NaturalMudDepositShape.Cell> visited = new HashSet<>();

        while (cursor.hasNext()) {
            assertTrue(visited.add(cursor.next()));
        }

        assertEquals(new HashSet<>(NaturalMudDepositShape.buildForWand(
                request.type().naturalForm(), 173L, 14)), visited);
        assertEquals(1.0F, cursor.progress());
    }

}
