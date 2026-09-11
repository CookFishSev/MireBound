package com.fish.mirebound.client.skin;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class SkinEditorDraw {
    private static final RenderType DISCS = RenderType.create("mirebound_skin_editor_discs",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType MODEL_LINES = RenderType.create("mirebound_skin_editor_grid",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("lequal", 515))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(false));
    private SkinEditorDraw() {}

    static RenderType type(ResourceLocation texture) {
        return type(texture, false);
    }

    static RenderType modelType(ResourceLocation texture) {
        return type(texture, true);
    }

    private static RenderType type(ResourceLocation texture, boolean depth) {
        return RenderType.create("mirebound_skin_editor", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(depth ? new RenderStateShard.DepthTestStateShard("lequal", 515) : RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(depth ? new RenderStateShard.WriteMaskStateShard(true, true) : RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    static void quad(GuiGraphics graphics, RenderType type, List<SkinEditorProjection.Point> points, int color) {
        var consumer = graphics.bufferSource().getBuffer(type);
        var pose = graphics.pose().last().pose();
        for (var point : points) consumer.addVertex(pose, (float)point.x(), (float)point.y(), 0)
                .setUv(point.u(), point.v()).setColor(color);
    }

    static void modelQuad(GuiGraphics graphics, RenderType type, List<SkinEditorProjection.Point> points, int color) {
        var consumer = graphics.bufferSource().getBuffer(type);
        var pose = graphics.pose().last().pose();
        for (var point : points) consumer.addVertex(pose, (float)point.x(), (float)point.y(), (float)(50D - point.depth()))
                .setUv(point.u(), point.v()).setColor(color);
    }

    static void beginModel(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.clearDepth(1D);
        RenderSystem.clear(256, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);
    }

    static void endModel(GuiGraphics graphics) {
        graphics.bufferSource().endBatch();
        RenderSystem.disableDepthTest();
    }

    static void texture(GuiGraphics graphics, RenderType type, double x, double y, double width, double height) {
        texture(graphics,type,x,y,width,height,-1);
    }

    static void texture(GuiGraphics graphics, RenderType type, double x, double y, double width, double height, int color) {
        quad(graphics, type, List.of(new SkinEditorProjection.Point(x,y,0,0,0),
                new SkinEditorProjection.Point(x+width,y,0,1,0),
                new SkinEditorProjection.Point(x+width,y+height,0,1,1),
                new SkinEditorProjection.Point(x,y+height,0,0,1)), color);
    }

    static void line(GuiGraphics graphics, double x0, double y0, double x1, double y1, int color) {
        double distance = Math.hypot(x1-x0, y1-y0);
        if (distance < .001) return;
        float dx = (float)(-(y1-y0)/distance*.85), dy=(float)((x1-x0)/distance*.85);
        var consumer = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        var pose = graphics.pose().last().pose();
        consumer.addVertex(pose,(float)x0+dx,(float)y0+dy,1).setColor(color);
        consumer.addVertex(pose,(float)x1+dx,(float)y1+dy,1).setColor(color);
        consumer.addVertex(pose,(float)x1-dx,(float)y1-dy,1).setColor(color);
        consumer.addVertex(pose,(float)x0-dx,(float)y0-dy,1).setColor(color);
    }

    static void disc(GuiGraphics graphics, double cx, double cy, double radius, int color) {
        var consumer=graphics.bufferSource().getBuffer(DISCS);
        var pose=graphics.pose().last().pose();
        int segments=64;
        for(int i=0;i<segments;i++) {
            double a0=Math.PI*2*i/segments,a1=Math.PI*2*(i+1)/segments;
            consumer.addVertex(pose,(float)cx,(float)cy,2).setColor(color);
            consumer.addVertex(pose,(float)(cx+Math.cos(a0)*radius),(float)(cy+Math.sin(a0)*radius),2).setColor(color);
            consumer.addVertex(pose,(float)(cx+Math.cos(a1)*radius),(float)(cy+Math.sin(a1)*radius),2).setColor(color);
        }
    }

    static void modelLine(GuiGraphics graphics, double x0, double y0, double depth0,
            double x1, double y1, double depth1, int color) {
        double distance = Math.hypot(x1-x0, y1-y0);
        if (distance < .001) return;
        float dx = (float)(-(y1-y0)/distance*.85), dy=(float)((x1-x0)/distance*.85);
        var consumer = graphics.bufferSource().getBuffer(MODEL_LINES);
        var pose = graphics.pose().last().pose();
        consumer.addVertex(pose,(float)x0+dx,(float)y0+dy,(float)(10D-depth0)).setColor(color);
        consumer.addVertex(pose,(float)x1+dx,(float)y1+dy,(float)(10D-depth1)).setColor(color);
        consumer.addVertex(pose,(float)x1-dx,(float)y1-dy,(float)(10D-depth1)).setColor(color);
        consumer.addVertex(pose,(float)x0-dx,(float)y0-dy,(float)(10D-depth0)).setColor(color);
    }

    static void checker(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0,y0,x1,y1,0xFF222328);
        for (int y=y0;y<y1;y+=8) for(int x=x0;x<x1;x+=8)
            if (((x-x0)/8+(y-y0)/8)%2==0) graphics.fill(x,y,Math.min(x1,x+8),Math.min(y1,y+8),0xFF2C2D32);
    }
}
