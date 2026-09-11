package com.fish.mirebound.client.skin;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import org.junit.jupiter.api.Test;

class SkinEditorSelectionTest {
    @Test void modelRectangleOnlyEditsTheFrontmostLayerAndIsOneUndoStep() {
        var mesh=SkinEditorMesh.create(false,false);
        var quads=SkinEditorProjection.project(mesh,f->f.part().equals("head"),0,0,8,120,100);
        var edit=new SkinMaskEdit(SkinStainMask.empty(64,64));
        var selection=new SkinEditorSelection(quads,64,64,70,-50,170,50,true);
        int steps=0;
        while(!selection.step(edit,7))assertTrue(++steps<100);
        edit.end();
        assertEquals(64,edit.snapshot().count());
        assertTrue(edit.blocked(44,12));
        assertFalse(edit.blocked(12,12));
        edit.undo();assertTrue(edit.snapshot().isEmpty());
        edit.redo();assertEquals(64,edit.snapshot().count());
    }

    @Test void hidingTheOuterHeadDoesNotHideAnyOtherOuterPartOrModifyItsMask() {
        var visibility=new SkinEditorVisibility();
        visibility.toggle("head",true);
        assertTrue(visibility.visible("head",false));
        assertTrue(visibility.visible("right_arm",true));
        var quads=SkinEditorProjection.project(SkinEditorMesh.create(false,false),
                f->f.part().equals("head")&&visibility.visible(f),0,0,8,120,100);
        var edit=new SkinMaskEdit(SkinStainMask.empty(64,64));
        var selection=new SkinEditorSelection(quads,64,64,70,-50,170,50,true);
        assertTrue(selection.step(edit,8192));edit.end();
        assertTrue(edit.blocked(12,12));assertFalse(edit.blocked(44,12));
        visibility.togglePart("head",true);
        assertFalse(visibility.visible("head",false));assertFalse(visibility.visible("head",true));
        visibility.togglePart("head",true);
        assertTrue(visibility.visible("head",false));assertTrue(visibility.visible("head",true));
        assertEquals(64,edit.snapshot().count());
    }

    @Test void hdSelectionHasABoundedWorkBudgetAndEmptySpaceChangesNothing() {
        var quads=SkinEditorProjection.project(SkinEditorMesh.create(true,false),f->f.part().equals("head"),0,0,8,120,100);
        var edit=new SkinMaskEdit(SkinStainMask.empty(4096,4096));
        var selection=new SkinEditorSelection(quads,4096,4096,70,-50,170,50,true);
        assertFalse(selection.step(edit,128));
        assertTrue(edit.snapshot().count()<=128);
        assertTrue(selection.percent()<100);
        var empty=new SkinEditorSelection(quads,4096,4096,300,300,310,310,true);
        var untouched=new SkinMaskEdit(SkinStainMask.empty(4096,4096));
        assertTrue(empty.step(untouched,128));untouched.end();
        assertTrue(untouched.snapshot().isEmpty());assertFalse(untouched.canUndo());
    }
}
