package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowToggleButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Preview visibility only; hiding a part never edits its mask. */
final class SkinStainPartsScreen extends Screen {
    private final SkinStainEditorScreen editor;
    SkinStainPartsScreen(SkinStainEditorScreen editor) { super(SkinStainEditorScreen.text("parts")); this.editor=editor; }
    @Override protected void init() {
        int left=width/2-110, row=28;
        for(String part:SkinEditorMesh.PARTS) {
            String key=part;
            toggle(left,row,SkinStainEditorScreen.text("part."+part),editor.visibleParts.contains(part),()->{
                if(!editor.visibleParts.remove(key))editor.visibleParts.add(key);
                rebuildWidgets();
            });
            row+=22;
        }
        toggle(left,row,SkinStainEditorScreen.text("base"),editor.baseVisible,()->{editor.baseVisible=!editor.baseVisible;rebuildWidgets();});
        toggle(left,row+22,SkinStainEditorScreen.text("outer_layer"),editor.outerVisible,()->{editor.outerVisible=!editor.outerVisible;rebuildWidgets();});
        addRenderableWidget(MireflowButton.builder(SkinStainEditorScreen.text("back"),ignored->onClose())
                .bounds(width/2-50,height-25,100,20).build());
    }
    private void toggle(int x,int y,Component label,boolean enabled,Runnable action) {
        var button=new MireflowToggleButton(x+145,y,70,20,enabled,ignored->action.run());
        addRenderableWidget(button);
    }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partialTick) {}
    @Override public void render(GuiGraphics graphics,int x,int y,float partialTick) {
        graphics.fill(0,0,width,height,0xF0161D23);
        graphics.drawCenteredString(font,title,width/2,9,0xFFEFE8D8);
        int left=width/2-110,row=34;
        for(String part:SkinEditorMesh.PARTS) { graphics.drawString(font,SkinStainEditorScreen.text("part."+part),left,row,0xFFEFE8D8,false); row+=22; }
        graphics.drawString(font,SkinStainEditorScreen.text("base"),left,row,0xFFEFE8D8,false);
        graphics.drawString(font,SkinStainEditorScreen.text("outer_layer"),left,row+22,0xFFEFE8D8,false);
        super.render(graphics,x,y,partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(editor); }
}
