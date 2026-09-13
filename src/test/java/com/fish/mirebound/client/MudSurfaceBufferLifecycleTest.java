package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Test;

class MudSurfaceBufferLifecycleTest {
    @Test void mixedSurfaceBatchesCanReturnToPreviouslyFlushedMaterial() {
        try (var source = new SharedSource()) {
            var batches = new MudSurfaceBatches<String>();
            batches.begin();
            for (String type : new String[] {"pile", "decal", "pile", "other_pile", "decal", "bubble", "pile"})
                quad(batches.buffer(type, source::get));
            batches.end(source::end);
            assertEquals(28, source.vertices);
        }
    }

    @Test void anotherRendererMayFlushTheSameMaterialBetweenSurfaceCells() {
        try (var source = new SharedSource()) {
            var batches = new MudSurfaceBatches<String>();
            batches.begin();
            quad(batches.buffer("pile", source::get));
            source.end("pile");
            quad(batches.buffer("pile", source::get));
            batches.end(source::end);
            assertEquals(8, source.vertices);
        }
    }

    @Test void consumersBelongToTheRequestedBufferSource() {
        try (var first = new SharedSource(); var second = new SharedSource()) {
            var batches = new MudSurfaceBatches<String>();
            batches.begin();
            VertexConsumer firstVertices = batches.buffer("pile", first::get);
            VertexConsumer secondVertices = batches.buffer("pile", second::get);
            assertNotSame(firstVertices, secondVertices);
            quad(firstVertices);
            quad(secondVertices);
            first.end("pile");
            second.end("pile");
            assertEquals(4, first.vertices);
            assertEquals(4, second.vertices);
        }
    }

    @Test void consecutiveCellsStillShareOneDrawBatchAndCompletionDoesNotLeak() {
        try (var source = new SharedSource()) {
            var batches = new MudSurfaceBatches<String>();
            batches.begin();
            for (int i = 0; i < 256; i++) quad(batches.buffer("pile", source::get));
            batches.end(source::end);
            assertEquals(1, source.draws);
            assertEquals(1024, source.vertices);
            batches.end(type -> fail("The completed frame retained a render type"));
        }
    }

    private static void quad(VertexConsumer vertices) {
        vertices.addVertex(0, 0, 0);
        vertices.addVertex(1, 0, 0);
        vertices.addVertex(1, 1, 0);
        vertices.addVertex(0, 1, 0);
    }

    /** Real builders with the shared-source switch/flush contract, without GPU uploads. */
    private static final class SharedSource implements AutoCloseable {
        private final ByteBufferBuilder memory = new ByteBufferBuilder(16384);
        private BufferBuilder builder;
        private String current;
        private int draws, vertices;

        VertexConsumer get(String type) {
            if (!type.equals(current)) {
                if (current != null) end(current);
                builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                current = type;
            }
            return builder;
        }

        void end(String type) {
            if (!type.equals(current)) return;
            var mesh = builder.build();
            if (mesh != null) {
                draws++;
                vertices += mesh.drawState().vertexCount();
                mesh.close();
            }
            builder = null;
            current = null;
        }

        @Override public void close() { memory.close(); }
    }
}
