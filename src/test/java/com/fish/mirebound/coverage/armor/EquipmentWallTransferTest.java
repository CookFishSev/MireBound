package com.fish.mirebound.coverage.armor;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.network.payload.EquipmentSurfaceContactPayload;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EquipmentWallTransferTest {
    private static EquipmentSurfaceContactPayload payload() {
        var patch = new EquipmentSurfacePatch(7, 2, 1, Vec3.ZERO, new Vec3(1, 0, 0),
                new Vec3(1, 1, 0), new Vec3(0, 1, 0),
                List.of(EquipmentSurfacePatch.probe(0, .5, .5), EquipmentSurfacePatch.probe(1, .5, .5)));
        return new EquipmentSurfaceContactPayload(EquipmentSurfaceTarget.backpack(),
                ResourceLocation.parse("test:backpack"), Vec3.ZERO, List.of(), List.of(patch), true);
    }

    @Test
    void onlyDirtyVisibleCellsSupplyTheExistingWallPipelineWithTheirOwnColorAndPose() {
        var data = new EquipmentSurfaceData(List.of(new EquipmentSurfaceData.Cell(7, 0, 240, 0, 123)), 2);
        var contacts = new EquipmentSurfaceContacts();
        contacts.offer(7, 0, Vec3.ZERO, 10);
        contacts.offer(7, 1, Vec3.ZERO, 10);
        var transfer = new EquipmentWallTransfer(payload(), data, contacts, 10, 1, .35F);
        transfer.offer(7, 0, new Vec3(1, 2, 3));
        transfer.offer(7, 1, new Vec3(2, 2, 3));
        assertEquals(1, transfer.sources().size());
        var source = transfer.sources().getFirst();
        assertEquals(7, source.face());
        assertEquals(0, source.cell());
        assertEquals(new Vec3(1, 2, 3), source.point());
        assertEquals(new Vec3(0, 0, 1), source.normal());
        assertEquals(SinkingMedium.byId(0), source.medium());
        assertEquals(123, source.visualSource());
    }

    @Test
    void movingContactFramesKeepTheConfiguredPerCellTransferInterval() {
        var contacts = new EquipmentSurfaceContacts();
        contacts.offer(7, 0, Vec3.ZERO, 10);
        assertTrue(contacts.reserveWallTransfer(7, 0, 10, 4));
        contacts.offer(7, 0, new Vec3(0, 1, 0), 12);
        assertFalse(contacts.reserveWallTransfer(7, 0, 12, 4));
        contacts.offer(7, 1, Vec3.ZERO, 12);
        assertTrue(contacts.reserveWallTransfer(7, 1, 12, 4));
        assertTrue(contacts.reserveWallTransfer(7, 0, 14, 4));
    }

    @Test
    void successfulWallTransferDrainsOnlyItsSourceAndNeverRestoresWaterWashedDirt() {
        var data = new EquipmentSurfaceData(List.of(new EquipmentSurfaceData.Cell(7, 0, 240, 0, 123),
                new EquipmentSurfaceData.Cell(7, 1, 240, 0, 123)), 2);
        var builder = data.toBuilder();
        builder.transferToWall(7, 0, .1F, .35F);
        assertTrue(builder.build().cell(7, 0).strength() < 240);
        assertEquals(240, builder.build().cell(7, 1).strength());
        builder.wash(7, 0, 210);
        int cleaned = builder.build().cell(7, 0).strength();
        builder.transferToWall(7, 0, .1F, .35F);
        assertEquals(cleaned, builder.build().cell(7, 0).strength());
        assertTrue(builder.build().coverageFraction() < data.coverageFraction());
    }
}
