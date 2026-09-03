package com.fish.mirebound.assimilation;

import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.util.Mth;

/** Stable multi-patch growth field used by skin and armor assimilation. */
public final class AssimilationCoveragePattern {
    private static final float FADE_SPAN = 0.16F;
    private static final float LATEST_START = 1.0F - FADE_SPAN;
    private static final float MAX_DISTANCE_DELAY = 0.72F;
    private static final float MAX_NEIGHBOR_DELAY = 0.09F;

    private AssimilationCoveragePattern() {
    }

    public static float[] buildThresholds(int patternSeed) {
        int count = MudSurfaceLayout.CELL_COUNT;
        float[] raw = new float[count];
        Integer[] order = new Integer[count];
        for (int cell = 0; cell < count; cell++) {
            raw[cell] = activationThreshold(patternSeed, cell);
            order[cell] = cell;
        }
        Arrays.sort(order, Comparator
                .comparingDouble((Integer cell) -> raw[cell])
                .thenComparingInt(cell -> mix(patternSeed ^ cell * 0x6D2B79F5)));
        float[] thresholds = new float[count];
        for (int rank = 0; rank < count; rank++) {
            thresholds[order[rank]] = rank / (float) Math.max(1, count - 1) * LATEST_START;
        }
        return limitLocalGradient(thresholds);
    }

    public static float strength(float progress, float activationThreshold) {
        float amount = Mth.clamp(
                (progress - activationThreshold) / FADE_SPAN, 0.0F, 1.0F);
        return amount * amount * (3.0F - amount * 2.0F);
    }

    static float activationThreshold(int patternSeed, int cell) {
        MudBodyPart part = MudSurfaceLayout.part(cell);
        MudSurface surface = MudSurfaceLayout.surface(cell);
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int row = MudSurfaceLayout.row(cell);
        int column = MudSurfaceLayout.column(cell);
        int seedCount = face.cellCount() >= 64 ? 9 : face.cellCount() >= 32 ? 7 : 5;
        float diagonal = (float) Math.sqrt(
                square(Math.max(1, face.width() - 1))
                        + square(Math.max(1, face.height() - 1)));
        float distanceDelay = Math.min(MAX_DISTANCE_DELAY,
                diagonal * MAX_NEIGHBOR_DELAY);
        int faceSalt = patternSeed
                ^ part.ordinal() * 0x632BE5AB
                ^ surface.ordinal() * 0x85157AF5;
        float earliest = LATEST_START;
        for (int seed = 0; seed < seedCount; seed++) {
            int seedSalt = mix(faceSalt ^ seed * 0x9E3779B9);
            float seedColumn = unit(mix(seedSalt ^ 0x68BC21EB))
                    * Math.max(1, face.width() - 1);
            float seedRow = unit(mix(seedSalt ^ 0x02E5BE93))
                    * Math.max(1, face.height() - 1);
            float ignitionNoise = unit(mix(seedSalt ^ 0x967A889B));
            float ignition;
            if (seed == 0) {
                // Every face receives one early patch; the other patches are stratified
                // across the full buildup instead of letting a whole face start late.
                ignition = ignitionNoise * 0.055F;
            } else {
                float phase = seed / (float) Math.max(1, seedCount - 1);
                float phaseJitter = (ignitionNoise - 0.5F) * (0.10F / seedCount);
                ignition = 0.08F + Mth.clamp(phase + phaseJitter, 0.0F, 1.0F) * 0.68F;
            }
            float distance = (float) Math.sqrt(
                    square(column - seedColumn) + square(row - seedRow));
            earliest = Math.min(earliest,
                    ignition + distance / Math.max(1.0F, diagonal) * distanceDelay);
        }
        float roughEdge = (unit(mix(faceSalt ^ cell * 0x27D4EB2D)) - 0.5F) * 0.018F;
        return Mth.clamp(earliest + roughEdge, 0.0F, LATEST_START);
    }

    private static float square(float value) {
        return value * value;
    }

    private static float[] limitLocalGradient(float[] source) {
        float[] result = source.clone();
        // CDF calibration fixes the global painted percentage. Pairwise projection
        // keeps that mean intact while restoring the source field's soft local edges.
        for (int pass = 0; pass < 24; pass++) {
            for (MudBodyPart part : MudBodyPart.values()) {
                for (MudSurface surface : MudSurface.values()) {
                    MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
                    for (int row = 0; row < face.height(); row++) {
                        for (int column = 0; column < face.width(); column++) {
                            int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                            if (row + 1 < face.height()) {
                                limitPair(result, cell, MudSurfaceLayout.cellIndex(
                                        part, surface, row + 1, column));
                            }
                            if (column + 1 < face.width()) {
                                limitPair(result, cell, MudSurfaceLayout.cellIndex(
                                        part, surface, row, column + 1));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void limitPair(float[] values, int first, int second) {
        float delta = values[second] - values[first];
        float maximum = 0.118F;
        if (Math.abs(delta) <= maximum) {
            return;
        }
        float correction = (Math.abs(delta) - maximum) * 0.5F;
        float direction = Math.signum(delta);
        values[first] += correction * direction;
        values[second] -= correction * direction;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private static float unit(int value) {
        return (value & 0xFFFF) / 65535.0F;
    }
}
