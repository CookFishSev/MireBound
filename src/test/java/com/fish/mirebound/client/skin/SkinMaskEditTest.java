package com.fish.mirebound.client.skin;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import org.junit.jupiter.api.Test;

class SkinMaskEditTest {
    @Test void continuousStrokeIsOneUndoOperationAndBrushNeverWrapsToAnotherRow() {
        SkinMaskEdit edit=new SkinMaskEdit(SkinStainMask.empty(256,256));
        edit.begin(); edit.line(0,0,10,10,1,true); edit.line(10,10,20,20,1,true); edit.end();
        for(int i=0;i<=20;i++)assertTrue(edit.blocked(i,i));
        assertEquals(21,edit.snapshot().count());
        edit.undo(); assertEquals(0,edit.snapshot().count());
        edit.redo(); assertEquals(21,edit.snapshot().count());
        edit.rectangle(-2,-2,1,1,true); edit.end();
        assertFalse(edit.blocked(255,0));
    }

    @Test void eraseUndoAndPartialUploadsKeepTheSameUvCells() {
        SkinMaskEdit edit=new SkinMaskEdit(SkinStainMask.empty(1024,1024));
        edit.takeDirtyRegion(true);
        edit.rectangle(144,190,147,192,true);
        assertArrayEquals(new int[]{144,190,148,193},edit.takeDirtyRegion(false));
        edit.end(); assertNull(edit.takeDirtyRegion(false));
        edit.rectangle(145,191,145,191,false); edit.end();
        assertFalse(edit.blocked(145,191)); assertTrue(edit.blocked(144,191));
        edit.undo(); assertTrue(edit.blocked(145,191));
        assertArrayEquals(new int[]{0,0,1024,1024},edit.takeDirtyRegion(false));
    }

    @Test void largeModelBrushCannotPaintOutsideTheSelectedFace() {
        var edit=new SkinMaskEdit(SkinStainMask.empty(64,64));
        edit.line(40,8,47,15,32,true,40,8,48,16);
        edit.end();
        assertEquals(64,edit.snapshot().count());
        assertTrue(edit.blocked(40,8)); assertTrue(edit.blocked(47,15));
        assertFalse(edit.blocked(39,8)); assertFalse(edit.blocked(48,15));
        assertFalse(edit.blocked(47,16));
        edit.undo();assertTrue(edit.snapshot().isEmpty());
    }
}
