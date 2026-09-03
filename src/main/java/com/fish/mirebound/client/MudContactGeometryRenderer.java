package com.fish.mirebound.client;

import com.fish.mirebound.client.compat.ClientRenderCompat;
import com.fish.mirebound.mud.MudCapeGeometry;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudEntityGeometry;
import com.fish.mirebound.mud.AnimatedPlayerGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws the authoritative body, armor-shell, and moving cape contact geometry. */
final class MudContactGeometryRenderer {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {0, 2}, {0, 4},
            {1, 3}, {1, 5},
            {2, 3}, {2, 6},
            {3, 7},
            {4, 5}, {4, 6},
            {5, 7},
            {6, 7}
    };

    private MudContactGeometryRenderer() {
    }

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || ClientRenderCompat.isRenderingShaderShadowPass()
                || !ClientMudDebugOptions.contactGeometry()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poseStack.last();

        AnimatedPlayerGeometry.Source bodySource = AnimatedPlayerGeometry.bodySource(player);
        for (MudEntityGeometry.DebugBox box : MudEntityGeometry.debugBoxes(player)) {
            float red = box.armor() ? 1.0F
                    : bodySource == AnimatedPlayerGeometry.Source.SODIUM_VERTICES ? 1.0F : 0.1F;
            float green = box.armor() ? 0.58F
                    : bodySource == AnimatedPlayerGeometry.Source.MODEL_PART ? 1.0F
                            : bodySource == AnimatedPlayerGeometry.Source.SODIUM_VERTICES ? 0.92F : 0.86F;
            float blue = box.armor() ? 0.08F
                    : bodySource == AnimatedPlayerGeometry.Source.MODEL_PART ? 0.22F
                            : bodySource == AnimatedPlayerGeometry.Source.SODIUM_VERTICES ? 0.08F : 1.0F;
            renderBox(pose, lines, box.corners(), red, green, blue, 0.92F);
        }

        boolean animatedCape = AnimatedPlayerGeometry.cape(player) != null;
        MudCapeGeometry.CapeBasis cape = MudEntityGeometry.capeBasis(player);
        renderCapeGrid(pose, lines, cape, true,
                1.0F, animatedCape ? 0.68F : 0.18F, 0.76F);
        renderCapeGrid(pose, lines, cape, false,
                animatedCape ? 0.92F : 0.66F, animatedCape ? 0.52F : 0.24F, 1.0F);
        for (int row : new int[]{0, MudCapeLayout.ROWS}) {
            for (int column : new int[]{0, MudCapeLayout.COLUMNS}) {
                line(pose, lines,
                        MudCapeGeometry.gridPoint(cape, row, column, true),
                        MudCapeGeometry.gridPoint(cape, row, column, false),
                        0.92F, 0.30F, 1.0F, 0.85F);
            }
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderBox(PoseStack.Pose pose, VertexConsumer lines,
            java.util.List<Vec3> corners, float red, float green, float blue, float alpha) {
        if (corners.size() != 8) {
            return;
        }
        for (int[] edge : BOX_EDGES) {
            line(pose, lines, corners.get(edge[0]), corners.get(edge[1]),
                    red, green, blue, alpha);
        }
    }

    private static void renderCapeGrid(PoseStack.Pose pose, VertexConsumer lines,
            MudCapeGeometry.CapeBasis cape, boolean front,
            float red, float green, float blue) {
        for (int row = 0; row <= MudCapeLayout.ROWS; row++) {
            line(pose, lines,
                    MudCapeGeometry.gridPoint(cape, row, 0, front),
                    MudCapeGeometry.gridPoint(cape, row, MudCapeLayout.COLUMNS, front),
                    red, green, blue, 0.78F);
        }
        for (int column = 0; column <= MudCapeLayout.COLUMNS; column++) {
            line(pose, lines,
                    MudCapeGeometry.gridPoint(cape, 0, column, front),
                    MudCapeGeometry.gridPoint(cape, MudCapeLayout.ROWS, column, front),
                    red, green, blue, 0.78F);
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines,
            Vec3 start, Vec3 end, float red, float green, float blue, float alpha) {
        Vec3 normal = end.subtract(start).normalize();
        lines.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
