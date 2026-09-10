package com.fish.mirebound.client.coverage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SurfaceTexelClipTest {
    private static final class Pixels implements SurfaceMaterial {
        int[] values = {0xFFFFFFFF,0,0,0xFFFFFFFF};
        public int width(){return 2;} public int height(){return 2;}
        public int pixel(int x,int y){return values[y*2+x];}
    }
    @Test void aCoarseContactCellNeverFillsTransparentTextureHoles() {
        var face=EquipmentSurfaceGeometry.face("test",EquipmentSurfaceGeometryTest.square(0),1);
        Pixels pixels=new Pixels(); double[] area={0};
        int visits=SurfaceTexelClip.visit(face,0,0,pixels,32,(s0,t0,s1,t1,rgba)->{
            assertEquals(255,rgba>>>24);area[0]+=(s1-s0)*(t1-t0);
            assertTrue(s1<=.5&&t1<=.5 || s0>=.5&&t0>=.5);
        });
        assertEquals(4,visits);assertEquals(.5,area[0],1e-8);
    }
    @Test void dynamicPixelBecomingOpaqueIsRecheckedWithoutChangingFaceIdentity() {
        var face=EquipmentSurfaceGeometry.face("test",EquipmentSurfaceGeometryTest.square(0),1);
        Pixels pixels=new Pixels();java.util.Arrays.fill(pixels.values,0);
        AtomicInteger draws=new AtomicInteger();
        SurfaceTexelClip.visit(face,0,0,pixels,32,(a,b,c,d,e)->draws.incrementAndGet());
        assertEquals(0,draws.get());pixels.values[1]=0x80FFFFFF;
        SurfaceTexelClip.visit(face,0,0,pixels,32,(a,b,c,d,e)->{assertEquals(128,e>>>24);draws.incrementAndGet();});
        assertEquals(1,draws.get());pixels.values[1]=0;
        SurfaceTexelClip.visit(face,0,0,pixels,32,(a,b,c,d,e)->draws.incrementAndGet());
        assertEquals(1,draws.get());
    }
    @Test void mirroredUvsPreserveTheCorrectPhysicalHole() {
        var v=EquipmentSurfaceGeometryTest.square(0);
        for(int i=0;i<4;i++)v[i]=new EquipmentSurfaceGeometry.Vertex(v[i].x(),v[i].y(),v[i].z(),1-v[i].u(),v[i].v());
        var face=EquipmentSurfaceGeometry.face("test",v,1);double[] area={0};
        SurfaceTexelClip.visit(face,0,0,new Pixels(),32,(a,b,c,d,e)->{
            assertTrue(a>=.5&&d<=.5 || c<=.5&&b>=.5);area[0]+=(c-a)*(d-b);
        });assertEquals(.5,area[0],1e-8);
    }
    @Test void boundedWorkDoesNotFillUnexaminedPixels() {
        var face=EquipmentSurfaceGeometry.face("test",EquipmentSurfaceGeometryTest.square(0),1);
        AtomicInteger draws=new AtomicInteger();
        assertEquals(1,SurfaceTexelClip.visit(face,0,0,new Pixels(),1,(a,b,c,d,e)->draws.incrementAndGet()));
        assertEquals(1,draws.get());
    }
}
