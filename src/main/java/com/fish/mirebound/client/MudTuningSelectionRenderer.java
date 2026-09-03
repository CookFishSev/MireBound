package com.fish.mirebound.client;

import com.fish.mirebound.client.tuning.MudTuningClientState;
import com.fish.mirebound.client.tuning.MudTuningClientSettings;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.client.tuning.MudTuningWandMode;
import com.fish.mirebound.compat.sable.SableCompat;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.tuning.MudTuningSelectionElement;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class MudTuningSelectionRenderer {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };
    private static final float SELECTED_RED = 0.18F;
    private static final float SELECTED_GREEN = 0.96F;
    private static final float SELECTED_BLUE = 0.98F;
    private static final int SELECTED_COLOR = 0x2EF5FA;
    private static final float AREA_OUTLINE = 0.62F;
    private static final float SELECTED_AREA_OUTLINE = 0.84F;

    private MudTuningSelectionRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || MudTuningInputController.heldWandHand(minecraft.player) == null) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();

        if (rendersManualSelection(MudTuningClientState.mode())) {
            renderSelection(minecraft, pose, lines, camera);
        }
        MudTuningConnectedHighlightRenderer.render(
                minecraft, pose, buffers, camera, minecraft.player.position(),
                event.getFrustum());

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(MudTuningTargetRenderTypes.face());
    }

    static boolean rendersManualSelection(MudTuningWandMode mode) {
        return mode == MudTuningWandMode.RANGE;
    }

    private static Object resolveSubLevel(Minecraft minecraft, MudTuningAnchor anchor) {
        return anchor.isSable()
                ? SableCompat.subLevelById(minecraft.level, anchor.subLevelId()) : null;
    }

    private static void renderSelection(
            Minecraft minecraft, PoseStack.Pose pose, VertexConsumer lines, Vec3 camera) {
        if (!MudTuningClientState.hasFirst()) {
            return;
        }
        MudTuningAnchor first = MudTuningClientState.first();
        MudTuningAnchor second = MudTuningClientState.hasSecond()
                ? MudTuningClientState.second() : null;
        if (second != null && !first.sameDomain(second)) {
            return;
        }
        Object subLevel = resolveSubLevel(minecraft, first);
        if (first.isSable() && subLevel == null) {
            return;
        }
        MudTuningSelectionElement selected = MudTuningClientState.selectedElement();
        if (second == null) {
            renderPointBox(pose, lines, first.pos(), subLevel, camera,
                    selected == MudTuningSelectionElement.FIRST,
                    MudTuningClientSettings.color(
                            MudTuningClientSettings.HudColor.POINT_ONE));
            return;
        }

        BlockPos firstPos = first.pos();
        BlockPos secondPos = second.pos();
        if (firstPos.equals(secondPos)) {
            if (selected == MudTuningSelectionElement.BODY) {
                renderBlockBox(pose, lines, firstPos, subLevel, camera, 0.006D,
                        SELECTED_AREA_OUTLINE, SELECTED_AREA_OUTLINE,
                        SELECTED_AREA_OUTLINE, 0.94F);
            } else {
                renderOverlappingPointBox(
                        pose, lines, firstPos, subLevel, camera, selected);
            }
            return;
        }

        double minX = Math.min(firstPos.getX(), secondPos.getX()) - 0.004D;
        double minY = Math.min(firstPos.getY(), secondPos.getY()) - 0.004D;
        double minZ = Math.min(firstPos.getZ(), secondPos.getZ()) - 0.004D;
        double maxX = Math.max(firstPos.getX(), secondPos.getX()) + 1.004D;
        double maxY = Math.max(firstPos.getY(), secondPos.getY()) + 1.004D;
        double maxZ = Math.max(firstPos.getZ(), secondPos.getZ()) + 1.004D;
        renderAreaOutlineWithoutPointEdges(pose, lines,
                minX, minY, minZ, maxX, maxY, maxZ,
                firstPos, secondPos, subLevel, camera,
                selected == MudTuningSelectionElement.BODY);
        renderPointBox(pose, lines, firstPos, subLevel, camera,
                selected == MudTuningSelectionElement.FIRST,
                MudTuningClientSettings.color(
                        MudTuningClientSettings.HudColor.POINT_ONE));
        renderPointBox(pose, lines, secondPos, subLevel, camera,
                selected == MudTuningSelectionElement.SECOND,
                MudTuningClientSettings.color(
                        MudTuningClientSettings.HudColor.POINT_TWO));
    }

    private static void renderPointBox(PoseStack.Pose pose, VertexConsumer lines,
            BlockPos pos, Object subLevel, Vec3 camera, boolean selected,
            int color) {
        renderBlockBox(pose, lines, pos, subLevel, camera, 0.006D,
                selected ? SELECTED_RED : red(color),
                selected ? SELECTED_GREEN : green(color),
                selected ? SELECTED_BLUE : blue(color), 0.98F);
    }

    private static void renderOverlappingPointBox(PoseStack.Pose pose, VertexConsumer lines,
            BlockPos pos, Object subLevel, Vec3 camera,
            MudTuningSelectionElement selected) {
        Vec3[] corners = corners(
                pos.getX() - 0.006D, pos.getY() - 0.006D, pos.getZ() - 0.006D,
                pos.getX() + 1.006D, pos.getY() + 1.006D, pos.getZ() + 1.006D,
                subLevel);
        if (corners == null) {
            return;
        }
        for (int[] edge : BOX_EDGES) {
            int color;
            if (edge[0] == 0 || edge[1] == 0) {
                color = selected == MudTuningSelectionElement.FIRST
                        ? SELECTED_COLOR : MudTuningClientSettings.color(
                                MudTuningClientSettings.HudColor.POINT_ONE);
            } else if (edge[0] == 7 || edge[1] == 7) {
                color = selected == MudTuningSelectionElement.SECOND
                        ? SELECTED_COLOR : MudTuningClientSettings.color(
                                MudTuningClientSettings.HudColor.POINT_TWO);
            } else {
                color = 0x9E9E9E;
            }
            line(pose, lines, corners[edge[0]], corners[edge[1]], camera,
                    red(color), green(color), blue(color), 0.98F);
        }
    }

    private static void renderBlockBox(PoseStack.Pose pose, VertexConsumer lines, BlockPos pos,
            Object subLevel, Vec3 camera, double inflation,
            float red, float green, float blue, float alpha) {
        renderBox(pose, lines,
                pos.getX() - inflation, pos.getY() - inflation, pos.getZ() - inflation,
                pos.getX() + 1.0D + inflation, pos.getY() + 1.0D + inflation,
                pos.getZ() + 1.0D + inflation, subLevel, camera,
                red, green, blue, alpha);
    }

    private static void renderBox(PoseStack.Pose pose, VertexConsumer lines,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            Object subLevel, Vec3 camera,
            float red, float green, float blue, float alpha) {
        Vec3[] corners = corners(
                minX, minY, minZ, maxX, maxY, maxZ, subLevel);
        if (corners == null) {
            return;
        }
        for (int[] edge : BOX_EDGES) {
            line(pose, lines, corners[edge[0]], corners[edge[1]], camera,
                    red, green, blue, alpha);
        }
    }

    private static void renderAreaOutlineWithoutPointEdges(
            PoseStack.Pose pose, VertexConsumer lines,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            BlockPos first, BlockPos second, Object subLevel, Vec3 camera,
            boolean selectedBody) {
        Vec3[] localCorners = localCorners(minX, minY, minZ, maxX, maxY, maxZ);
        int minimumX = Math.min(first.getX(), second.getX());
        int minimumY = Math.min(first.getY(), second.getY());
        int minimumZ = Math.min(first.getZ(), second.getZ());
        int maximumX = Math.max(first.getX(), second.getX());
        int maximumY = Math.max(first.getY(), second.getY());
        int maximumZ = Math.max(first.getZ(), second.getZ());
        for (int[] edge : BOX_EDGES) {
            int startIndex = edge[0];
            int endIndex = edge[1];
            int difference = edge[0] ^ edge[1];
            Vec3 direction = difference == 1 ? new Vec3(1.0D, 0.0D, 0.0D)
                    : difference == 2 ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(0.0D, 0.0D, 1.0D);
            Vec3 start = localCorners[startIndex];
            Vec3 end = localCorners[endIndex];
            BlockPos startCorner = cornerBlock(startIndex,
                    minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
            BlockPos endCorner = cornerBlock(endIndex,
                    minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
            if (first.equals(startCorner) || second.equals(startCorner)) {
                start = start.add(direction);
            }
            if (first.equals(endCorner) || second.equals(endCorner)) {
                end = end.subtract(direction);
            }
            if (end.subtract(start).dot(direction) <= 0.02D) {
                continue;
            }
            Vec3 worldStart = worldPoint(subLevel, start);
            Vec3 worldEnd = worldPoint(subLevel, end);
            if (worldStart == null || worldEnd == null) {
                continue;
            }
            float outline = selectedBody ? SELECTED_AREA_OUTLINE : AREA_OUTLINE;
            line(pose, lines, worldStart, worldEnd, camera,
                    outline, outline, outline,
                    selectedBody ? 0.94F : 0.90F);
        }
    }

    private static BlockPos cornerBlock(int index,
            int minimumX, int minimumY, int minimumZ,
            int maximumX, int maximumY, int maximumZ) {
        return new BlockPos(
                (index & 1) == 0 ? minimumX : maximumX,
                (index & 2) == 0 ? minimumY : maximumY,
                (index & 4) == 0 ? minimumZ : maximumZ);
    }

    private static Vec3[] localCorners(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = new Vec3(
                    (index & 1) == 0 ? minX : maxX,
                    (index & 2) == 0 ? minY : maxY,
                    (index & 4) == 0 ? minZ : maxZ);
        }
        return corners;
    }

    private static Vec3[] corners(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, Object subLevel) {
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = worldPoint(subLevel, new Vec3(
                    (index & 1) == 0 ? minX : maxX,
                    (index & 2) == 0 ? minY : maxY,
                    (index & 4) == 0 ? minZ : maxZ));
            if (corners[index] == null) {
                return null;
            }
        }
        return corners;
    }

    private static Vec3 worldPoint(Object subLevel, Vec3 point) {
        return subLevel == null ? point : SableCompat.toRenderWorld(subLevel, point);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines,
            Vec3 start, Vec3 end, Vec3 camera,
            float red, float green, float blue, float alpha) {
        Vec3 normal = end.subtract(start).normalize();
        lines.addVertex(pose,
                        (float) (start.x - camera.x),
                        (float) (start.y - camera.y),
                        (float) (start.z - camera.z))
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(pose,
                        (float) (end.x - camera.x),
                        (float) (end.y - camera.y),
                        (float) (end.z - camera.z))
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static float red(int color) {
        return (color >> 16 & 0xFF) / 255.0F;
    }

    private static float green(int color) {
        return (color >> 8 & 0xFF) / 255.0F;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0F;
    }
}
