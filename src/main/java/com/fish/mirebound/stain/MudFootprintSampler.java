package com.fish.mirebound.stain;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageAppearanceSnapshot;
import com.fish.mirebound.mud.MudMediumRuntime;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Reads only the model pixels that can transfer residue from a foot to the ground. */
public final class MudFootprintSampler {
    private static final float SOLE_WEIGHT = 1.0F;
    private static final float LOWER_EDGE_WEIGHT = 0.55F;
    private static final float SECOND_EDGE_WEIGHT = 0.22F;
    private static final float TRANSFER_FLOOR = 0.55F;
    private static final float TRANSFER_RANGE = 1.0F - TRANSFER_FLOOR;

    private MudFootprintSampler() {
    }

    public static Sample sample(MudPlayerData data, MudBodyPart part) {
        return sample(part, data.footprintMediumWeightsScratch(), data, null, null, false);
    }

    public static Sample sample(Level level, MudPlayerData data, MudBodyPart part) {
        return sample(part, data.footprintMediumWeightsScratch(), data, null, level, true);
    }

    public static Sample sample(ArmorMudData data, MudBodyPart part, float[] mediumWeightsScratch) {
        return sample(part, mediumWeightsScratch, null, data, null, false);
    }

    public static Sample sample(Level level, ArmorMudData data, MudBodyPart part,
            float[] mediumWeightsScratch) {
        return sample(part, mediumWeightsScratch, null, data, level, true);
    }

    private static Sample sample(MudBodyPart part, float[] mediumWeights,
            MudPlayerData playerData, ArmorMudData armorData,
            Level level, boolean useVisibleOpacity) {
        if (part != MudBodyPart.LEFT_LEG && part != MudBodyPart.RIGHT_LEG) {
            throw new IllegalArgumentException("Footprint samples require a leg body part");
        }

        Arrays.fill(mediumWeights, 0.0F);
        Accumulator accumulator = new Accumulator(mediumWeights);
        addFace(playerData, armorData, part, MudSurface.BOTTOM, SOLE_WEIGHT, false,
                level, useVisibleOpacity, accumulator);
        addFace(playerData, armorData, part, MudSurface.FRONT, LOWER_EDGE_WEIGHT, true,
                level, useVisibleOpacity, accumulator);
        addFace(playerData, armorData, part, MudSurface.BACK, LOWER_EDGE_WEIGHT, true,
                level, useVisibleOpacity, accumulator);
        addFace(playerData, armorData, part, MudSurface.LEFT, LOWER_EDGE_WEIGHT, true,
                level, useVisibleOpacity, accumulator);
        addFace(playerData, armorData, part, MudSurface.RIGHT, LOWER_EDGE_WEIGHT, true,
                level, useVisibleOpacity, accumulator);

        if (accumulator.maximum <= 0.0F || accumulator.capacity <= 0.0F) {
            return new Sample(0.0F, SinkingMedium.MUD, 0L);
        }

        float average = accumulator.coverage / accumulator.capacity;
        float strength = Mth.clamp(accumulator.maximum * 0.58F + average * 0.72F, 0.0F, 1.0F);
        int bestMedium = 0;
        for (int i = 1; i < mediumWeights.length; i++) {
            if (mediumWeights[i] > mediumWeights[bestMedium]) {
                bestMedium = i;
            }
        }
        return new Sample(strength, SinkingMedium.byId(bestMedium),
                accumulator.visualSource);
    }

    public static void fadeSkinSource(MudPlayerData data, MudBodyPart part, float amount) {
        fadeFace(data, part, MudSurface.BOTTOM, amount, false);
        fadeFace(data, part, MudSurface.FRONT, amount * LOWER_EDGE_WEIGHT, true);
        fadeFace(data, part, MudSurface.BACK, amount * LOWER_EDGE_WEIGHT, true);
        fadeFace(data, part, MudSurface.LEFT, amount * LOWER_EDGE_WEIGHT, true);
        fadeFace(data, part, MudSurface.RIGHT, amount * LOWER_EDGE_WEIGHT, true);
        fadeFaceRow(data, part, MudSurface.FRONT, 1, amount * SECOND_EDGE_WEIGHT);
        fadeFaceRow(data, part, MudSurface.BACK, 1, amount * SECOND_EDGE_WEIGHT);
        fadeFaceRow(data, part, MudSurface.LEFT, 1, amount * SECOND_EDGE_WEIGHT);
        fadeFaceRow(data, part, MudSurface.RIGHT, 1, amount * SECOND_EDGE_WEIGHT);
        data.refreshCoverageAfterSurfaceUpdate();
    }

    public static void fadeArmorSource(ArmorMudData.Builder builder, MudBodyPart part, float amount) {
        fadeFace(builder, part, MudSurface.BOTTOM, amount, false);
        fadeFace(builder, part, MudSurface.FRONT, amount * LOWER_EDGE_WEIGHT, true);
        fadeFace(builder, part, MudSurface.BACK, amount * LOWER_EDGE_WEIGHT, true);
        fadeFace(builder, part, MudSurface.LEFT, amount * LOWER_EDGE_WEIGHT, true);
        fadeFace(builder, part, MudSurface.RIGHT, amount * LOWER_EDGE_WEIGHT, true);
        fadeFaceRow(builder, part, MudSurface.FRONT, 1, amount * SECOND_EDGE_WEIGHT);
        fadeFaceRow(builder, part, MudSurface.BACK, 1, amount * SECOND_EDGE_WEIGHT);
        fadeFaceRow(builder, part, MudSurface.LEFT, 1, amount * SECOND_EDGE_WEIGHT);
        fadeFaceRow(builder, part, MudSurface.RIGHT, 1, amount * SECOND_EDGE_WEIGHT);
    }

    private static void addFace(MudPlayerData playerData, ArmorMudData armorData,
            MudBodyPart part, MudSurface surface, float weight, boolean bottomRowOnly,
            Level level, boolean useVisibleOpacity, Accumulator accumulator) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int rows = bottomRowOnly ? 1 : face.height();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < face.width(); column++) {
                int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                float coverage = armorData == null
                        ? playerData.surfaceCoverage[cell]
                        : armorData.coverageAt(cell);
                accumulator.capacity += weight;
                SinkingMedium medium = armorData == null
                        ? SinkingMedium.byId(playerData.surfaceMedium[cell] & 0xFF)
                        : armorData.mediumAt(cell);
                float opacity = !useVisibleOpacity
                        ? 1.0F
                        : armorData == null
                                ? MudCoverageAppearanceSnapshot.opacity(
                                        playerData.surfaceAppearance[cell], medium)
                                : MudMediumRuntime.coverageOpacity(level, medium);
                float transferable = transferableCoverage(coverage) * opacity;
                accumulator.coverage += transferable * weight;
                accumulator.maximum = Math.max(accumulator.maximum, transferable);
                if (transferable > 0.0F) {
                    float score = transferable * weight;
                    accumulator.mediumWeights[medium.id()] += score;
                    long visualSource = armorData == null
                            ? playerData.surfacePixelVisualSource(
                                    part, surface, row, column)
                            : armorData.visualSourceAt(cell);
                    if (score > accumulator.visualSourceScore) {
                        accumulator.visualSourceScore = score;
                        accumulator.visualSource = visualSource;
                    }
                }
            }
        }
    }

    private static float transferableCoverage(float coverage) {
        return Mth.clamp((coverage - TRANSFER_FLOOR) / TRANSFER_RANGE, 0.0F, 1.0F);
    }

    private static void fadeFace(MudPlayerData data, MudBodyPart part, MudSurface surface,
            float amount, boolean bottomRowOnly) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int rows = bottomRowOnly ? 1 : face.height();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < face.width(); column++) {
                int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
                data.surfaceCoverage[cell] = Math.max(
                        Math.min(data.surfaceCoverage[cell], TRANSFER_FLOOR),
                        data.surfaceCoverage[cell] - naturalFadeAmount(amount, surface, row, column));
            }
        }
    }

    private static void fadeFaceRow(MudPlayerData data, MudBodyPart part, MudSurface surface,
            int row, float amount) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        if (row >= face.height()) {
            return;
        }
        for (int column = 0; column < face.width(); column++) {
            int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
            data.surfaceCoverage[cell] = Math.max(
                    Math.min(data.surfaceCoverage[cell], TRANSFER_FLOOR),
                    data.surfaceCoverage[cell] - naturalFadeAmount(amount, surface, row, column));
        }
    }

    private static void fadeFace(ArmorMudData.Builder builder, MudBodyPart part, MudSurface surface,
            float amount, boolean bottomRowOnly) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        int rows = bottomRowOnly ? 1 : face.height();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < face.width(); column++) {
                builder.fadeToFloor(
                        MudSurfaceLayout.cellIndex(part, surface, row, column),
                        naturalFadeAmount(amount, surface, row, column),
                        TRANSFER_FLOOR);
            }
        }
    }

    private static void fadeFaceRow(ArmorMudData.Builder builder, MudBodyPart part, MudSurface surface,
            int row, float amount) {
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        if (row >= face.height()) {
            return;
        }
        for (int column = 0; column < face.width(); column++) {
            builder.fadeToFloor(
                    MudSurfaceLayout.cellIndex(part, surface, row, column),
                    naturalFadeAmount(amount, surface, row, column),
                    TRANSFER_FLOOR);
        }
    }

    private static float naturalFadeAmount(float amount, MudSurface surface, int row, int column) {
        int pattern = Math.floorMod(surface.ordinal() * 13 + row * 7 + column * 11, 5);
        return amount * (0.82F + pattern * 0.045F);
    }

    public record Sample(float strength, SinkingMedium medium,
            long visualSource) {
    }

    private static final class Accumulator {
        private final float[] mediumWeights;
        private float capacity;
        private float coverage;
        private float maximum;
        private float visualSourceScore;
        private long visualSource;

        private Accumulator(float[] mediumWeights) {
            this.mediumWeights = mediumWeights;
        }
    }
}
