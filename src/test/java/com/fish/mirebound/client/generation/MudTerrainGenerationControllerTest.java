package com.fish.mirebound.client.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fish.mirebound.generation.MudTerrainGenerationSettings;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MudTerrainGenerationControllerTest {
    @Test
    void lakeVolumeShortcutGrowsVerticallyEverySecondStep() {
        var first = MudTerrainGenerationController.adjustedSize(
                MudTerrainGenerationType.LAKE_POOL, 12, 8, 4, 1);
        var second = MudTerrainGenerationController.adjustedSize(
                MudTerrainGenerationType.LAKE_SURFACE, 12,
                first.lakeHorizontalRadius(), first.lakeVerticalRadius(), 1);

        assertEquals(9, first.lakeHorizontalRadius());
        assertEquals(4, first.lakeVerticalRadius());
        assertEquals(10, second.lakeHorizontalRadius());
        assertEquals(5, second.lakeVerticalRadius());
    }

    @Test
    void generationVolumeShortcutHonorsBothTypeLimits() {
        var deposit = MudTerrainGenerationController.adjustedSize(
                MudTerrainGenerationType.SURFACE_DEPOSIT,
                MudTerrainGenerationSettings.MAXIMUM_RADIUS,
                8, 4, 1);
        var lake = MudTerrainGenerationController.adjustedSize(
                MudTerrainGenerationType.LAKE_POOL, 12,
                MudTerrainLakeSettings.MINIMUM_HORIZONTAL_RADIUS,
                MudTerrainLakeSettings.MINIMUM_VERTICAL_RADIUS, -1);

        assertEquals(MudTerrainGenerationSettings.MAXIMUM_RADIUS,
                deposit.depositRadius());
        assertEquals(MudTerrainLakeSettings.MINIMUM_HORIZONTAL_RADIUS,
                lake.lakeHorizontalRadius());
        assertEquals(MudTerrainLakeSettings.MINIMUM_VERTICAL_RADIUS,
                lake.lakeVerticalRadius());
    }

    @Test
    void naturalShapeShortcutAdjustsDepositRadius() {
        var adjusted = MudTerrainGenerationController.adjustedSize(
                MudTerrainGenerationType.RIVERBED_RIBBON,
                12, 8, 4, 1);

        assertEquals(13, adjusted.depositRadius());
        assertEquals(8, adjusted.lakeHorizontalRadius());
        assertEquals(4, adjusted.lakeVerticalRadius());
    }

    @Test
    void rerolledSeedNeverRepeatsTheCurrentSeed() {
        assertNotEquals(92821,
                MudTerrainGenerationController.nextSeed(92821, 92821));
        assertEquals(0, MudTerrainGenerationController.nextSeed(
                2_000_000_000, 2_000_000_000));
    }

    @Test
    void centerLockTogglesBetweenTargetAndFollowingMode() {
        BlockPos target = new BlockPos(12, 63, -9);
        BlockPos locked = MudTerrainGenerationController.nextLockedCenter(
                null, target);

        assertEquals(target, locked);
        assertNull(MudTerrainGenerationController.nextLockedCenter(
                locked, new BlockPos(20, 70, 4)));
    }

    @Test
    void rotationAxisCyclesInWorldAxisOrder() {
        assertEquals(Direction.Axis.Y,
                MudTerrainGenerationController.nextRotationAxis(
                        Direction.Axis.X));
        assertEquals(Direction.Axis.Z,
                MudTerrainGenerationController.nextRotationAxis(
                        Direction.Axis.Y));
        assertEquals(Direction.Axis.X,
                MudTerrainGenerationController.nextRotationAxis(
                        Direction.Axis.Z));
    }
}
