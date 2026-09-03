package com.fish.mirebound.client;

import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Owns static GPU buffers for ordinary-world tuning highlight sections. */
final class MudTuningSectionHighlightGpuCache {
    private static final double FACE_OFFSET = 0.004D;
    private static final Map<MudTuningSectionHighlightCache.SectionKey, SectionBuffers>
            BUFFERS = new HashMap<>();
    private static final List<SectionBuffers> VISIBLE = new ArrayList<>();
    private static final ByteBufferBuilder STAGING = new ByteBufferBuilder(64 * 1024);

    private MudTuningSectionHighlightGpuCache() {
    }

    static void render(
            Collection<MudTuningSectionHighlightGeometry.SectionGeometry> sections,
            PoseStack.Pose pose, Vec3 camera, Vec3 playerPosition,
            double maxDistanceSquared, Frustum frustum,
            boolean renderIncompatible) {
        VISIBLE.clear();
        try {
            for (MudTuningSectionHighlightGeometry.SectionGeometry section : sections) {
                if (visible(section.bounds(), playerPosition,
                        maxDistanceSquared, frustum)) {
                    SectionBuffers buffers = buffers(section);
                    if (buffers.hasGeometry(renderIncompatible)) {
                        VISIBLE.add(buffers);
                    }
                }
            }
            renderPass(VISIBLE, pose, camera, true, renderIncompatible);
            renderPass(VISIBLE, pose, camera, false, renderIncompatible);
            renderMixedPass(VISIBLE, pose, camera, true);
            renderMixedPass(VISIBLE, pose, camera, false);
        } finally {
            VISIBLE.clear();
        }
    }

    static void invalidate(MudTuningSectionHighlightCache.SectionKey key) {
        SectionBuffers removed = BUFFERS.remove(key);
        if (removed != null) {
            removed.close();
        }
    }

    static void reset() {
        for (SectionBuffers buffers : BUFFERS.values()) {
            buffers.close();
        }
        BUFFERS.clear();
    }

    private static void renderPass(
            List<SectionBuffers> visible, PoseStack.Pose pose,
            Vec3 camera, boolean faces, boolean renderIncompatible) {
        RenderType renderType = faces
                ? MudTuningTargetRenderTypes.face() : RenderType.lines();
        renderType.setupRenderState();
        try {
            // Vertex colors are already baked per highlight kind; do not inherit a tint
            // left by another render pass or by the animated mixed-color pass.
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) {
                return;
            }
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            Matrix4f baseModelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                    .mul(pose.pose());
            Matrix4f modelView = new Matrix4f();
            for (SectionBuffers buffers : visible) {
                MudTuningSectionHighlightCache.SectionKey key = buffers.source.key();
                modelView.set(baseModelView).translate(
                        (float) ((key.x() << 4) - camera.x),
                        (float) ((key.y() << 4) - camera.y),
                        (float) ((key.z() << 4) - camera.z));
                draw(faces ? buffers.persistent.faces : buffers.persistent.edges,
                        modelView, projection, shader);
                if (renderIncompatible) {
                    draw(faces ? buffers.incompatible.faces : buffers.incompatible.edges,
                            modelView, projection, shader);
                }
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    private static void renderMixedPass(
            List<SectionBuffers> visible, PoseStack.Pose pose, Vec3 camera,
            boolean faces) {
        RenderType renderType = faces
                ? MudTuningTargetRenderTypes.face() : RenderType.lines();
        renderType.setupRenderState();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) {
                return;
            }
            MudTuningConnectedHighlightRenderer.Color color =
                    MudTuningConnectedHighlightRenderer.Color.forKind(
                            MudTuningSelectionPayload.HighlightKind.MODIFIED_NATIVE_FLOW_MIXED);
            RenderSystem.setShaderColor(color.red(), color.green(), color.blue(), 1.0F);
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            Matrix4f baseModelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                    .mul(pose.pose());
            Matrix4f modelView = new Matrix4f();
            for (SectionBuffers buffers : visible) {
                MudTuningSectionHighlightCache.SectionKey key = buffers.source.key();
                modelView.set(baseModelView).translate(
                        (float) ((key.x() << 4) - camera.x),
                        (float) ((key.y() << 4) - camera.y),
                        (float) ((key.z() << 4) - camera.z));
                draw(faces ? buffers.mixed.faces : buffers.mixed.edges,
                        modelView, projection, shader);
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    private static void draw(VertexBuffer buffer, Matrix4f modelView,
            Matrix4f projection, ShaderInstance shader) {
        if (buffer == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
    }

    private static boolean visible(AABB bounds, Vec3 playerPosition,
            double maxDistanceSquared, Frustum frustum) {
        return distanceToSqr(bounds, playerPosition) <= maxDistanceSquared
                && (frustum == null || frustum.isVisible(bounds));
    }

    private static SectionBuffers buffers(
            MudTuningSectionHighlightGeometry.SectionGeometry geometry) {
        SectionBuffers cached = BUFFERS.get(geometry.key());
        if (cached != null && cached.source == geometry) {
            return cached;
        }
        if (cached != null) {
            cached.close();
        }
        SectionBuffers rebuilt = buildBuffers(geometry);
        BUFFERS.put(geometry.key(), rebuilt);
        return rebuilt;
    }

    private static SectionBuffers buildBuffers(
            MudTuningSectionHighlightGeometry.SectionGeometry geometry) {
        RenderBuffers persistent = buildBuffers(geometry, false);
        try {
            return new SectionBuffers(
                    geometry, persistent, buildBuffers(geometry, true),
                    buildBuffers(geometry, false, true));
        } catch (RuntimeException exception) {
            persistent.close();
            throw exception;
        }
    }

    private static RenderBuffers buildBuffers(
            MudTuningSectionHighlightGeometry.SectionGeometry geometry,
            boolean incompatible) {
        return buildBuffers(geometry, incompatible, false);
    }

    private static RenderBuffers buildBuffers(
            MudTuningSectionHighlightGeometry.SectionGeometry geometry,
            boolean incompatible, boolean animated) {
        if (!containsKinds(geometry, incompatible, animated)) {
            return RenderBuffers.EMPTY;
        }
        VertexBuffer faces = buildFaces(geometry, incompatible, animated);
        try {
            return new RenderBuffers(faces, buildEdges(geometry, incompatible, animated));
        } catch (RuntimeException exception) {
            if (faces != null) {
                faces.close();
            }
            throw exception;
        }
    }

    private static boolean containsKinds(
            MudTuningSectionHighlightGeometry.SectionGeometry geometry,
            boolean incompatible, boolean animated) {
        for (MudTuningSectionHighlightGeometry.KindGeometry kind : geometry.kinds()) {
            if (isIncompatible(kind) == incompatible
                    && MudTuningConnectedHighlightRenderer.isAnimated(kind.kind()) == animated) {
                return true;
            }
        }
        return false;
    }

    private static VertexBuffer buildFaces(
            MudTuningSectionHighlightGeometry.SectionGeometry geometry,
            boolean incompatible, boolean animated) {
        RenderType renderType = MudTuningTargetRenderTypes.face();
        BufferBuilder builder = begin(renderType);
        int baseX = geometry.key().x() << 4;
        int baseY = geometry.key().y() << 4;
        int baseZ = geometry.key().z() << 4;
        for (MudTuningSectionHighlightGeometry.KindGeometry kind : geometry.kinds()) {
            if (isIncompatible(kind) != incompatible
                    || MudTuningConnectedHighlightRenderer.isAnimated(kind.kind()) != animated) {
                continue;
            }
            MudTuningConnectedHighlightRenderer.Color color = animated
                    ? MudTuningConnectedHighlightRenderer.Color.WHITE
                    : MudTuningConnectedHighlightRenderer.Color.forKind(kind.kind());
            for (MudTuningConnectedHighlightRenderer.FaceKey face : kind.faces()) {
                emitFace(builder, face.pos(), face.direction(),
                        baseX, baseY, baseZ, color);
            }
        }
        return upload(builder.build());
    }

    private static VertexBuffer buildEdges(
            MudTuningSectionHighlightGeometry.SectionGeometry geometry,
            boolean incompatible, boolean animated) {
        RenderType renderType = RenderType.lines();
        BufferBuilder builder = begin(renderType);
        int baseX = geometry.key().x() << 4;
        int baseY = geometry.key().y() << 4;
        int baseZ = geometry.key().z() << 4;
        for (MudTuningSectionHighlightGeometry.KindGeometry kind : geometry.kinds()) {
            if (isIncompatible(kind) != incompatible
                    || MudTuningConnectedHighlightRenderer.isAnimated(kind.kind()) != animated) {
                continue;
            }
            MudTuningConnectedHighlightRenderer.Color color = animated
                    ? MudTuningConnectedHighlightRenderer.Color.WHITE
                    : MudTuningConnectedHighlightRenderer.Color.forKind(kind.kind());
            for (MudTuningConnectedHighlightRenderer.EdgeRun edge : kind.edges()) {
                emitEdge(builder, edge, baseX, baseY, baseZ, color);
            }
        }
        return upload(builder.build());
    }

    private static boolean isIncompatible(
            MudTuningSectionHighlightGeometry.KindGeometry kind) {
        return kind.kind() == MudTuningSelectionPayload.HighlightKind.INCOMPATIBLE;
    }

    private static BufferBuilder begin(RenderType renderType) {
        STAGING.discard();
        return new BufferBuilder(STAGING, renderType.mode(), renderType.format());
    }

    private static VertexBuffer upload(MeshData mesh) {
        if (mesh == null) {
            return null;
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            buffer.bind();
            buffer.upload(mesh);
            return buffer;
        } catch (RuntimeException exception) {
            buffer.close();
            throw exception;
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void emitFace(VertexConsumer vertices, BlockPos pos,
            Direction direction, int baseX, int baseY, int baseZ,
            MudTuningConnectedHighlightRenderer.Color color) {
        float minX = (float) (pos.getX() - baseX - FACE_OFFSET);
        float minY = (float) (pos.getY() - baseY - FACE_OFFSET);
        float minZ = (float) (pos.getZ() - baseZ - FACE_OFFSET);
        float maxX = (float) (pos.getX() - baseX + 1.0D + FACE_OFFSET);
        float maxY = (float) (pos.getY() - baseY + 1.0D + FACE_OFFSET);
        float maxZ = (float) (pos.getZ() - baseZ + 1.0D + FACE_OFFSET);
        switch (direction) {
            case DOWN -> quad(vertices,
                    minX, minY, minZ, maxX, minY, minZ,
                    maxX, minY, maxZ, minX, minY, maxZ,
                    0.0F, -1.0F, 0.0F, color);
            case UP -> quad(vertices,
                    minX, maxY, minZ, minX, maxY, maxZ,
                    maxX, maxY, maxZ, maxX, maxY, minZ,
                    0.0F, 1.0F, 0.0F, color);
            case NORTH -> quad(vertices,
                    minX, minY, minZ, minX, maxY, minZ,
                    maxX, maxY, minZ, maxX, minY, minZ,
                    0.0F, 0.0F, -1.0F, color);
            case SOUTH -> quad(vertices,
                    minX, minY, maxZ, maxX, minY, maxZ,
                    maxX, maxY, maxZ, minX, maxY, maxZ,
                    0.0F, 0.0F, 1.0F, color);
            case WEST -> quad(vertices,
                    minX, minY, minZ, minX, minY, maxZ,
                    minX, maxY, maxZ, minX, maxY, minZ,
                    -1.0F, 0.0F, 0.0F, color);
            case EAST -> quad(vertices,
                    maxX, minY, minZ, maxX, maxY, minZ,
                    maxX, maxY, maxZ, maxX, minY, maxZ,
                    1.0F, 0.0F, 0.0F, color);
        }
    }

    private static void quad(VertexConsumer vertices,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float normalX, float normalY, float normalZ,
            MudTuningConnectedHighlightRenderer.Color color) {
        vertex(vertices, x0, y0, z0, 0.0F, 0.0F,
                normalX, normalY, normalZ, color, 0.12F);
        vertex(vertices, x1, y1, z1, 0.0F, 1.0F,
                normalX, normalY, normalZ, color, 0.12F);
        vertex(vertices, x2, y2, z2, 1.0F, 1.0F,
                normalX, normalY, normalZ, color, 0.12F);
        vertex(vertices, x3, y3, z3, 1.0F, 0.0F,
                normalX, normalY, normalZ, color, 0.12F);
    }

    private static void vertex(VertexConsumer vertices,
            float x, float y, float z, float u, float v,
            float normalX, float normalY, float normalZ,
            MudTuningConnectedHighlightRenderer.Color color, float alpha) {
        vertices.addVertex(x, y, z)
                .setColor(color.red(), color.green(), color.blue(), alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normalX, normalY, normalZ);
    }

    private static void emitEdge(VertexConsumer vertices,
            MudTuningConnectedHighlightRenderer.EdgeRun edge,
            int baseX, int baseY, int baseZ,
            MudTuningConnectedHighlightRenderer.Color color) {
        float startX = edge.x() - baseX;
        float startY = edge.y() - baseY;
        float startZ = edge.z() - baseZ;
        float endX = startX + (edge.axis() == 0 ? edge.length() : 0);
        float endY = startY + (edge.axis() == 1 ? edge.length() : 0);
        float endZ = startZ + (edge.axis() == 2 ? edge.length() : 0);
        float normalX = edge.axis() == 0 ? 1.0F : 0.0F;
        float normalY = edge.axis() == 1 ? 1.0F : 0.0F;
        float normalZ = edge.axis() == 2 ? 1.0F : 0.0F;
        vertices.addVertex(startX, startY, startZ)
                .setColor(color.red(), color.green(), color.blue(), 0.98F)
                .setNormal(normalX, normalY, normalZ);
        vertices.addVertex(endX, endY, endZ)
                .setColor(color.red(), color.green(), color.blue(), 0.98F)
                .setNormal(normalX, normalY, normalZ);
    }

    private static double distanceToSqr(AABB bounds, Vec3 point) {
        double dx = Math.max(Math.max(bounds.minX - point.x, 0.0D), point.x - bounds.maxX);
        double dy = Math.max(Math.max(bounds.minY - point.y, 0.0D), point.y - bounds.maxY);
        double dz = Math.max(Math.max(bounds.minZ - point.z, 0.0D), point.z - bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class SectionBuffers implements AutoCloseable {
        private final MudTuningSectionHighlightGeometry.SectionGeometry source;
        private final RenderBuffers persistent;
        private final RenderBuffers incompatible;
        private final RenderBuffers mixed;

        private SectionBuffers(
                MudTuningSectionHighlightGeometry.SectionGeometry source,
                RenderBuffers persistent, RenderBuffers incompatible, RenderBuffers mixed) {
            this.source = source;
            this.persistent = persistent;
            this.incompatible = incompatible;
            this.mixed = mixed;
        }

        private boolean hasGeometry(boolean renderIncompatible) {
            return persistent.hasGeometry()
                    || mixed.hasGeometry()
                    || renderIncompatible && incompatible.hasGeometry();
        }

        @Override
        public void close() {
            persistent.close();
            incompatible.close();
            mixed.close();
        }
    }

    private record RenderBuffers(VertexBuffer faces, VertexBuffer edges)
            implements AutoCloseable {
        private static final RenderBuffers EMPTY = new RenderBuffers(null, null);

        private boolean hasGeometry() {
            return faces != null || edges != null;
        }

        @Override
        public void close() {
            if (faces != null) {
                faces.close();
            }
            if (edges != null) {
                edges.close();
            }
        }
    }
}
