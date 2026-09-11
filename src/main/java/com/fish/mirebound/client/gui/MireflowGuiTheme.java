package com.fish.mirebound.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Shared flat pixel palette and surfaces for Mireflow configuration screens. */
public final class MireflowGuiTheme {
    public static final int DIM = 0xB0080B09;
    public static final int TILE_A = 0xFF111317;
    public static final int TILE_B = 0xFF171A20;
    public static final int HEADER = 0xFF1D2027;
    public static final int SIDEBAR = 0xFF15171C;
    public static final int FOOTER = 0xFF191B21;
    public static final int ROW_A = 0xFF20232A;
    public static final int ROW_B = 0xFF1B1E24;
    public static final int CONTROL = 0xFF292C34;
    public static final int CONTROL_HOVER = 0xFF393D47;
    public static final int INPUT = 0xFF101216;
    public static final int DIVIDER = 0xFF4E535E;
    public static final int ACCENT = 0xFFE2B85E;
    public static final int TEXT = 0xFFF4F1EA;
    public static final int MUTED = 0xFFB3B6BC;
    public static final int DISABLED = 0xFF70747C;
    public static final int ERROR = 0xFFFF756F;
    public static final int SUCCESS = 0xFF61D394;
    public static final int ENABLED_CONTROL = 0xFF27563D;
    public static final int ENABLED_HOVER = 0xFF34754F;
    public static final int DISABLED_CONTROL = 0xFF5A2D31;
    public static final int DISABLED_HOVER = 0xFF74383C;
    public static final int FULLSCREEN_HEADER = 0xD81D2027;
    public static final int FULLSCREEN_SIDEBAR = 0xD815171C;
    public static final int FULLSCREEN_FOOTER = 0xD8191B21;
    public static final int FULLSCREEN_ROW_A = 0xC820232A;
    public static final int FULLSCREEN_ROW_B = 0xC81B1E24;

    private static final int FULLSCREEN_TILE_A = 0xC0111317;
    private static final int FULLSCREEN_TILE_B = 0x70171A20;

    private MireflowGuiTheme() {
    }

    public static void drawTiledSurface(
            GuiGraphics graphics, int left, int top, int right, int bottom) {
        drawTiledSurface(graphics, left, top, right, bottom, TILE_A, TILE_B);
    }

    public static void drawTranslucentSurface(
            GuiGraphics graphics, int left, int top, int right, int bottom) {
        drawTiledSurface(graphics, left, top, right, bottom,
                FULLSCREEN_TILE_A, FULLSCREEN_TILE_B);
    }

    private static void drawTiledSurface(
            GuiGraphics graphics, int left, int top, int right, int bottom,
            int baseColor, int gridColor) {
        graphics.fill(left, top, right, bottom, baseColor);
        for (int y = top + 7; y < bottom; y += 8) {
            graphics.fill(left, y, right, y + 1, gridColor);
        }
        for (int x = left + 7; x < right; x += 8) {
            graphics.fill(x, top, x + 1, bottom, gridColor);
        }
    }

    public static Panel centeredPanel(
            int screenWidth, int screenHeight, int maximumWidth, int maximumHeight) {
        int width = Math.min(screenWidth, Math.min(maximumWidth, Math.max(260, screenWidth - 24)));
        int height = Math.min(screenHeight,
                Math.min(maximumHeight, Math.max(180, screenHeight - 24)));
        return new Panel((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height, screenWidth, screenHeight);
    }

    public static void drawPanel(GuiGraphics graphics, Panel panel) {
        graphics.fill(0, 0, panel.screenWidth(), panel.screenHeight(), DIM);
        graphics.fill(panel.left() - 1, panel.top() - 1,
                panel.right() + 1, panel.bottom() + 1, DIVIDER);
        drawTiledSurface(graphics, panel.left(), panel.top(), panel.right(), panel.bottom());
    }

    public record Panel(
            int left, int top, int width, int height, int screenWidth, int screenHeight) {
        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }
    }
}
