package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderBufferRegressionTest {
    @Test void reproducesIssueWithTwoConsumersAcquiredBeforeWriting() {
        try (SharedBuffers buffers = new SharedBuffers()) {
            VertexConsumer stale = buffers.get("face");
            buffers.get("edge");
            assertThrows(IllegalStateException.class, () -> stale.addVertex(0, 0, 0));
        }
    }

    @Test void targetAndRepeatedSummonBoxesUseOnlyLiveConsumers() {
        Vec3[] corners = new Vec3[8];
        for (int i = 0; i < 8; i++) corners[i] = new Vec3(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        try (SharedBuffers buffers = new SharedBuffers()) {
            for (int i = 0; i < 3; i++) {
                Vec3[] box = MudTuningTargetRenderer.rotateBox(corners, 1, i * .3F, .2F, .1F);
                MudTuningTargetRenderer.renderBox(new PoseStack().last(),
                        () -> buffers.get("face"), () -> buffers.get("edge"),
                        box, 1F, .5F, .2F, .12F, .96F);
            }
            buffers.finish();
            assertEquals(3 * (6 + 12 * 4 + 8 * 6) * 4, buffers.vertices);
            assertEquals(6, buffers.draws);
        }
    }

    @Test void originalSingletonNoopFailsFoilComposition() {
        var buffers = new ArmorMudProxyRenderer.CaptureBuffers();
        VertexConsumer singleton = buffers.consumer(new Object());
        assertThrows(IllegalArgumentException.class, () -> VertexMultiConsumer.create(singleton, singleton));
    }

    @Test void offscreenFoilUsesDistinctDelegatesAndReusesEachMaterial() {
        var buffers = new ArmorMudProxyRenderer.CaptureBuffers();
        Object material = new Object();
        VertexConsumer base = buffers.consumer(material);
        VertexConsumer foil = buffers.consumer(new Object());
        assertNotSame(base, foil);
        assertSame(base, buffers.consumer(material));
        assertDoesNotThrow(() -> VertexMultiConsumer.create(base, foil).addVertex(0, 0, 0));
        assertDoesNotThrow(() -> VertexMultiConsumer.create(new VertexConsumer[] {
                base, foil, buffers.consumer(new Object())}).addVertex(0, 0, 0));
        buffers.clear();
        assertNotSame(base, buffers.consumer(material));
    }

    @Test void exceedingNoopCacheBudgetStillNeverAliasesMaterials() {
        var buffers = new ArmorMudProxyRenderer.CaptureBuffers();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<VertexConsumer, Boolean>());
        for (int i = 0; i < 512; i++) assertTrue(seen.add(buffers.consumer(new Object())));
        buffers.clear();
    }

    @Test void sodiumNoopFactoryNeverReturnsASingleton() throws Exception {
        boolean present;
        try {
            Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
            present = true;
        } catch (ClassNotFoundException absent) {
            present = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(present, "Optional Sodium API not on this test classpath");
        var factory = Class.forName("com.fish.mirebound.client.SodiumVertexGeometryCapture").getMethod("noop");
        VertexConsumer first = (VertexConsumer) factory.invoke(null);
        VertexConsumer second = (VertexConsumer) factory.invoke(null);
        assertNotSame(first, second);
        assertDoesNotThrow(() -> VertexMultiConsumer.create(first, second));
    }

    /** Real builders and shared-source switch/flush behavior, without game or GPU bootstrap. */
    private static final class SharedBuffers implements AutoCloseable {
        private final ByteBufferBuilder memory = new ByteBufferBuilder(65536);
        private String current;
        private BufferBuilder builder;
        private int vertices;
        private int draws;

        VertexConsumer get(String type) {
            if (!type.equals(current)) {
                finish();
                current = type;
                builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            }
            return builder;
        }

        void finish() {
            if (builder == null) return;
            var mesh = builder.build();
            if (mesh != null) {
                vertices += mesh.drawState().vertexCount();
                draws++;
                mesh.close();
            }
            builder = null;
            current = null;
        }

        @Override public void close() {
            finish();
            memory.close();
        }
    }
}
