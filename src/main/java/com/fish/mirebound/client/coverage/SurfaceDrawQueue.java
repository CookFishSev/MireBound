package com.fish.mirebound.client.coverage;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Collect decals without invalidating vertex consumers held by the original equipment renderer. */
public final class SurfaceDrawQueue implements MultiBufferSource, VertexConsumer {
    private static final int MAX_VERTICES = 32768;
    private final MultiBufferSource delegate;
    private final BaseBuffers baseBuffers = new BaseBuffers();
    private float[] vertices = new float[0];
    private int[] attributes = new int[0];
    private boolean[] viewOffsets = new boolean[0];
    private int size;
    private boolean armorViewOffset;

    public SurfaceDrawQueue(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    public MultiBufferSource baseBuffers() {
        return baseBuffers;
    }

    VertexConsumer layer(boolean viewOffset) {
        armorViewOffset = viewOffset;
        return this;
    }

    @Override
    public VertexConsumer getBuffer(RenderType ignored) {
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (size >= MAX_VERTICES) return this;
        if (vertices.length < (size + 1) * 6) {
            int capacity = Math.min(MAX_VERTICES, Math.max(256, size * 2));
            vertices = Arrays.copyOf(vertices, capacity * 6);
            attributes = Arrays.copyOf(attributes, capacity * 3);
            viewOffsets = Arrays.copyOf(viewOffsets, capacity);
        }
        vertices[size * 6] = x;
        vertices[size * 6 + 1] = y;
        vertices[size * 6 + 2] = z;
        viewOffsets[size] = armorViewOffset;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        if (size < MAX_VERTICES) attributes[size * 3] = (a << 24) | (r << 16) | (g << 8) | b;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        if (size < MAX_VERTICES) attributes[size * 3 + 1] = (v << 16) | (u & 65535);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        if (size < MAX_VERTICES) attributes[size * 3 + 2] = (v << 16) | (u & 65535);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (size < MAX_VERTICES) {
            vertices[size * 6 + 3] = x;
            vertices[size * 6 + 4] = y;
            vertices[size * 6 + 5] = z;
            size++;
        }
        return this;
    }

    public void flush() {
        if (size == 0) return;
        // Atlas-backed equipment may use fixed buffers; changing type alone does not draw its base.
        // Finish only types used by this equipment, after its render method has returned.
        baseBuffers.finish();
        drainLayers(viewOffset -> delegate.getBuffer(SurfaceRenderTypes.translucent(viewOffset)));
        finishType(SurfaceRenderTypes.translucent(false));
        finishType(SurfaceRenderTypes.translucent(true));
    }

    void drain(VertexConsumer out) {
        drainLayers(ignored -> out);
    }

    void drainLayers(Function<Boolean, VertexConsumer> consumers) {
        VertexConsumer out = null;
        boolean previousOffset = false;
        for (int i = 0; i < size - size % 4; i++) {
            if (out == null || viewOffsets[i] != previousOffset) {
                previousOffset = viewOffsets[i];
                out = consumers.apply(previousOffset);
            }
            out.addVertex(vertices[i * 6], vertices[i * 6 + 1], vertices[i * 6 + 2], attributes[i * 3],
                    .5F, .5F, attributes[i * 3 + 1], attributes[i * 3 + 2],
                    vertices[i * 6 + 3], vertices[i * 6 + 4], vertices[i * 6 + 5]);
        }
        size = 0;
    }

    private void finishType(RenderType type) {
        if (delegate instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
        else if (delegate instanceof SurfaceDrawQueue.BaseBuffers parent) parent.finishType(type);
    }

    private final class BaseBuffers implements MultiBufferSource {
        private final Set<RenderType> types = new LinkedHashSet<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            types.add(type);
            return delegate.getBuffer(type);
        }

        void finish() {
            if (delegate instanceof SurfaceDrawQueue.BaseBuffers parent) parent.finish();
            for (RenderType type : types) finishType(type);
            types.clear();
        }

        void finishType(RenderType type) {
            SurfaceDrawQueue.this.finishType(type);
        }
    }
}
