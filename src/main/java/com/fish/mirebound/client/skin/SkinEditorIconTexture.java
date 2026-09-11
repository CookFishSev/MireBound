package com.fish.mirebound.client.skin;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/** Filtered icon atlas. Mip levels are generated once on load or resource reload, never while drawing. */
final class SkinEditorIconTexture extends SimpleTexture {
    private final int columns;

    SkinEditorIconTexture(ResourceLocation location, int columns) {
        super(location);
        this.columns = columns;
    }

    @Override public void load(ResourceManager resources) throws IOException {
        super.load(resources);
        if (RenderSystem.isOnRenderThreadOrInit()) generateMipmaps();
        else RenderSystem.recordRenderCall(this::generateMipmaps);
    }

    private void generateMipmaps() {
        bind();
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        int cellSize = Math.max(1, Math.min(width / columns, height));
        int levels = Math.min(Integer.numberOfTrailingZeros(width), Integer.numberOfTrailingZeros(height));
        levels = Math.min(levels, 31 - Integer.numberOfLeadingZeros(cellSize));
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, levels);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        setFilter(true, true);
    }
}
