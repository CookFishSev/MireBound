package com.fish.mirebound.client.coverage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SurfaceTexelClipTest {
    @Test void highResolutionOpaqueColorsNeedOnlyOneCoverageQuadPerCell() {
        var face = EquipmentSurfaceGeometry.face("test", EquipmentSurfaceGeometryTest.square(0), 1);
        SurfaceMaterial material = new SurfaceMaterial() {
            public int width() { return 128; }
            public int height() { return 128; }
            public int pixel(int x, int y) { return 0xff000000 | x << 8 | y; }
        };
        AtomicInteger quads = new AtomicInteger();
        double[] area = {0};
        int visits = SurfaceTexelClip.visitCoverage(face, 0, 0, material, 16384, (a,b,c,d,color) -> {
            quads.incrementAndGet();
            area[0] += (c-a)*(d-b);
        });
        assertEquals(16384, visits);
        assertEquals(1, quads.get());
        assertEquals(1, area[0], 1e-8);
    }

    @Test void mergedCoverageKeepsTransparentHolesAndPartialAlpha() {
        var face = EquipmentSurfaceGeometry.face("test", EquipmentSurfaceGeometryTest.square(0), 1);
        Pixels material = new Pixels();
        material.values = new int[] {0x80ffffff, 0, 0, 0xffffffff};
        double[] area = {0};
        SurfaceTexelClip.visitCoverage(face, 0, 0, material, 32, (a,b,c,d,color) -> {
            area[0] += (c-a)*(d-b) * (color >>> 24) / 255;
            assertTrue(c <= .5 && d <= .5 || a >= .5 && b >= .5);
        });
        assertEquals(.25 * (128 / 255D + 1), area[0], 1e-8);
    }

    @Test void mergingNeverBridgesACompletelyTransparentRow() {
        var face = EquipmentSurfaceGeometry.face("test", EquipmentSurfaceGeometryTest.square(0), 1);
        SurfaceMaterial pixels = new SurfaceMaterial() {
            public int width() { return 4; }
            public int height() { return 4; }
            public int pixel(int x, int y) { return y == 1 ? 0 : 0xffffffff; }
        };
        double[] area = {0};
        SurfaceTexelClip.visitCoverage(face, 0, 0, pixels, 32, (a,b,c,d,color) -> {
            assertTrue(d <= .25 || b >= .5);
            area[0] += (c-a)*(d-b);
        });
        assertEquals(.75, area[0], 1e-8);
    }

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
