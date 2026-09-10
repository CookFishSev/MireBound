package com.fish.mirebound.client.skin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkinEditorProjectionTest {
    @Test void modelPickingRoundTripsUvsAfterRotationZoomAndPanning() {
        for(boolean slim:new boolean[]{false,true}) {
            var mesh=SkinEditorMesh.create(slim,false);
            assertEquals(72,mesh.size());
            for(double yaw:new double[]{0,.4,Math.PI/2,Math.PI}) {
                var quads=SkinEditorProjection.project(mesh,f->!f.outer(),yaw,.25,7,150,130);
                assertFalse(quads.isEmpty());
                for(var quad:quads) {
                    float u=0,v=0;
                    for(var p:quad.points()) {u+=p.u()/4;v+=p.v()/4;}
                    var point=SkinEditorProjection.uvPoint(quad,u,v);
                    assertNotNull(point);
                    var hit=SkinEditorProjection.pick(List.of(quad),point.x(),point.y(),(a,b)->true);
                    assertNotNull(hit);assertEquals(u,hit.u(),1e-5);assertEquals(v,hit.v(),1e-5);
                }
            }
        }
    }

    @Test void transparentHatFallsThroughToTheHeadAndHiddenPartsAreNotPicked() {
        var mesh=SkinEditorMesh.create(false,false);
        var quads=SkinEditorProjection.project(mesh,f->f.part().equals("head"),0,0,8,120,100);
        var outer=SkinEditorProjection.pick(quads,120,4,(u,v)->true);
        assertNotNull(outer);assertTrue(outer.quad().face().outer());
        var base=SkinEditorProjection.pick(quads,120,4,(u,v)->u<.5F);
        assertNotNull(base);assertFalse(base.quad().face().outer());
        assertNull(SkinEditorProjection.pick(SkinEditorProjection.project(mesh,f->false,0,0,8,120,100),120,4,(u,v)->true));
    }

    @Test void legacySkinsUseOnlyTheirOwnTextureHalf() {
        var legacy=SkinEditorMesh.create(false,true);
        assertEquals(42,legacy.size());
        for(var face:legacy)for(var vertex:face.vertices()) {
            assertTrue(vertex.u()>=0&&vertex.u()<=1);assertTrue(vertex.v()>=0&&vertex.v()<=1);
        }
    }
}
