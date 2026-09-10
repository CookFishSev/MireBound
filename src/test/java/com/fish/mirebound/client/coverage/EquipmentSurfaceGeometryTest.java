package com.fish.mirebound.client.coverage;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceData;
import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EquipmentSurfaceGeometryTest {
    static EquipmentSurfaceGeometry.Vertex[] square(float x) {
        return new EquipmentSurfaceGeometry.Vertex[] {
            new EquipmentSurfaceGeometry.Vertex(x,0,0,0,0), new EquipmentSurfaceGeometry.Vertex(x+1,0,0,1,0),
            new EquipmentSurfaceGeometry.Vertex(x+1,1,0,1,1), new EquipmentSurfaceGeometry.Vertex(x,1,0,0,1)};
    }
    @Test void twoHelmetAntennaeWithIdenticalUvsStaySeparateAfterSaving() {
        var left = EquipmentSurfaceGeometry.face("helmet/left/0",square(0),1);
        var right = EquipmentSurfaceGeometry.face("helmet/right/0",square(0),1);
        assertNotEquals(left.id(),right.id());
        var builder = EquipmentSurfaceData.EMPTY.toBuilder();
        builder.stain(left.id(),0,230,SinkingMedium.MUD.id(),71L);
        var data = builder.build();
        JsonElement saved = EquipmentSurfaceData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow();
        var restored = EquipmentSurfaceData.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow();
        assertEquals(230,restored.cell(left.id(),0).strength());
        assertNull(restored.cell(right.id(),0));
        assertEquals(left.id(),EquipmentSurfaceGeometry.face("helmet/left/0",square(0),1).id());
    }
    @Test void bakedFacesSharingSpriteButAtDifferentLocationsDoNotAlias() {
        var left = EquipmentSurfaceGeometry.face("sprite:shared",square(-2),1);
        var right = EquipmentSurfaceGeometry.face("sprite:shared",square(2),1);
        assertNotEquals(left.id(),right.id());
        assertEquals(new Vec3(-1.5,.5,0),left.point(.5,.5));
        assertEquals(new Vec3(2.5,.5,0),right.point(.5,.5));
    }
    @Test void washDoesNotAffectTheOtherInstanceOrRegainStrength() {
        var builder = EquipmentSurfaceData.EMPTY.toBuilder();
        builder.stain(1,0,100,0,0);builder.stain(2,0,200,0,0);builder.wash(1,0,80);
        var data = builder.build();
        assertEquals(20,data.cell(1,0).strength());assertEquals(200,data.cell(2,0).strength());
        builder.wash(1,0,100);assertNull(builder.build().cell(1,0));
    }
    @Test void zeroStrengthAndInvalidCellsCannotCreateMud() {
        var builder = EquipmentSurfaceData.EMPTY.toBuilder();
        builder.stain(1,0,0,0,0); builder.stain(0,0,1,0,0); builder.stain(1,256,1,0,0);
        assertFalse(builder.changed()); assertTrue(builder.build().isEmpty());
    }
}
