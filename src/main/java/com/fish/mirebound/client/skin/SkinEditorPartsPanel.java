package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.gui.MireflowGuiTheme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Scrollable outliner; preview controls never change the saved UV mask. */
final class SkinEditorPartsPanel extends AbstractWidget {
    private static final int ROW = 19;
    private final SkinEditorVisibility visibility;
    private final BooleanSupplier legacy;
    private final Consumer<String> selectPart;
    private final java.util.function.Supplier<String> selectedPart;
    private final Set<String> collapsed = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private int scroll, focusedRow = -1;
    private record Row(String part, int layer) {}

    SkinEditorPartsPanel(SkinEditorVisibility visibility, BooleanSupplier legacy,
            Consumer<String> selectPart, java.util.function.Supplier<String> selectedPart) {
        super(0,0,100,100,SkinStainEditorScreen.text("parts"));
        this.visibility=visibility;
        this.legacy=legacy;
        this.selectPart=selectPart;
        this.selectedPart=selectedPart;
        refresh();
    }

    void refresh() {
        rows.clear();
        for(String part:SkinEditorMesh.PARTS) {
            rows.add(new Row(part,-1));
            if(collapsed.contains(part)) continue;
            rows.add(new Row(part,0));
            if(hasOuter(part)) rows.add(new Row(part,1));
        }
        focusedRow=Mth.clamp(focusedRow,-1,rows.size()-1);
        scroll=Mth.clamp(scroll,0,maxScroll());
    }

    private boolean hasOuter(String part) { return !legacy.getAsBoolean() || part.equals("head"); }
    private int maxScroll() { return Math.max(0,rows.size()*ROW-height); }
    private boolean shown(Row row) {
        return row.layer<0 ? visibility.visible(row.part,false) || hasOuter(row.part)&&visibility.visible(row.part,true)
                : visibility.visible(row.part,row.layer==1);
    }
    private Component label(Row row) {
        return SkinStainEditorScreen.text((row.layer==1 ? "layer." : "part.")+row.part);
    }

    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float pt) {
        scroll=Mth.clamp(scroll,0,maxScroll());
        g.enableScissor(getX(),getY(),getX()+width,getY()+height);
        var font=Minecraft.getInstance().font;
        for(int index=scroll/ROW;index<rows.size();index++) {
            int y=getY()+index*ROW-scroll;
            if(y>=getY()+height) break;
            Row row=rows.get(index);
            boolean hover=mx>=getX()&&mx<getX()+width&&my>=y&&my<y+ROW;
            if(row.part().equals(selectedPart.get()) && focusedRow==index) g.fill(getX(),y,getX()+width-3,y+ROW,0xFF4A4435);
            else if(focusedRow==index) g.fill(getX(),y,getX()+width-3,y+ROW,0xFF38383F);
            else if(hover) g.fill(getX(),y,getX()+width-3,y+ROW,0xFF292A30);
            int color=shown(row)?MireflowGuiTheme.TEXT:MireflowGuiTheme.DISABLED;
            if(row.layer<0) {
                int cx=getX()+7,cy=y+9;
                if(!collapsed.contains(row.part)) {
                    int children=hasOuter(row.part)?2:1;
                    g.fill(getX()+22,cy,getX()+23,cy+ROW*children,0xFF3D3A32);
                }
                if(collapsed.contains(row.part)) {
                    SkinEditorDraw.line(g,cx-2,cy-3,cx+1,cy,color);SkinEditorDraw.line(g,cx+1,cy,cx-2,cy+3,color);
                } else {
                    SkinEditorDraw.line(g,cx-3,cy-1,cx,cy+2,color);SkinEditorDraw.line(g,cx,cy+2,cx+3,cy-1,color);
                }
                SkinEditorIconButton.draw(g,SkinEditorIconButton.Icon.FOLDER,getX()+23,y+8,MireflowGuiTheme.ACCENT);
            } else {
                boolean nextChild=index+1<rows.size()&&rows.get(index+1).part().equals(row.part())&&rows.get(index+1).layer>=0;
                g.fill(getX()+22,y,getX()+23,y+(nextChild?ROW:10),0xFF3D3A32);g.fill(getX()+22,y+9,getX()+30,y+10,0xFF3D3A32);
                SkinEditorIconButton.draw(g,SkinEditorIconButton.Icon.CUBE,getX()+39,y+8,color);
            }
            int textX=getX()+(row.layer<0?35:51);
            g.drawString(font,font.plainSubstrByWidth(label(row).getString(),Math.max(1,width-(textX-getX())-28)),textX,y+5,color,false);
            SkinEditorIconButton.draw(g,shown(row)?SkinEditorIconButton.Icon.EYE:SkinEditorIconButton.Icon.HIDDEN,getX()+width-14,y+9,color);
        }
        if(maxScroll()>0) {
            int thumb=Math.max(12,height*height/(rows.size()*ROW));
            int y=getY()+(height-thumb)*scroll/maxScroll();
            g.fill(getX()+width-2,y,getX()+width,y+thumb,MireflowGuiTheme.ACCENT);
        }
        g.flush();g.disableScissor();
    }

    @Override public void onClick(double x,double y,int button) {
        int index=(int)(y-getY()+scroll)/ROW;
        if(index<0||index>=rows.size()) return;
        focusedRow=index;
        Row row=rows.get(index);
        if(row.layer<0&&x<getX()+18) {
            if(!collapsed.remove(row.part)) collapsed.add(row.part);
            refresh();
        } else if(x>=getX()+width-27) {toggle(row);}
        else selectPart.accept(row.part);
    }
    private void toggle(Row row) {
        if(row.layer<0) visibility.togglePart(row.part,hasOuter(row.part));
        else visibility.toggle(row.part,row.layer==1);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(!isMouseOver(x,y))return false;
        scroll=Mth.clamp(scroll-(int)(dy*ROW*2),0,maxScroll());return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==264||key==265) {
            focusedRow=Mth.clamp(focusedRow+(key==264?1:-1),0,rows.size()-1);
            scroll=Mth.clamp(scroll,Math.max(0,(focusedRow+1)*ROW-height),focusedRow*ROW);
            return true;
        }
        if(key==257||key==32) {
            focusedRow=Math.max(0,focusedRow);toggle(rows.get(focusedRow));
            playDownSound(Minecraft.getInstance().getSoundManager());return true;
        }
        if((key==262||key==263)&&focusedRow>=0) {
            Row row=rows.get(focusedRow);
            if(row.layer<0) {if(key==263)collapsed.add(row.part);else collapsed.remove(row.part);refresh();return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        Row row=rows.get(Math.max(0,focusedRow));
        output.add(NarratedElementType.TITLE,Component.translatable("gui.mirebound.skin.part."+row.part)
                .append(" ").append(label(row)).append(" ").append(SkinStainEditorScreen.text(shown(row)?"shown":"hidden")));
    }
}
