package com.fish.mirebound.generation;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Complete client intent shared by preview construction and server validation. */
public record MudTerrainGenerationRequest(
        MudTerrainGenerationType type,
        BlockPos center,
        boolean spatialPlacement,
        MudTerrainGenerationSettings depositSettings,
        MudTerrainLakeSettings lakeSettings,
        MudTerrainRotation rotation,
        boolean wireValuesAccepted) {
    public MudTerrainGenerationRequest(
            MudTerrainGenerationType type,
            BlockPos center,
            boolean spatialPlacement,
            MudTerrainGenerationSettings depositSettings,
            MudTerrainLakeSettings lakeSettings) {
        this(type, center, spatialPlacement, depositSettings, lakeSettings,
                MudTerrainRotation.IDENTITY, true);
    }

    public MudTerrainGenerationRequest(
            MudTerrainGenerationType type,
            BlockPos center,
            boolean spatialPlacement,
            MudTerrainGenerationSettings depositSettings,
            MudTerrainLakeSettings lakeSettings,
            boolean wireValuesAccepted) {
        this(type, center, spatialPlacement, depositSettings, lakeSettings,
                MudTerrainRotation.IDENTITY, wireValuesAccepted);
    }

    public MudTerrainGenerationRequest(
            MudTerrainGenerationType type,
            BlockPos center,
            boolean spatialPlacement,
            MudTerrainGenerationSettings depositSettings,
            MudTerrainLakeSettings lakeSettings,
            MudTerrainRotation rotation) {
        this(type, center, spatialPlacement, depositSettings, lakeSettings,
                rotation, true);
    }

    public MudTerrainGenerationRequest {
        type = Objects.requireNonNullElse(
                type, MudTerrainGenerationType.defaultType());
        center = Objects.requireNonNullElse(center, BlockPos.ZERO).immutable();
        depositSettings = Objects.requireNonNull(depositSettings);
        lakeSettings = Objects.requireNonNull(lakeSettings);
        rotation = Objects.requireNonNullElse(rotation, MudTerrainRotation.IDENTITY);
    }

    public boolean validWireValues() {
        return wireValuesAccepted
                && MudTerrainGenerationSettings.validWireValues(
                depositSettings.radius(), depositSettings.thickness(),
                depositSettings.edgeRoughness(),
                depositSettings.heightTolerance(), depositSettings.seed())
                && MudTerrainLakeSettings.validWireValues(
                        lakeSettings.horizontalRadius(),
                        lakeSettings.verticalRadius(), lakeSettings.seed(),
                        lakeSettings.shellBlockId(), lakeSettings.innerBlockId(),
                        lakeSettings.surfaceHeightPixels())
                && rotation.valid();
    }
}
