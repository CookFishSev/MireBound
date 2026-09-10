package com.fish.mirebound.network.payload;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceData;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceService;
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
