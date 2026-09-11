package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.coverage.SurfaceMaterial;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Editor-only previews; closed on leaving the screen, including modal dialogs. */
final class SkinEditorTextures implements AutoCloseable {
    static final int MARKER_COLOR = 0xFF6464EB;
    private final DynamicTexture model, uv, markers;
    private final ResourceLocation modelLocation, uvLocation, markerLocation;
    final RenderType modelType, uvType, markerType;
    final RenderType modelMarkerType;
    private long revision = Long.MIN_VALUE;

    SkinEditorTextures(int width, int height) {
        model = new DynamicTexture(width, height, true);
        uv = new DynamicTexture(width, height, true);
        markers = new DynamicTexture(width, height, true);
        model.setFilter(false, false); uv.setFilter(false, false);
        markers.setFilter(false, false);
        modelLocation = Minecraft.getInstance().getTextureManager().register("mirebound_skin_editor_model", model);
        uvLocation = Minecraft.getInstance().getTextureManager().register("mirebound_skin_editor_uv", uv);
        markerLocation = Minecraft.getInstance().getTextureManager().register("mirebound_skin_editor_markers", markers);
        modelType = SkinEditorDraw.modelType(modelLocation);
        uvType = SkinEditorDraw.type(uvLocation);
        markerType = SkinEditorDraw.type(markerLocation);
        modelMarkerType = SkinEditorDraw.modelType(markerLocation);
    }

    void update(SurfaceMaterial source, SkinMaskEdit edit) {
        if (revision == edit.revision()) return;
        NativeImage a = model.getPixels(), b = uv.getPixels(), c = markers.getPixels();
        if (a == null || b == null || c == null) return;
        int[] dirty=edit.takeDirtyRegion(revision==Long.MIN_VALUE);
        if(dirty==null) { revision=edit.revision(); return; }
        for (int y=dirty[1]; y<dirty[3]; y++) for (int x=dirty[0]; x<dirty[2]; x++) {
            int color = source == null ? 0 : source.pixel(x,y);
            int alpha = color >>> 24;
            if (edit.blocked(x,y)) {
                int r=((color&255)*2+240*3)/5, g=((color>>>8&255)*2+70*3)/5, blue=((color>>>16&255)*2+70*3)/5;
                a.setPixelRGBA(x,y,alpha<<24|blue<<16|g<<8|r);
                b.setPixelRGBA(x,y,Math.max(150,alpha)<<24|blue<<16|g<<8|r);
                c.setPixelRGBA(x,y,MARKER_COLOR);
            } else { a.setPixelRGBA(x,y,color); b.setPixelRGBA(x,y,color); c.setPixelRGBA(x,y,0); }
        }
        // Brush motion uploads only the affected rectangle, not two complete HD skins per frame.
        model.bind(); a.upload(0,dirty[0],dirty[1],dirty[0],dirty[1],dirty[2]-dirty[0],dirty[3]-dirty[1],false,false);
        uv.bind(); b.upload(0,dirty[0],dirty[1],dirty[0],dirty[1],dirty[2]-dirty[0],dirty[3]-dirty[1],false,false);
        markers.bind(); c.upload(0,dirty[0],dirty[1],dirty[0],dirty[1],dirty[2]-dirty[0],dirty[3]-dirty[1],false,false);
        revision = edit.revision();
    }

    void invalidate() { revision = Long.MIN_VALUE; }

    @Override public void close() {
        Minecraft.getInstance().getTextureManager().release(modelLocation);
        Minecraft.getInstance().getTextureManager().release(uvLocation);
        Minecraft.getInstance().getTextureManager().release(markerLocation);
    }
}
