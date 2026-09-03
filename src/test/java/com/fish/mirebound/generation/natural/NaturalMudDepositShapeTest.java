package com.fish.mirebound.generation.natural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.generation.natural.NaturalMudDepositShape.Cell;
import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile.Rule;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NaturalMudDepositShapeTest {
    @Test
    void sameSeedProducesSameShape() {
        for (NaturalMudDepositForm form : NaturalMudDepositForm.values()) {
            assertEquals(
                    NaturalMudDepositShape.build(form, 0x51A7C0DEL, 8),
                    NaturalMudDepositShape.build(form, 0x51A7C0DEL, 8));
        }
    }

    @Test
    void everyFormIsBoundedUniqueAndNonEmpty() {
        for (NaturalMudDepositForm form : NaturalMudDepositForm.values()) {
            List<Cell> cells = NaturalMudDepositShape.build(form, 917_241L, 12);
            assertFalse(cells.isEmpty(), form.name());
            assertEquals(cells.size(), new HashSet<>(cells).size(), form.name());
            for (Cell cell : cells) {
                assertTrue(Math.abs(cell.dx()) <= 24, form + " x=" + cell.dx());
                assertTrue(Math.abs(cell.dz()) <= 24, form + " z=" + cell.dz());
                assertTrue(cell.strength() >= 0.15D && cell.strength() <= 1.0D,
                        form + " strength=" + cell.strength());
            }
        }
    }

    @Test
    void formsHaveGenuinelyDifferentSilhouettes() {
        Set<Set<CellCoordinate>> silhouettes = new HashSet<>();
        for (NaturalMudDepositForm form : NaturalMudDepositForm.values()) {
            Set<CellCoordinate> silhouette = new HashSet<>();
            for (Cell cell : NaturalMudDepositShape.build(form, 44_189L, 8)) {
                silhouette.add(new CellCoordinate(cell.dx(), cell.dz()));
            }
            silhouettes.add(Set.copyOf(silhouette));
        }
        assertTrue(silhouettes.size() >= 6,
                "natural generation needs at least six distinct silhouettes");
    }

    @Test
    void requestedRadiusIsClampedToSupportedRange() {
        assertEquals(
                NaturalMudDepositShape.build(
                        NaturalMudDepositForm.MARSH_MOSAIC, 73L, 2),
                NaturalMudDepositShape.build(
                        NaturalMudDepositForm.MARSH_MOSAIC, 73L, -20));
        assertEquals(
                NaturalMudDepositShape.build(
                        NaturalMudDepositForm.MARSH_MOSAIC, 73L, 12),
                NaturalMudDepositShape.build(
                        NaturalMudDepositForm.MARSH_MOSAIC, 73L, 200));
    }

    @Test
    void previewAndPlacementShareTheSameColumnDepthRule() {
        Rule rule = NaturalMudGenerationProfile.defaults()
                .rule(SinkingMedium.MIRE);

        assertEquals(rule.minimumDepth(), NaturalMudDepositShape.columnDepth(
                rule, new Cell(0, 0, 0.15D)));
        assertEquals(rule.maximumDepth(), NaturalMudDepositShape.columnDepth(
                rule, new Cell(0, 0, 1.0D)));
    }

    @Test
    void wandShapesRetainTheWandsLargerRadius() {
        List<Cell> cells = NaturalMudDepositShape.buildForWand(
                NaturalMudDepositForm.RIVERBED_RIBBON, 8127L, 30);

        assertTrue(cells.stream().anyMatch(cell ->
                Math.abs(cell.dx()) > 12 || Math.abs(cell.dz()) > 12));
    }

    private record CellCoordinate(int x, int z) {
    }
}
