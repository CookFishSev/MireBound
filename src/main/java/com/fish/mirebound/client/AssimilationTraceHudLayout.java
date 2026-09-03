package com.fish.mirebound.client;

import com.fish.mirebound.mixin.client.mud.GameRendererFovAccessor;
import com.fish.mirebound.assimilation.AssimilationTracePattern;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared Zoom-aware screen layout used by trace rendering and crosshair hit testing. */
final class AssimilationTraceHudLayout {
    private static final int EDGE_MARGIN = 22;
    private static final int PANEL_GAP = 13;
    private static int lockedSequence = -1;
    private static int lockedCell = -1;
    private static int lockedWidth = -1;
    private static int lockedHeight = -1;
    private static Placement lockedPlacement;

    private AssimilationTraceHudLayout() {
    }

    static Layout create(Minecraft minecraft, ClientAssimilationState.View view, float partialTick) {
        if (view == null || view.qteCell() < 0) {
            return null;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (lockedPlacement != null && lockedSequence == view.qteSequence()
                && lockedCell == view.qteCell() && lockedWidth == width && lockedHeight == height) {
            return createLocked(minecraft, view, partialTick, width, height, lockedPlacement);
        }
        if (lockedPlacement != null) {
            reset();
        }
        return createLive(minecraft, view, partialTick, width, height);
    }

    static Layout lock(Minecraft minecraft, ClientAssimilationState.View view, float partialTick) {
        if (view == null || view.qteCell() < 0) {
            return null;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        Layout layout = createLive(minecraft, view, partialTick, width, height);
        if (layout == null) {
            return null;
        }
        lockedSequence = view.qteSequence();
        lockedCell = view.qteCell();
        lockedWidth = width;
        lockedHeight = height;
        lockedPlacement = new Placement(
                layout.centerX - layout.targetX,
                layout.centerY - layout.targetY,
                layout.previewCenterX - layout.targetX,
                layout.previewCenterY - layout.targetY,
                layout.spacing, layout.previewSpacing);
        return layout;
    }

    private static Layout createLocked(Minecraft minecraft,
            ClientAssimilationState.View view, float partialTick,
            int width, int height, Placement placement) {
        Point target = projectTarget(minecraft, view, partialTick, width, height);
        if (target == null) {
            return null;
        }
        return new Layout(target.x, target.y,
                target.x + placement.centerOffsetX,
                target.y + placement.centerOffsetY,
                placement.spacing,
                target.x + placement.previewOffsetX,
                target.y + placement.previewOffsetY,
                placement.previewSpacing);
    }

    private static Layout createLive(Minecraft minecraft, ClientAssimilationState.View view,
            float partialTick, int width, int height) {
        Point target = projectTarget(minecraft, view, partialTick, width, height);
        if (target == null) {
            return null;
        }
        int spacing = view.profile().selfRescueQteTraceSpacing();
        int previewSpacing = Math.max(8, Math.round(spacing * 0.67F));
        int answerRadius = gridRadius(spacing) + 6;
        int previewRadius = gridRadius(previewSpacing) + 4;
        double centerDx = width * 0.5D - target.x;
        double centerDy = height * 0.5D - target.y;
        double length = Math.max(1.0D, Math.hypot(centerDx, centerDy));
        int side = target.clipped
                ? (centerDx < 0.0D ? -1 : 1)
                : (target.x > width / 2 ? -1 : 1);
        int centerX = target.x + side * (answerRadius + 13);
        int centerY = target.clipped
                ? target.y + Mth.floor(centerDy / length * 14.0D) : target.y;
        int previewX = centerX + side * (answerRadius + PANEL_GAP + previewRadius);
        int previewY = centerY;

        int minX = Math.min(centerX - answerRadius, previewX - previewRadius);
        int maxX = Math.max(centerX + answerRadius, previewX + previewRadius);
        int shiftX = minX < EDGE_MARGIN ? EDGE_MARGIN - minX
                : maxX > width - EDGE_MARGIN ? width - EDGE_MARGIN - maxX : 0;
        int minY = Math.min(centerY - answerRadius, previewY - previewRadius);
        int maxY = Math.max(centerY + answerRadius + 25, previewY + previewRadius);
        int shiftY = minY < EDGE_MARGIN ? EDGE_MARGIN - minY
                : maxY > height - EDGE_MARGIN ? height - EDGE_MARGIN - maxY : 0;
        return new Layout(target.x, target.y,
                centerX + shiftX, centerY + shiftY, spacing,
                previewX + shiftX, previewY + shiftY, previewSpacing);
    }

    private static Point projectTarget(Minecraft minecraft,
            ClientAssimilationState.View view, float partialTick, int width, int height) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) {
            return null;
        }
        Vec3 direction = AssimilationBodyCellGeometry.worldPoint(view, view.qteCell())
                .subtract(camera.getPosition());
        return project(direction, camera, width, height,
                effectiveFov(minecraft, camera, partialTick));
    }

    static int nodeAtCrosshair(Minecraft minecraft, ClientAssimilationState.View view) {
        return nodeAtPointer(minecraft, view,
                minecraft.getWindow().getGuiScaledWidth() / 2,
                minecraft.getWindow().getGuiScaledHeight() / 2);
    }

    static int nodeAtPointer(Minecraft minecraft, ClientAssimilationState.View view,
            int pointerX, int pointerY) {
        Layout layout = create(minecraft, view,
                minecraft.getTimer().getGameTimeDeltaPartialTick(false));
        if (layout == null) {
            return -1;
        }
        double radius = view.profile().selfRescueQteTraceHitRadius();
        double maximumDistance = radius * radius;
        int nearest = -1;
        for (int node = 0; node < AssimilationTracePattern.NODE_COUNT; node++) {
            int dx = layout.nodeX(node) - pointerX;
            int dy = layout.nodeY(node) - pointerY;
            double distance = dx * dx + dy * dy;
            if (distance <= maximumDistance) {
                maximumDistance = distance;
                nearest = node;
            }
        }
        return nearest;
    }

    static void reset() {
        lockedSequence = -1;
        lockedCell = -1;
        lockedWidth = -1;
        lockedHeight = -1;
        lockedPlacement = null;
    }

    private static int gridRadius(int spacing) {
        return Math.round((AssimilationTracePattern.GRID_SIZE - 1) * spacing * 0.5F);
    }

    private static double effectiveFov(Minecraft minecraft, Camera camera, float partialTick) {
        try {
            return ((GameRendererFovAccessor) (Object) minecraft.gameRenderer)
                    .mirebound$invokeGetFov(camera, partialTick, true);
        } catch (RuntimeException | LinkageError ignored) {
            return minecraft.options.fov().get();
        }
    }

    private static Point project(Vec3 direction, Camera camera,
            int width, int height, double fovDegrees) {
        var lookVector = camera.getLookVector();
        var upVector = camera.getUpVector();
        var leftVector = camera.getLeftVector();
        Vec3 forward = new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
        Vec3 right = new Vec3(-leftVector.x(), -leftVector.y(), -leftVector.z());
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());
        double localX = direction.dot(right);
        double localY = direction.dot(up);
        double localZ = direction.dot(forward);
        double tanHalfVertical = Math.tan(Math.toRadians(
                Mth.clamp(fovDegrees, 1.0D, 170.0D)) * 0.5D);
        double aspect = width / (double) Math.max(1, height);
        double safeDepth = Math.max(0.05D, Math.abs(localZ));
        double normalizedX = localX / (safeDepth * tanHalfVertical * aspect);
        double normalizedY = -localY / (safeDepth * tanHalfVertical);
        boolean behind = localZ <= 0.0D;
        if (behind) {
            normalizedX = Math.abs(normalizedX) < 0.05D
                    ? Math.copySign(1.0D, localX == 0.0D ? 1.0D : localX)
                    : -normalizedX;
            normalizedY = Mth.clamp(normalizedY, -1.0D, 1.0D);
        }
        double scale = Math.max(1.0D,
                Math.max(Math.abs(normalizedX) / 0.90D, Math.abs(normalizedY) / 0.82D));
        boolean clipped = behind || scale > 1.0D;
        normalizedX /= scale;
        normalizedY /= scale;
        double maxX = width * 0.5D - EDGE_MARGIN;
        double maxY = height * 0.5D - EDGE_MARGIN;
        return new Point(width / 2 + Mth.floor(normalizedX * maxX),
                height / 2 + Mth.floor(normalizedY * maxY), clipped);
    }

    record Layout(int targetX, int targetY, int centerX, int centerY, int spacing,
            int previewCenterX, int previewCenterY, int previewSpacing) {
        int nodeX(int node) {
            return gridCoordinate(centerX, node % AssimilationTracePattern.GRID_SIZE, spacing);
        }

        int nodeY(int node) {
            return gridCoordinate(centerY, node / AssimilationTracePattern.GRID_SIZE, spacing);
        }

        int previewNodeX(int node) {
            return gridCoordinate(previewCenterX,
                    node % AssimilationTracePattern.GRID_SIZE, previewSpacing);
        }

        int previewNodeY(int node) {
            return gridCoordinate(previewCenterY,
                    node / AssimilationTracePattern.GRID_SIZE, previewSpacing);
        }

        private static int gridCoordinate(int center, int index, int spacing) {
            return center + Math.round((index
                    - (AssimilationTracePattern.GRID_SIZE - 1) * 0.5F) * spacing);
        }
    }

    private record Point(int x, int y, boolean clipped) {
    }

    private record Placement(int centerOffsetX, int centerOffsetY,
            int previewOffsetX, int previewOffsetY, int spacing, int previewSpacing) {
    }
}
