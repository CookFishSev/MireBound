package com.fish.mirebound.client.tuning;

import net.minecraft.client.gui.GuiGraphics;

/** Pixel icons shared by the summon-mode HUD and radial selector. */
final class MudTuningSummonIconRenderer {
    private static final String[] TENTACLE_ICON = {
            ".......###...",
            "......#####..",
            "......##.##..",
            ".....###.....",
            "....###......",
            "...###.......",
            "..###........",
            "..##.........",
            "..###........",
            "...###.......",
            "....##.......",
            "..########...",
            ".##########.."
    };

    private MudTuningSummonIconRenderer() {
    }

    static void render(GuiGraphics graphics, MudTuningSummonType type,
            int x, int y, int size, int color) {
        switch (type) {
            case TENTACLE -> renderTentacle(graphics, x, y, size, color);
        }
    }

    private static void renderTentacle(GuiGraphics graphics,
            int x, int y, int size, int color) {
        renderMask(graphics, TENTACLE_ICON, x + 1, y + 1, size, shade(color, 0.38F));
        renderMask(graphics, TENTACLE_ICON, x, y, size, color);
    }

    private static void renderMask(GuiGraphics graphics, String[] mask,
            int x, int y, int size, int color) {
        int columns = mask[0].length();
        for (int row = 0; row < mask.length; row++) {
            int top = y + row * size / mask.length;
            int bottom = y + (row + 1) * size / mask.length;
            for (int column = 0; column < columns; column++) {
                if (mask[row].charAt(column) != '#') {
                    continue;
                }
                int left = x + column * size / columns;
                int right = x + (column + 1) * size / columns;
                graphics.fill(left, top, right, bottom, color);
            }
        }
    }

    private static int shade(int color, float scale) {
        int alpha = color >>> 24;
        int red = Math.round((color >> 16 & 0xFF) * scale);
        int green = Math.round((color >> 8 & 0xFF) * scale);
        int blue = Math.round((color & 0xFF) * scale);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
