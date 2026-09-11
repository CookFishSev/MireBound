package com.fish.mirebound.client.skin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SkinEditorLayoutTest {
    @Test void panelsAndToolsStayReachableAtVanillaGuiScales() {
        for(int width:new int[]{320,427,640,854,1280,2560}) {
            int height=Math.max(240,width*9/16);
            var layout=SkinEditorLayout.fit(width,height,.24,.8);
            for(int divider:new int[]{1,2})for(int position:new int[]{-10000,0,width/2,width,width+10000}) {
                var moved=layout.drag(divider,position);
                assertTrue(moved.left()>=80);
                assertTrue(moved.right()-moved.left()>=130);
                assertTrue(width-moved.right()>=100);
                assertTrue(moved.inModel((moved.left()+moved.right())/2D,100));
                assertFalse(moved.inModel(moved.left(),100));
                assertFalse(moved.inModel(moved.right(),100));
                assertFalse(moved.inModel((moved.left()+moved.right())/2D,40));
                assertEquals(divider,moved.dividerAt(divider==1?moved.left():moved.right(),100));
            }
        }
    }

    @Test void resizingAfterDraggingKeepsThreeNonOverlappingPanels() {
        var wide=SkinEditorLayout.fit(2560,1380,.24,.8).drag(1,1400).drag(2,2300);
        var narrow=SkinEditorLayout.fit(320,240,wide.left()/2560D,wide.right()/2560D);
        assertTrue(narrow.modelRight()>narrow.modelLeft());
        assertTrue(narrow.right()<=220);
        assertFalse(narrow.inUv(narrow.left()+2,100));
        assertFalse(narrow.inModel(narrow.left()-2,100));
    }
}
