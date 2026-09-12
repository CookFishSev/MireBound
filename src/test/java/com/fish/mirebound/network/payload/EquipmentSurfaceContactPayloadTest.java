package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceData;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceService;
import com.fish.mirebound.coverage.armor.EquipmentSurfacePatch;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class EquipmentSurfaceContactPayloadTest {
    @Test void aPartialBatchPreservesTheFullModelDenominatorOnTheWire() {
        var patch = new EquipmentSurfacePatch(1, 1, 1, Vec3.ZERO, new Vec3(1, 0, 0),
                new Vec3(1, 1, 0), new Vec3(0, 1, 0), List.of(EquipmentSurfacePatch.probe(0, .5, .5)));
        var expected = new EquipmentSurfaceContactPayload(EquipmentSurfaceTarget.backpack(),
                ResourceLocation.parse("test:backpack"), Vec3.ZERO, List.of(), List.of(patch), true, 780);
        var b = buffer();
        try {
            EquipmentSurfaceContactPayload.STREAM_CODEC.encode(b, expected);
            assertEquals(expected, EquipmentSurfaceContactPayload.STREAM_CODEC.decode(b));
            assertEquals(0, b.readableBytes());
        } finally { b.release(); }
    }

    @Test void fullBackpackPassUsesCompactFacesAndRetainsEveryVisibleCell() {
        var probes = new java.util.ArrayList<Integer>();
        for (int cell = 0; cell < 256; cell++) probes.add(EquipmentSurfacePatch.probe(cell, .5, .5));
        var patches = new java.util.ArrayList<EquipmentSurfacePatch>();
        for (int face = 1; face <= 4; face++) patches.add(new EquipmentSurfacePatch(face, 16, 16,
                new Vec3(0, 0, face / 4D), new Vec3(1, 0, face / 4D),
                new Vec3(1, 1, face / 4D), new Vec3(0, 1, face / 4D), probes));
        var expected = new EquipmentSurfaceContactPayload(EquipmentSurfaceTarget.backpack(),
                ResourceLocation.parse("test:backpack"), Vec3.ZERO, List.of(), patches, true);
        var b = buffer();
        try {
            EquipmentSurfaceContactPayload.STREAM_CODEC.encode(b, expected);
            assertTrue(b.readableBytes() < 4096, () -> "packet bytes=" + b.readableBytes());
            var restored = EquipmentSurfaceContactPayload.STREAM_CODEC.decode(b);
            assertEquals(expected, restored);
            assertEquals(1024, restored.patches().stream().mapToInt(p -> p.probes().size()).sum());
            assertEquals(0, b.readableBytes());
        } finally { b.release(); }
    }

    @Test void tooManyFullSurfaceCellsAreRejected() {
        var probes = new java.util.ArrayList<Integer>();
        for (int cell = 0; cell < 256; cell++) probes.add(EquipmentSurfacePatch.probe(cell, .5, .5));
        var patches = new java.util.ArrayList<EquipmentSurfacePatch>();
        for (int face = 1; face <= 33; face++) patches.add(new EquipmentSurfacePatch(face, 16, 16,
                Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(0, 1, 0), probes));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentSurfaceContactPayload(
                EquipmentSurfaceTarget.backpack(), ResourceLocation.parse("test:backpack"), Vec3.ZERO, List.of(), patches, true));
    }

    @Test void compactItemSyncKeepsSurfaceSizeAndStainValues() {
        var cells = new java.util.ArrayList<EquipmentSurfaceData.Cell>();
        for (int i = 0; i < 256; i++) cells.add(new EquipmentSurfaceData.Cell(1, i, 200, 0, 0));
        var expected = new EquipmentSurfaceData(cells, 1024);
        var b = buffer();
        try {
            EquipmentSurfaceData.STREAM_CODEC.encode(b, expected);
            assertTrue(b.readableBytes() < 1500);
            assertEquals(expected, EquipmentSurfaceData.STREAM_CODEC.decode(b));
            assertEquals(0, b.readableBytes());
        } finally { b.release(); }
    }

    private RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.OTHER);
    }
    @Test void addressesAndPhysicalFaceIdsSurviveWireRoundTrip() {
        for (var target : List.of(EquipmentSurfaceTarget.armor(EquipmentSlot.HEAD),
                new EquipmentSurfaceTarget(1,2,"back",true),EquipmentSurfaceTarget.backpack())) {
            var expected = new EquipmentSurfaceContactPayload(target,ResourceLocation.parse("test:helmet"),new Vec3(1,2,3),
                    List.of(new EquipmentSurfaceContactPayload.Sample(0x1234567890abcdefL,255,-.5F,.5F,0F)));
            var b=buffer();
            try {
                EquipmentSurfaceContactPayload.STREAM_CODEC.encode(b,expected);
                assertEquals(expected,EquipmentSurfaceContactPayload.STREAM_CODEC.decode(b));
                assertEquals(0,b.readableBytes());
            } finally {b.release();}
        }
    }
    @Test void oversizeCountsAreRejectedBeforeReadingSampleBodies() {
        var b=buffer();
        try {
            b.writeByte(2);b.writeVarInt(0);b.writeUtf("");b.writeBoolean(false);
            b.writeResourceLocation(ResourceLocation.parse("test:backpack"));
            b.writeDouble(0);b.writeDouble(0);b.writeDouble(0);b.writeVarInt(257);
            assertThrows(IllegalArgumentException.class,()->EquipmentSurfaceContactPayload.STREAM_CODEC.decode(b));
        } finally {b.release();}
    }
    @Test void invalidAddressesAndNonFinitePositionsAreRejected() {
        assertFalse(new EquipmentSurfaceTarget(0,EquipmentSlot.MAINHAND.ordinal(),"",false).valid());
        assertFalse(new EquipmentSurfaceTarget(2,1,"",false).valid());
        assertFalse(new EquipmentSurfaceTarget(2,0,"",true).valid());
        assertFalse(new EquipmentSurfaceTarget(1,128,"back",false).valid());
        assertFalse(new EquipmentSurfaceTarget(4,0,"",false).valid());
        assertFalse(EquipmentSurfaceService.validPoint(new Vec3(Double.NaN,0,0)));
        assertFalse(EquipmentSurfaceService.validPoint(new Vec3(0,Double.POSITIVE_INFINITY,0)));
    }
    @Test void itemDataCodecRetainsIndependentFacesAndRejectsHugeCount() {
        var expected = new EquipmentSurfaceData(List.of(new EquipmentSurfaceData.Cell(1,5,200,0,19),
                new EquipmentSurfaceData.Cell(2,5,60,0,20)));
        var b=buffer();
        try {
            EquipmentSurfaceData.STREAM_CODEC.encode(b,expected);
            assertEquals(expected,EquipmentSurfaceData.STREAM_CODEC.decode(b));
            b.clear();b.writeVarInt(EquipmentSurfaceData.MAX_CELLS+1);
            assertThrows(IllegalArgumentException.class,()->EquipmentSurfaceData.STREAM_CODEC.decode(b));
        } finally {b.release();}
    }
}
