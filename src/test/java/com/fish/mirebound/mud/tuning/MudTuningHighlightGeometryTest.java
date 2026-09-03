package com.fish.mirebound.mud.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MudTuningHighlightGeometryTest {
    @Test
    void largePlaneProducesOnlyItsCompleteOuterWireBoundary() {
        Set<Long> positions = new HashSet<>();
        for (int x = 0; x < 80; x++) {
            for (int z = 0; z < 80; z++) {
                positions.add(BlockPos.asLong(x, 0, z));
            }
        }

        MudTuningHighlightGeometry.Result result =
                MudTuningHighlightGeometry.visibleEdges(positions, 4_096);

        assertTrue(result.complete());
        assertEquals(644, result.edges().size());
        for (MudTuningHighlightGeometry.Edge edge : result.edges()) {
            assertTrue(onOuterBoundary(edge), () -> "interior edge " + edge);
        }
    }

    @Test
    void anOverflowNeverReturnsAMisleadingPartialContour() {
        Set<Long> positions = new HashSet<>();
        for (int x = 0; x < 40; x += 2) {
            for (int z = 0; z < 40; z += 2) {
                positions.add(BlockPos.asLong(x, 0, z));
            }
        }

        MudTuningHighlightGeometry.Result result =
                MudTuningHighlightGeometry.visibleEdges(positions, 64);

        assertTrue(!result.complete());
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void sparseOverflowFallsBackToACompleteNearbyContour() {
        Set<Long> positions = new HashSet<>();
        for (int x = 0; x < 80; x += 2) {
            for (int z = 0; z < 80; z += 2) {
                positions.add(BlockPos.asLong(x, 0, z));
            }
        }

        MudTuningHighlightGeometry.BudgetedResult result =
                MudTuningHighlightGeometry.fitToBudget(
                        positions, new BlockPos(40, 0, 40), 128, false);

        assertFalse(result.edges().isEmpty());
        assertTrue(result.primitiveCount() <= 128);
    }

    @Test
    void incompatibleFacesAndEdgesSurviveTheSamePrimitiveBudget() {
        Set<Long> positions = new HashSet<>();
        for (int x = 0; x < 80; x += 2) {
            for (int z = 0; z < 80; z += 2) {
                positions.add(BlockPos.asLong(x, 0, z));
            }
        }

        MudTuningHighlightGeometry.BudgetedResult result =
                MudTuningHighlightGeometry.fitToBudget(
                        positions, new BlockPos(40, 0, 40), 128, true);

        assertTrue(result.positions().length > 0);
        assertFalse(result.edges().isEmpty());
        assertTrue(result.primitiveCount() <= 128);
    }

    private static boolean onOuterBoundary(MudTuningHighlightGeometry.Edge edge) {
        return switch (edge.axis()) {
            case 0 -> (edge.y() == 0 || edge.y() == 1)
                    && (edge.z() == 0 || edge.z() == 80);
            case 1 -> (edge.x() == 0 || edge.x() == 80)
                    && (edge.z() == 0 || edge.z() == 80);
            default -> (edge.y() == 0 || edge.y() == 1)
                    && (edge.x() == 0 || edge.x() == 80);
        };
    }
}
