package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class SkinStainPresetScreen extends Screen {
    private final SkinStainEditorScreen editor;
    private final SkinStainMask current;
    private final List<Entry> entries=new ArrayList<>();
    private EditBox name;
    private int scroll,selected=-1;
    private String error;
    private Button save,update,delete,use;
    private record Entry(UUID id,Component title,SkinStainMask mask) {}

    SkinStainPresetScreen(SkinStainEditorScreen editor) {
        super(SkinStainEditorScreen.text("presets")); this.editor=editor; current=editor.currentMask(); reload();
    }

    private void reload() {
        entries.clear();
        for(int i=0;i<SkinMaskPresets.NAMES.length;i++)entries.add(new Entry(null,
                SkinStainEditorScreen.text("preset."+SkinMaskPresets.NAMES[i]),SkinMaskPresets.create(i,current.width(),current.height())));
        try { for(var preset:ClientSkinStainRules.storage().presets())entries.add(new Entry(preset.id(),Component.literal(preset.name()),preset.mask())); }
        catch(IOException exception) { error="gui.mirebound.skin.io_error"; }
    }

    private int left() { return Math.max(8,width/2-170); }
    private int panelWidth() { return Math.min(width-16,340); }
    private int visibleRows() { return Math.max(1,(height-128)/22); }

    @Override protected void init() { rebuildWidgets(); }
    @Override protected void rebuildWidgets() {
        String old=name==null?"":name.getValue();
        clearWidgets();
        scroll=Mth.clamp(scroll,0,Math.max(0,entries.size()-visibleRows()));
        for(int row=0;row<visibleRows()&&row+scroll<entries.size();row++) {
            int index=row+scroll; Entry entry=entries.get(index);
            var button=MireflowButton.builder(entry.title,ignored->{selected=index;name.setValue(entry.id==null?"":entry.title.getString());rebuildWidgets();})
                    .selected(index==selected).bounds(left(),30+row*22,panelWidth(),20).build();
            if(entry.id==null)button.setTooltip(Tooltip.create(SkinStainEditorScreen.text("preset_hint")));
            addRenderableWidget(button);
        }
        name=new EditBox(font,left(),height-82,panelWidth(),18,SkinStainEditorScreen.text("preset_name"));
        name.setMaxLength(64); name.setValue(old);
        name.setHint(SkinStainEditorScreen.text("preset_name")); addRenderableWidget(name);
        int unit=(panelWidth()-8)/3;
        save=button("save_preset",left(),height-58,unit,()->write(false));
        update=button("update_preset",left()+unit+4,height-58,unit,()->write(true));
        delete=button("delete_preset",left()+2*(unit+4),height-58,unit,this::delete);
        use=button("use_preset",left(),height-25,(panelWidth()-4)/2,()->{editor.replaceMask(entries.get(selected).mask);onClose();});
        button("back",left()+(panelWidth()+4)/2,height-25,(panelWidth()-4)/2,this::onClose);
    }

    private Button button(String key,int x,int y,int width,Runnable action) {
        return addRenderableWidget(MireflowButton.builder(SkinStainEditorScreen.text(key),ignored->action.run()).bounds(x,y,width,20).build());
    }
    private void write(boolean replace) {
        UUID id=replace&&selected>=0?entries.get(selected).id:null;
        if(replace&&id==null)return;
        try {
            var saved=ClientSkinStainRules.storage().savePreset(id,name.getValue(),current);
            reload();
            for(int i=0;i<entries.size();i++)if(saved.id().equals(entries.get(i).id))selected=i;
            scroll=Math.max(0,selected-visibleRows()+1); error=null; rebuildWidgets();
        } catch(IOException|IllegalArgumentException exception) { error="gui.mirebound.skin.io_error"; }
    }
    private void delete() {
        if(selected<0||entries.get(selected).id==null)return;
        Entry entry=entries.get(selected);
        minecraft.setScreen(new ConfirmScreen(confirmed->{
            if(confirmed) {
                try { ClientSkinStainRules.storage().deletePreset(entry.id); reload(); selected=-1; error=null; }
                catch(IOException exception) { error="gui.mirebound.skin.io_error"; }
            }
            minecraft.setScreen(this);
        },SkinStainEditorScreen.text("delete_preset"),entry.title));
    }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partialTick) {}
    @Override public void render(GuiGraphics graphics,int x,int y,float partialTick) {
        graphics.fill(0,0,width,height,0xF0161D23);
        graphics.drawCenteredString(font,title,width/2,10,0xFFEFE8D8);
        save.active=!name.getValue().isBlank();
        boolean custom=selected>=0&&selected<entries.size()&&entries.get(selected).id!=null;
        update.active=custom&&!name.getValue().isBlank()&&(!current.equals(entries.get(selected).mask)||!name.getValue().equals(entries.get(selected).title.getString()));
        delete.active=custom; use.active=selected>=0&&selected<entries.size();
        if(error!=null)graphics.drawString(font,Component.translatable(error),left(),height-36,0xFFFF8974,false);
        super.render(graphics,x,y,partialTick);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(y<height-86) { scroll=Mth.clamp(scroll+(dy>0?-1:1),0,Math.max(0,entries.size()-visibleRows()));rebuildWidgets();return true; }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public void onClose() { minecraft.setScreen(editor); }
}
