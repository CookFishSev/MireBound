package com.fish.mirebound.client.coverage;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurfaceDrawQueueTest {
    @Test void armorAndOrdinaryFacesKeepTheirOwnDepthLayerAcrossBufferGrowth() {
        List<Boolean> layers = new ArrayList<>();
        int[] count = {0};
        VertexConsumer sink = new VertexConsumer() {
            public VertexConsumer addVertex(float x, float y, float z) {
                // Contact geometry stays on the base surface, including mirrored positions.
                assertEquals(-count[0]++, x);
                assertEquals(.0625F, y);
                assertEquals(-2F, z);
                return this;
            }
            public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            public VertexConsumer setUv(float u, float v) { return this; }
            public VertexConsumer setUv1(int u, int v) { return this; }
            public VertexConsumer setUv2(int u, int v) { return this; }
            public VertexConsumer setNormal(float x, float y, float z) { return this; }
        };
        SurfaceDrawQueue queue = new SurfaceDrawQueue(type -> {
            fail("Original drawing must finish before a decal buffer is acquired");
            return sink;
        });
        for (int i = 0; i < 600; i++) {
            queue.layer(i >= 200 && i < 400).addVertex(-i, .0625F, -2F,
                    0x80805020, .5F, .5F, 0, 0, 0, 1, 0);
        }
        queue.drainLayers(offset -> { layers.add(offset); return sink; });
        assertEquals(List.of(false, true, false), layers);
        assertEquals(600, count[0]);
        queue.drainLayers(offset -> { fail("The queue should be empty after drawing"); return sink; });
    }

    @Test void packedVerticesAreDeferredAndKeepLightingAndColor() {
        List<Integer> colors = new ArrayList<>(), lights = new ArrayList<>();
        int[] bufferCalls={0},vertices={0};
        VertexConsumer sink=new VertexConsumer(){
            public VertexConsumer addVertex(float x,float y,float z){assertEquals(vertices[0]++,x);return this;}
            public VertexConsumer setColor(int r,int g,int b,int a){colors.add(a<<24|r<<16|g<<8|b);return this;}
            public VertexConsumer setUv(float u,float v){return this;}
            public VertexConsumer setUv1(int u,int v){assertEquals(0x00030004,v<<16|u);return this;}
            public VertexConsumer setUv2(int u,int v){lights.add(v<<16|u);return this;}
            public VertexConsumer setNormal(float x,float y,float z){assertEquals(1F,y);return this;}
        };
        SurfaceDrawQueue queue=new SurfaceDrawQueue(type->{bufferCalls[0]++;return sink;});
        for(int i=0;i<4;i++)queue.addVertex(i,1,2,0x80402010,.5F,.5F,0x00030004,0x00F000A0,0,1,0);
        assertEquals(0,bufferCalls[0]);queue.drain(sink);
        assertEquals(List.of(0x80402010,0x80402010,0x80402010,0x80402010),colors);
        assertEquals(List.of(0x00F000A0,0x00F000A0,0x00F000A0,0x00F000A0),lights);
        queue.drain(sink);assertEquals(0,bufferCalls[0]);assertEquals(4,vertices[0]);
    }
}
