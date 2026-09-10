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

    @Test void eyePresetsCoverBothLayersAndScaleToHd() {
        var mask=SkinMaskPresets.create(2,256,256);
        assertTrue(mask.blocked(9*4,12*4)); assertTrue(mask.blocked(41*4,12*4));
        assertFalse(mask.blocked(9*4,8*4));
        assertEquals(0,SkinMaskPresets.create(0,256,256).count());
        assertEquals(256*256,SkinMaskPresets.create(1,256,256).count());
        var fresh=SkinMaskPresets.create(5,64,64);
        assertTrue(fresh.blocked(4,6)); assertTrue(fresh.blocked(26,4)); assertTrue(fresh.blocked(33,2));
        assertFalse(fresh.blocked(9,12)); assertFalse(fresh.blocked(60,4));
    }
}
