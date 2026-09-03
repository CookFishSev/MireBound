package com.fish.mirebound.client;

import com.fish.mirebound.mud.AnimatedPlayerGeometry;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Sodium 0.6.x batch-writer bridge for exact final rendered geometry capture. */
public final class SodiumVertexGeometryCapture {
    private SodiumVertexGeometryCapture() {
    }

    public static VertexConsumer wrapBody(VertexConsumer delegate, LocalPlayer player) {
        return new BodyConsumer(delegate, player);
    }

    public static VertexConsumer wrapCape(VertexConsumer delegate, LocalPlayer player) {
        return new CapeConsumer(delegate, player);
    }

    public static VertexConsumer noop() {
        return NoopSodiumVertexConsumer.INSTANCE;
    }

    private abstract static class CaptureConsumer
            implements VertexConsumer, VertexBufferWriter, AnimatedGeometryConsumer {
        final VertexConsumer delegate;
        final LocalPlayer player;
        final GeometryQuadAssembler assembler;
        private final VertexBufferWriter delegateWriter;
        private final boolean discardOutput;
        final Vec3 cameraPosition;
        private boolean finished;
        private boolean samplingCurrentVertex;
        private int batchCalls;
        private int batchVertices;
        private int directBulkVertices;
        private int chainedVertices;

        CaptureConsumer(VertexConsumer delegate, LocalPlayer player) {
            this.delegate = delegate;
            this.player = player;
            this.delegateWriter = VertexBufferWriter.tryOf(delegate);
            this.discardOutput = delegate instanceof NoopGeometryVertexSink;
            this.cameraPosition = AnimatedPlayerGeometryCapture.worldCameraPosition();
            this.assembler = new GeometryQuadAssembler(this::sampleQuad);
        }

        @Override
        public boolean canUseIntrinsics() {
            return delegateWriter != null || discardOutput;
        }

        @Override
        public void push(MemoryStack stack, long pointer, int count, VertexFormat format) {
            batchCalls++;
            sampleBatch(pointer, count, format);
            if (delegateWriter != null) {
                delegateWriter.push(stack, pointer, count, format);
            }
        }

        private void sampleBatch(long pointer, int count, VertexFormat format) {
            if (!DefaultVertexFormat.NEW_ENTITY.equals(format)
                    || !format.contains(VertexFormatElement.POSITION)
                    || !format.contains(VertexFormatElement.UV0)) {
                return;
            }
            int stride = format.getVertexSize();
            int positionOffset = format.getOffset(VertexFormatElement.POSITION);
            int textureOffset = format.getOffset(VertexFormatElement.UV0);
            for (int index = 0; index < count; index++) {
                long vertex = pointer + (long) index * stride;
                assembler.append(
                        MemoryUtil.memGetFloat(vertex + positionOffset),
                        MemoryUtil.memGetFloat(vertex + positionOffset + 4L),
                        MemoryUtil.memGetFloat(vertex + positionOffset + 8L),
                        MemoryUtil.memGetFloat(vertex + textureOffset),
                        MemoryUtil.memGetFloat(vertex + textureOffset + 4L));
            }
            batchVertices += count;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            assembler.begin(x, y, z);
            samplingCurrentVertex = true;
            chainedVertices++;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color,
                float u, float v, int overlay, int light,
                float normalX, float normalY, float normalZ) {
            assembler.append(x, y, z, u, v);
            directBulkVertices++;
            delegate.addVertex(x, y, z, color, u, v, overlay, light,
                    normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (samplingCurrentVertex) {
                assembler.setUv(u, v);
            }
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (samplingCurrentVertex) {
                assembler.commitCurrent();
                samplingCurrentVertex = false;
            }
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public final void finishGeometry() {
            if (finished) {
                return;
            }
            finished = true;
            assembler.commitCurrent();
            complete();
        }

        final String counters() {
            return "batches=" + batchCalls
                    + ",batch_vertices=" + batchVertices
                    + ",direct_bulk_vertices=" + directBulkVertices
                    + ",chained_vertices=" + chainedVertices
                    + ",quads=" + assembler.completedQuads();
        }

        abstract void sampleQuad(GeometryQuadAssembler.Quad quad);

        abstract void complete();
    }

    private static final class BodyConsumer extends CaptureConsumer {
        private final RenderedGeometryCollector.Body collector;

        BodyConsumer(VertexConsumer delegate, LocalPlayer player) {
            super(delegate, player);
            collector = new RenderedGeometryCollector.Body(player, cameraPosition);
        }

        @Override
        void sampleQuad(GeometryQuadAssembler.Quad quad) {
            collector.sample(quad);
        }

        @Override
        void complete() {
            AnimatedPlayerGeometry.PartPose[] poses = collector.finish();
            String detail = counters() + ",matched_faces="
                    + collector.matchedFaces() + "/36";
            if (poses != null) {
                AnimatedPlayerGeometryCapture.recordBodyDetail(detail);
                AnimatedPlayerGeometryCapture.submitBody(
                        player, poses, AnimatedPlayerGeometry.Source.SODIUM_VERTICES);
            } else {
                AnimatedPlayerGeometryCapture.recordBodyFailure(
                        "sodium:" + collector.diagnostic() + '[' + detail + ']');
            }
        }
    }

    private static final class CapeConsumer extends CaptureConsumer {
        private final RenderedGeometryCollector.Cape collector;

        CapeConsumer(VertexConsumer delegate, LocalPlayer player) {
            super(delegate, player);
            collector = new RenderedGeometryCollector.Cape(player, cameraPosition);
        }

        @Override
        void sampleQuad(GeometryQuadAssembler.Quad quad) {
            collector.sample(quad);
        }

        @Override
        void complete() {
            AnimatedPlayerGeometry.CapePose cape = collector.finish();
            String detail = counters();
            if (cape != null) {
                AnimatedPlayerGeometryCapture.recordCapeDetail(detail);
                AnimatedPlayerGeometryCapture.submitCape(
                        player, cape, AnimatedPlayerGeometry.Source.SODIUM_VERTICES);
            } else {
                AnimatedPlayerGeometryCapture.recordCapeFailure(
                        "sodium:missing_faces[" + detail + ']');
            }
        }
    }

    private enum NoopSodiumVertexConsumer
            implements VertexConsumer, VertexBufferWriter, NoopGeometryVertexSink {
        INSTANCE;

        @Override
        public boolean canUseIntrinsics() {
            return true;
        }

        @Override
        public void push(MemoryStack stack, long pointer, int count, VertexFormat format) {
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
