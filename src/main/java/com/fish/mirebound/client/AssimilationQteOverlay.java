package com.fish.mirebound.client;

import com.fish.mirebound.assimilation.AssimilationQteAction;
import com.fish.mirebound.assimilation.AssimilationStage;
import com.fish.mirebound.assimilation.AssimilationTracePattern;
import com.fish.mirebound.mixin.client.mud.GameRendererFovAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Target-local pixel QTE with a mouse glyph and a segmented radial timer. */
final class AssimilationQteOverlay {
    private static final int EDGE_MARGIN = 22;
    private static final int MARKER_RADIUS = 5;
    private static final int ICON_OFFSET = 31;
    private static final int[][] TIMER_SEGMENTS = {
            {-5, -15}, {-2, -15}, {1, -15}, {4, -15}, {7, -14},
            {10, -12}, {12, -10}, {14, -7}, {15, -4}, {15, -1},
            {15, 2}, {14, 5}, {13, 8}, {11, 11}, {8, 13},
            {5, 15}, {2, 15}, {-1, 15}, {-4, 15}, {-7, 14},
            {-10, 12}, {-12, 10}, {-14, 7}, {-15, 4}, {-15, 1},
            {-15, -2}, {-14, -5}, {-13, -8}, {-11, -11}, {-8, -13}
    };

    private AssimilationQteOverlay() {
    }

    static void render(GuiGraphics graphics, Minecraft minecraft, float partialTick) {
        if (minecraft.player == null || !ClientAssimilationState.localSoulActive(minecraft)) {
            return;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(minecraft.player.getId());
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (view == null || camera == null || view.stage() != AssimilationStage.SEALED) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (!AssimilationQteClient.inRange(view)) {
            renderReturnPrompt(graphics, minecraft, view, camera, width, height, partialTick);
            return;
        }
        if (view.qteCell() < 0 || view.qteButton() == 0 || view.qteTicksRemaining() <= 0) {
            return;
        }
        if (view.qteAction() == AssimilationQteAction.TRACE) {
            renderTrace(graphics, minecraft, view, partialTick);
            return;
        }
        Vec3 direction = AssimilationBodyCellGeometry.worldPoint(view, view.qteCell())
                .subtract(camera.getPosition());
        ScreenPoint target = project(direction, camera, width, height,
                effectiveFov(minecraft, camera, partialTick));
        IconPoint icon = iconPoint(target, width, height);

        boolean aimed = AssimilationQteClient.aimedAt(view);
        boolean holding = AssimilationQteClient.holding(view);
        float holdProgress = AssimilationQteClient.holdProgress(view);
        int timeout = Math.max(1, view.profile().selfRescueQteTimeoutTicks());
        float remaining = Mth.clamp(view.qteTicksRemaining()
                / (float) timeout, 0.0F, 1.0F);
        float elapsed = 1.0F - remaining;
        float appear = Mth.clamp(elapsed * timeout
                / Math.max(1.0F, view.profile().selfRescueQteFadeTicks()), 0.0F, 1.0F);
        float pulse = 0.86F + 0.14F * Mth.sin((minecraft.player.tickCount
                + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) * 0.42F);
        int alpha = Mth.clamp(Math.round(255.0F * appear * pulse), 0, 255);
        if (alpha <= 2) {
            return;
        }

        int accent = aimed
                ? argb(alpha, 220, 255, 142)
                : remaining < 0.25F
                        ? argb(alpha, 255, 104, 88)
                        : argb(alpha, 255, 207, 178);
        int shadow = argb(Math.round(alpha * 0.72F), 14, 10, 12);
        drawTargetMarker(graphics, target.x(), target.y(), accent, shadow, aimed || holding);
        drawConnector(graphics, target.x(), target.y(), icon.x(), icon.y(), shadow, accent);
        drawTimerRing(graphics, icon.x(), icon.y(), remaining, alpha, accent);
        drawMouseGlyph(graphics, icon.x(), icon.y(), view.qteButton(),
                view.qteAction(), holdProgress, alpha, accent);
        if (view.qteAction() == AssimilationQteAction.RAPID) {
            drawRapidClicks(graphics, icon.x(), icon.y() + 11,
                    view.qteRapidClicks(), view.profile().selfRescueQteRapidClicks(),
                    alpha, accent);
        }
        drawStreak(graphics, icon.x(), icon.y() + 21, view.qteStreak(),
                view.profile().selfRescueQteRequiredStreak(), alpha, accent);

        Component buttonName = Component.translatable(view.qteButton() == 1
                ? "hud.mirebound.assimilation.qte.left"
                : "hud.mirebound.assimilation.qte.right");
        Component button = switch (view.qteAction()) {
            case HOLD -> Component.translatable(holdProgress >= 0.999F
                    ? "hud.mirebound.assimilation.qte.release"
                    : "hud.mirebound.assimilation.qte.hold", buttonName);
            case RAPID -> Component.translatable(
                    "hud.mirebound.assimilation.qte.rapid", buttonName,
                    view.profile().selfRescueQteRapidClicks());
            default -> buttonName;
        };
        int textX = icon.x() - minecraft.font.width(button) / 2;
        graphics.drawString(minecraft.font, button, textX, icon.y() + 26,
                argb(alpha, 255, 244, 226), true);
    }

    private static void renderTrace(GuiGraphics graphics, Minecraft minecraft,
            ClientAssimilationState.View view, float partialTick) {
        AssimilationTraceHudLayout.Layout layout = AssimilationTraceHudLayout.create(
                minecraft, view, partialTick);
        if (layout == null) {
            return;
        }
        int[] path = AssimilationQteClient.tracePath(view);
        int progress = Mth.clamp(AssimilationQteClient.traceDisplayProgress(view), 0, path.length);
        int traceTimeout = Math.max(1, view.profile().selfRescueQteTraceTimeoutTicks());
        float remaining = Mth.clamp(view.qteTicksRemaining()
                / (float) traceTimeout, 0.0F, 1.0F);
        float elapsed = 1.0F - remaining;
        float appear = Mth.clamp(elapsed * traceTimeout
                / Math.max(1.0F, view.profile().selfRescueQteFadeTicks()), 0.0F, 1.0F);
        float pulse = 0.78F + 0.22F * Mth.sin((minecraft.player.tickCount
                + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) * 0.38F);
        int alpha = Mth.clamp(Math.round(255.0F * appear), 0, 255);
        int completedColor = argb(alpha, 48, 255, 48);
        int referenceBase = argb(alpha, 92, 224, 255);
        int referencePath = argb(alpha, 255, 24, 24);
        int active = argb(Math.round(alpha * pulse), 255, 240, 32);
        int pending = argb(Math.round(alpha * 0.70F), 118, 106, 111);
        int shadow = argb(Math.round(alpha * 0.72F), 14, 10, 12);
        drawTargetMarker(graphics, layout.targetX(), layout.targetY(), referenceBase, shadow, false);
        drawConnector(graphics, layout.targetX(), layout.targetY(),
                layout.centerX(), layout.centerY(), shadow, pending);

        // The smaller board is the immutable reference. The larger board records the gesture.
        for (int index = 1; index < path.length; index++) {
            int from = path[index - 1];
            int to = path[index];
            drawPixelLine(graphics,
                    layout.previewNodeX(from), layout.previewNodeY(from),
                    layout.previewNodeX(to), layout.previewNodeY(to), shadow, referencePath);
        }
        for (int node = 0; node < AssimilationTracePattern.NODE_COUNT; node++) {
            drawTraceNode(graphics, layout.previewNodeX(node), layout.previewNodeY(node),
                    referenceBase, shadow, node == path[path.length - 1], node == path[0], 2);
        }

        for (int index = 1; index < progress; index++) {
            int from = path[index - 1];
            int to = path[index];
            drawPixelLine(graphics, layout.nodeX(from), layout.nodeY(from),
                    layout.nodeX(to), layout.nodeY(to), shadow, completedColor);
        }
        if (progress > 0 && progress < path.length && AssimilationQteClient.tracing(view)) {
            int from = path[progress - 1];
            drawPixelLine(graphics, layout.nodeX(from), layout.nodeY(from),
                    minecraft.getWindow().getGuiScaledWidth() / 2,
                    minecraft.getWindow().getGuiScaledHeight() / 2, shadow, active);
        }
        for (int node = 0; node < AssimilationTracePattern.NODE_COUNT; node++) {
            int pathIndex = indexOf(path, node);
            boolean visited = pathIndex >= 0 && pathIndex < progress;
            boolean endpoint = progress > 0 && node == path[Math.min(progress - 1, path.length - 1)];
            boolean start = progress == 0 && node == path[0];
            drawTraceNode(graphics, layout.nodeX(node), layout.nodeY(node),
                    visited ? completedColor : pending, shadow, endpoint, start, 4);
        }
        int nextNode = progress < path.length ? path[progress] : -1;
        if (nextNode >= 0 && AssimilationQteClient.tracing(view)) {
            int pointerX = minecraft.getWindow().getGuiScaledWidth() / 2;
            int pointerY = minecraft.getWindow().getGuiScaledHeight() / 2;
            graphics.fill(pointerX - 2, pointerY - 2, pointerX + 3, pointerY + 3, shadow);
            graphics.fill(pointerX - 1, pointerY - 1, pointerX + 2, pointerY + 2, active);
        }
        int gridRadius = Math.round((AssimilationTracePattern.GRID_SIZE - 1)
                * layout.spacing() * 0.5F);
        drawTimerBar(graphics, layout.centerX(), layout.centerY() + gridRadius + 10,
                remaining, alpha, active);
        Component buttonName = Component.translatable(view.qteButton() == 1
                ? "hud.mirebound.assimilation.qte.left"
                : "hud.mirebound.assimilation.qte.right");
        Component message = Component.translatable(
                "hud.mirebound.assimilation.qte.trace", buttonName);
        graphics.drawString(minecraft.font, message,
                layout.centerX() - minecraft.font.width(message) / 2,
                layout.centerY() + gridRadius + 15,
                argb(alpha, 255, 244, 226), true);
    }

    private static void renderReturnPrompt(GuiGraphics graphics, Minecraft minecraft,
            ClientAssimilationState.View view, Camera camera, int width, int height,
            float partialTick) {
        Vec3 direction = AssimilationQteClient.bodyCenter(view).subtract(camera.getPosition());
        ScreenPoint target = project(direction, camera, width, height,
                effectiveFov(minecraft, camera, partialTick));
        IconPoint icon = iconPoint(target, width, height);
        float pulse = 0.82F + 0.18F * Mth.sin((minecraft.player.tickCount
                + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) * 0.24F);
        int alpha = Mth.clamp(Math.round(235.0F * pulse), 0, 255);
        int accent = argb(alpha, 164, 224, 255);
        int shadow = argb(Math.round(alpha * 0.72F), 8, 14, 20);
        drawTargetMarker(graphics, target.x(), target.y(), accent, shadow, false);
        drawConnector(graphics, target.x(), target.y(), icon.x(), icon.y(), shadow, accent);
        drawBodyGlyph(graphics, icon.x(), icon.y(), alpha, accent);
        Component message = Component.translatable(
                "hud.mirebound.assimilation.qte.return",
                String.format(java.util.Locale.ROOT, "%.1f", AssimilationQteClient.distanceToBody(view)),
                String.format(java.util.Locale.ROOT, "%.1f", view.profile().selfRescueQteRange()));
        graphics.drawString(minecraft.font, message,
                icon.x() - minecraft.font.width(message) / 2, icon.y() + 15,
                argb(alpha, 225, 244, 255), true);
    }

    private static double effectiveFov(Minecraft minecraft, Camera camera, float partialTick) {
        try {
            return ((GameRendererFovAccessor) (Object) minecraft.gameRenderer)
                    .mirebound$invokeGetFov(camera, partialTick, true);
        } catch (RuntimeException | LinkageError ignored) {
            return minecraft.options.fov().get();
        }
    }

    private static IconPoint iconPoint(ScreenPoint target, int width, int height) {
        double towardCenterX = width * 0.5D - target.x();
        double towardCenterY = height * 0.5D - target.y();
        double length = Math.max(1.0D, Math.sqrt(
                towardCenterX * towardCenterX + towardCenterY * towardCenterY));
        int x;
        int y;
        if (target.clipped()) {
            x = target.x() + Mth.floor(towardCenterX / length * ICON_OFFSET);
            y = target.y() + Mth.floor(towardCenterY / length * ICON_OFFSET);
        } else {
            int horizontal = target.x() > width - 54 ? -ICON_OFFSET : ICON_OFFSET;
            x = target.x() + horizontal;
            y = target.y() - 5;
        }
        return new IconPoint(Mth.clamp(x, 20, width - 20),
                Mth.clamp(y, 20, height - 39));
    }

    private static ScreenPoint project(Vec3 direction, Camera camera,
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
        return new ScreenPoint(width / 2 + Mth.floor(normalizedX * maxX),
                height / 2 + Mth.floor(normalizedY * maxY), clipped);
    }

    private static void drawTargetMarker(GuiGraphics graphics, int x, int y,
            int color, int shadow, boolean aimed) {
        int r = aimed ? MARKER_RADIUS + 1 : MARKER_RADIUS;
        graphics.fill(x - r - 1, y - 1, x + r + 2, y + 2, shadow);
        graphics.fill(x - 1, y - r - 1, x + 2, y + r + 2, shadow);
        graphics.fill(x - r, y, x + r + 1, y + 1, color);
        graphics.fill(x, y - r, x + 1, y + r + 1, color);
    }

    private static void drawConnector(GuiGraphics graphics, int fromX, int fromY,
            int toX, int toY, int shadow, int color) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        for (int step = 7; step < Math.max(7, steps - 17); step += 3) {
            int x = fromX + Math.round(dx * step / (float) Math.max(1, steps));
            int y = fromY + Math.round(dy * step / (float) Math.max(1, steps));
            graphics.fill(x, y, x + 2, y + 2, shadow);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawTimerRing(GuiGraphics graphics, int x, int y,
            float remaining, int alpha, int accent) {
        int active = Mth.clamp((int) Math.ceil(remaining * TIMER_SEGMENTS.length),
                0, TIMER_SEGMENTS.length);
        int empty = argb(Math.round(alpha * 0.36F), 43, 30, 33);
        int shadow = argb(Math.round(alpha * 0.62F), 10, 8, 9);
        for (int index = 0; index < TIMER_SEGMENTS.length; index++) {
            int sx = x + TIMER_SEGMENTS[index][0];
            int sy = y + TIMER_SEGMENTS[index][1];
            graphics.fill(sx, sy, sx + 3, sy + 3, shadow);
            graphics.fill(sx, sy, sx + 2, sy + 2, index < active ? accent : empty);
        }
    }

    private static void drawTimerBar(GuiGraphics graphics, int x, int y,
            float remaining, int alpha, int accent) {
        int width = 31;
        int filled = Mth.clamp(Math.round(width * remaining), 0, width);
        graphics.fill(x - 17, y - 2, x + 18, y + 3,
                argb(Math.round(alpha * 0.66F), 14, 10, 12));
        graphics.fill(x - 16, y - 1, x + 17, y + 2,
                argb(Math.round(alpha * 0.44F), 65, 47, 50));
        if (filled > 0) {
            graphics.fill(x - 16, y - 1, x - 16 + filled, y + 2, accent);
        }
    }

    private static void drawTraceNode(GuiGraphics graphics, int x, int y,
            int color, int shadow, boolean current, boolean start, int baseRadius) {
        int radius = current || start ? baseRadius + 1 : baseRadius;
        graphics.fill(x - radius - 1, y - radius - 1,
                x + radius + 2, y + radius + 2, shadow);
        graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
        int inset = Math.max(1, radius - 1);
        graphics.fill(x - inset, y - inset, x + inset + 1, y + inset + 1,
                argb(FastColor.ARGB32.alpha(color), 31, 23, 25));
        if (current) {
            graphics.fill(x - 1, y - 1, x + 2, y + 2,
                    argb(FastColor.ARGB32.alpha(color), 255, 246, 226));
        } else if (start) {
            graphics.fill(x - 2, y - 1, x + 3, y + 2,
                    argb(FastColor.ARGB32.alpha(color), 255, 246, 226));
        }
    }

    private static int indexOf(int[] path, int node) {
        for (int index = 0; index < path.length; index++) {
            if (path[index] == node) {
                return index;
            }
        }
        return -1;
    }

    private static void drawPixelLine(GuiGraphics graphics, int fromX, int fromY,
            int toX, int toY, int shadow, int color) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        // Draw the whole outline first. Interleaving outline and color lets each
        // following outline overwrite the previous bright line pixel.
        for (int step = 0; step <= steps; step++) {
            int x = fromX + Math.round(dx * step / (float) Math.max(1, steps));
            int y = fromY + Math.round(dy * step / (float) Math.max(1, steps));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, shadow);
        }
        for (int step = 0; step <= steps; step++) {
            int x = fromX + Math.round(dx * step / (float) Math.max(1, steps));
            int y = fromY + Math.round(dy * step / (float) Math.max(1, steps));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawMouseGlyph(GuiGraphics graphics, int x, int y,
            int button, AssimilationQteAction action, float holdProgress,
            int alpha, int accent) {
        int outline = argb(alpha, 245, 235, 220);
        int body = argb(Math.round(alpha * 0.92F), 35, 27, 29);
        int inactive = argb(Math.round(alpha * 0.72F), 92, 76, 76);
        graphics.fill(x - 3, y - 8, x + 4, y - 7, outline);
        graphics.fill(x - 5, y - 6, x + 6, y + 6, outline);
        graphics.fill(x - 3, y + 7, x + 4, y + 8, outline);
        graphics.fill(x - 4, y - 6, x + 5, y + 6, body);
        int selected = action == AssimilationQteAction.HOLD
                ? argb(Math.round(alpha * 0.68F), 132, 104, 90) : accent;
        graphics.fill(x - 4, y - 6, x, y, button == 1 ? selected : inactive);
        graphics.fill(x + 1, y - 6, x + 5, y, button == 2 ? selected : inactive);
        if (action == AssimilationQteAction.HOLD && holdProgress > 0.0F) {
            int fillHeight = Math.max(1, Mth.ceil(holdProgress * 6.0F));
            int minX = button == 1 ? x - 4 : x + 1;
            int maxX = button == 1 ? x : x + 5;
            graphics.fill(minX, y - fillHeight, maxX, y, accent);
        }
        graphics.fill(x, y - 6, x + 1, y + 1, outline);
        graphics.fill(x - 4, y, x + 5, y + 1, outline);
        graphics.fill(x - 2, y + 3, x + 3, y + 5,
                argb(Math.round(alpha * 0.55F), 66, 50, 52));
        if (action == AssimilationQteAction.HOLD) {
            int arrow = holdProgress >= 0.999F
                    ? argb(alpha, 225, 255, 148) : outline;
            int arrowX = button == 1 ? x - 2 : x + 2;
            graphics.fill(arrowX - 2, y - 12, arrowX + 3, y - 11, arrow);
            graphics.fill(arrowX - 1, y - 11, arrowX + 2, y - 10, arrow);
            graphics.fill(arrowX, y - 10, arrowX + 1, y - 9, arrow);
        }
    }

    private static void drawBodyGlyph(GuiGraphics graphics, int x, int y,
            int alpha, int accent) {
        int shadow = argb(Math.round(alpha * 0.68F), 9, 18, 25);
        graphics.fill(x - 3, y - 9, x + 4, y - 2, shadow);
        graphics.fill(x - 5, y - 1, x + 6, y + 8, shadow);
        graphics.fill(x - 2, y - 8, x + 3, y - 3, accent);
        graphics.fill(x - 4, y, x + 5, y + 3, accent);
        graphics.fill(x - 2, y + 3, x + 3, y + 8, accent);
    }

    private static void drawRapidClicks(GuiGraphics graphics, int x, int y,
            int clicks, int required, int alpha, int accent) {
        int count = Mth.clamp(required, 2, 8);
        int start = x - (count * 3 - 1) / 2;
        int empty = argb(Math.round(alpha * 0.48F), 76, 58, 60);
        for (int index = 0; index < count; index++) {
            graphics.fill(start + index * 3, y, start + index * 3 + 2, y + 2,
                    index < clicks ? accent : empty);
        }
    }

    private static void drawStreak(GuiGraphics graphics, int x, int y,
            int streak, int required, int alpha, int accent) {
        int count = Math.min(12, Math.max(1, required));
        int start = x - (count * 4 - 1) / 2;
        int empty = argb(Math.round(alpha * 0.52F), 74, 57, 59);
        for (int index = 0; index < count; index++) {
            int color = index < streak ? accent : empty;
            graphics.fill(start + index * 4, y, start + index * 4 + 3, y + 3, color);
        }
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return FastColor.ARGB32.color(Mth.clamp(alpha, 0, 255), red, green, blue);
    }

    private record ScreenPoint(int x, int y, boolean clipped) {
    }

    private record IconPoint(int x, int y) {
    }
}
