package com.fish.mirebound.client;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.util.Mth;

/** One-model-pixel, non-recursive armor fringe shared by canonical and projected armor textures. */
final class ArmorMudPaintPlan {
    private static final float MIN_COVERAGE = 0.001F;
    private final float[] coverage = new float[MudSurfaceLayout.CELL_COUNT];
    private final byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
    private final long[] visualSource = new long[MudSurfaceLayout.CELL_COUNT];

    private ArmorMudPaintPlan(ArmorMudData data, MudBodyPart targetPart) {
        data.forEachVisual((cell, strength, sinkingMedium, sourceVisual) -> {
            if (MudSurfaceLayout.part(cell) == targetPart) {
                coverage[cell] = strength;
                medium[cell] = (byte) sinkingMedium.id();
                visualSource[cell] = sourceVisual;
            }
        });
        float[] sourceCoverage = coverage.clone();
        byte[] sourceMedium = medium.clone();
        long[] sourceVisualSource = visualSource.clone();
        for (MudSurface surface : MudSurface.values()) {
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(targetPart, surface);
            for (int row = 0; row < face.height(); row++) {
                for (int column = 0; column < face.width(); column++) {
                    int source = MudSurfaceLayout.cellIndex(targetPart, surface, row, column);
                    if (sourceCoverage[source] <= MIN_COVERAGE) {
                        continue;
                    }
                    offer(sourceCoverage, sourceMedium, sourceVisualSource, source, targetPart, surface, row - 1, column, 0, false);
                    offer(sourceCoverage, sourceMedium, sourceVisualSource, source, targetPart, surface, row + 1, column, 1, false);
                    offer(sourceCoverage, sourceMedium, sourceVisualSource, source, targetPart, surface, row, column - 1, 2, false);
                    offer(sourceCoverage, sourceMedium, sourceVisualSource, source, targetPart, surface, row, column + 1, 3, false);
                    for (MudSurfaceLayout.Edge edge : MudSurfaceLayout.Edge.values()) {
                        boolean onEdge = switch (edge) {
                            case ROW_MIN -> row == 0;
                            case ROW_MAX -> row == face.height() - 1;
                            case COLUMN_MIN -> column == 0;
                            case COLUMN_MAX -> column == face.width() - 1;
                        };
                        if (!onEdge) {
                            continue;
                        }
                        MudSurfaceLayout.AdjacentCell adjacent = MudSurfaceLayout.neighborAcrossEdge(
                                targetPart, surface, row, column, edge);
                        offer(sourceCoverage, sourceMedium, sourceVisualSource, source, targetPart, adjacent.surface(),
                                adjacent.row(), adjacent.column(), 4 + edge.ordinal(), true);
                    }
                }
            }
        }
    }

    static ArmorMudPaintPlan build(ArmorMudData data, MudBodyPart targetPart) {
        return new ArmorMudPaintPlan(data, targetPart);
    }

    float coverage(int cell) {
        return coverage[cell];
    }

    SinkingMedium medium(int cell) {
        return SinkingMedium.byId(medium[cell] & 0xFF);
    }

    long visualSource(int cell) {
        return visualSource[cell];
    }

    private void offer(float[] sourceCoverage, byte[] sourceMedium,
            long[] sourceVisualSource, int source, MudBodyPart part,
            MudSurface surface, int row, int column, int direction, boolean crossFace) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        if (row < 0 || column < 0 || row >= face.height() || column >= face.width()) {
            return;
        }
        int target = MudSurfaceLayout.cellIndex(part, surface, row, column);
        if (sourceCoverage[target] > MIN_COVERAGE) {
            return;
        }
        SinkingMedium sinkingMedium = SinkingMedium.byId(sourceMedium[source] & 0xFF);
        float chance = sinkingMedium.opaqueCoverage() ? 0.64F : 0.48F;
        if (crossFace) {
            chance *= 0.82F;
        }
        float noise = noise(target, source * 31 + direction * 101);
        if (noise > chance) {
            return;
        }
        float scale = sinkingMedium.opaqueCoverage() ? 0.28F : 0.20F;
        if (crossFace) {
            scale *= 0.86F;
        }
        float strength = Mth.clamp(sourceCoverage[source] * scale, 0.0F, 0.42F);
        if (strength > coverage[target]) {
            coverage[target] = strength;
            medium[target] = sourceMedium[source];
            visualSource[target] = sourceVisualSource[source];
        }
    }

    private static float noise(int cell, int salt) {
        int value = cell * 73428767 ^ salt * 9122719;
        value ^= value >>> 13;
        value *= 1274126177;
        value ^= value >>> 16;
        return (value & 1023) / 1023.0F;
    }
}
