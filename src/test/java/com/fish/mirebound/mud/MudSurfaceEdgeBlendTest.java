package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MudSurfaceEdgeBlendTest {
    private static final float EPSILON = 1.0F / 255.0F;

    @Test
    void blendsOnePixelAcrossAnAdjacentCubeFace() {
        float[] coverage = new float[MudSurfaceLayout.CELL_COUNT];
        byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        MudBodyPart part = MudBodyPart.HEAD;
        MudSurface surface = MudSurface.TOP;
        int row = 0;
        int column = 3;
        int source = MudSurfaceLayout.cellIndex(part, surface, row, column);
        MudSurfaceLayout.AdjacentCell adjacent = MudSurfaceLayout.neighborAcrossEdge(
                part, surface, row, column, MudSurfaceLayout.Edge.ROW_MIN);
        int target = MudSurfaceLayout.cellIndex(
                part, adjacent.surface(), adjacent.row(), adjacent.column());
        coverage[source] = 1.0F;
        medium[source] = (byte) SinkingMedium.TAR.id();

        MudSurfaceEdgeBlend.blend(
                coverage,
                medium,
                ignored -> true,
                new MudSurfaceEdgeBlend.Profile(0.1F, 0.01F, 0.76F, 0.0F, 0.44F));

        assertEquals(1.0F, coverage[source], EPSILON);
        assertEquals(0.76F, coverage[target], EPSILON);
        assertEquals(SinkingMedium.TAR.id(), medium[target] & 0xFF);
    }

    @Test
    void secondPixelIsSparseAndWeaker() {
        float[] coverage = new float[MudSurfaceLayout.CELL_COUNT];
        byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        MudBodyPart part = MudBodyPart.HEAD;
        MudSurface surface = MudSurface.TOP;
        int row = 0;
        int column = 3;
        int source = MudSurfaceLayout.cellIndex(part, surface, row, column);
        MudSurfaceLayout.AdjacentCell adjacent = MudSurfaceLayout.neighborAcrossEdge(
                part, surface, row, column, MudSurfaceLayout.Edge.ROW_MIN);
        int first = MudSurfaceLayout.cellIndex(
                part, adjacent.surface(), adjacent.row(), adjacent.column());
        int second = inwardCell(part, adjacent);
        coverage[source] = 1.0F;
        medium[source] = (byte) SinkingMedium.MUD.id();

        MudSurfaceEdgeBlend.blend(
                coverage,
                medium,
                ignored -> true,
                new MudSurfaceEdgeBlend.Profile(0.1F, 0.01F, 0.76F, 1.0F, 0.44F));

        assertEquals(0.76F, coverage[first], EPSILON);
        assertEquals(0.44F, coverage[second], EPSILON);
    }

    @Test
    void neverCrossesOutsideTheOwnedArmorCells() {
        float[] coverage = new float[MudSurfaceLayout.CELL_COUNT];
        byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        MudBodyPart part = MudBodyPart.HEAD;
        int source = MudSurfaceLayout.cellIndex(part, MudSurface.TOP, 0, 3);
        coverage[source] = 1.0F;

        MudSurfaceEdgeBlend.blend(
                coverage,
                medium,
                cell -> cell == source,
                new MudSurfaceEdgeBlend.Profile(0.1F, 0.01F, 0.76F, 1.0F, 0.44F));

        for (int cell = 0; cell < coverage.length; cell++) {
            assertEquals(cell == source ? 1.0F : 0.0F, coverage[cell], EPSILON);
        }
    }

    @Test
    void crossFaceBlendKeepsTheSourceAppearanceSnapshot() {
        float[] coverage = new float[MudSurfaceLayout.CELL_COUNT];
        byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        int[] appearance = new int[MudSurfaceLayout.CELL_COUNT];
        MudBodyPart part = MudBodyPart.HEAD;
        int source = MudSurfaceLayout.cellIndex(part, MudSurface.TOP, 0, 3);
        MudSurfaceLayout.AdjacentCell adjacent = MudSurfaceLayout.neighborAcrossEdge(
                part, MudSurface.TOP, 0, 3, MudSurfaceLayout.Edge.ROW_MIN);
        int target = MudSurfaceLayout.cellIndex(part, adjacent.surface(), adjacent.row(), adjacent.column());
        int snapshot = MudCoverageAppearanceSnapshot.pack(0.75F, 0.82F, 0.12F);
        coverage[source] = 1.0F;
        medium[source] = (byte) SinkingMedium.RED_QUICKSAND.id();
        appearance[source] = snapshot;

        MudSurfaceEdgeBlend.blend(coverage, medium, appearance, ignored -> true, (cell, mediumId) -> true);

        assertEquals(snapshot, appearance[target]);
    }

    private static int inwardCell(MudBodyPart part, MudSurfaceLayout.AdjacentCell adjacent) {
        int row = adjacent.row();
        int column = adjacent.column();
        switch (adjacent.edge()) {
            case ROW_MIN -> row++;
            case ROW_MAX -> row--;
            case COLUMN_MIN -> column++;
            case COLUMN_MAX -> column--;
        }
        return MudSurfaceLayout.cellIndex(part, adjacent.surface(), row, column);
    }
}
