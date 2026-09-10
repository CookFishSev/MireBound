package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.MudSkinTextureCache;
import com.fish.mirebound.client.coverage.SurfaceMaterial;
import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.coverage.skin.SkinStainMask;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Self-only UV editor; no live player pose is mutated to render its model preview. */
public final class SkinStainEditorScreen extends Screen {
    private final Screen parent;
    private SkinMaskEdit edit;
    private SurfaceMaterial material;
    private ResourceLocation skinTexture;
    private boolean slim, legacy;
    private SkinEditorTextures textures;
    private List<SkinEditorMesh.Face> mesh = List.of();
    private List<SkinEditorProjection.Quad> projected = List.of();
    final Set<String> visibleParts = new HashSet<>(SkinEditorMesh.PARTS);
    boolean baseVisible = true, outerVisible = true;
    private boolean deny = true;
    private int brush = 1;
    private double yaw = -.35, pitch = -.15, modelZoom = 1, modelPanX, modelPanY;
    private double uvZoom = 1, uvPanX, uvPanY;
    private int gesture, lastX = -1, lastY = -1, rectangleX, rectangleY;
    private SkinEditorMesh.Face lastFace;
    private boolean rectangle;
    private int skinRefreshTicks;
    private Button undo, redo, apply;
    private String message;

    public SkinStainEditorScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    static Component text(String key, Object... args) { return Component.translatable("gui.mirebound.skin." + key, args); }

    @Override protected void init() {
        refreshSkin();
        rebuildWidgets();
        if (message == null) message = ClientSkinStainRules.loadError();
    }

    private void refreshSkin() {
        PlayerSkin skin = minecraft.player == null ? minecraft.getSkinManager().getInsecureSkin(minecraft.getGameProfile())
                : minecraft.player.getSkin();
        ResourceLocation texture = MudSkinTextureCache.originalSkinTexture(skin.texture());
        SurfaceMaterial pixels = MudSkinTextureCache.currentMaterial(texture);
        int w = pixels == null ? 64 : pixels.width(), h = pixels == null ? 64 : pixels.height();
        if (w > SkinStainMask.MAX_DIMENSION || h > SkinStainMask.MAX_DIMENSION) {
            message = "gui.mirebound.skin.too_large";
            return;
        }
        boolean nextSlim = skin.model() == PlayerSkin.Model.SLIM, nextLegacy = w == h * 2;
        boolean resized=edit!=null&&(edit.width()!=w||edit.height()!=h);
        if (edit == null) {
            edit = new SkinMaskEdit(ClientSkinStainRules.ownMask().resized(w, h));
        } else if (edit.width() != w || edit.height() != h) {
            edit.end();
            edit = new SkinMaskEdit(edit.snapshot().resized(w, h));
        }
        if (resized || !texture.equals(skinTexture) || slim != nextSlim || legacy != nextLegacy || textures == null) {
            closeTextures();
            skinTexture = texture; slim = nextSlim; legacy = nextLegacy;
            material = pixels;
            mesh = SkinEditorMesh.create(slim, legacy);
            textures = new SkinEditorTextures(w, h);
        } else if (material == null && pixels != null) {
            material = pixels;
            textures.invalidate();
        }
    }

    @Override public void tick() {
        if (++skinRefreshTicks >= 20) { skinRefreshTicks = 0; refreshSkin(); }
    }

    @Override protected void rebuildWidgets() {
        clearWidgets();
        int unit = (width - 24) / 3;
        button(text("deny").copy().withStyle(ChatFormatting.RED), 8, 23, unit, deny, () -> { deny = true; rebuildWidgets(); });
        button(text("allow").copy().withStyle(ChatFormatting.GREEN), 12 + unit, 23, unit, !deny, () -> { deny = false; rebuildWidgets(); });
        button(text("brush", brush), 16 + 2 * unit, 23, unit, false, () -> { brush = brush >= 32 ? 1 : brush * 2; rebuildWidgets(); });
        button(text("presets"), 8, 47, unit, false, () -> { finishStroke(); minecraft.setScreen(new SkinStainPresetScreen(this)); });
        button(text("parts"), 12 + unit, 47, unit, false, () -> { finishStroke(); minecraft.setScreen(new SkinStainPartsScreen(this)); });
        button(text("reset_view"), 16 + 2 * unit, 47, unit, false, this::resetView);
        int foot = (width - 32) / 5, y = height - 24;
        undo = button(text("undo"), 8, y, foot, false, () -> { edit.undo(); message = null; });
        redo = button(text("redo"), 12 + foot, y, foot, false, () -> { edit.redo(); message = null; });
        button(text("clear"), 16 + 2 * foot, y, foot, false, () -> replaceMask(SkinStainMask.empty(edit.width(), edit.height())));
        apply = button(text("apply"), 20 + 3 * foot, y, foot, false, this::save);
        button(text("cancel"), 24 + 4 * foot, y, foot, false, this::onClose).active=true;
        button(Component.literal("?"), width - 24, 2, 16, false, () -> {})
                .setTooltip(Tooltip.create(text("help")));
    }

    private Button button(Component label, int x, int y, int w, boolean selected, Runnable action) {
        Button button = MireflowButton.builder(label, ignored -> action.run()).selected(selected).bounds(x,y,w,20).build();
        button.setTooltip(Tooltip.create(label));
        button.active=edit!=null;
        return addRenderableWidget(button);
    }

    private void save() {
        finishStroke();
        if (edit == null) return;
        try {
            ClientSkinStainRules.save(edit.snapshot());
            minecraft.setScreen(parent);
        } catch (IOException error) { message = "gui.mirebound.skin.io_error"; }
        catch (IllegalArgumentException error) { message = "gui.mirebound.skin.too_complex"; }
    }

    SkinStainMask currentMask() { finishStroke(); return edit.snapshot(); }
    void replaceMask(SkinStainMask mask) { finishStroke(); edit.replace(mask.resized(edit.width(),edit.height())); message = null; }

    private void resetView() {
        yaw=-.35; pitch=-.15; modelZoom=uvZoom=1; modelPanX=modelPanY=uvPanX=uvPanY=0;
    }

    private int divider() { return width / 2; }
    private int viewTop() { return 82; }
    private int viewBottom() { return Math.max(viewTop()+16, height-46); }
    private boolean inModel(double x,double y) { return x>=8 && x<divider()-3 && y>=viewTop() && y<viewBottom(); }
    private boolean inUv(double x,double y) { return x>=divider()+3 && x<width-8 && y>=viewTop() && y<viewBottom(); }
    private double uvScale() { return Math.min((width-divider()-16D)/edit.width(), (viewBottom()-viewTop()-8D)/edit.height())*uvZoom; }
    private double uvLeft() { return (divider()+3+width-8)*.5 - edit.width()*uvScale()*.5 + uvPanX; }
    private double uvTop() { return (viewTop()+viewBottom())*.5-edit.height()*uvScale()*.5+uvPanY; }

    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {}

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xF0161D23);
        graphics.drawCenteredString(font,title,width/2,7,MireflowGuiTheme.TEXT);
        graphics.drawString(font,text("model"),10,72,0xFF89BDD0,false);
        graphics.drawString(font,text("uv"),divider()+6,72,0xFFE0B96C,false);
        if(edit != null && textures != null) {
            textures.update(material,edit);
            renderModel(graphics);
            renderUv(graphics);
            var hit = inModel(mouseX,mouseY) ? modelHit(mouseX,mouseY) : null;
            int[] uv = hit != null ? new int[]{hit.x(edit.width()),hit.y(edit.height())} : inUv(mouseX,mouseY) ? uvPixel(mouseX,mouseY,false) : null;
            if(uv != null) renderHover(graphics,uv[0],uv[1]);
            Component status = message == null ? uv==null ? text("status",edit.width(),edit.height())
                    : text("pixel",uv[0],uv[1],text(edit.blocked(uv[0],uv[1])?"denied":"allowed"))
                    : Component.translatable(message);
            graphics.drawString(font,font.plainSubstrByWidth(status.getString(),width-16),8,height-38,
                    message==null?MireflowGuiTheme.MUTED:MireflowGuiTheme.ERROR,false);
            undo.active=gesture==0&&edit.canUndo(); redo.active=gesture==0&&edit.canRedo();
            apply.active=edit.revision()>0;
        } else if(message!=null) {
            graphics.drawString(font,font.plainSubstrByWidth(Component.translatable(message).getString(),width-16),8,height-38,0xFFFF8974,false);
        }
        super.render(graphics,mouseX,mouseY,partialTick);
    }

    private void renderModel(GuiGraphics graphics) {
        graphics.fill(8,viewTop(),divider()-3,viewBottom(),0xFF192630);
        graphics.enableScissor(8,viewTop(),divider()-3,viewBottom());
        double scale=Math.max(.1,Math.min((divider()-19D)/22,(viewBottom()-viewTop()-8D)/34))*modelZoom;
        projected=SkinEditorProjection.project(mesh,face -> visibleParts.contains(face.part())&&(face.outer()?outerVisible:baseVisible),
                yaw,pitch,scale,(8+divider()-3)*.5+modelPanX,(viewTop()+viewBottom())*.5+modelPanY);
        for(var quad:projected) {
            int light=(int)(quad.light()*255);
            SkinEditorDraw.quad(graphics,textures.modelType,quad.points(),0xFF000000|light<<16|light<<8|light);
        }
        graphics.flush();
        graphics.disableScissor();
    }

    private void renderUv(GuiGraphics graphics) {
        SkinEditorDraw.checker(graphics,divider()+3,viewTop(),width-8,viewBottom());
        graphics.enableScissor(divider()+3,viewTop(),width-8,viewBottom());
        double scale=uvScale(), x=uvLeft(), y=uvTop();
        SkinEditorDraw.texture(graphics,textures.uvType,x,y,edit.width()*scale,edit.height()*scale);
        graphics.flush();
        if(scale>=4) {
            int x0=Math.max(0,(int)((divider()+3-x)/scale)), x1=Math.min(edit.width(),(int)((width-8-x)/scale)+1);
            int y0=Math.max(0,(int)((viewTop()-y)/scale)), y1=Math.min(edit.height(),(int)((viewBottom()-y)/scale)+1);
            for(int i=x0;i<=x1;i++) graphics.fill((int)(x+i*scale),Math.max(viewTop(),(int)y),(int)(x+i*scale)+1,Math.min(viewBottom(),(int)(y+edit.height()*scale)),0x55303940);
            for(int j=y0;j<=y1;j++) graphics.fill(Math.max(divider()+3,(int)x),(int)(y+j*scale),Math.min(width-8,(int)(x+edit.width()*scale)),(int)(y+j*scale)+1,0x55303940);
        }
        graphics.disableScissor();
    }

    private void renderHover(GuiGraphics graphics,int px,int py) {
        float u0=px/(float)edit.width(), u1=(px+1F)/edit.width(), v0=py/(float)edit.height(), v1=(py+1F)/edit.height();
        graphics.enableScissor(8,viewTop(),divider()-3,viewBottom());
        for(var quad:projected) {
            float minU=1,maxU=0,minV=1,maxV=0;
            for(var p:quad.points()) { minU=Math.min(minU,p.u()); maxU=Math.max(maxU,p.u()); minV=Math.min(minV,p.v()); maxV=Math.max(maxV,p.v()); }
            if((u0+u1)*.5<minU||(u0+u1)*.5>=maxU||(v0+v1)*.5<minV||(v0+v1)*.5>=maxV)continue;
            var a=SkinEditorProjection.uvPoint(quad,u0,v0); var b=SkinEditorProjection.uvPoint(quad,u1,v0);
            var c=SkinEditorProjection.uvPoint(quad,u1,v1); var d=SkinEditorProjection.uvPoint(quad,u0,v1);
            if(a!=null&&b!=null&&c!=null&&d!=null) {
                SkinEditorDraw.line(graphics,a.x(),a.y(),b.x(),b.y(),-1); SkinEditorDraw.line(graphics,b.x(),b.y(),c.x(),c.y(),-1);
                SkinEditorDraw.line(graphics,c.x(),c.y(),d.x(),d.y(),-1); SkinEditorDraw.line(graphics,d.x(),d.y(),a.x(),a.y(),-1);
            }
        }
        graphics.flush(); graphics.disableScissor();
        graphics.enableScissor(divider()+3,viewTop(),width-8,viewBottom());
        int x=(int)(uvLeft()+px*uvScale()), y=(int)(uvTop()+py*uvScale());
        graphics.renderOutline(x,y,Math.max(1,(int)Math.ceil(uvScale())),Math.max(1,(int)Math.ceil(uvScale())),-1);
        if(rectangle&&gesture==2) {
            int x0=(int)(uvLeft()+Math.min(rectangleX,px)*uvScale()), y0=(int)(uvTop()+Math.min(rectangleY,py)*uvScale());
            graphics.renderOutline(x0,y0,Math.max(1,(int)((Math.abs(px-rectangleX)+1)*uvScale())),
                    Math.max(1,(int)((Math.abs(py-rectangleY)+1)*uvScale())),0xFFFFCC66);
        }
        graphics.disableScissor();
    }

    private SkinEditorProjection.Hit modelHit(double x,double y) {
        return SkinEditorProjection.pick(projected,x,y,(u,v)->material!=null&&(material.sample(u,v)>>>24)>0);
    }

    private int[] uvPixel(double x,double y,boolean clamp) {
        int px=(int)Math.floor((x-uvLeft())/uvScale()),py=(int)Math.floor((y-uvTop())/uvScale());
        if(clamp)return new int[]{Mth.clamp(px,0,edit.width()-1),Mth.clamp(py,0,edit.height()-1)};
        return px<0||py<0||px>=edit.width()||py>=edit.height()?null:new int[]{px,py};
    }

    @Override public boolean mouseClicked(double x,double y,int button) {
        if(super.mouseClicked(x,y,button))return true;
        if(edit==null)return false;
        if(inModel(x,y)) {
            if(button==1||button==0&&hasAltDown())gesture=3;
            else if(button==2)gesture=4;
            else if(button==0) { gesture=1; edit.begin(); paintModel(x,y); }
        } else if(inUv(x,y)) {
            if(button==1||button==2)gesture=5;
            else if(button==0) {
                int[] pixel=uvPixel(x,y,false); if(pixel==null)return false;
                gesture=2; rectangle=hasShiftDown(); rectangleX=pixel[0]; rectangleY=pixel[1];
                edit.begin(); if(!rectangle)paintUv(x,y);
            }
        }
        return gesture!=0;
    }

    private void paintModel(double x,double y) {
        var hit=modelHit(x,y);
        if(hit==null) { lastX=-1; lastFace=null; return; }
        int px=hit.x(edit.width()),py=hit.y(edit.height());
        if(lastX<0||lastFace!=hit.quad().face()) { lastX=px; lastY=py; }
        edit.line(lastX,lastY,px,py,brush,deny); lastX=px; lastY=py; lastFace=hit.quad().face(); message=null;
    }

    private void paintUv(double x,double y) {
        int[] pixel=uvPixel(x,y,false);
        if(pixel==null) { lastX=-1; return; }
        if(lastX<0) { lastX=pixel[0]; lastY=pixel[1]; }
        edit.line(lastX,lastY,pixel[0],pixel[1],brush,deny); lastX=pixel[0]; lastY=pixel[1]; message=null;
    }

    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        switch(gesture) {
            case 1 -> { if(inModel(x,y))paintModel(x,y); }
            case 2 -> { if(!rectangle&&inUv(x,y))paintUv(x,y); }
            case 3 -> { yaw+=dx*.012; pitch=Mth.clamp(pitch+dy*.012,-Math.PI,Math.PI); }
            case 4 -> { modelPanX+=dx; modelPanY+=dy; }
            case 5 -> { uvPanX+=dx; uvPanY+=dy; }
            default -> { return super.mouseDragged(x,y,button,dx,dy); }
        }
        return true;
    }

    @Override public boolean mouseReleased(double x,double y,int button) {
        if(gesture==0)return super.mouseReleased(x,y,button);
        if(gesture==2&&rectangle) {
            int[] pixel=uvPixel(x,y,true);
            edit.rectangle(rectangleX,rectangleY,pixel[0],pixel[1],deny);
        }
        finishStroke(); return true;
    }

    private void finishStroke() {
        if(edit!=null)edit.end();
        gesture=0; rectangle=false; lastX=-1; lastFace=null;
    }

    @Override public boolean mouseScrolled(double x,double y,double sx,double sy) {
        if(edit==null)return false;
        if(inModel(x,y)) { modelZoom=Mth.clamp(modelZoom*Math.pow(1.15,sy),.2,16); return true; }
        if(inUv(x,y)) {
            double before=uvScale(), px=(x-uvLeft())/before,py=(y-uvTop())/before;
            uvZoom=Mth.clamp(uvZoom*Math.pow(1.2,sy),.25,512);
            double after=uvScale();
            uvPanX=x-(divider()+3+width-8)*.5+edit.width()*after*.5-px*after;
            uvPanY=y-(viewTop()+viewBottom())*.5+edit.height()*after*.5-py*after;
            return true;
        }
        return super.mouseScrolled(x,y,sx,sy);
    }

    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(edit!=null&&hasControlDown()&&key==GLFW.GLFW_KEY_Z) { finishStroke(); if(hasShiftDown())edit.redo();else edit.undo(); return true; }
        if(edit!=null&&hasControlDown()&&key==GLFW.GLFW_KEY_Y) { finishStroke(); edit.redo(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { finishStroke(); closeTextures(); }
    private void closeTextures() { if(textures!=null) { textures.close(); textures=null; } }
}
