package com.fish.mirebound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Shared pixel geometry for the escape bars and their reserved vanilla HUD slot. */
public final class StruggleHudLayout {
    public static final int BAR_WIDTH = 182;
    public static final int BAR_HEIGHT = 7;
    public static final int INNER_WIDTH = BAR_WIDTH - 4;
    public static final int VANILLA_STATUS_LIFT = 11;

    private static final int BOTTOM_METER_OFFSET = 7;
    private static final int HUD_GAP = 2;
    private static final int FRAME_OUTSET = 1;
    private static int capturedVanillaHeight = -1;
    private static int capturedGuiHeight = -1;

    private StruggleHudLayout() {
    }

    public static int barX(int guiWidth) {
        return guiWidth / 2 - BAR_WIDTH / 2;
    }

    public static int barY(Minecraft minecraft) {
        boolean hasBottomMeter = minecraft.player != null
                && (minecraft.player.jumpableVehicle() != null
                || minecraft.gameMode != null && minecraft.gameMode.hasExperience());
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int vanillaHeight = guiHeight == capturedGuiHeight
                ? capturedVanillaHeight : -1;
        return barY(guiHeight, hasBottomMeter,
                vanillaHeight > 0 ? vanillaHeight : 39);
    }

    public static int barY(int guiHeight, boolean hasBottomMeter) {
        return barY(guiHeight, hasBottomMeter, 39);
    }

    static int barY(int guiHeight, boolean hasBottomMeter, int vanillaHeight) {
        int vanillaAnchor = guiHeight - Math.max(0, vanillaHeight);
        return vanillaAnchor + (hasBottomMeter ? 0 : BOTTOM_METER_OFFSET);
    }

    /** Captures this frame's vanilla HUD cursor before custom bars reserve space. */
    public static void captureVanillaHeight(Minecraft minecraft) {
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        capturedVanillaHeight = Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight);
        capturedGuiHeight = minecraft.getWindow().getGuiScaledHeight();
    }

    public static int customBarLift(int barCount) {
        return Math.max(0, barCount) * VANILLA_STATUS_LIFT;
    }

    /** Places another bar above the standard struggle-bar slot with one pixel of air. */
    public static int barYAbove(int baseY) {
        return baseY - BAR_HEIGHT - HUD_GAP - FRAME_OUTSET;
    }

    /**
     * Fills a horizontal pixel-art capsule. The cap deliberately uses a small
     * stepped profile instead of a smooth radius so it stays consistent with
     * Minecraft's low-resolution HUD icons.
     */
    public static void fillPixelRounded(GuiGraphics graphics, int x, int y,
            int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int cap = height == 3 ? 1 : Math.max(0, height / 2 - 1);
        for (int row = 0; row < height; row++) {
            int distanceFromEdge = Math.min(row, height - 1 - row);
            int inset = Math.max(0, cap - distanceFromEdge);
            if (inset * 2 >= width) {
                inset = Math.max(0, (width - 1) / 2);
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    /**
     * Fills a clipped portion of one fixed capsule. Unlike rounding the
     * already-clipped width, this keeps both end caps stable as progress moves.
     */
    public static void fillPixelRoundedProgress(GuiGraphics graphics, int x, int y,
            int width, int height, float progress, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int clipEnd = x + Mth.clamp(Math.round(width * progress), 0, width);
        int cap = height == 3 ? 1 : Math.max(0, height / 2 - 1);
        for (int row = 0; row < height; row++) {
            int distanceFromEdge = Math.min(row, height - 1 - row);
            int inset = Math.max(0, cap - distanceFromEdge);
            if (inset * 2 >= width) {
                inset = Math.max(0, (width - 1) / 2);
            }
            int rowStart = x + inset;
            int rowEnd = Math.min(x + width - inset, clipEnd);
            if (rowEnd > rowStart) {
                graphics.fill(rowStart, y + row, rowEnd, y + row + 1, color);
            }
        }
    }
}
