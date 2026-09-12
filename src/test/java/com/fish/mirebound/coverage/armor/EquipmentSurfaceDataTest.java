package com.fish.mirebound.coverage.armor;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class EquipmentSurfaceDataTest {
    @Test
    void percentageUsesCleanSurfaceAsWellAsStainedCells() {
        var builder = EquipmentSurfaceData.EMPTY.toBuilder();
        builder.surfaceCells(100);
        builder.stain(1, 0, 255, 0, 0);
        builder.stain(1, 1, 255, 0, 0);
        assertEquals(.02F, builder.build().coverageFraction(), 1e-6);
        builder.wash(1, 0, 128);
        assertEquals((255 + 127) / 25500F, builder.build().coverageFraction(), 1e-6);
        builder.wash(1, 0, 255);
        builder.wash(1, 1, 255);
        assertEquals(0F, builder.build().coverageFraction());
        assertTrue(builder.build().isEmpty());
    }

    @Test
    void denominatorSurvivesSavingAndWashing() {
        var original = new EquipmentSurfaceData(List.of(new EquipmentSurfaceData.Cell(42, 0, 255, 0, 19)), 100);
        var json = EquipmentSurfaceData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var loaded = EquipmentSurfaceData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(original, loaded);
        assertEquals(.01F, loaded.coverageFraction(), 1e-6);
        var builder = loaded.toBuilder();
        builder.wash(42, 0, 127);
        assertEquals(100, builder.build().surfaceCells());
        assertTrue(builder.build().coverageFraction() < loaded.coverageFraction());
    }

    @Test
    void previousSavedListsStillLoad() {
        var json = JsonParser.parseString("[{\"face\":42,\"cell\":0,\"strength\":230,\"medium\":0,\"source\":19}]");
        var restored = EquipmentSurfaceData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(230, restored.cell(42, 0).strength());
        assertEquals(19, restored.cell(42, 0).source());
        var builder = restored.toBuilder();
        builder.surfaceCells(100);
        assertEquals(230 / 25500F, builder.build().coverageFraction(), 1e-6);
    }
}
