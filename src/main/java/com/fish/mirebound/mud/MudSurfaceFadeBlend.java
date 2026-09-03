package com.fish.mirebound.mud;

import java.util.BitSet;
import java.util.function.IntPredicate;
import net.minecraft.util.Mth;

/** Applies a monotonic fade transition across adjacent faces of one model cube. */
final class MudSurfaceFadeBlend {
    private static final float EPSILON = 1.0F / 255.0F;
    private static final MudSurfaceLayout.Edge[] EDGES = MudSurfaceLayout.Edge.values();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private MudSurfaceFadeBlend() {
    }

    static boolean fade(float[] coverage, byte[] medium, int[] appearance,
            long[] visualSource, BitSet transferredCells, IntPredicate ownsCell) {
        return fade(coverage, medium, appearance, visualSource, transferredCells,
                ownsCell, Profile.current());
    }

    static boolean fade(byte[] coverage, byte[] medium, long[] visualSource,
            BitSet transferredCells, IntPredicate ownsCell) {
        return fade(coverage, medium, visualSource, transferredCells, ownsCell,
                Profile.current());
    }

    static boolean fade(float[] coverage, byte[] medium, int[] appearance,
            long[] visualSource, BitSet transferredCells, IntPredicate ownsCell,
            Profile profile) {
        if (transferredCells == null || transferredCells.isEmpty()) {
            return false;
        }
        Scratch scratch = SCRATCH.get();
        System.arraycopy(coverage, 0, scratch.floatCoverage, 0,
                MudSurfaceLayout.CELL_COUNT);
        return fade(new FloatCells(
                        coverage, scratch.floatCoverage, medium, appearance, visualSource),
                transferredCells, ownsCell, profile);
    }

    static boolean fade(byte[] coverage, byte[] medium, long[] visualSource,
            BitSet transferredCells, IntPredicate ownsCell, Profile profile) {
        if (transferredCells == null || transferredCells.isEmpty()) {
            return false;
        }
        Scratch scratch = SCRATCH.get();
        System.arraycopy(coverage, 0, scratch.byteCoverage, 0,
                MudSurfaceLayout.CELL_COUNT);
        return fade(new ByteCells(
                        coverage, scratch.byteCoverage, medium, visualSource),
                transferredCells, ownsCell, profile);
    }

    private static boolean fade(Cells cells, BitSet transferredCells,
            IntPredicate ownsCell, Profile profile) {
        boolean changed = false;
        for (int sourceCell = transferredCells.nextSetBit(0);
                sourceCell >= 0 && sourceCell < MudSurfaceLayout.CELL_COUNT;
                sourceCell = transferredCells.nextSetBit(sourceCell + 1)) {
            if (!ownsCell.test(sourceCell)) {
                continue;
            }
            MudBodyPart part = MudSurfaceLayout.part(sourceCell);
            MudSurface surface = MudSurfaceLayout.surface(sourceCell);
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
            int row = MudSurfaceLayout.row(sourceCell);
            int column = MudSurfaceLayout.column(sourceCell);
            for (MudSurfaceLayout.Edge edge : EDGES) {
                if (!onEdge(face, row, column, edge)) {
                    continue;
                }
                MudSurfaceLayout.AdjacentCell adjacent =
                        MudSurfaceLayout.neighborAcrossEdge(part, surface, row, column, edge);
                int firstCell = MudSurfaceLayout.cellIndex(
                        part, adjacent.surface(), adjacent.row(), adjacent.column());
                if (!eligibleTarget(
                        cells, transferredCells, ownsCell, sourceCell, firstCell)) {
                    continue;
                }
                int secondCell = inwardCell(part, adjacent, 1);
                if (secondCell < 0
                        || !compatible(cells, ownsCell, sourceCell, secondCell)) {
                    continue;
                }
                int thirdCell = inwardCell(part, adjacent, 2);
                boolean hasFarAnchor = thirdCell >= 0
                        && compatible(cells, ownsCell, sourceCell, thirdCell);
                float anchorCoverage = cells.sourceCoverage(
                        hasFarAnchor ? thirdCell : secondCell);
                changed |= fadeCell(cells, sourceCell, firstCell, anchorCoverage,
                        profile.firstContrastRetention());

                if (hasFarAnchor && !transferredCells.get(secondCell)) {
                    changed |= fadeCell(cells, sourceCell, secondCell, anchorCoverage,
                            profile.secondContrastRetention());
                }
            }
        }
        return changed;
    }

    private static boolean eligibleTarget(Cells cells, BitSet transferredCells,
            IntPredicate ownsCell, int sourceCell, int targetCell) {
        return !transferredCells.get(targetCell)
                && compatible(cells, ownsCell, sourceCell, targetCell);
    }

    private static boolean compatible(Cells cells, IntPredicate ownsCell,
            int sourceCell, int targetCell) {
        return ownsCell.test(targetCell)
                && cells.sourceMedium(sourceCell) == cells.sourceMedium(targetCell)
                && cells.sourceAppearance(sourceCell) == cells.sourceAppearance(targetCell)
                && cells.sourceVisualSource(sourceCell) == cells.sourceVisualSource(targetCell);
    }

    private static boolean fadeCell(Cells cells, int sourceCell, int targetCell,
            float anchorCoverage, float contrastRetention) {
        float sourceCoverage = cells.sourceCoverage(sourceCell);
        if (anchorCoverage <= sourceCoverage + EPSILON) {
            return false;
        }
        float maximum = sourceCoverage
                + (anchorCoverage - sourceCoverage) * contrastRetention;
        if (maximum >= cells.outputCoverage(targetCell) - EPSILON) {
            return false;
        }
        cells.writeCoverage(targetCell, maximum);
        return true;
    }

    private static boolean onEdge(MudSurfaceLayout.Face face, int row, int column,
            MudSurfaceLayout.Edge edge) {
        return switch (edge) {
            case ROW_MIN -> row == 0;
            case ROW_MAX -> row == face.height() - 1;
            case COLUMN_MIN -> column == 0;
            case COLUMN_MAX -> column == face.width() - 1;
        };
    }

    private static int inwardCell(MudBodyPart part,
            MudSurfaceLayout.AdjacentCell adjacent, int distance) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, adjacent.surface());
        int row = adjacent.row();
        int column = adjacent.column();
        switch (adjacent.edge()) {
            case ROW_MIN -> row += distance;
            case ROW_MAX -> row -= distance;
            case COLUMN_MIN -> column += distance;
            case COLUMN_MAX -> column -= distance;
        }
        if (row < 0 || row >= face.height() || column < 0 || column >= face.width()) {
            return -1;
        }
        return MudSurfaceLayout.cellIndex(part, adjacent.surface(), row, column);
    }

    record Profile(float firstContrastRetention, float secondContrastRetention) {
        Profile {
            firstContrastRetention = Mth.clamp(firstContrastRetention, 0.0F, 1.0F);
            secondContrastRetention = Mth.clamp(
                    secondContrastRetention, firstContrastRetention, 1.0F);
        }

        static Profile current() {
            return new Profile(
                    MudPhysicsSettings.wallTransferEdgeFadeFirstContrastRetention(),
                    MudPhysicsSettings.wallTransferEdgeFadeSecondContrastRetention());
        }
    }

    private interface Cells {
        float sourceCoverage(int cell);

        float outputCoverage(int cell);

        byte sourceMedium(int cell);

        int sourceAppearance(int cell);

        long sourceVisualSource(int cell);

        void writeCoverage(int cell, float coverage);
    }

    private record FloatCells(float[] outputCoverage, float[] sourceCoverage,
            byte[] medium, int[] appearance, long[] visualSource) implements Cells {
        @Override
        public float sourceCoverage(int cell) {
            return sourceCoverage[cell];
        }

        @Override
        public float outputCoverage(int cell) {
            return outputCoverage[cell];
        }

        @Override
        public byte sourceMedium(int cell) {
            return medium[cell];
        }

        @Override
        public int sourceAppearance(int cell) {
            return appearance[cell];
        }

        @Override
        public long sourceVisualSource(int cell) {
            return visualSource[cell];
        }

        @Override
        public void writeCoverage(int cell, float coverage) {
            outputCoverage[cell] = Mth.clamp(coverage, 0.0F, 1.0F);
        }
    }

    private record ByteCells(byte[] outputCoverage, byte[] sourceCoverage,
            byte[] medium, long[] visualSource) implements Cells {
        @Override
        public float sourceCoverage(int cell) {
            return (sourceCoverage[cell] & 0xFF) / 255.0F;
        }

        @Override
        public float outputCoverage(int cell) {
            return (outputCoverage[cell] & 0xFF) / 255.0F;
        }

        @Override
        public byte sourceMedium(int cell) {
            return medium[cell];
        }

        @Override
        public int sourceAppearance(int cell) {
            return MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
        }

        @Override
        public long sourceVisualSource(int cell) {
            return visualSource[cell];
        }

        @Override
        public void writeCoverage(int cell, float coverage) {
            outputCoverage[cell] = (byte) Mth.clamp(
                    Math.round(coverage * 255.0F), 0, 255);
        }
    }

    private static final class Scratch {
        private final float[] floatCoverage = new float[MudSurfaceLayout.CELL_COUNT];
        private final byte[] byteCoverage = new byte[MudSurfaceLayout.CELL_COUNT];
    }
}
