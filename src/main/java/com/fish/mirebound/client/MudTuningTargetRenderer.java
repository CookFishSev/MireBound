package com.fish.mirebound.client;

import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningClientSettings;
import com.fish.mirebound.client.tuning.MudTuningSpatialPlacement;
import com.fish.mirebound.client.tuning.MudTuningTentacleTargeting;
import com.fish.mirebound.client.tuning.MudTuningWandTargeting;
import com.fish.mirebound.client.tuning.MudTuningWandMode;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Replaces the thin vanilla block outline while the tuning wand is held. */
final class MudTuningTargetRenderer {
    private static final double INFLATION = 0.006D;
    private static final double EDGE_HALF_WIDTH = 1.0D / 64.0D;
    private static final float FACE_ALPHA = 0.12F;
    private static final float EDGE_ALPHA = 0.96F;
    private static final double SUMMON_INNER_SCALE = 0.72D;
    private static final int[][] FACES = {
            {0, 2, 3, 1}, {4, 5, 7, 6},
            {0, 4, 6, 2}, {1, 3, 7, 5},
            {0, 1, 5, 4}, {2, 6, 7, 3}
    };
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };
    private static final int[] EDGE_SIDE_FACES = {0, 1, 4, 5};

    private MudTuningTargetRenderer() {
    }

    static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && isHoldingTuningWand(minecraft)) {
            event.setCanceled(true);
        }
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui || !isHoldingTuningWand(minecraft)) {
            return;
        }

        RenderTarget target = renderTarget(minecraft);
        if (target == null) {
            return;
        }
        BlockPos pos = target.pos();
        Object subLevel = target.subLevel();

        AABB localBounds = target.bounds() == null
                ? targetBounds(minecraft, pos) : target.bounds();
        Vec3 camera = event.getCamera().getPosition();
        Vec3[] corners = boxCorners(pos, localBounds, subLevel, camera);
        if (corners == null) {
            return;
        }

        RenderType faceType = MudTuningTargetRenderTypes.face();
        RenderType edgeType = MudTuningTargetRenderTypes.edge();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer faces = buffers.getBuffer(faceType);
        VertexConsumer edges = buffers.getBuffer(edgeType);
        PoseStack.Pose pose = event.getPoseStack().last();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = minecraft.level.getGameTime() + partialTick;
        int color = MudTuningClientSettings.color(
                MudTuningClientSettings.HudColor.TARGET);
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        if (target.rotatingSummon()) {
            float outerAngle = (float) Math.toRadians(time * 2.6D);
            float innerAngle = (float) Math.toRadians(-time * 3.7D);
            renderBox(pose, faces, edges,
                    rotateBox(corners, 1.0D, outerAngle,
                            (float) Math.toRadians(28.0D), outerAngle * 0.18F),
                    red, green, blue, FACE_ALPHA * 0.72F, EDGE_ALPHA);
            renderBox(pose, faces, edges,
                    rotateBox(corners, SUMMON_INNER_SCALE, innerAngle,
                            (float) Math.toRadians(52.0D), -innerAngle * 0.22F),
                    red, green, blue, FACE_ALPHA * 0.92F, EDGE_ALPHA * 0.88F);
        } else {
            renderBox(pose, faces, edges, corners,
                    red, green, blue, FACE_ALPHA, EDGE_ALPHA);
        }
        buffers.endBatch(faceType);
        buffers.endBatch(edgeType);
    }

    private static boolean isHoldingTuningWand(Minecraft minecraft) {
        return minecraft.player.getMainHandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()
                || minecraft.player.getOffhandItem().getItem()
                        == ModBlocks.MUD_TUNING_WAND.get();
    }

    private static RenderTarget renderTarget(Minecraft minecraft) {
        if (MudTuningClientState.mode() == MudTuningWandMode.GENERATION) {
            return null;
        }
        if (MudTuningTentacleTargeting.target(minecraft) != null) {
            return null;
        }
        Vec3 placement = MudTuningSpatialPlacement.target(minecraft);
        if (placement != null) {
            return new RenderTarget(BlockPos.ZERO,
                    MudTuningSpatialPlacement.bounds(placement), null,
                    MudTuningClientState.mode() == MudTuningWandMode.SUMMON);
        }
        BlockHitResult hit = MudTuningWandTargeting.blockHit(minecraft);
        if (hit == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        UUID subLevelId = SableCompat.subLevelIdAtStorage(minecraft.level, pos);
        Object subLevel = subLevelId == null
                ? null : SableCompat.subLevelById(minecraft.level, subLevelId);
        return subLevelId != null && subLevel == null
                ? null : new RenderTarget(pos, null, subLevel, false);
    }

    private static void renderBox(PoseStack.Pose pose,
            VertexConsumer faces, VertexConsumer edges, Vec3[] corners,
            float red, float green, float blue,
            float faceAlpha, float edgeAlpha) {
        for (int[] face : FACES) {
            quad(pose, faces,
                    corners[face[0]], corners[face[1]],
                    corners[face[2]], corners[face[3]],
                    red, green, blue, faceAlpha);
        }
        Vec3[] edgeAxes = boxAxes(corners);
        for (int[] edge : EDGES) {
            edgeCuboid(pose, edges, corners, edgeAxes, edge,
                    red, green, blue, edgeAlpha);
        }
        for (Vec3 corner : corners) {
            cornerCube(pose, edges, edgeAxes, corner,
                    red, green, blue, edgeAlpha);
        }
    }

    static Vec3[] rotateBox(Vec3[] corners, double scale,
            float yawRadians, float pitchRadians, float rollRadians) {
        Vec3 center = Vec3.ZERO;
        for (Vec3 corner : corners) {
            center = center.add(corner);
        }
        center = center.scale(1.0D / corners.length);
        Quaternionf rotation = new Quaternionf().rotationYXZ(
                yawRadians, pitchRadians, rollRadians);
        Vec3[] rotated = new Vec3[corners.length];
        for (int index = 0; index < corners.length; index++) {
            Vec3 offset = corners[index].subtract(center).scale(scale);
            Vector3f transformed = rotation.transform(new Vector3f(
                    (float) offset.x, (float) offset.y, (float) offset.z));
            rotated[index] = center.add(
                    transformed.x, transformed.y, transformed.z);
        }
        return rotated;
    }

    private static AABB targetBounds(Minecraft minecraft, BlockPos pos) {
        var shape = minecraft.level.getBlockState(pos).getShape(
                minecraft.level, pos, net.minecraft.world.phys.shapes.CollisionContext.of(
                        minecraft.player));
        return shape.isEmpty() ? new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
                : shape.bounds();
    }

    private static Vec3[] boxCorners(BlockPos pos, AABB bounds,
            Object subLevel, Vec3 camera) {
        double minX = pos.getX() + bounds.minX - INFLATION;
        double minY = pos.getY() + bounds.minY - INFLATION;
        double minZ = pos.getZ() + bounds.minZ - INFLATION;
        double maxX = pos.getX() + bounds.maxX + INFLATION;
        double maxY = pos.getY() + bounds.maxY + INFLATION;
        double maxZ = pos.getZ() + bounds.maxZ + INFLATION;
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            Vec3 local = new Vec3(
                    (index & 1) == 0 ? minX : maxX,
                    (index & 2) == 0 ? minY : maxY,
                    (index & 4) == 0 ? minZ : maxZ);
            Vec3 world = subLevel == null ? local : SableCompat.toRenderWorld(subLevel, local);
            if (world == null) {
                return null;
            }
            corners[index] = world.subtract(camera);
        }
        return corners;
    }

    private static void edgeCuboid(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3[] boxCorners, Vec3[] axes, int[] edgeIndices,
            float red, float green, float blue, float alpha) {
        int edgeAxis = edgeIndices[0] ^ edgeIndices[1];
        int firstWidthAxis;
        int secondWidthAxis;
        if (edgeAxis == 1) {
            firstWidthAxis = 1;
            secondWidthAxis = 2;
        } else if (edgeAxis == 2) {
            firstWidthAxis = 0;
            secondWidthAxis = 2;
        } else if (edgeAxis == 4) {
            firstWidthAxis = 0;
            secondWidthAxis = 1;
        } else {
            return;
        }

        Vec3 firstWidth = axes[firstWidthAxis].scale(EDGE_HALF_WIDTH);
        Vec3 secondWidth = axes[secondWidthAxis].scale(EDGE_HALF_WIDTH);
        Vec3 start = boxCorners[edgeIndices[0]];
        Vec3 end = boxCorners[edgeIndices[1]];
        Vec3 endInset = end.subtract(start).normalize().scale(EDGE_HALF_WIDTH);
        start = start.add(endInset);
        end = end.subtract(endInset);
        Vec3[] corners = {
                start.subtract(firstWidth).subtract(secondWidth),
                end.subtract(firstWidth).subtract(secondWidth),
                start.add(firstWidth).subtract(secondWidth),
                end.add(firstWidth).subtract(secondWidth),
                start.subtract(firstWidth).add(secondWidth),
                end.subtract(firstWidth).add(secondWidth),
                start.add(firstWidth).add(secondWidth),
                end.add(firstWidth).add(secondWidth)
        };
        for (int faceIndex : EDGE_SIDE_FACES) {
            int[] face = FACES[faceIndex];
            quad(pose, vertices,
                    corners[face[0]], corners[face[1]],
                    corners[face[2]], corners[face[3]],
                    red, green, blue, alpha);
        }
    }

    private static void cornerCube(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3[] axes, Vec3 center,
            float red, float green, float blue, float alpha) {
        Vec3 xWidth = axes[0].scale(EDGE_HALF_WIDTH);
        Vec3 yWidth = axes[1].scale(EDGE_HALF_WIDTH);
        Vec3 zWidth = axes[2].scale(EDGE_HALF_WIDTH);
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = center
                    .add((index & 1) == 0 ? xWidth.scale(-1.0D) : xWidth)
                    .add((index & 2) == 0 ? yWidth.scale(-1.0D) : yWidth)
                    .add((index & 4) == 0 ? zWidth.scale(-1.0D) : zWidth);
        }
        for (int[] face : FACES) {
            quad(pose, vertices,
                    corners[face[0]], corners[face[1]],
                    corners[face[2]], corners[face[3]],
                    red, green, blue, alpha);
        }
    }

    private static Vec3[] boxAxes(Vec3[] corners) {
        return new Vec3[] {
                corners[1].subtract(corners[0]).normalize(),
                corners[2].subtract(corners[0]).normalize(),
                corners[4].subtract(corners[0]).normalize()
        };
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 first, Vec3 second, Vec3 third, Vec3 fourth,
            float red, float green, float blue, float alpha) {
        Vec3 normal = second.subtract(first).cross(fourth.subtract(first)).normalize();
        vertex(pose, vertices, first, 0.0F, 0.0F, normal,
                red, green, blue, alpha);
        vertex(pose, vertices, second, 0.0F, 1.0F, normal,
                red, green, blue, alpha);
        vertex(pose, vertices, third, 1.0F, 1.0F, normal,
                red, green, blue, alpha);
        vertex(pose, vertices, fourth, 1.0F, 0.0F, normal,
                red, green, blue, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices,
            Vec3 point, float u, float v, Vec3 normal,
            float red, float green, float blue, float alpha) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record RenderTarget(
            BlockPos pos, AABB bounds, Object subLevel, boolean rotatingSummon) {
    }
}
