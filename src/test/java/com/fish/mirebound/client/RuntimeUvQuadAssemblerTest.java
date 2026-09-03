package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RuntimeUvQuadAssemblerTest {
    @Test
    void assemblesOneQuadAndInterpolatesItsUvCenter() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Vec3> center = new AtomicReference<>();
        RuntimeUvQuadAssembler assembler = new RuntimeUvQuadAssembler(quad -> {
            calls.incrementAndGet();
            center.set(RuntimeUvQuadAssembler.interpolateTriangle(0.5F, 0.5F,
                    quad[0], quad[1], quad[2]));
            if (center.get() == null) {
                center.set(RuntimeUvQuadAssembler.interpolateTriangle(0.5F, 0.5F,
                        quad[0], quad[2], quad[3]));
            }
        });

        vertex(assembler, new Vec3(0.0D, 0.0D, 0.0D), 0.0F, 0.0F);
        vertex(assembler, new Vec3(1.0D, 0.0D, 0.0D), 1.0F, 0.0F);
        vertex(assembler, new Vec3(1.0D, 1.0D, 0.0D), 1.0F, 1.0F);
        vertex(assembler, new Vec3(0.0D, 1.0D, 0.0D), 0.0F, 1.0F);
        assembler.commitCurrent();

        assertEquals(1, calls.get());
        assertNotNull(center.get());
        assertEquals(0.5D, center.get().x, 1.0E-6D);
        assertEquals(0.5D, center.get().y, 1.0E-6D);
    }

    private static void vertex(RuntimeUvQuadAssembler assembler, Vec3 point, float u, float v) {
        assembler.beginVertex(point);
        assembler.setUv(u, v);
    }
}
