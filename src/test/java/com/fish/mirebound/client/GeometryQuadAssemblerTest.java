package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GeometryQuadAssemblerTest {
    @Test
    void keepsScanningAfterLargeAnimatedHeadPrefix() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Vec3> lastCenter = new AtomicReference<>();
        GeometryQuadAssembler assembler = new GeometryQuadAssembler(quad -> {
            calls.incrementAndGet();
            lastCenter.set(quad.interpolate(0.5F, 0.5F, Vec3.ZERO));
        });

        for (int quad = 0; quad < 2048; quad++) {
            appendUnitQuad(assembler, quad, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
        }
        appendUnitQuad(assembler, 4096.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);

        assertEquals(2049, calls.get());
        assertEquals(2049, assembler.completedQuads());
        assertNotNull(lastCenter.get());
        assertEquals(4096.5D, lastCenter.get().x, 1.0E-6D);
        assertEquals(0.5D, lastCenter.get().y, 1.0E-6D);
    }

    @Test
    void assemblesChainedVertexCallsWithoutAllocatingPerVertexObjects() {
        AtomicReference<Vec3> center = new AtomicReference<>();
        GeometryQuadAssembler assembler = new GeometryQuadAssembler(
                quad -> center.set(quad.interpolate(0.5F, 0.5F,
                        new Vec3(10.0D, 20.0D, 30.0D))));

        chainedVertex(assembler, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        chainedVertex(assembler, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        chainedVertex(assembler, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F);
        chainedVertex(assembler, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F);

        assertNotNull(center.get());
        assertEquals(10.5D, center.get().x, 1.0E-6D);
        assertEquals(20.5D, center.get().y, 1.0E-6D);
        assertEquals(30.0D, center.get().z, 1.0E-6D);
    }

    private static void appendUnitQuad(GeometryQuadAssembler assembler,
            float x, float y, float minU, float minV, float maxU, float maxV) {
        assembler.append(x, y, 0.0F, minU, minV);
        assembler.append(x + 1.0F, y, 0.0F, maxU, minV);
        assembler.append(x + 1.0F, y + 1.0F, 0.0F, maxU, maxV);
        assembler.append(x, y + 1.0F, 0.0F, minU, maxV);
    }

    private static void chainedVertex(GeometryQuadAssembler assembler,
            float x, float y, float z, float u, float v) {
        assembler.begin(x, y, z);
        assembler.setUv(u, v);
        assembler.commitCurrent();
    }
}
