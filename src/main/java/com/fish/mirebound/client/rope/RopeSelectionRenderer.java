package com.fish.mirebound.client.rope;

import com.fish.mirebound.client.compat.freecam.FreecamCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Renders client-only rope interaction and fixed-section boxes. */
public final class RopeSelectionRenderer {
    private static final RenderType FACE_TYPE = RopeSelectionRenderTypes.face();
    private static final RenderType EDGE_TYPE = RopeSelectionRenderTypes.edge();
    private static final double HALF_WIDTH = 2.125D / 16.0D + 0.018D;
    private static final double HALF_LENGTH = 0.5D + 0.018D;

    private RopeSelectionRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui
                || minecraft.player == null || minecraft.player.isSpectator()
                || FreecamCompat.isExternalCameraActive(minecraft)
                || ClientRopes.isHoldingTuningWand(minecraft.player)) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (ClientRopes.isHoldingRope(minecraft.player)) {
            List<ClientRopes.Selection> endpoints = ClientRopes.endpointSelections(partialTick);
            List<ClientRopes.Selection> anchored = minecraft.player.isCreative()
                    ? ClientRopes.anchoredSelections(partialTick) : List.of();
            List<ClientRopes.Selection> rescueAnchored = minecraft.player.isCreative()
                    ? ClientRopes.rescueAnchoredSelections(partialTick) : List.of();
            ClientRopes.Selection selection = ClientRopes.selection(partialTick);
            if (endpoints.isEmpty() && anchored.isEmpty() && rescueAnchored.isEmpty()
                    && selection == null) {
                return;
            }
            if (!anchored.isEmpty()) {
                renderSelections(event, anchored, 1.0F, 0.12F, 0.08F, 0.10F);
            }
            if (!endpoints.isEmpty()) {
                renderSelections(event, endpoints, 1.0F, 0.78F, 0.12F, 0.10F);
            }
            if (!rescueAnchored.isEmpty()) {
                renderSelections(event, rescueAnchored, 0.08F, 0.24F, 0.72F, 0.14F);
            }
            boolean selectedEndpoint = selection != null
                    && endpoints.stream().anyMatch(endpoint ->
                            endpoint.ropeId() == selection.ropeId()
                                    && endpoint.segmentIndex() == selection.segmentIndex());
            if (selection != null && !selectedEndpoint) {
                renderSelections(event, List.of(selection), 1.0F, 1.0F, 1.0F, 0.10F);
            }
            return;
        }
        List<ClientRopes.Selection> anchored = minecraft.player.isCreative()
                ? ClientRopes.anchoredSelections(partialTick) : List.of();
        List<ClientRopes.Selection> rescueAnchored = minecraft.player.isCreative()
                ? ClientRopes.rescueAnchoredSelections(partialTick) : List.of();
        List<ClientRopes.Selection> connectable = ClientRopes.connectSelections(partialTick);
        ClientRopes.Selection hauling = ClientRopes.rescueHaulSelection(partialTick);
        ClientRopes.Selection selection = ClientRopes.selection(partialTick);
        if (anchored.isEmpty() && rescueAnchored.isEmpty()
                && connectable.isEmpty() && hauling == null && selection == null) {
            return;
        }
        renderSelections(event, rescueAnchored, 0.08F, 0.24F, 0.72F, 0.14F);
        renderSelections(event, anchored, 1.0F, 0.12F, 0.08F, 0.10F);
        renderSelections(event, connectable, 1.0F, 0.78F, 0.12F, 0.10F);
        if (hauling != null) {
            renderSelections(event, List.of(hauling), 0.20F, 0.95F, 0.38F, 0.14F);
        } else if (selection != null
                && (!selection.anchored() || !minecraft.player.isCreative())) {
            float red = selection.dragging() ? 0.45F : 1.0F;
            float green = selection.dragging() ? 0.82F : 1.0F;
            renderSelections(event, List.of(selection), red, green, 1.0F,
                    selection.dragging() ? 0.12F : 0.10F);
        }
    }

    private static void renderSelections(RenderLevelStageEvent event,
            List<ClientRopes.Selection> selections, float red, float green,
            float blue, float faceAlpha) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer faces = buffers.getBuffer(FACE_TYPE);
        VertexConsumer edges = buffers.getBuffer(EDGE_TYPE);
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();
        for (ClientRopes.Selection fixed : selections) {
            renderSelection(pose, faces, edges, fixed, red, green, blue, faceAlpha);
        }
        poseStack.popPose();
        buffers.endBatch(FACE_TYPE);
        buffers.endBatch(EDGE_TYPE);
    }

    private static void renderSelection(PoseStack.Pose pose, VertexConsumer faces,
            VertexConsumer edges, ClientRopes.Selection selection,
            float red, float green, float blue, float faceAlpha) {
        RopeSelectionGeometry.Box box = RopeSelectionGeometry.of(
                selection.start(), selection.end(), selection.frame(),
                HALF_WIDTH, HALF_LENGTH);
        List<Vec3> corners = box.corners();
        for (int[] face : RopeSelectionGeometry.faces()) {
            quad(pose, faces, corners.get(face[0]), corners.get(face[1]),
                    corners.get(face[2]), corners.get(face[3]),
                    red, green, blue, faceAlpha);
        }
        for (int[] edge : RopeSelectionGeometry.edges()) {
            line(pose, edges, corners.get(edge[0]), corners.get(edge[1]),
                    red, green, blue, selection.anchored() ? 0.98F
                            : selection.dragging() ? 0.92F : 0.98F);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float red, float green, float blue, float alpha) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() <= 1.0E-10D) {
            return;
        }
        normal = normal.normalize();
        vertex(pose, vertices, a, 0.0F, 0.0F, normal, red, green, blue, alpha);
        vertex(pose, vertices, b, 0.0F, 1.0F, normal, red, green, blue, alpha);
        vertex(pose, vertices, c, 1.0F, 1.0F, normal, red, green, blue, alpha);
        vertex(pose, vertices, d, 1.0F, 0.0F, normal, red, green, blue, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, Vec3 normal,
            float red, float green, float blue, float alpha) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 start, Vec3 end, float red, float green, float blue, float alpha) {
        Vec3 normal = end.subtract(start);
        if (normal.lengthSqr() <= 1.0E-10D) {
            return;
        }
        normal = normal.normalize();
        vertices.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        vertices.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
