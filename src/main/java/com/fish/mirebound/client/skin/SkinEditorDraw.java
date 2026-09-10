package com.fish.mirebound.client.skin;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class SkinEditorDraw {
    private SkinEditorDraw() {}

    static RenderType type(ResourceLocation texture) {
        return RenderType.create("mirebound_skin_editor", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    }

    static void quad(GuiGraphics graphics, RenderType type, List<SkinEditorProjection.Point> points, int color) {
        var consumer = graphics.bufferSource().getBuffer(type);
        var pose = graphics.pose().last().pose();
        for (var point : points) consumer.addVertex(pose, (float)point.x(), (float)point.y(), 0)
                .setUv(point.u(), point.v()).setColor(color);
    }

    static void texture(GuiGraphics graphics, RenderType type, double x, double y, double width, double height) {
        quad(graphics, type, List.of(new SkinEditorProjection.Point(x,y,0,0,0),
                new SkinEditorProjection.Point(x+width,y,0,1,0),
                new SkinEditorProjection.Point(x+width,y+height,0,1,1),
                new SkinEditorProjection.Point(x,y+height,0,0,1)), -1);
    }

    static void line(GuiGraphics graphics, double x0, double y0, double x1, double y1, int color) {
        double distance = Math.hypot(x1-x0, y1-y0);
        if (distance < .001) return;
        float dx = (float)(-(y1-y0)/distance*.55), dy=(float)((x1-x0)/distance*.55);
        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var pose = graphics.pose().last().pose();
        consumer.addVertex(pose,(float)x0+dx,(float)y0+dy,1).setColor(color);
        consumer.addVertex(pose,(float)x1+dx,(float)y1+dy,1).setColor(color);
        consumer.addVertex(pose,(float)x1-dx,(float)y1-dy,1).setColor(color);
        consumer.addVertex(pose,(float)x0-dx,(float)y0-dy,1).setColor(color);
    }

    static void checker(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0,y0,x1,y1,0xFF20282D);
        for (int y=y0;y<y1;y+=8) for(int x=x0;x<x1;x+=8)
            if (((x-x0)/8+(y-y0)/8)%2==0) graphics.fill(x,y,Math.min(x1,x+8),Math.min(y1,y+8),0xFF29333B);
    }
}
