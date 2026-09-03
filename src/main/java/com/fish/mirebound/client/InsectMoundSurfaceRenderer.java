package com.fish.mirebound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Grid-aligned half-pixel-high insect trails, shared across adjacent mound blocks. */
final class InsectMoundSurfaceRenderer {
    private static final ResourceLocation TRAIL_TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/block/white_concrete.png");
    private static final RenderType TRAIL_RENDER_TYPE = RenderType.entityCutoutNoCull(
            TRAIL_TEXTURE);
    private static final double HALF_PIXEL_HEIGHT = 0.5D / 16.0D;
    private static final double HALF_PIXEL_WIDTH = 0.5D / 16.0D;
    private static final int MAX_RENDERED_CELLS = 2048;

    private InsectMoundSurfaceRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || !MudSurfaceClientSettings.enabled()
                || !MudSurfaceClientSettings.insectMoundEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double renderDistanceSquared = Mth.square(MudSurfaceClientSettings.renderDistance());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        boolean rendered = false;
        int renderedCells = 0;

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();
        outer:
        for (InsectMoundSurfaceManager.Face face : InsectMoundSurfaceManager.faces()) {
            if (face.center().distanceToSqr(camera) > renderDistanceSquared) {
                continue;
            }
            AABB bounds = new AABB(face.center(), face.center()).inflate(0.82D);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(bounds)) {
                continue;
            }
            for (InsectMoundSurfaceManager.Trail trail : face.trails()) {
                float emergence = Mth.lerp(partialTick,
                        trail.previousEmergence(), trail.emergence());
                int length = trail.length();
                for (int index = 0; index < length; index++) {
                    if (renderedCells >= MAX_RENDERED_CELLS) {
                        break outer;
                    }
                    double cellEmergence = Mth.clamp(
                            emergence * length - index, 0.0D, 1.0D);
                    if (cellEmergence <= 0.015D) {
                        continue;
                    }
                    Vec3 point = face.pointFor(trail.u(index), trail.v(index));
                    int light = LevelRenderer.getLightColor(minecraft.level,
                            BlockPos.containing(point.subtract(face.normal().scale(0.02D))));
                    int shade = cellShade(face.seed(), trail.u(index), trail.v(index), index);
                    renderCell(pose, buffers.getBuffer(TRAIL_RENDER_TYPE), point,
                            face.axisU(), face.axisV(), face.normal(),
                            HALF_PIXEL_HEIGHT * cellEmergence,
                            HALF_PIXEL_WIDTH, shade, light);
                    rendered = true;
                    renderedCells++;
                }
            }
        }
        poseStack.popPose();
        if (rendered) {
            buffers.endBatch(TRAIL_RENDER_TYPE);
        }
    }

    private static void renderCell(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 base, Vec3 axisU, Vec3 axisV, Vec3 normal,
            double height, double halfWidth, int shade, int light) {
        Vec3 u = axisU.scale(halfWidth);
        Vec3 v = axisV.scale(halfWidth);
        Vec3 a = base.subtract(u).subtract(v);
        Vec3 b = base.add(u).subtract(v);
        Vec3 c = base.add(u).add(v);
        Vec3 d = base.subtract(u).add(v);
        Vec3 lift = normal.scale(height);
        Vec3 ta = a.add(lift);
        Vec3 tb = b.add(lift);
        Vec3 tc = c.add(lift);
        Vec3 td = d.add(lift);
        quad(pose, vertices, ta, tb, tc, td, normal, shade, light);
        quad(pose, vertices, a, b, tb, ta, axisV.scale(-1.0D), shade - 22, light);
        quad(pose, vertices, b, c, tc, tb, axisU, shade - 12, light);
        quad(pose, vertices, c, d, td, tc, axisV, shade - 30, light);
        quad(pose, vertices, d, a, ta, td, axisU.scale(-1.0D), shade - 18, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal,
            int shade, int light) {
        int color = Mth.clamp(shade, 0, 255);
        vertex(pose, vertices, a, 0.0F, 0.0F, normal, color, light);
        vertex(pose, vertices, b, 1.0F, 0.0F, normal, color, light);
        vertex(pose, vertices, c, 1.0F, 1.0F, normal, color, light);
        vertex(pose, vertices, d, 0.0F, 1.0F, normal, color, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, Vec3 normal, int shade, int light) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(shade, shade, Math.min(255, shade + 6), 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static int cellShade(long seed, int u, int v, int index) {
        long value = seed ^ (long) u * 0x9E3779B97F4A7C15L
                ^ (long) v * 0xBF58476D1CE4E5B9L ^ index * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return 218 + (int) ((value >>> 12) & 0x1FL);
    }
}
