package com.fish.mirebound.client;

import com.fish.mirebound.coverage.MudVisionSamplingLayout;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Draws the optional vision sampling diagnostics without owning overlay state. */
final class ScreenMudDebugRenderer {
    private static final float MIN_VISIBLE_COVERAGE = 0.025F;

    private ScreenMudDebugRenderer() {
    }

    static void render(GuiGraphics graphics, int width, int height) {
        int columns = ScreenMudOverlay.debugVisionColumns();
        int rows = ScreenMudOverlay.debugVisionRows();
        int mappedRows = ScreenMudOverlay.debugMappedVisionRows();
        int topExtension = ScreenMudOverlay.debugTopExtensionRows();
        int bottomExtension = rows - topExtension - mappedRows;
        int cell = Math.max(8, Math.min(width, height) / 60);
        int leftX = 10;
        int topY = Math.max(10, height / 2 - cell * rows / 2);
        int mappedX = width - cell * columns - 10;
        int mappedY = topY + topExtension * cell;
        int debugWidth = cell * columns;
        int debugHeight = cell * rows;

        graphics.fill(leftX - 3, topY - 14,
                leftX + debugWidth + 3, topY + debugHeight + 3, 0x99000000);
        graphics.drawString(Minecraft.getInstance().font,
                "camera vision " + columns + "x" + rows
                        + " (+" + topExtension + "/-" + bottomExtension + ")",
                leftX, topY - 12, 0xFFFFE08A, true);
        drawLocalGrid(graphics, leftX, topY, cell, columns, rows, mappedRows, topExtension);

        graphics.fill(mappedX - 3, mappedY - 14,
                mappedX + cell * columns + 3, mappedY + cell * mappedRows + 3, 0x99000000);
        graphics.drawString(Minecraft.getInstance().font,
                "screen mapped", mappedX, mappedY - 12, 0xFF9AF2FF, true);
        drawMappedGrid(graphics, mappedX, mappedY, cell, columns, mappedRows);

        int centerX = width / 2;
        int lineTop = ScreenMudOverlay.debugMappedScreenY(mappedRows - 1, height);
        int lineBottom = ScreenMudOverlay.debugMappedScreenY(0, height);
        graphics.fill(centerX - 54, lineTop - 1, centerX + 54, lineTop + 1, 0xCC33D6FF);
        graphics.fill(centerX - 54, lineBottom - 1, centerX + 54, lineBottom + 1, 0xCCFFB433);
        graphics.drawString(Minecraft.getInstance().font,
                "top", centerX + 58, lineTop - 5, 0xFF33D6FF, true);
        graphics.drawString(Minecraft.getInstance().font,
                "bottom", centerX + 58, lineBottom - 5, 0xFFFFB433, true);

        String settings = String.format(Locale.ROOT,
                "shiftY=%.3f overscanY=%.3f edgePullY=%.3f faceW=%.2f faceH=%.2f bottom=%.2f",
                ClientMudDebugOptions.visionScreenShiftY(),
                ClientMudDebugOptions.visionScreenOverscanY(),
                ClientMudDebugOptions.visionScreenEdgePullY(),
                MudVisionSamplingLayout.widthPixels(),
                MudVisionSamplingLayout.heightPixels(),
                MudVisionSamplingLayout.bottomOffsetPixels());
        graphics.drawString(Minecraft.getInstance().font,
                settings, 10, height - 18, 0xFFEAD7A2, true);
    }

    private static void drawLocalGrid(GuiGraphics graphics, int x, int y, int cell,
            int columns, int rows, int mappedRows, int topExtension) {
        int width = columns * cell;
        int topExtensionHeight = topExtension * cell;
        int originalY = y + topExtensionHeight;
        int originalHeight = mappedRows * cell;
        graphics.fill(x, y, x + width, y + topExtensionHeight, 0x221C9CFF);
        graphics.fill(x, originalY + originalHeight, x + width, y + rows * cell, 0x221C9CFF);
        for (int row = 0; row < rows; row++) {
            for (int lane = 0; lane < columns; lane++) {
                float coverage = ScreenMudOverlay.debugLocalCoverage(row, lane);
                int alpha = Mth.clamp(Math.round(coverage * 210.0F), 28, 230);
                boolean extension = row < topExtension || row >= topExtension + mappedRows;
                int color = coverage > MIN_VISIBLE_COVERAGE
                        ? FastColor.ARGB32.color(alpha, 60, 220, 255)
                        : extension ? 0x33202A36 : 0x33202020;
                drawCell(graphics, x + lane * cell, y + row * cell, cell, color);
            }
        }
        outline(graphics, x - 2, y - 2, width + 4, rows * cell + 4, 0xFF11A9FF, 2);
        outline(graphics, x - 2, originalY - 2, width + 4, originalHeight + 4, 0xFFFF3838, 2);
    }

    private static void drawMappedGrid(GuiGraphics graphics, int x, int y, int cell,
            int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int lane = 0; lane < columns; lane++) {
                float coverage = ScreenMudOverlay.debugMappedCoverage(row, lane);
                int alpha = Mth.clamp(Math.round(coverage * 210.0F), 28, 230);
                int color = coverage > MIN_VISIBLE_COVERAGE
                        ? FastColor.ARGB32.color(alpha, 60, 220, 255) : 0x33202020;
                drawCell(graphics, x + lane * cell, y + row * cell, cell, color);
            }
        }
    }

    private static void drawCell(GuiGraphics graphics, int x, int y, int cell, int color) {
        graphics.fill(x, y, x + cell - 1, y + cell - 1, color);
        graphics.fill(x, y, x + cell, y + 1, 0xAAFFFFFF);
        graphics.fill(x, y, x + 1, y + cell, 0xAAFFFFFF);
    }

    private static void outline(GuiGraphics graphics, int x, int y,
            int width, int height, int color, int thickness) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y, x + thickness, y + height, color);
        graphics.fill(x + width - thickness, y, x + width, y + height, color);
    }
}
