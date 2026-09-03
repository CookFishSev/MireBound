package com.fish.mirebound.generation;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Parameters for a deterministic, open lake-style mud basin. */
public record MudTerrainLakeSettings(
        int horizontalRadius,
        int verticalRadius,
        int seed,
        ResourceLocation shellBlockId,
        ResourceLocation innerBlockId,
        int surfaceHeightPixels,
        boolean clearUpperCavity) {
    public static final int MINIMUM_HORIZONTAL_RADIUS = 2;
    public static final int MAXIMUM_HORIZONTAL_RADIUS = 24;
    public static final int MINIMUM_VERTICAL_RADIUS = 1;
    public static final int MAXIMUM_VERTICAL_RADIUS = 12;
    public static final int MINIMUM_SURFACE_HEIGHT_PIXELS = 1;
    public static final int MAXIMUM_SURFACE_HEIGHT_PIXELS = 16;
    public static final int DEFAULT_SURFACE_HEIGHT_PIXELS = 14;
    public static final ResourceLocation AIR =
            ResourceLocation.withDefaultNamespace("air");

    public MudTerrainLakeSettings {
        horizontalRadius = Mth.clamp(
                horizontalRadius,
                MINIMUM_HORIZONTAL_RADIUS, MAXIMUM_HORIZONTAL_RADIUS);
        verticalRadius = Mth.clamp(
                verticalRadius,
                MINIMUM_VERTICAL_RADIUS, MAXIMUM_VERTICAL_RADIUS);
        seed = Math.max(0, seed);
        shellBlockId = Objects.requireNonNullElse(shellBlockId, AIR);
        innerBlockId = Objects.requireNonNullElse(innerBlockId, AIR);
        surfaceHeightPixels = Mth.clamp(
                surfaceHeightPixels,
                MINIMUM_SURFACE_HEIGHT_PIXELS, MAXIMUM_SURFACE_HEIGHT_PIXELS);
        // Retained in the wire format for compatibility; lake cavities are now intrinsic.
        clearUpperCavity = true;
    }

    public MudTerrainLakeSettings(
            int horizontalRadius, int verticalRadius, int seed,
            ResourceLocation shellBlockId, ResourceLocation innerBlockId,
            boolean clearUpperCavity) {
        this(horizontalRadius, verticalRadius, seed,
                shellBlockId, innerBlockId, DEFAULT_SURFACE_HEIGHT_PIXELS,
                clearUpperCavity);
    }

    public MudTerrainLakeSettings(
            int horizontalRadius, int verticalRadius, int seed,
            ResourceLocation shellBlockId, ResourceLocation innerBlockId) {
        this(horizontalRadius, verticalRadius, seed,
                shellBlockId, innerBlockId, DEFAULT_SURFACE_HEIGHT_PIXELS, true);
    }

    public static boolean validWireValues(
            int horizontalRadius, int verticalRadius, int seed,
            ResourceLocation shellBlockId, ResourceLocation innerBlockId,
            int surfaceHeightPixels) {
        return horizontalRadius >= MINIMUM_HORIZONTAL_RADIUS
                && horizontalRadius <= MAXIMUM_HORIZONTAL_RADIUS
                && verticalRadius >= MINIMUM_VERTICAL_RADIUS
                && verticalRadius <= MAXIMUM_VERTICAL_RADIUS
                && seed >= 0
                && shellBlockId != null
                && innerBlockId != null
                && surfaceHeightPixels >= MINIMUM_SURFACE_HEIGHT_PIXELS
                && surfaceHeightPixels <= MAXIMUM_SURFACE_HEIGHT_PIXELS;
    }
}
