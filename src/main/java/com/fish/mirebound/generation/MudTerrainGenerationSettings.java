package com.fish.mirebound.generation;

import net.minecraft.util.Mth;

/** Parameters shared by terrain preview and server generation. */
public record MudTerrainGenerationSettings(
        int radius,
        int thickness,
        double edgeRoughness,
        int heightTolerance,
        int seed,
        boolean sameSourceOnly) {
    public static final int MINIMUM_RADIUS = 2;
    public static final int MAXIMUM_RADIUS = 48;
    public static final int MINIMUM_THICKNESS = 1;
    public static final int MAXIMUM_THICKNESS = 8;
    public static final int MAXIMUM_HEIGHT_TOLERANCE = 24;

    public MudTerrainGenerationSettings {
        radius = Mth.clamp(radius, MINIMUM_RADIUS, MAXIMUM_RADIUS);
        thickness = Mth.clamp(
                thickness, MINIMUM_THICKNESS, MAXIMUM_THICKNESS);
        edgeRoughness = Mth.clamp(edgeRoughness, 0.0D, 1.0D);
        heightTolerance = Mth.clamp(
                heightTolerance, 0, MAXIMUM_HEIGHT_TOLERANCE);
        seed = Math.max(0, seed);
    }

    public static boolean validWireValues(
            int radius, int thickness, double edgeRoughness,
            int heightTolerance, int seed) {
        return radius >= MINIMUM_RADIUS && radius <= MAXIMUM_RADIUS
                && thickness >= MINIMUM_THICKNESS
                && thickness <= MAXIMUM_THICKNESS
                && Double.isFinite(edgeRoughness)
                && edgeRoughness >= 0.0D && edgeRoughness <= 1.0D
                && heightTolerance >= 0
                && heightTolerance <= MAXIMUM_HEIGHT_TOLERANCE
                && seed >= 0;
    }

    public int diameter() {
        return radius * 2 + 1;
    }

    public int columnCount() {
        return diameter() * diameter();
    }
}
