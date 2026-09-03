package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MudSurfaceLayoutTest {
    @Test
    void everyCubeEdgeMapsBackToItsSourceCell() {
        for (MudBodyPart part : MudBodyPart.values()) {
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (int row = 0; row < face.height(); row++) {
                    for (int column = 0; column < face.width(); column++) {
                        verifyEdge(part, surface, row, column, MudSurfaceLayout.Edge.ROW_MIN, row == 0);
                        verifyEdge(part, surface, row, column, MudSurfaceLayout.Edge.ROW_MAX, row == face.height() - 1);
                        verifyEdge(part, surface, row, column, MudSurfaceLayout.Edge.COLUMN_MIN, column == 0);
                        verifyEdge(part, surface, row, column, MudSurfaceLayout.Edge.COLUMN_MAX, column == face.width() - 1);
                    }
                }
            }
        }
    }

    private static void verifyEdge(MudBodyPart part, MudSurface surface, int row, int column,
            MudSurfaceLayout.Edge edge, boolean onEdge) {
        if (!onEdge) {
            return;
        }
        MudSurfaceLayout.AdjacentCell neighbor = MudSurfaceLayout.neighborAcrossEdge(part, surface, row, column, edge);
        MudSurfaceLayout.AdjacentCell returned = MudSurfaceLayout.neighborAcrossEdge(
                part,
                neighbor.surface(),
                neighbor.row(),
                neighbor.column(),
                neighbor.edge());
        assertEquals(surface, returned.surface());
        assertEquals(row, returned.row());
        assertEquals(column, returned.column());
    }
}
