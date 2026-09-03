package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

class MudVariantModelsTest {
    @Test
    void horizontalSurfaceUsesWorldXZGrid() {
        assertEquals(0, MudVariantModels.connectedTileIndex(
                new BlockPos(0, 40, 0), Direction.UP));
        assertEquals(1, MudVariantModels.connectedTileIndex(
                new BlockPos(1, 40, 0), Direction.UP));
        assertEquals(3, MudVariantModels.connectedTileIndex(
                new BlockPos(0, 40, 1), Direction.UP));
    }

    @Test
    void wallSurfaceUsesHorizontalAxisAndWorldHeight() {
        assertEquals(0, MudVariantModels.connectedTileIndex(
                new BlockPos(0, 0, 0), Direction.NORTH));
        assertEquals(1, MudVariantModels.connectedTileIndex(
                new BlockPos(1, 0, 0), Direction.NORTH));
        assertEquals(3, MudVariantModels.connectedTileIndex(
                new BlockPos(0, 1, 0), Direction.NORTH));
    }

    @Test
    void negativeCoordinatesWrapWithoutBreakingTheGrid() {
        assertEquals(8, MudVariantModels.connectedTileIndex(
                new BlockPos(-1, 0, -1), Direction.UP));
    }

    @Test
    void opaqueMudInterfacesCullAcrossDifferentMedia() {
        assertTrue(MudVariantModels.shouldCullCoveredMudInterface(
                SinkingMedium.MUD, SinkingMedium.SOFT_QUICKSAND));
    }

    @Test
    void identicalTransparentMudStillCullsItsInternalFaces() {
        assertTrue(MudVariantModels.shouldCullCoveredMudInterface(
                SinkingMedium.LIVING_SLIME, SinkingMedium.LIVING_SLIME));
    }

    @Test
    void transparentMudKeepsItsBoundaryAgainstOtherMud() {
        assertFalse(MudVariantModels.shouldCullCoveredMudInterface(
                SinkingMedium.LIVING_SLIME, SinkingMedium.MUD));
        assertFalse(MudVariantModels.shouldCullCoveredMudInterface(
                SinkingMedium.MUD, SinkingMedium.ASSIMILATION_SLIME));
    }

    @Test
    void fullNeighborCoversTheWholeSharedFace() {
        assertTrue(MudVariantModels.coversCurrentSharedFace(
                Shapes.block(), Shapes.block(), Direction.EAST));
    }

    @Test
    void halfHeightNeighborDoesNotHideAFullSideFace() {
        assertFalse(MudVariantModels.coversCurrentSharedFace(
                Shapes.block(), Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
                Direction.EAST));
    }

    @Test
    void horizontalThinLayerStillCoversTheSharedHorizontalFace() {
        assertTrue(MudVariantModels.coversCurrentSharedFace(
                Shapes.block(), Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.25D, 1.0D),
                Direction.UP));
    }
}
