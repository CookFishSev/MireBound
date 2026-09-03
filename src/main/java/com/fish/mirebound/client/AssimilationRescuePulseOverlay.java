package com.fish.mirebound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Small owner-only cue that points toward the frozen body when rescue opens new cells. */
final class AssimilationRescuePulseOverlay {
    private static final int CELL = 4;

    private AssimilationRescuePulseOverlay() {
    }

    static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || !ClientAssimilationState.localSoulActive(minecraft)) {
            return;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(minecraft.player.getId());
        if (view == null || view.rescuePulseTicks() <= 0 || view.lastRescueCell() < 0) {
            return;
        }
        float progress = view.rescuePulseTicks()
                / (float) Math.max(1, view.profile().rescuePulseTicks());
        float pulse = (float) Math.sin(Math.PI * progress)
                * view.profile().rescuePulseStrength();
        if (pulse <= 0.001F) {
            return;
        }
        Vec3 soul = AssimilationSoulCamera.position();
        if (soul == null) {
            return;
        }
        Vec3 target = AssimilationBodyCellGeometry.worldPoint(view, view.lastRescueCell());
        Vec3 direction = target.subtract(soul);
        double yawToTarget = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double relative = Math.toRadians(Mth.wrapDegrees(yawToTarget - AssimilationSoulCamera.yaw()));
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        double radiusX = width * 0.40D;
        double radiusY = height * 0.34D;
        int centerX = width / 2 + Mth.floor(Math.sin(relative) * radiusX);
        int centerY = height / 2 - Mth.floor(Math.cos(relative) * radiusY * 0.72D);
        int alpha = Mth.clamp(Math.round(pulse * 255.0F), 0, 255);
        Vec3 color = AssimilationSoulPresentation.mediumColor(
                view.medium(view.lastRescueCell()));
        int red = Mth.clamp((int) Math.round(color.x * 255.0D + 72.0D), 0, 255);
        int green = Mth.clamp((int) Math.round(color.y * 255.0D + 72.0D), 0, 255);
        int blue = Mth.clamp((int) Math.round(color.z * 255.0D + 72.0D), 0, 255);
        int argb = FastColor.ARGB32.color(alpha, red, green, blue);
        int size = CELL * 3;
        graphics.fill(centerX - size, centerY - CELL / 2,
                centerX + size, centerY + CELL / 2, argb);
        graphics.fill(centerX - CELL / 2, centerY - size,
                centerX + CELL / 2, centerY + size, argb);
        graphics.fill(centerX - CELL * 2, centerY - CELL * 2,
                centerX + CELL * 2, centerY + CELL * 2,
                FastColor.ARGB32.color(alpha / 3, red, green, blue));
    }

}
