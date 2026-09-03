package com.fish.mirebound.client.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MudTerrainGenerationPreviewGeometryTest {
    @Test
    void adjacentVoxelsRemoveTheirSharedFaces() {
        BlockPos first = new BlockPos(2, 4, 8);
        BlockPos second = first.east();
        MudTerrainGenerationPreviewGeometry.Geometry geometry =
                MudTerrainGenerationPreviewGeometry.build(Set.of(
                        first.asLong(), second.asLong()));

        assertEquals(10, geometry.faces().size());
        assertFalse(geometry.faces().contains(
                new MudTerrainGenerationPreviewGeometry.Face(
                        first, Direction.EAST)));
        assertFalse(geometry.faces().contains(
                new MudTerrainGenerationPreviewGeometry.Face(
                        second, Direction.WEST)));
        assertFalse(geometry.edges().isEmpty());
    }

    @Test
    void cavityFacesNeverProduceOutlineEdges() {
        BlockPos cavity = new BlockPos(3, 7, -2);
        MudTerrainGenerationPreviewGeometry.Geometry geometry =
                MudTerrainGenerationPreviewGeometry.build(
                        Set.of(), Set.of(cavity.asLong()), Set.of());

        assertEquals(6, geometry.faces().size());
        assertTrue(geometry.edges().isEmpty());
    }

    @Test
    void interiorAndShellTopFacesKeepTheirSharedBoundary() {
        BlockPos interior = BlockPos.ZERO;
        BlockPos shell = interior.east();
        MudTerrainGenerationPreviewGeometry.Geometry geometry =
                MudTerrainGenerationPreviewGeometry.build(
                        Set.of(interior.asLong()), Set.of(),
                        Set.of(shell.asLong()));

        assertTrue(geometry.edges().contains(
                new MudTerrainGenerationPreviewGeometry.EdgeRun(
                        2, 1, 1, 0, 1)));
    }
}
