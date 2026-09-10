package com.fish.mirebound.client.skin;

import com.fish.mirebound.client.coverage.SurfaceMaterial;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Editor-only previews; closed on leaving the screen, including modal dialogs. */
final class SkinEditorTextures implements AutoCloseable {
    private final DynamicTexture model, uv;
    private final ResourceLocation modelLocation, uvLocation;
    final RenderType modelType, uvType;
    private long revision = Long.MIN_VALUE;

    SkinEditorTextures(int width, int height) {
        model = new DynamicTexture(width, height, true);
        uv = new DynamicTexture(width, height, true);
        model.setFilter(false, false); uv.setFilter(false, false);
        modelLocation = Minecraft.getInstance().getTextureManager().register("mirebound_skin_editor_model", model);
        uvLocation = Minecraft.getInstance().getTextureManager().register("mirebound_skin_editor_uv", uv);
        modelType = SkinEditorDraw.type(modelLocation);
        uvType = SkinEditorDraw.type(uvLocation);
    }

    void update(SurfaceMaterial source, SkinMaskEdit edit) {
        if (revision == edit.revision()) return;
        NativeImage a = model.getPixels(), b = uv.getPixels();
        if (a == null || b == null) return;
        int[] dirty=edit.takeDirtyRegion(revision==Long.MIN_VALUE);
        if(dirty==null) { revision=edit.revision(); return; }
        for (int y=dirty[1]; y<dirty[3]; y++) for (int x=dirty[0]; x<dirty[2]; x++) {
            int color = source == null ? 0 : source.pixel(x,y);
            int alpha = color >>> 24;
            if (edit.blocked(x,y)) {
                int r=((color&255)*2+240*3)/5, g=((color>>>8&255)*2+70*3)/5, blue=((color>>>16&255)*2+70*3)/5;
                a.setPixelRGBA(x,y,alpha<<24|blue<<16|g<<8|r);
                b.setPixelRGBA(x,y,Math.max(150,alpha)<<24|blue<<16|g<<8|r);
            } else { a.setPixelRGBA(x,y,color); b.setPixelRGBA(x,y,color); }
        }
        // Brush motion uploads only the affected rectangle, not two complete HD skins per frame.
        model.bind(); a.upload(0,dirty[0],dirty[1],dirty[0],dirty[1],dirty[2]-dirty[0],dirty[3]-dirty[1],false,false);
        uv.bind(); b.upload(0,dirty[0],dirty[1],dirty[0],dirty[1],dirty[2]-dirty[0],dirty[3]-dirty[1],false,false);
        revision = edit.revision();
    }

    void invalidate() { revision = Long.MIN_VALUE; }

    @Override public void close() {
        Minecraft.getInstance().getTextureManager().release(modelLocation);
        Minecraft.getInstance().getTextureManager().release(uvLocation);
    }
}
