package com.fish.mirebound.client.generation;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningWandMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Batched translucent voxel shell and connected gray outline for live previews. */
public final class MudTerrainGenerationPreviewRenderer {
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final double FACE_OFFSET = 0.002D;
    private static final double LINE_CAMERA_OFFSET = 0.006D;
    private static final double AXIS_PADDING = 0.42D;
    private static final RenderType FACE = RenderType.create(
            Mirebound.MOD_ID + "_terrain_generation_preview",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            8_192,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            WHITE_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .createCompositeState(false));
    private static final RenderType OUTLINE = RenderType.create(
            Mirebound.MOD_ID + "_terrain_generation_outline",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            8_192,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(
                            OptionalDouble.of(2.0D)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .createCompositeState(false));

    private MudTerrainGenerationPreviewRenderer() {
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || minecraft.options.hideGui
                || MudTuningInputController.heldWandHand(minecraft.player) == null
                || MudTuningClientState.mode() != MudTuningWandMode.GENERATION) {
            return;
        }
        MudTerrainGenerationPreview.Preview preview =
                MudTerrainGenerationPreview.active(minecraft.level);
        if (preview == null || event.getFrustum() != null
                && !event.getFrustum().isVisible(preview.geometry().bounds())) {
            return;
        }

        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();
        VertexConsumer faces = buffers.getBuffer(FACE);
        VertexConsumer lines = buffers.getBuffer(OUTLINE);
        PoseStack.Pose pose = event.getPoseStack().last();
        Vec3 camera = event.getCamera().getPosition();
        for (MudTerrainGenerationPreviewGeometry.Face face
                : preview.geometry().faces()) {
            long packed = face.pos().asLong();
            boolean shell = preview.shell().contains(packed);
            renderFace(minecraft, pose, faces, camera,
                    face.pos(), face.direction(), shell);
        }
        for (MudTerrainGenerationPreviewGeometry.EdgeRun edge
                : preview.geometry().edges()) {
            renderEdge(pose, lines, camera, edge);
        }
        renderCore(minecraft, pose, faces, lines, camera,
                preview.request().center());
        renderAxis(pose, lines, camera, preview.request().center(),
                preview.geometry().bounds(),
                MudTerrainGenerationController.rotationAxis());
        buffers.endBatch(FACE);
        buffers.endBatch(OUTLINE);
    }

    private static void renderFace(
            Minecraft minecraft, PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 camera, BlockPos pos, Direction direction,
            boolean shell) {
        double minX = pos.getX() - FACE_OFFSET - camera.x;
        double minY = pos.getY() - FACE_OFFSET - camera.y;
        double minZ = pos.getZ() - FACE_OFFSET - camera.z;
        double maxX = pos.getX() + 1.0D + FACE_OFFSET - camera.x;
        double maxY = pos.getY() + 1.0D + FACE_OFFSET - camera.y;
        double maxZ = pos.getZ() + 1.0D + FACE_OFFSET - camera.z;
        float red = shell ? 0.58F : 0.92F;
        float green = shell ? 0.61F : 0.66F;
        float blue = shell ? 0.64F : 0.20F;
        float alpha = shell ? 0.13F : 0.20F;
        int light = LevelRenderer.getLightColor(minecraft.level, pos);
        switch (direction) {
            case DOWN -> quad(pose, vertices,
                    minX, minY, minZ, maxX, minY, minZ,
                    maxX, minY, maxZ, minX, minY, maxZ,
                    0.0F, -1.0F, 0.0F, red, green, blue, alpha, light);
            case UP -> quad(pose, vertices,
                    minX, maxY, minZ, minX, maxY, maxZ,
                    maxX, maxY, maxZ, maxX, maxY, minZ,
                    0.0F, 1.0F, 0.0F, red, green, blue, alpha, light);
            case NORTH -> quad(pose, vertices,
                    minX, minY, minZ, minX, maxY, minZ,
                    maxX, maxY, minZ, maxX, minY, minZ,
                    0.0F, 0.0F, -1.0F, red, green, blue, alpha, light);
            case SOUTH -> quad(pose, vertices,
                    minX, minY, maxZ, maxX, minY, maxZ,
                    maxX, maxY, maxZ, minX, maxY, maxZ,
                    0.0F, 0.0F, 1.0F, red, green, blue, alpha, light);
            case WEST -> quad(pose, vertices,
                    minX, minY, minZ, minX, minY, maxZ,
                    minX, maxY, maxZ, minX, maxY, minZ,
                    -1.0F, 0.0F, 0.0F, red, green, blue, alpha, light);
            case EAST -> quad(pose, vertices,
                    maxX, minY, minZ, maxX, maxY, minZ,
                    maxX, maxY, maxZ, maxX, minY, maxZ,
                    1.0F, 0.0F, 0.0F, red, green, blue, alpha, light);
        }
    }

    private static void renderEdge(
            PoseStack.Pose pose, VertexConsumer lines, Vec3 camera,
            MudTerrainGenerationPreviewGeometry.EdgeRun edge) {
        double endX = edge.axis() == 0 ? edge.x() + edge.length() : edge.x();
        double endY = edge.axis() == 1 ? edge.y() + edge.length() : edge.y();
        double endZ = edge.axis() == 2 ? edge.z() + edge.length() : edge.z();
        float normalX = edge.axis() == 0 ? 1.0F : 0.0F;
        float normalY = edge.axis() == 1 ? 1.0F : 0.0F;
        float normalZ = edge.axis() == 2 ? 1.0F : 0.0F;
        cameraBiasedLineVertex(pose, lines,
                edge.x() - camera.x, edge.y() - camera.y, edge.z() - camera.z,
                normalX, normalY, normalZ, 0.76F, 0.78F, 0.80F, 0.94F);
        cameraBiasedLineVertex(pose, lines,
                endX - camera.x, endY - camera.y, endZ - camera.z,
                normalX, normalY, normalZ, 0.76F, 0.78F, 0.80F, 0.94F);
    }

    private static void renderCore(
            Minecraft minecraft, PoseStack.Pose pose,
            VertexConsumer faces, VertexConsumer lines, Vec3 camera, BlockPos center) {
        Vec3 target = Vec3.atCenterOf(center).subtract(camera);
        double radius = 0.18D;
        int light = LevelRenderer.getLightColor(minecraft.level, center);
        double minX = target.x - radius;
        double minY = target.y - radius;
        double minZ = target.z - radius;
        double maxX = target.x + radius;
        double maxY = target.y + radius;
        double maxZ = target.z + radius;
        quad(pose, faces, minX, minY, minZ, maxX, minY, minZ,
                maxX, minY, maxZ, minX, minY, maxZ,
                0.0F, -1.0F, 0.0F, 1.0F, 0.72F, 0.18F, 0.42F, light);
        quad(pose, faces, minX, maxY, minZ, minX, maxY, maxZ,
                maxX, maxY, maxZ, maxX, maxY, minZ,
                0.0F, 1.0F, 0.0F, 1.0F, 0.72F, 0.18F, 0.42F, light);
        quad(pose, faces, minX, minY, minZ, minX, maxY, minZ,
                maxX, maxY, minZ, maxX, minY, minZ,
                0.0F, 0.0F, -1.0F, 1.0F, 0.72F, 0.18F, 0.42F, light);
        quad(pose, faces, minX, minY, maxZ, maxX, minY, maxZ,
                maxX, maxY, maxZ, minX, maxY, maxZ,
                0.0F, 0.0F, 1.0F, 1.0F, 0.72F, 0.18F, 0.42F, light);
        quad(pose, faces, minX, minY, minZ, minX, minY, maxZ,
                minX, maxY, maxZ, minX, maxY, minZ,
                -1.0F, 0.0F, 0.0F, 1.0F, 0.72F, 0.18F, 0.42F, light);
        quad(pose, faces, maxX, minY, minZ, maxX, maxY, minZ,
                maxX, maxY, maxZ, maxX, minY, maxZ,
                1.0F, 0.0F, 0.0F, 1.0F, 0.72F, 0.18F, 0.42F, light);
        double[][] edges = {
                {minX, minY, minZ, maxX, minY, minZ},
                {minX, minY, maxZ, maxX, minY, maxZ},
                {minX, maxY, minZ, maxX, maxY, minZ},
                {minX, maxY, maxZ, maxX, maxY, maxZ},
                {minX, minY, minZ, minX, maxY, minZ},
                {maxX, minY, minZ, maxX, maxY, minZ},
                {minX, minY, maxZ, minX, maxY, maxZ},
                {maxX, minY, maxZ, maxX, maxY, maxZ},
                {minX, minY, minZ, minX, minY, maxZ},
                {maxX, minY, minZ, maxX, minY, maxZ},
                {minX, maxY, minZ, minX, maxY, maxZ},
                {maxX, maxY, minZ, maxX, maxY, maxZ}
        };
        for (double[] edge : edges) {
            Vec3 normal = new Vec3(
                    edge[3] - edge[0], edge[4] - edge[1], edge[5] - edge[2])
                    .normalize();
            cameraBiasedLineVertex(pose, lines, edge[0], edge[1], edge[2],
                    (float) normal.x, (float) normal.y, (float) normal.z,
                    1.0F, 0.82F, 0.28F, 1.0F);
            cameraBiasedLineVertex(pose, lines, edge[3], edge[4], edge[5],
                    (float) normal.x, (float) normal.y, (float) normal.z,
                    1.0F, 0.82F, 0.28F, 1.0F);
        }
    }

    private static void renderAxis(
            PoseStack.Pose pose, VertexConsumer lines, Vec3 camera,
            BlockPos center, AABB bounds, Direction.Axis axis) {
        double centerX = center.getX() + 0.5D;
        double centerY = center.getY() + 0.5D;
        double centerZ = center.getZ() + 0.5D;
        double startX = centerX;
        double startY = centerY;
        double startZ = centerZ;
        double endX = centerX;
        double endY = centerY;
        double endZ = centerZ;
        switch (axis) {
            case X -> {
                startX = bounds.minX - AXIS_PADDING;
                endX = bounds.maxX + AXIS_PADDING;
            }
            case Y -> {
                startY = bounds.minY - AXIS_PADDING;
                endY = bounds.maxY + AXIS_PADDING;
            }
            case Z -> {
                startZ = bounds.minZ - AXIS_PADDING;
                endZ = bounds.maxZ + AXIS_PADDING;
            }
        }
        float red = axis == Direction.Axis.X ? 1.0F : 0.28F;
        float green = axis == Direction.Axis.Y ? 1.0F : 0.38F;
        float blue = axis == Direction.Axis.Z ? 1.0F : 0.26F;
        float normalX = axis == Direction.Axis.X ? 1.0F : 0.0F;
        float normalY = axis == Direction.Axis.Y ? 1.0F : 0.0F;
        float normalZ = axis == Direction.Axis.Z ? 1.0F : 0.0F;
        cameraBiasedLineVertex(pose, lines,
                startX - camera.x, startY - camera.y, startZ - camera.z,
                normalX, normalY, normalZ, red, green, blue, 1.0F);
        cameraBiasedLineVertex(pose, lines,
                endX - camera.x, endY - camera.y, endZ - camera.z,
                normalX, normalY, normalZ, red, green, blue, 1.0F);
        renderAxisArrow(pose, lines, camera, axis,
                endX, endY, endZ, red, green, blue);
    }

    private static void renderAxisArrow(
            PoseStack.Pose pose, VertexConsumer lines, Vec3 camera,
            Direction.Axis axis, double tipX, double tipY, double tipZ,
            float red, float green, float blue) {
        double backX = tipX - (axis == Direction.Axis.X ? 0.28D : 0.0D);
        double backY = tipY - (axis == Direction.Axis.Y ? 0.28D : 0.0D);
        double backZ = tipZ - (axis == Direction.Axis.Z ? 0.28D : 0.0D);
        for (int sign = -1; sign <= 1; sign += 2) {
            double wingX = backX + (axis == Direction.Axis.X ? 0.0D : sign * 0.14D);
            double wingY = backY + (axis == Direction.Axis.Y ? 0.0D
                    : axis == Direction.Axis.X ? sign * 0.14D : 0.0D);
            double wingZ = backZ + (axis == Direction.Axis.Z ? 0.0D
                    : axis == Direction.Axis.Y ? sign * 0.14D : 0.0D);
            cameraBiasedLineVertex(pose, lines,
                    tipX - camera.x, tipY - camera.y, tipZ - camera.z,
                    0.0F, 1.0F, 0.0F, red, green, blue, 1.0F);
            cameraBiasedLineVertex(pose, lines,
                    wingX - camera.x, wingY - camera.y, wingZ - camera.z,
                    0.0F, 1.0F, 0.0F, red, green, blue, 1.0F);
        }
    }

    private static void quad(
            PoseStack.Pose pose, VertexConsumer vertices,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            float normalX, float normalY, float normalZ,
            float red, float green, float blue, float alpha, int light) {
        vertex(pose, vertices, x0, y0, z0, 0.0F, 0.0F,
                normalX, normalY, normalZ, red, green, blue, alpha, light);
        vertex(pose, vertices, x1, y1, z1, 0.0F, 1.0F,
                normalX, normalY, normalZ, red, green, blue, alpha, light);
        vertex(pose, vertices, x2, y2, z2, 1.0F, 1.0F,
                normalX, normalY, normalZ, red, green, blue, alpha, light);
        vertex(pose, vertices, x3, y3, z3, 1.0F, 0.0F,
                normalX, normalY, normalZ, red, green, blue, alpha, light);
    }

    private static void vertex(
            PoseStack.Pose pose, VertexConsumer vertices,
            double x, double y, double z, float u, float v,
            float normalX, float normalY, float normalZ,
            float red, float green, float blue, float alpha, int light) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static void lineVertex(
            PoseStack.Pose pose, VertexConsumer lines,
            double x, double y, double z,
            float normalX, float normalY, float normalZ,
            float red, float green, float blue, float alpha) {
        lines.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static void cameraBiasedLineVertex(
            PoseStack.Pose pose, VertexConsumer lines,
            double x, double y, double z,
            float normalX, float normalY, float normalZ,
            float red, float green, float blue, float alpha) {
        double distance = Math.sqrt(x * x + y * y + z * z);
        double scale = distance > LINE_CAMERA_OFFSET
                ? (distance - LINE_CAMERA_OFFSET) / distance : 1.0D;
        lineVertex(pose, lines, x * scale, y * scale, z * scale,
                normalX, normalY, normalZ, red, green, blue, alpha);
    }
}
