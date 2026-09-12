package com.fish.mirebound.client.coverage;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.armor.EquipmentSurfacePatch;
import com.fish.mirebound.network.payload.EquipmentSurfaceContactPayload;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EquipmentSurfaceBatchTest {
    private static EquipmentSurfacePatch face(long id, int side) {
        List<Integer> cells = new ArrayList<>();
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++)
            cells.add(EquipmentSurfacePatch.probe(y * 16 + x, .5, .5));
        return new EquipmentSurfacePatch(id, side, side, Vec3.ZERO,
                new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(0, 1, 0), cells);
    }

    @Test
    void a195FaceBackpackDoesNotPermanentlyLoseThePocketsAfterFace128() {
        List<EquipmentSurfacePatch> faces = new ArrayList<>();
        for (int i = 1; i <= 195; i++) faces.add(face(i, 2));
        HashSet<Long> visited = new HashSet<>();
        int cursor = 0;
        for (int pass = 0; pass < 2; pass++) {
            var batch = EquipmentSurfaceBatch.select(faces, cursor);
            assertTrue(batch.patches().size() <= 128);
            batch.patches().forEach(p -> visited.add(p.face()));
            cursor = batch.next();
            var payload = new EquipmentSurfaceContactPayload(EquipmentSurfaceTarget.backpack(),
                    ResourceLocation.parse("test:backpack"), Vec3.ZERO, List.of(), batch.patches(), true, batch.surfaceCells());
            assertEquals(780, payload.surfaceCells(), "The tooltip denominator must include unsent clean faces");
        }
        assertEquals(195, visited.size());
        assertTrue(visited.contains(195L));
    }

    @Test
    void aCellBudgetRolloverContinuesAtTheNextFace() {
        List<EquipmentSurfacePatch> faces = new ArrayList<>();
        for (int i = 1; i <= 40; i++) faces.add(face(i, 16));
        var first = EquipmentSurfaceBatch.select(faces, 0);
        assertEquals(32, first.patches().size());
        var second = EquipmentSurfaceBatch.select(faces, first.next());
        assertEquals(33, second.patches().getFirst().face());
        assertEquals(10240, second.surfaceCells());
    }

    @Test
    void removedModelPartsDoNotLeaveTheCursorOutsideTheNewModel() {
        var batch = EquipmentSurfaceBatch.select(List.of(face(1, 1), face(2, 1)), 128);
        assertEquals(2, batch.patches().size());
        assertEquals(0, batch.next());
        assertTrue(EquipmentSurfaceBatch.select(List.of(), 128).patches().isEmpty());
    }
}
