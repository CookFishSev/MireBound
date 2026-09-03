package com.fish.mirebound.mud;

import java.util.function.IntPredicate;
import net.minecraft.util.Mth;

/** Bounded one/two-pixel blending across the edges of one model cube. */
final class MudSurfaceEdgeBlend {
    private static final MudSurfaceLayout.Edge[] EDGES = MudSurfaceLayout.Edge.values();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private MudSurfaceEdgeBlend() {
    }

    static boolean blend(float[] coverage, byte[] medium, IntPredicate ownsCell) {
        return blend(coverage, medium, ownsCell, (cell, mediumId) -> true, Profile.current());
    }

    static boolean blend(byte[] coverage, byte[] medium, IntPredicate ownsCell) {
        return blend(coverage, medium, ownsCell, (cell, mediumId) -> true, Profile.current());
    }

    static boolean blend(float[] coverage, byte[] medium, IntPredicate ownsCell,
            CellMediumPredicate allowsPaint) {
        return blend(coverage, medium, ownsCell, allowsPaint, Profile.current());
    }

    static boolean blend(float[] coverage, byte[] medium, int[] appearance,
            IntPredicate ownsCell, CellMediumPredicate allowsPaint) {
        return blend(coverage, medium, appearance, null, ownsCell, allowsPaint);
    }

    static boolean blend(float[] coverage, byte[] medium, int[] appearance,
            long[] visualSource, IntPredicate ownsCell,
            CellMediumPredicate allowsPaint) {
        Scratch scratch = SCRATCH.get();
        System.arraycopy(coverage, 0, scratch.floatCoverage, 0, MudSurfaceLayout.CELL_COUNT);
        System.arraycopy(medium, 0, scratch.medium, 0, MudSurfaceLayout.CELL_COUNT);
        System.arraycopy(appearance, 0, scratch.appearance, 0, MudSurfaceLayout.CELL_COUNT);
        if (visualSource != null) {
            System.arraycopy(visualSource, 0, scratch.visualSource, 0,
                    MudSurfaceLayout.CELL_COUNT);
        }
        return blend(new FloatCells(coverage, medium, appearance, visualSource,
                        scratch.floatCoverage, scratch.medium, scratch.appearance,
                        visualSource == null ? null : scratch.visualSource),
                ownsCell, allowsPaint, Profile.current());
    }

    static boolean blend(byte[] coverage, byte[] medium, long[] visualSource,
            IntPredicate ownsCell, CellMediumPredicate allowsPaint) {
        Scratch scratch = SCRATCH.get();
        System.arraycopy(coverage, 0, scratch.byteCoverage, 0, MudSurfaceLayout.CELL_COUNT);
        System.arraycopy(medium, 0, scratch.medium, 0, MudSurfaceLayout.CELL_COUNT);
        System.arraycopy(visualSource, 0, scratch.visualSource, 0,
                MudSurfaceLayout.CELL_COUNT);
        return blend(new ByteCells(coverage, medium, visualSource,
                        scratch.byteCoverage, scratch.medium, scratch.visualSource),
                ownsCell, allowsPaint, Profile.current());
    }

    static boolean blend(byte[] coverage, byte[] medium, IntPredicate ownsCell,
            CellMediumPredicate allowsPaint) {
        return blend(coverage, medium, ownsCell, allowsPaint, Profile.current());
    }

    static boolean blend(float[] coverage, byte[] medium, IntPredicate ownsCell, Profile profile) {
        return blend(coverage, medium, ownsCell, (cell, mediumId) -> true, profile);
    }

    static boolean blend(float[] coverage, byte[] medium, IntPredicate ownsCell,
            CellMediumPredicate allowsPaint, Profile profile) {
        Scratch scratch = SCRATCH.get();
        System.arraycopy(coverage, 0, scratch.floatCoverage, 0, MudSurfaceLayout.CELL_COUNT);
        System.arraycopy(medium, 0, scratch.medium, 0, MudSurfaceLayout.CELL_COUNT);
        return blend(new FloatCells(coverage, medium, scratch.floatCoverage, scratch.medium),
                ownsCell, allowsPaint, profile);
    }

    static boolean blend(byte[] coverage, byte[] medium, IntPredicate ownsCell, Profile profile) {
        return blend(coverage, medium, ownsCell, (cell, mediumId) -> true, profile);
    }

    static boolean blend(byte[] coverage, byte[] medium, IntPredicate ownsCell,
            CellMediumPredicate allowsPaint, Profile profile) {
        Scratch scratch = SCRATCH.get();
        System.arraycopy(coverage, 0, scratch.byteCoverage, 0, MudSurfaceLayout.CELL_COUNT);
        System.arraycopy(medium, 0, scratch.medium, 0, MudSurfaceLayout.CELL_COUNT);
        return blend(new ByteCells(coverage, medium, scratch.byteCoverage, scratch.medium),
                ownsCell, allowsPaint, profile);
    }

    private static boolean blend(Cells cells, IntPredicate ownsCell,
            CellMediumPredicate allowsPaint, Profile profile) {
        boolean changed = false;
        for (MudBodyPart part : MudBodyPart.values()) {
            for (MudSurface surface : MudSurface.values()) {
                MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                for (MudSurfaceLayout.Edge edge : EDGES) {
                    int edgeLength = edge == MudSurfaceLayout.Edge.ROW_MIN
                            || edge == MudSurfaceLayout.Edge.ROW_MAX
                            ? face.width()
                            : face.height();
                    for (int along = 0; along < edgeLength; along++) {
                        int row = switch (edge) {
                            case ROW_MIN -> 0;
                            case ROW_MAX -> face.height() - 1;
                            case COLUMN_MIN, COLUMN_MAX -> along;
                        };
                        int column = switch (edge) {
                            case ROW_MIN, ROW_MAX -> along;
                            case COLUMN_MIN -> 0;
                            case COLUMN_MAX -> face.width() - 1;
                        };
                        int sourceCell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                        if (!ownsCell.test(sourceCell)) {
                            continue;
                        }
                        float sourceCoverage = cells.sourceCoverage(sourceCell);
                        if (sourceCoverage < profile.minimumSourceCoverage()) {
                            continue;
                        }

                        MudSurfaceLayout.AdjacentCell adjacent =
                                MudSurfaceLayout.neighborAcrossEdge(part, surface, row, column, edge);
                        int firstCell = MudSurfaceLayout.cellIndex(
                                part, adjacent.surface(), adjacent.row(), adjacent.column());
                        byte sourceMedium = cells.sourceMedium(sourceCell);
                        if (ownsCell.test(firstCell) && allowsPaint.test(firstCell, sourceMedium)) {
                            changed |= blendCell(
                                    cells,
                                    sourceCell,
                                    firstCell,
                                    sourceCoverage,
                                    profile.firstPixelRetention(),
                                    profile.minimumDifference());
                        }

                        if (profile.secondPixelChance() <= 0.0F
                                || stableNoise(part, surface, edge, along) >= profile.secondPixelChance()) {
                            continue;
                        }
                        int secondCell = inwardCell(part, adjacent);
                        if (secondCell >= 0 && ownsCell.test(secondCell)
                                && allowsPaint.test(secondCell, sourceMedium)) {
                            changed |= blendCell(
                                    cells,
                                    sourceCell,
                                    secondCell,
                                    sourceCoverage,
                                    Math.min(profile.firstPixelRetention(), profile.secondPixelRetention()),
                                    profile.minimumDifference());
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean blendCell(Cells cells, int sourceCell, int targetCell,
            float sourceCoverage, float retention, float minimumDifference) {
        float targetCoverage = cells.sourceCoverage(targetCell);
        if (sourceCoverage - targetCoverage < minimumDifference) {
            return false;
        }
        float desired = sourceCoverage * retention;
        if (desired <= cells.outputCoverage(targetCell) + 1.0F / 255.0F) {
            return false;
        }
        cells.write(targetCell, desired, cells.sourceMedium(sourceCell),
                cells.sourceAppearance(sourceCell), cells.sourceVisualSource(sourceCell));
        return true;
    }

    private static int inwardCell(MudBodyPart part, MudSurfaceLayout.AdjacentCell adjacent) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, adjacent.surface());
        int row = adjacent.row();
        int column = adjacent.column();
        switch (adjacent.edge()) {
            case ROW_MIN -> row++;
            case ROW_MAX -> row--;
            case COLUMN_MIN -> column++;
            case COLUMN_MAX -> column--;
        }
        if (row < 0 || row >= face.height() || column < 0 || column >= face.width()) {
            return -1;
        }
        return MudSurfaceLayout.cellIndex(part, adjacent.surface(), row, column);
    }

    private static float stableNoise(MudBodyPart part, MudSurface surface,
            MudSurfaceLayout.Edge edge, int along) {
        int value = part.ordinal() * 73428767
                ^ surface.ordinal() * 9122719
                ^ edge.ordinal() * 42317861
                ^ along * 1274126177;
        value ^= value >>> 13;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        return (value & 0xFFFF) / 65535.0F;
    }

    record Profile(float minimumSourceCoverage, float minimumDifference,
            float firstPixelRetention, float secondPixelChance, float secondPixelRetention) {
        Profile {
            minimumSourceCoverage = Mth.clamp(minimumSourceCoverage, 0.0F, 1.0F);
            minimumDifference = Mth.clamp(minimumDifference, 0.0F, 1.0F);
            firstPixelRetention = Mth.clamp(firstPixelRetention, 0.0F, 1.0F);
            secondPixelChance = Mth.clamp(secondPixelChance, 0.0F, 1.0F);
            secondPixelRetention = Mth.clamp(secondPixelRetention, 0.0F, firstPixelRetention);
        }

        static Profile current() {
            return new Profile(
                    MudPhysicsSettings.coverageEdgeBlendMinimumSource(),
                    MudPhysicsSettings.coverageEdgeBlendMinimumDifference(),
                    MudPhysicsSettings.coverageEdgeBlendFirstRetention(),
                    MudPhysicsSettings.coverageEdgeBlendSecondChance(),
                    MudPhysicsSettings.coverageEdgeBlendSecondRetention());
        }
    }

    private interface Cells {
        float sourceCoverage(int cell);

        float outputCoverage(int cell);

        byte sourceMedium(int cell);

        int sourceAppearance(int cell);

        long sourceVisualSource(int cell);

        void write(int cell, float coverage, byte medium, int appearance,
                long visualSource);
    }

    @FunctionalInterface
    interface CellMediumPredicate {
        boolean test(int cell, byte mediumId);
    }

    private record FloatCells(float[] outputCoverage, byte[] outputMedium,
            int[] outputAppearance, long[] outputVisualSource,
            float[] sourceCoverage, byte[] sourceMedium,
            int[] sourceAppearance, long[] sourceVisualSource) implements Cells {
        private FloatCells(float[] outputCoverage, byte[] outputMedium,
                float[] sourceCoverage, byte[] sourceMedium) {
            this(outputCoverage, outputMedium, null, null,
                    sourceCoverage, sourceMedium, null, null);
        }

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
            return sourceMedium[cell];
        }

        @Override
        public int sourceAppearance(int cell) {
            return sourceAppearance == null
                    ? MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK
                    : sourceAppearance[cell];
        }

        @Override
        public long sourceVisualSource(int cell) {
            return sourceVisualSource == null ? 0L : sourceVisualSource[cell];
        }

        @Override
        public void write(int cell, float coverage, byte medium, int appearance,
                long visualSource) {
            outputCoverage[cell] = Mth.clamp(coverage, 0.0F, 1.0F);
            outputMedium[cell] = medium;
            if (outputAppearance != null) {
                outputAppearance[cell] = appearance;
            }
            if (outputVisualSource != null) {
                outputVisualSource[cell] = visualSource;
            }
        }
    }

    private record ByteCells(byte[] outputCoverage, byte[] outputMedium,
            long[] outputVisualSource, byte[] sourceCoverage, byte[] sourceMedium,
            long[] sourceVisualSource) implements Cells {
        private ByteCells(byte[] outputCoverage, byte[] outputMedium,
                byte[] sourceCoverage, byte[] sourceMedium) {
            this(outputCoverage, outputMedium, null,
                    sourceCoverage, sourceMedium, null);
        }
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
            return sourceMedium[cell];
        }

        @Override
        public int sourceAppearance(int cell) {
            return MudCoverageAppearanceSnapshot.GLOBAL_FALLBACK;
        }

        @Override
        public long sourceVisualSource(int cell) {
            return sourceVisualSource == null ? 0L : sourceVisualSource[cell];
        }

        @Override
        public void write(int cell, float coverage, byte medium, int appearance,
                long visualSource) {
            outputCoverage[cell] = (byte) Mth.clamp(Math.round(coverage * 255.0F), 0, 255);
            outputMedium[cell] = medium;
            if (outputVisualSource != null) {
                outputVisualSource[cell] = visualSource;
            }
        }
    }

    private static final class Scratch {
        private final float[] floatCoverage = new float[MudSurfaceLayout.CELL_COUNT];
        private final byte[] byteCoverage = new byte[MudSurfaceLayout.CELL_COUNT];
        private final byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        private final int[] appearance = new int[MudSurfaceLayout.CELL_COUNT];
        private final long[] visualSource = new long[MudSurfaceLayout.CELL_COUNT];
    }
}
