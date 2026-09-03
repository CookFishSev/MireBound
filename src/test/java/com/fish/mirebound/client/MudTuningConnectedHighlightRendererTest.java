package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.fish.mirebound.client.tuning.MudTuningWandMode;
import com.fish.mirebound.mud.tuning.MudTuningHighlightGeometry;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MudTuningConnectedHighlightRendererTest {
    @Test
    void manualSelectionGeometryOnlyBelongsToRangeMode() {
        assertTrue(MudTuningSelectionRenderer.rendersManualSelection(
                MudTuningWandMode.RANGE));
        assertFalse(MudTuningSelectionRenderer.rendersManualSelection(
                MudTuningWandMode.SINGLE));
        assertFalse(MudTuningSelectionRenderer.rendersManualSelection(
                MudTuningWandMode.CONVERT));
        assertFalse(MudTuningSelectionRenderer.rendersManualSelection(
                MudTuningWandMode.SUMMON));
        assertFalse(MudTuningSelectionRenderer.rendersManualSelection(
                MudTuningWandMode.GENERATION));
    }

    @Test
    void incompatibleHighlightsOnlyRenderInRangeMode() {
        for (MudTuningWandMode mode : MudTuningWandMode.values()) {
            assertEquals(mode == MudTuningWandMode.RANGE,
                    MudTuningConnectedHighlightRenderer.rendersHighlightKind(
                            mode, MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE));
            assertTrue(MudTuningConnectedHighlightRenderer.rendersHighlightKind(
                    mode, MudTuningSelectionPayload.HighlightKind.CONVERTED_DEFAULT));
            assertTrue(MudTuningConnectedHighlightRenderer.rendersHighlightKind(
                    mode, MudTuningSelectionPayload.HighlightKind.CONVERTED_MODIFIED));
            assertTrue(MudTuningConnectedHighlightRenderer.rendersHighlightKind(
                    mode, MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE));
        }
    }

    @Test
    void removesCoplanarSeamsBetweenConnectedCells() {
        Set<Long> positions = positions(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));

        assertFalse(MudTuningConnectedHighlightRenderer.edgeVisible(
                positions, new MudTuningConnectedHighlightRenderer.EdgeKey(1, 1, 0, 0)));
        assertTrue(MudTuningConnectedHighlightRenderer.edgeVisible(
                positions, new MudTuningConnectedHighlightRenderer.EdgeKey(1, 0, 0, 0)));
    }

    @Test
    void keepsDiagonalContourWhereCellsOnlyMeetAroundAnEdge() {
        Set<Long> positions = positions(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 1));

        assertTrue(MudTuningConnectedHighlightRenderer.edgeVisible(
                positions, new MudTuningConnectedHighlightRenderer.EdgeKey(1, 1, 0, 1)));
    }

    @Test
    void mergesOnlyAdjacentCollinearEdges() {
        List<MudTuningConnectedHighlightRenderer.EdgeRun> merged =
                MudTuningConnectedHighlightRenderer.mergeEdges(List.of(
                        new MudTuningConnectedHighlightRenderer.EdgeKey(0, 2, 4, 6),
                        new MudTuningConnectedHighlightRenderer.EdgeKey(0, 0, 4, 6),
                        new MudTuningConnectedHighlightRenderer.EdgeKey(0, 1, 4, 6),
                        new MudTuningConnectedHighlightRenderer.EdgeKey(0, 4, 4, 6),
                        new MudTuningConnectedHighlightRenderer.EdgeKey(0, 0, 5, 6),
                        new MudTuningConnectedHighlightRenderer.EdgeKey(2, 8, 9, 3),
                        new MudTuningConnectedHighlightRenderer.EdgeKey(2, 8, 9, 4)));

        assertEquals(List.of(
                new MudTuningConnectedHighlightRenderer.EdgeRun(0, 0, 4, 6, 3),
                new MudTuningConnectedHighlightRenderer.EdgeRun(0, 4, 4, 6, 1),
                new MudTuningConnectedHighlightRenderer.EdgeRun(0, 0, 5, 6, 1),
                new MudTuningConnectedHighlightRenderer.EdgeRun(2, 8, 9, 3, 2)), merged);
    }

    @Test
    void largePlaneCollapsesToTwelveLongBoundaryRuns() {
        Set<Long> blocks = new HashSet<>();
        for (int x = 0; x < 80; x++) {
            for (int z = 0; z < 80; z++) {
                blocks.add(BlockPos.asLong(x, 0, z));
            }
        }
        MudTuningHighlightGeometry.Result geometry =
                MudTuningHighlightGeometry.visibleEdges(blocks, 4_096);
        List<MudTuningConnectedHighlightRenderer.EdgeKey> edges = geometry.edges().stream()
                .map(edge -> new MudTuningConnectedHighlightRenderer.EdgeKey(
                        edge.axis(), edge.x(), edge.y(), edge.z()))
                .toList();

        assertTrue(geometry.complete());
        assertEquals(644, edges.size());
        assertEquals(12, MudTuningConnectedHighlightRenderer.mergeEdges(edges).size());
    }

    private static Set<Long> positions(BlockPos... blocks) {
        Set<Long> result = new HashSet<>();
        for (BlockPos block : blocks) {
            result.add(block.asLong());
        }
        return result;
    }
}
