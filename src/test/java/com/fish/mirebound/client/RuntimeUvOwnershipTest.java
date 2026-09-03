package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RuntimeUvOwnershipTest {
    @Test
    void detectsOppositeUvWindingWithoutChangingCoveredArea() {
        RuntimeUvQuadAssembler.Vertex[] forward = quad(
                uv(0.0F, 0.0F), uv(1.0F, 0.0F), uv(1.0F, 1.0F), uv(0.0F, 1.0F));
        RuntimeUvQuadAssembler.Vertex[] mirrored = quad(
                uv(1.0F, 0.0F), uv(0.0F, 0.0F), uv(0.0F, 1.0F), uv(1.0F, 1.0F));

        assertTrue(RuntimeUvOwnership.signedUvArea(forward) > 0.0F);
        assertTrue(RuntimeUvOwnership.signedUvArea(mirrored) < 0.0F);
        assertTrue(RuntimeUvQuadAssembler.containsUv(0.25F, 0.75F, forward));
        assertTrue(RuntimeUvQuadAssembler.containsUv(0.25F, 0.75F, mirrored));
    }

    @Test
    void ownershipTableCanResolveSharedPhysicalPixelsWithoutLosingUniquePixels() {
        RuntimeUvOwnership.Frame<String> frame = new RuntimeUvOwnership.Frame<>(16, 16);
        int first = frame.register(new int[] {7, 8});
        int second = frame.register(new int[] {7});
        frame.offer(first, 7, "mud", (left, right) -> right);
        frame.offer(first, 8, "mud", (left, right) -> right);

        List<RuntimeUvOwnership.Resolved<String>> before = frame.resolve(
                (owners, values) -> values.size() == owners ? values.values().iterator().next() : null);
        assertEquals(1, before.size());
        assertEquals(8, before.getFirst().pixel());
        assertEquals(2, frame.ownerCount(7));

        frame.offer(second, 7, "mud", (left, right) -> right);
        List<RuntimeUvOwnership.Resolved<String>> after = frame.resolve(
                (owners, values) -> values.size() == owners ? values.values().iterator().next() : null);
        assertEquals(2, after.size());
    }

    @Test
    void duplicateRenderPassesReuseOnePhysicalOwner() {
        RuntimeUvOwnership.Frame<String> frame = new RuntimeUvOwnership.Frame<>(16, 16);
        int first = frame.register(42L, new int[] {7, 8});
        int repeated = frame.register(42L, new int[] {7, 8});

        assertEquals(first, repeated);
        assertEquals(1, frame.ownerCount(7));
        assertEquals(1, frame.ownerCount(8));
    }

    @Test
    void invalidatesInsteadOfGrowingPastTheConfiguredBudget() {
        RuntimeUvOwnership.Frame<String> frame = new RuntimeUvOwnership.Frame<>(2, 2);
        frame.register(new int[] {1, 2, 3});

        assertFalse(frame.reliable());
        assertTrue(frame.resolve((owners, values) -> "mud").isEmpty());
    }

    @Test
    void oversizedUvFacesStopBeforeAllocatingTheWholeTexture() {
        assertEquals(32769, RuntimeUvOwnership.boundedRasterCapacity(1024 * 1024));
        assertEquals(4096, RuntimeUvOwnership.boundedRasterCapacity(64 * 64));
    }

    private static RuntimeUvQuadAssembler.Vertex uv(float u, float v) {
        RuntimeUvQuadAssembler.Vertex vertex = new RuntimeUvQuadAssembler.Vertex(Vec3.ZERO);
        vertex.u = u;
        vertex.v = v;
        vertex.hasUv = true;
        return vertex;
    }

    private static RuntimeUvQuadAssembler.Vertex[] quad(RuntimeUvQuadAssembler.Vertex... vertices) {
        return vertices;
    }
}
