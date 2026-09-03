package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

class MudSurfaceFadeBlendTest {
    private static final float EPSILON = 1.0F / 255.0F;
    private static final MudSurfaceFadeBlend.Profile PROFILE =
            new MudSurfaceFadeBlend.Profile(0.38F, 0.72F);

    @Test
    void fadesTwoPixelsAcrossTheAdjacentSkinFaceWithoutChangingTheSource() {
        FloatFixture fixture = floatFixture();
        float before = sum(fixture.coverage);

        assertTrue(MudSurfaceFadeBlend.fade(
                fixture.coverage, fixture.medium, fixture.appearance,
                fixture.visualSource, fixture.transferred, ignored -> true, PROFILE));

        assertEquals(0.35F, fixture.coverage[fixture.source], EPSILON);
        assertEquals(0.597F, fixture.coverage[fixture.first], EPSILON);
        assertEquals(0.818F, fixture.coverage[fixture.second], EPSILON);
        assertTrue(sum(fixture.coverage) < before);
    }

    @Test
    void neverChangesAnotherDirectlyTransferredCell() {
        FloatFixture fixture = floatFixture();
        fixture.coverage[fixture.first] = 0.31F;
        fixture.transferred.set(fixture.first);

        MudSurfaceFadeBlend.fade(
                fixture.coverage, fixture.medium, fixture.appearance,
                fixture.visualSource, fixture.transferred, ignored -> true, PROFILE);

        assertEquals(0.31F, fixture.coverage[fixture.first], EPSILON);
    }

    @Test
    void doesNotCrossBetweenDifferentAdaptiveVisualSources() {
        FloatFixture fixture = floatFixture();
        fixture.visualSource[fixture.first] = 91L;

        assertFalse(MudSurfaceFadeBlend.fade(
                fixture.coverage, fixture.medium, fixture.appearance,
                fixture.visualSource, fixture.transferred, ignored -> true, PROFILE));

        assertEquals(1.0F, fixture.coverage[fixture.first], EPSILON);
        assertEquals(1.0F, fixture.coverage[fixture.second], EPSILON);
    }

    @Test
    void armorCoverageUsesTheSameMonotonicTransition() {
        FloatFixture fixture = floatFixture();
        byte[] coverage = new byte[MudSurfaceLayout.CELL_COUNT];
        coverage[fixture.source] = (byte) Math.round(0.35F * 255.0F);
        coverage[fixture.first] = (byte) 255;
        coverage[fixture.second] = (byte) 255;
        coverage[fixture.third] = (byte) 255;

        assertTrue(MudSurfaceFadeBlend.fade(
                coverage, fixture.medium, fixture.visualSource,
                fixture.transferred, ignored -> true, PROFILE));

        assertEquals(0.35F, (coverage[fixture.source] & 0xFF) / 255.0F, EPSILON);
        assertEquals(0.597F, (coverage[fixture.first] & 0xFF) / 255.0F, EPSILON);
        assertEquals(0.818F, (coverage[fixture.second] & 0xFF) / 255.0F, EPSILON);
    }

    @Test
    void neverDarkensACleanerAdjacentFace() {
        FloatFixture fixture = floatFixture();
        fixture.coverage[fixture.first] = 0.10F;
        fixture.coverage[fixture.second] = 0.12F;

        assertFalse(MudSurfaceFadeBlend.fade(
                fixture.coverage, fixture.medium, fixture.appearance,
                fixture.visualSource, fixture.transferred, ignored -> true, PROFILE));

        assertEquals(0.10F, fixture.coverage[fixture.first], EPSILON);
        assertEquals(0.12F, fixture.coverage[fixture.second], EPSILON);
    }

    @Test
    void repeatedUpdatesKeepTheTransitionAnchoredToTheUnchangedInterior() {
        FloatFixture fixture = floatFixture();

        MudSurfaceFadeBlend.fade(
                fixture.coverage, fixture.medium, fixture.appearance,
                fixture.visualSource, fixture.transferred, ignored -> true, PROFILE);
        float first = fixture.coverage[fixture.first];
        float second = fixture.coverage[fixture.second];
        MudSurfaceFadeBlend.fade(
                fixture.coverage, fixture.medium, fixture.appearance,
                fixture.visualSource, fixture.transferred, ignored -> true, PROFILE);

        assertEquals(first, fixture.coverage[fixture.first], EPSILON);
        assertEquals(second, fixture.coverage[fixture.second], EPSILON);
    }

    private static FloatFixture floatFixture() {
        float[] coverage = new float[MudSurfaceLayout.CELL_COUNT];
        byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        int[] appearance = new int[MudSurfaceLayout.CELL_COUNT];
        long[] visualSource = new long[MudSurfaceLayout.CELL_COUNT];
        MudBodyPart part = MudBodyPart.HEAD;
        int source = MudSurfaceLayout.cellIndex(part, MudSurface.TOP, 0, 3);
        MudSurfaceLayout.AdjacentCell adjacent = MudSurfaceLayout.neighborAcrossEdge(
                part, MudSurface.TOP, 0, 3, MudSurfaceLayout.Edge.ROW_MIN);
        int first = MudSurfaceLayout.cellIndex(
                part, adjacent.surface(), adjacent.row(), adjacent.column());
        int second = inwardCell(part, adjacent);
        int third = inwardCell(part, adjacent, 2);
        int snapshot = MudCoverageAppearanceSnapshot.pack(0.75F, 0.82F, 0.12F);
        for (int cell : new int[] {source, first, second, third}) {
            medium[cell] = (byte) SinkingMedium.MUD.id();
            appearance[cell] = snapshot;
            visualSource[cell] = 37L;
        }
        coverage[source] = 0.35F;
        coverage[first] = 1.0F;
        coverage[second] = 1.0F;
        coverage[third] = 1.0F;
        BitSet transferred = new BitSet(MudSurfaceLayout.CELL_COUNT);
        transferred.set(source);
        return new FloatFixture(
                coverage, medium, appearance, visualSource,
                transferred, source, first, second, third);
    }

    private static int inwardCell(MudBodyPart part,
            MudSurfaceLayout.AdjacentCell adjacent) {
        return inwardCell(part, adjacent, 1);
    }

    private static int inwardCell(MudBodyPart part,
            MudSurfaceLayout.AdjacentCell adjacent, int distance) {
        int row = adjacent.row();
        int column = adjacent.column();
        switch (adjacent.edge()) {
            case ROW_MIN -> row += distance;
            case ROW_MAX -> row -= distance;
            case COLUMN_MIN -> column += distance;
            case COLUMN_MAX -> column -= distance;
        }
        return MudSurfaceLayout.cellIndex(part, adjacent.surface(), row, column);
    }

    private static float sum(float[] values) {
        float result = 0.0F;
        for (float value : values) {
            result += value;
        }
        return result;
    }

    private record FloatFixture(float[] coverage, byte[] medium, int[] appearance,
            long[] visualSource, BitSet transferred, int source, int first, int second,
            int third) {
    }
}
