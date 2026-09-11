package com.fish.mirebound.client.skin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SkinEditorViewTest {
    @Test void draggingTheViewportOrbitsTheModelWithTheEstablishedCameraConvention() {
        for(double yaw:new double[]{0,.4,Math.PI/2,Math.PI}) {
            double x=Math.sin(yaw),z=-Math.cos(yaw);
            var right=SkinEditorView.drag(yaw,0,10,0);
            assertTrue(SkinEditorView.frame(right.yaw(),right.pitch()).project(x,0,z).x()>0);
            var down=SkinEditorView.drag(yaw,0,0,10);
            assertTrue(SkinEditorView.frame(down.yaw(),down.pitch()).project(x,0,z).y()>0);
        }
    }

    @Test void gridAndGizmoUseTheExactModelProjectionFrame() {
        double yaw=.63,pitch=-.47,scale=8,cx=150,cy=130;
        var frame=SkinEditorView.frame(yaw,pitch);
        var quads=SkinEditorProjection.project(SkinEditorMesh.create(false,false),f->true,yaw,pitch,scale,cx,cy);
        for(var quad:quads)for(int i=0;i<4;i++) {
            var vertex=quad.face().vertices().get(i);
            var point=frame.project(vertex.x(),vertex.y()-8,vertex.z());
            assertEquals(quad.points().get(i).x(),cx+point.x()*scale,1e-8);
            assertEquals(quad.points().get(i).y(),cy+point.y()*scale,1e-8);
            assertEquals(quad.points().get(i).depth(),point.depth(),1e-8);
        }
    }

    @Test void directionButtonsFaceTheCameraAndRemainInsideTheViewport() {
        for(var axis:SkinEditorView.Axis.values()) {
            var point=SkinEditorView.frame(axis.yaw,axis.pitch).project(axis.x,axis.y,axis.z);
            assertEquals(0,point.x(),1e-8);assertEquals(0,point.y(),1e-8);assertEquals(-1,point.depth(),1e-8);
        }
        for(int width:new int[]{320,640,1280}) {
            var layout=SkinEditorLayout.fit(width,240,.24,.8);
            int cx=SkinEditorView.centerX(layout),cy=SkinEditorView.centerY(layout);
            assertTrue(layout.inModel(cx-32,cy-32));assertTrue(layout.inModel(cx+32,cy+32));
        }
    }
}
