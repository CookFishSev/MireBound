package com.fish.mirebound.coverage.armor;

import static org.junit.jupiter.api.Assertions.*;

import com.fish.mirebound.network.payload.EquipmentSurfaceContactPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EquipmentSurfacePatchTest {
    static EquipmentSurfacePatch face(long id, double z) {
        List<Integer> cells = new ArrayList<>();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            cells.add(EquipmentSurfacePatch.probe(y * 16 + x, .5, .5));
        return new EquipmentSurfacePatch(id, 16, 16, new Vec3(-.5, 0, z),
                new Vec3(.5, 0, z), new Vec3(.5, 1, z), new Vec3(-.5, 1, z), cells);
    }

    @Test
    void aSinglePassVisitsEveryVisibleCellBeyondTheOld256PointLimit() {
        List<EquipmentSurfacePatch> faces = List.of(face(1, -.3), face(2, -.5), face(3, .3), face(4, .5));
        var payload = new EquipmentSurfaceContactPayload(EquipmentSurfaceTarget.backpack(),
                ResourceLocation.parse("test:backpack"), Vec3.ZERO, List.of(), faces, true);
        HashSet<String> visited = new HashSet<>();
        EquipmentSurfaceService.visitSamples(payload, (id, cell, point) -> visited.add(id + ":" + cell));
        assertEquals(1024, visited.size());
        for (var face : faces) for (int probe : face.probes())
            assertTrue(visited.contains(face.face() + ":" + EquipmentSurfacePatch.cell(probe)));
    }

    @Test
    void hiddenPixelsAreAbsentAndAnOpaqueSubpixelKeepsItsActualLocation() {
        int probe = EquipmentSurfacePatch.probe(0, .2, .75);
        var patch = new EquipmentSurfacePatch(1, 16, 16, Vec3.ZERO, new Vec3(1, 0, 0),
                new Vec3(1, 1, 0), new Vec3(0, 1, 0), List.of(probe));
        assertEquals(1, patch.probes().size());
        assertEquals(.2 / 16, patch.point(probe).x, 1.0 / 4096);
        assertEquals(.75 / 16, patch.point(probe).y, 1.0 / 4096);
    }

    @Test
    void skewedPosesAndMirroredFacesInterpolateTheRenderedSurface() {
        var patch = new EquipmentSurfacePatch(1, 1, 1, new Vec3(1, 0, 1), new Vec3(0, 0, 1),
                new Vec3(0, 1, 0), new Vec3(1, 1, 0), List.of(EquipmentSurfacePatch.probe(0, .5, .5)));
        Vec3 middle = patch.point(patch.probes().getFirst());
        assertEquals(.5, middle.x, .002);
        assertEquals(.5, middle.y, .002);
        assertEquals(.5, middle.z, .002);
    }

    @Test
    void invalidOrDuplicateCellsCannotExpandTheSurfaceBudget() {
        var face = face(1, 0);
        assertThrows(IllegalArgumentException.class, () -> new EquipmentSurfacePatch(1, 1, 1,
                face.a(), face.b(), face.c(), face.d(), List.of(EquipmentSurfacePatch.probe(17, .5, .5))));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentSurfacePatch(1, 16, 16,
                face.a(), face.b(), face.c(), face.d(), List.of(0, 0)));
        assertFalse(EquipmentSurfacePatch.validCorner(new Vec3(Double.NaN, 0, 0)));
        assertFalse(EquipmentSurfacePatch.validCorner(new Vec3(5, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentSurfaceContactPayload(
                EquipmentSurfaceTarget.backpack(), ResourceLocation.parse("test:backpack"), Vec3.ZERO,
                List.of(), List.of(face, face), true));
    }

    @Test
    void completeSamplingWashesOnlyTheWetHalfWithoutWaitingForAnotherRandomBatch() {
        var patch = face(1, 0);
        var payload = new EquipmentSurfaceContactPayload(EquipmentSurfaceTarget.backpack(),
                ResourceLocation.parse("test:backpack"), Vec3.ZERO, List.of(), List.of(patch), true);
        var builder = EquipmentSurfaceData.EMPTY.toBuilder();
        builder.surfaceCells(256);
        EquipmentSurfaceService.visitSamples(payload, (id, cell, p) -> builder.stain(id, cell, 255, 0, 0));
        assertEquals(1F, builder.build().coverageFraction());
        for (int pass = 0; pass < 11; pass++)
            EquipmentSurfaceService.visitSamples(payload, (id, cell, p) -> { if (p.y < .5) builder.wash(id, cell, 24); });
        var result = builder.build();
        assertEquals(.5F, result.coverageFraction());
        for (int cell = 0; cell < 256; cell++) {
            if (cell / 16 < 8) assertNull(result.cell(1, cell));
            else assertEquals(255, result.cell(1, cell).strength());
        }
    }

    @Test
    void duplicateFramesCannotWashAnItemTwiceInTheSameTick() {
        var contacts = new EquipmentSurfaceContacts();
        assertTrue(contacts.acceptFullPass(10));
        assertFalse(contacts.acceptFullPass(10));
        assertTrue(contacts.acceptFullPass(12));
    }
}
