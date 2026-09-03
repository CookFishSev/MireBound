package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

class ArmorMudDataTest {
    @Test
    void sparseCellsKeepIndependentCoverageAndMedium() {
        int headFront = MudSurfaceLayout.cellIndex(MudBodyPart.HEAD, MudSurface.FRONT, 2, 5);
        int rightLegBottom = MudSurfaceLayout.cellIndex(MudBodyPart.RIGHT_LEG, MudSurface.BOTTOM, 1, 3);
        ArmorMudData.Builder builder = ArmorMudData.EMPTY.toBuilder();

        builder.mark(headFront, 0.42F, SinkingMedium.MUD);
        builder.mark(rightLegBottom, 0.87F, SinkingMedium.SOFT_QUICKSAND);
        ArmorMudData data = builder.build();

        Map<Integer, CellValue> cells = collect(data);
        assertEquals(2, data.dirtyCellCount());
        assertEquals(SinkingMedium.MUD, cells.get(headFront).medium());
        assertEquals(0.42F, cells.get(headFront).coverage(), 1.0F / 255.0F);
        assertEquals(SinkingMedium.SOFT_QUICKSAND, cells.get(rightLegBottom).medium());
        assertEquals(0.87F, cells.get(rightLegBottom).coverage(), 1.0F / 255.0F);
        assertEquals(0.87F, data.maximumCoverage(), 1.0F / 255.0F);
    }

    @Test
    void sourceAwareCellsPreserveTheirVisualDescriptor() {
        int adaptiveCell = MudSurfaceLayout.cellIndex(
                MudBodyPart.BODY, MudSurface.FRONT, 5, 3);
        int ordinaryCell = MudSurfaceLayout.cellIndex(
                MudBodyPart.LEFT_ARM, MudSurface.LEFT, 4, 1);
        long visualSource = 0x1234_5678_9ABCDEFL;
        ArmorMudData.Builder builder = ArmorMudData.EMPTY.toBuilder();

        builder.mark(adaptiveCell, 0.73F, SinkingMedium.MIRE, visualSource);
        builder.mark(ordinaryCell, 0.31F, SinkingMedium.MUD);
        ArmorMudData data = builder.build();

        assertEquals(2, data.dirtyCellCount());
        assertEquals(visualSource, data.visualSourceAt(adaptiveCell));
        assertEquals(0L, data.visualSourceAt(ordinaryCell));
        assertEquals(0.73F, data.coverageAt(adaptiveCell), 1.0F / 255.0F);
        assertEquals(SinkingMedium.MUD, data.mediumAt(ordinaryCell));
    }

    @Test
    void repeatedSplashHitsAccumulateOnArmorUpToTheSharedLimit() {
        int cell = MudSurfaceLayout.cellIndex(
                MudBodyPart.BODY, MudSurface.FRONT, 4, 3);
        ArmorMudData current = ArmorMudData.EMPTY;

        for (int hit = 0; hit < 128; hit++) {
            ArmorMudData.Builder builder = current.toBuilder();
            builder.markSplash(cell, 0.42F, SinkingMedium.MUD, 0L);
            current = builder.build();
        }

        assertEquals(Math.round(MudCoverageRules.SPLASH_COVERAGE_MAXIMUM * 255.0F),
                Math.round(current.coverageAt(cell) * 255.0F));
    }

    @Test
    void splashFromAnotherMediumReplacesArmorVisualWithoutLoweringCoverage() {
        int cell = MudSurfaceLayout.cellIndex(
                MudBodyPart.BODY, MudSurface.FRONT, 4, 3);
        ArmorMudData.Builder initial = ArmorMudData.EMPTY.toBuilder();
        initial.mark(cell, 0.85F, SinkingMedium.MUD, 11L);

        ArmorMudData.Builder replacement = initial.build().toBuilder();
        replacement.markSplash(cell, 0.5F, SinkingMedium.SOFT_QUICKSAND, 22L);
        ArmorMudData result = replacement.build();

        assertEquals(Math.round(0.85F * 255.0F),
                Math.round(result.coverageAt(cell) * 255.0F));
        assertEquals(SinkingMedium.SOFT_QUICKSAND, result.mediumAt(cell));
        assertEquals(22L, result.visualSourceAt(cell));
    }

    @Test
    void contaminationPatternSeedPersistsWithTheArmorItem() {
        int cell = MudSurfaceLayout.cellIndex(
                MudBodyPart.BODY, MudSurface.FRONT, 5, 3);
        ArmorMudData.Builder initial = ArmorMudData.EMPTY.toBuilder();
        initial.mark(cell, 0.73F, SinkingMedium.MIRE);
        ArmorMudData first = initial.build();

        ArmorMudData.Builder edited = first.toBuilder();
        edited.mark(cell, 0.91F, SinkingMedium.MIRE);
        ArmorMudData second = edited.build();

        assertNotEquals(0, first.coveragePatternSeed());
        assertEquals(first.coveragePatternSeed(), second.coveragePatternSeed());
    }

    @Test
    void copyingAndEditingOneComponentDoesNotMutateTheOriginal() {
        int cell = MudSurfaceLayout.cellIndex(MudBodyPart.BODY, MudSurface.LEFT, 7, 2);
        ArmorMudData.Builder originalBuilder = ArmorMudData.EMPTY.toBuilder();
        originalBuilder.mark(cell, 0.35F, SinkingMedium.MIRE);
        ArmorMudData original = originalBuilder.build();

        ArmorMudData.Builder copyBuilder = original.toBuilder();
        copyBuilder.mark(cell, 0.90F, SinkingMedium.MIRE);
        ArmorMudData edited = copyBuilder.build();

        assertNotEquals(original, edited);
        assertEquals(0.35F, collect(original).get(cell).coverage(), 1.0F / 255.0F);
        assertEquals(0.90F, collect(edited).get(cell).coverage(), 1.0F / 255.0F);
        assertEquals(SinkingMedium.MIRE, collect(edited).get(cell).medium());
    }

    @Test
    void washingIsMonotonicAndEventuallyRemovesTheComponentPayload() {
        int cell = MudSurfaceLayout.cellIndex(MudBodyPart.LEFT_ARM, MudSurface.TOP, 2, 1);
        ArmorMudData.Builder initial = ArmorMudData.EMPTY.toBuilder();
        initial.mark(cell, 1.0F, SinkingMedium.PEAT_BOG);
        ArmorMudData current = initial.build();
        float previous = 1.0F;

        for (int tick = 0; tick < 500 && !current.isEmpty(); tick++) {
            ArmorMudData.Builder washing = current.toBuilder();
            washing.wash(cell, 0.08F, 0.02F, tick);
            ArmorMudData next = washing.build();
            float coverage = next.isEmpty() ? 0.0F : collect(next).get(cell).coverage();
            assertTrue(coverage <= previous);
            previous = coverage;
            current = next;
        }

        assertTrue(current.isEmpty());
        assertSame(ArmorMudData.EMPTY, current);
    }

    @Test
    void tinyWashAmountsStillClearTheLastCoverageBytes() {
        int cell = MudSurfaceLayout.cellIndex(MudBodyPart.BODY, MudSurface.FRONT, 3, 2);
        ArmorMudData.Builder initial = ArmorMudData.EMPTY.toBuilder();
        initial.mark(cell, 3.0F / 255.0F, SinkingMedium.MUD);
        ArmorMudData current = initial.build();

        for (int tick = 0; tick < 4 && !current.isEmpty(); tick++) {
            ArmorMudData.Builder washing = current.toBuilder();
            assertTrue(washing.wash(cell, 0.00001F, 0.0F, tick));
            current = washing.build();
        }

        assertSame(ArmorMudData.EMPTY, current);
    }

    @Test
    void wallTransferStopsAtConfiguredResidueFloor() {
        int cell = MudSurfaceLayout.cellIndex(MudBodyPart.LEFT_LEG, MudSurface.LEFT, 4, 1);
        ArmorMudData.Builder initial = ArmorMudData.EMPTY.toBuilder();
        initial.mark(cell, 0.80F, SinkingMedium.MIRE);
        ArmorMudData original = initial.build();

        ArmorMudData.Builder transfer = original.toBuilder();
        assertTrue(transfer.fadeToFloor(cell, 0.30F, 0.35F));
        ArmorMudData once = transfer.build();
        assertEquals(0.50F, once.coverageAt(cell), 1.0F / 255.0F);

        ArmorMudData.Builder secondTransfer = once.toBuilder();
        assertTrue(secondTransfer.fadeToFloor(cell, 0.30F, 0.35F));
        ArmorMudData atFloor = secondTransfer.build();
        assertEquals(0.35F, atFloor.coverageAt(cell), 1.0F / 255.0F);
        assertFalse(atFloor.toBuilder().fadeToFloor(cell, 0.30F, 0.35F));
    }

    @Test
    void emptyDataHasNoMarkerInformation() {
        assertTrue(ArmorMudData.EMPTY.isEmpty());
        assertEquals(0, ArmorMudData.EMPTY.dirtyCellCount());
        assertEquals(0.0F, ArmorMudData.EMPTY.averageCoverage());
        assertEquals(0.0F, ArmorMudData.EMPTY.maximumCoverage());
        assertFalse(ArmorMudData.EMPTY.toBuilder().changed());
    }

    @Test
    void displayedCoverageUsesTheWholeOwnedArmorSurfaceAsItsDenominator() {
        int headCell = MudSurfaceLayout.cellIndex(MudBodyPart.HEAD, MudSurface.FRONT, 2, 5);
        ArmorMudData.Builder builder = ArmorMudData.EMPTY.toBuilder();
        builder.mark(headCell, 1.0F, SinkingMedium.MUD);

        float fraction = ArmorMudManager.coverageFraction(builder.build(), EquipmentSlot.HEAD);

        assertEquals(1.0F / ArmorMudManager.ownedCellCount(EquipmentSlot.HEAD), fraction, 1.0E-6F);
        assertTrue(fraction < 0.01F);
    }

    @Test
    void fullyCoveredSlotReportsOneHundredPercent() {
        ArmorMudData.Builder builder = ArmorMudData.EMPTY.toBuilder();
        for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
            MudBodyPart part = MudSurfaceLayout.part(cell);
            MudSurface surface = MudSurfaceLayout.surface(cell);
            int row = MudSurfaceLayout.row(cell);
            if (ArmorMudManager.slotOwnsSurface(EquipmentSlot.FEET, part, surface, row)) {
                builder.mark(cell, 1.0F, SinkingMedium.MUD);
            }
        }

        assertEquals(1.0F, ArmorMudManager.coverageFraction(builder.build(), EquipmentSlot.FEET), 1.0E-6F);
    }

    @Test
    void bootsOwnAFullSixPixelHighCuffWithoutTakingTheLegTop() {
        assertTrue(ArmorMudManager.slotOwnsSurface(
                EquipmentSlot.FEET, MudBodyPart.LEFT_LEG, MudSurface.FRONT,
                ArmorMudManager.BOOT_SIDE_ROWS - 1));
        assertFalse(ArmorMudManager.slotOwnsSurface(
                EquipmentSlot.FEET, MudBodyPart.LEFT_LEG, MudSurface.FRONT,
                ArmorMudManager.BOOT_SIDE_ROWS));
        assertTrue(ArmorMudManager.slotOwnsSurface(
                EquipmentSlot.FEET, MudBodyPart.LEFT_LEG, MudSurface.BOTTOM, 0));
        assertFalse(ArmorMudManager.slotOwnsSurface(
                EquipmentSlot.FEET, MudBodyPart.LEFT_LEG, MudSurface.TOP, 0));
    }

    @Test
    void overlappingBootAndLeggingContactsRemainIndependent() {
        int cell = MudSurfaceLayout.cellIndex(MudBodyPart.LEFT_LEG, MudSurface.FRONT, 2, 1);
        MudPlayerData playerData = new MudPlayerData();

        playerData.setArmorContact(EquipmentSlot.FEET, cell, 0.82F, SinkingMedium.MUD);
        playerData.setArmorContact(EquipmentSlot.LEGS, cell, 0.47F, SinkingMedium.SOFT_QUICKSAND);

        assertEquals(Math.round(0.82F * 255.0F), playerData.armorContactCoverage(EquipmentSlot.FEET, cell));
        assertEquals(Math.round(0.47F * 255.0F), playerData.armorContactCoverage(EquipmentSlot.LEGS, cell));
        assertEquals(SinkingMedium.MUD, playerData.armorContactMedium(EquipmentSlot.FEET, cell));
        assertEquals(SinkingMedium.SOFT_QUICKSAND, playerData.armorContactMedium(EquipmentSlot.LEGS, cell));

        playerData.clearArmorContacts();
        assertEquals(0, playerData.armorContactCoverage(EquipmentSlot.FEET, cell));
        assertEquals(0, playerData.armorContactCoverage(EquipmentSlot.LEGS, cell));
    }

    private static Map<Integer, CellValue> collect(ArmorMudData data) {
        Map<Integer, CellValue> cells = new HashMap<>();
        data.forEach((cell, coverage, medium) -> cells.put(cell, new CellValue(coverage, medium)));
        return cells;
    }

    private record CellValue(float coverage, SinkingMedium medium) {
    }
}
