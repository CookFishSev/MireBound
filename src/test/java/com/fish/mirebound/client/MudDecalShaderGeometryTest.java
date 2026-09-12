package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.stain.MudFootprintBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MudDecalShaderGeometryTest {
    @Test
    void wallDepthMatchesGeometryInBothOpaqueAndFadingPasses() throws ReflectiveOperationException {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("mirebound", "test_wall");
        assertSame(RenderStateShard.NO_LAYERING, layering(MudSurfaceDecalRenderTypes.wallState(texture, false)));
        assertSame(RenderStateShard.NO_LAYERING, layering(MudSurfaceDecalRenderTypes.wallState(texture, true)));
    }

    @Test
    void onlyHangingGeometryCastsShadowsAndGravityFollowsTheSableFrame() {
        Vec3 down = new Vec3(0, -1, 0);
        for (Direction face : Direction.values()) {
            assertEquals(face == Direction.DOWN,
                    MudFootprintBlockEntityRenderer.hasHangingGeometry(entry(face, true, true), down));
            assertFalse(MudFootprintBlockEntityRenderer.hasHangingGeometry(entry(face, false, true), down));
            assertFalse(MudFootprintBlockEntityRenderer.hasHangingGeometry(entry(face, true, false), down));
        }
        Vec3 tiltedDown = new Vec3(1, -1, 0).normalize();
        assertTrue(MudFootprintBlockEntityRenderer.hasHangingGeometry(entry(Direction.EAST, true, true), tiltedDown));
        assertTrue(MudFootprintBlockEntityRenderer.hasHangingGeometry(entry(Direction.DOWN, true, true), tiltedDown));
        assertFalse(MudFootprintBlockEntityRenderer.hasHangingGeometry(entry(Direction.WEST, true, true), tiltedDown));
    }

    @Test
    void slopedModelDecalRemainsPlanarOutsideTheSupportAtLargeWorldCoordinates() {
        Vec3 first = new Vec3(0, 0.25, 0);
        Vec3 second = new Vec3(0, 0.25, 1);
        Vec3 third = new Vec3(1, 0.75, 1);
        Vec3 fourth = new Vec3(1, 0.75, 0);
        Vec3 normal = second.subtract(first).cross(third.subtract(first)).normalize();
        var quad = new MudRenderedSurfaceGeometry.RenderedQuad(Direction.UP, first, second, third, fourth, normal);
        BlockPos support = new BlockPos(29_000_000, 80, -29_000_000);
        RecordingVertices output = new RecordingVertices();
        MudFootprintBlockEntityRenderer.renderSurfaceQuad(new PoseStack().last(), output, quad,
                support, support.above(), Direction.UP, 0, 1, 255, 0);
        assertEquals(4, output.positions.size());
        Vec3 origin = first.add(0, -1, 0);
        for (Vec3 point : output.positions) {
            double distance = point.subtract(origin).dot(normal);
            assertTrue(distance > 0 && distance < 0.001, "decal stays close to the exterior surface");
            assertEquals(output.positions.getFirst().subtract(origin).dot(normal), distance, 1e-7);
        }
        Vec3 triangleNormal = output.positions.get(1).subtract(output.positions.get(0))
                .cross(output.positions.get(2).subtract(output.positions.get(0))).normalize();
        assertEquals(1, normal.dot(triangleNormal), 1e-7);
    }

    private static Object layering(RenderType.CompositeState state) throws ReflectiveOperationException {
        var layeringField = state.getClass().getDeclaredField("layeringState");
        layeringField.setAccessible(true);
        return layeringField.get(state);
    }

    private static MudFootprintBlockEntity.Entry entry(Direction face, boolean wall, boolean precise) {
        return new MudFootprintBlockEntity.Entry(1, 0.5F, 0.5F, 0.5F, 0, face, wall,
                1, 1, 1, SinkingMedium.MUD, 0, precise ? new long[] {1} : new long[0], 0, 100, 1);
    }

    private static final class RecordingVertices implements VertexConsumer {
        private final List<Vec3> positions = new ArrayList<>();

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            positions.add(new Vec3(x, y, z));
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
