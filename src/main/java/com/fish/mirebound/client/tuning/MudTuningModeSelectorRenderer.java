package com.fish.mirebound.client.tuning;

import com.fish.mirebound.Mirebound;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** Pixel-art mode cells for the tuning-wand HUD. */
final class MudTuningModeSelectorRenderer {
    private static final int MODE_LIGHT = 0xFF29382F;
    private static final int MODE_DARK = 0xFF18221D;
    private static final int LOCKED_BASE = 0xFF651A1A;
    private static final int LOCKED_BORDER = 0xFFE54B4B;
    private static final ResourceLocation SETTINGS_ICON =
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "textures/gui/tuning_settings_gear.png");
    private static final String[] CONVERT_ICON = {
            ".....#...",
            "..#####..",
            ".....#...",
            "###...###",
            "#.#...#.#",
            "###...###",
            "...#.....",
            "..#####..",
            "...#....."
    };
    private static final String[] LOCK_ICON = {
            "...###...",
            "..#...#..",
            "..#...#..",
            ".#######.",
            ".#######.",
            ".###.###.",
            ".###.###.",
            ".#######.",
            "........."
    };
    private static final String[] GENERATION_ICON = {
            "..#...#..",
            ".###.###.",
            "#########",
            "...###...",
            "...###...",
            "....#....",
            "...###...",
            "..#####..",
            "........."
    };
    private MudTuningModeSelectorRenderer() {
    }

    static void render(GuiGraphics graphics, Font font,
            int x, int y, int width, int accent) {
        var modes = MudTuningWandMode.hudOrder();
        int trackSize = width / modes.size();
        int modeHeight = MudTuningHudLayout.modeHeight(graphics.guiWidth());
        for (int index = 0; index < modes.size(); index++) {
            MudTuningWandMode mode = modes.get(index);
            boolean selected = mode == MudTuningClientState.mode();
            int cellSize = MudTuningHudLayout.modeCellSize(trackSize, selected);
            int cellX = x + index * trackSize + (trackSize - cellSize) / 2;
            int cellY = y + (modeHeight - cellSize) / 2;
            renderCell(graphics, font, mode, index,
                    cellX, cellY, cellSize, selected, accent);
        }
    }

    private static void renderCell(GuiGraphics graphics, Font font,
            MudTuningWandMode mode, int index, int x, int y, int size,
            boolean selected, int accent) {
        boolean locked = selected && mode == MudTuningWandMode.CONVERT
                && MudTuningInputController.conversionLocked();
        int base = index % 2 == 0 ? MODE_LIGHT : MODE_DARK;
        if (locked) {
            base = LOCKED_BASE;
        } else if (selected) {
            base = mixRgb(base, accent, 0.24F);
        }
        int border = locked ? LOCKED_BORDER
                : selected ? accent : mixRgb(base, 0xFFFFFFFF, 0.22F);
        graphics.fill(x, y, x + size, y + size, withConfiguredAlpha(border));
        renderMosaic(graphics, x + 1, y + 1, size - 2,
                base, mode.ordinal(), selected);

        Component label = fit(font, Component.translatable(mode.translationKey()),
                Math.max(8, size - 6));
        int labelY = y + size - font.lineHeight - 3;
        int textX = x + (size - font.width(label)) / 2;
        graphics.drawString(font, label, textX, labelY,
                selected ? 0xFFFFFFFF : 0xFFC1CBC5, false);

        int iconTop = y + 5;
        int iconBottom = labelY - 3;
        int iconSize = Math.max(4, Math.min(18, iconBottom - iconTop));
        int iconX = x + (size - iconSize) / 2;
        int iconY = iconTop + Math.max(0, (iconBottom - iconTop - iconSize) / 2);
        int iconColor = locked ? 0xFFFFD6D6 : selected
                ? mixRgb(accent, 0xFFFFFFFF, 0.32F)
                : 0xFFD0D8D3;
        if (locked) {
            renderPixelIcon(graphics, LOCK_ICON, iconX, iconY, iconSize, iconColor);
        } else {
            renderIcon(graphics, mode, iconX, iconY, iconSize, iconColor);
        }
    }

    private static void renderMosaic(GuiGraphics graphics, int x, int y,
            int size, int base, int seed, boolean selected) {
        for (int row = 0; row < 2; row++) {
            int top = y + row * size / 2;
            int bottom = y + (row + 1) * size / 2;
            for (int column = 0; column < 2; column++) {
                int left = x + column * size / 2;
                int right = x + (column + 1) * size / 2;
                int hash = seed * 31 + row * 11 + column * 17 + row * column * 7;
                float amount = 0.025F + Math.floorMod(hash, 4) * 0.018F;
                int target = (hash & 1) == 0 ? 0xFFFFFFFF : 0xFF000000;
                if (selected) {
                    amount += 0.012F;
                }
                graphics.fill(left, top, right, bottom,
                        withConfiguredAlpha(mixRgb(base, target, amount)));
            }
        }
    }

    private static void renderIcon(GuiGraphics graphics,
            MudTuningWandMode mode, int x, int y, int size, int color) {
        int stroke = Math.max(1, size / 8);
        switch (mode) {
            case RANGE -> renderRangeIcon(graphics, x, y, size, stroke, color);
            case SINGLE -> renderSingleIcon(graphics, x, y, size, stroke, color);
            case CONVERT -> renderConvertIcon(graphics, x, y, size, color);
            case SUMMON -> MudTuningSummonIconRenderer.render(
                    graphics, MudTuningClientState.summonType(), x, y, size, color);
            case GENERATION -> renderPixelIcon(
                    graphics, GENERATION_ICON, x, y, size, color);
            case SETTINGS -> renderSettingsIcon(graphics, x, y, size, color);
        }
    }

    private static void renderSettingsIcon(
            GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.setColor(
                FastColor.ARGB32.red(color) / 255.0F,
                FastColor.ARGB32.green(color) / 255.0F,
                FastColor.ARGB32.blue(color) / 255.0F,
                FastColor.ARGB32.alpha(color) / 255.0F);
        graphics.blit(SETTINGS_ICON, x, y, size, size,
                0.0F, 0.0F, 32, 32, 32, 32);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderRangeIcon(GuiGraphics graphics, int x, int y,
            int size, int stroke, int color) {
        int arm = Math.max(stroke + 2, size / 3);
        fill(graphics, x, y, arm, stroke, color);
        fill(graphics, x, y, stroke, arm, color);
        fill(graphics, x + size - arm, y, arm, stroke, color);
        fill(graphics, x + size - stroke, y, stroke, arm, color);
        fill(graphics, x, y + size - stroke, arm, stroke, color);
        fill(graphics, x, y + size - arm, stroke, arm, color);
        fill(graphics, x + size - arm, y + size - stroke, arm, stroke, color);
        fill(graphics, x + size - stroke, y + size - arm, stroke, arm, color);
    }

    private static void renderSingleIcon(GuiGraphics graphics, int x, int y,
            int size, int stroke, int color) {
        int center = size / 2;
        int arm = Math.max(2, size / 4);
        fill(graphics, x + center - stroke / 2, y, stroke, arm, color);
        fill(graphics, x + center - stroke / 2, y + size - arm,
                stroke, arm, color);
        fill(graphics, x, y + center - stroke / 2, arm, stroke, color);
        fill(graphics, x + size - arm, y + center - stroke / 2,
                arm, stroke, color);
        int dot = Math.max(2, stroke + 1);
        fill(graphics, x + center - dot / 2, y + center - dot / 2,
                dot, dot, color);
    }

    private static void renderConvertIcon(GuiGraphics graphics, int x, int y,
            int size, int color) {
        renderPixelIcon(graphics, CONVERT_ICON, x, y, size, color);
    }

    private static void renderPixelIcon(GuiGraphics graphics, String[] pixels,
            int x, int y, int size, int color) {
        for (int row = 0; row < pixels.length; row++) {
            int top = y + row * size / pixels.length;
            int bottom = y + (row + 1) * size / pixels.length;
            for (int column = 0; column < pixels[row].length(); column++) {
                if (pixels[row].charAt(column) != '#') {
                    continue;
                }
                int left = x + column * size / pixels[row].length();
                int right = x + (column + 1) * size / pixels[row].length();
                graphics.fill(left, top, right, bottom, color);
            }
        }
    }

    private static void fill(GuiGraphics graphics, int x, int y,
            int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    private static Component fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String suffix = "...";
        int available = Math.max(0, width - font.width(suffix));
        return Component.literal(font.plainSubstrByWidth(text.getString(), available) + suffix);
    }

    private static int withConfiguredAlpha(int color) {
        int sourceAlpha = color >>> 24;
        float opacity = MudTuningWandHud.configuredHudOpacity(
                MudTuningHudElement.CENTER);
        int alpha = Math.round(sourceAlpha * opacity);
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private static int mixRgb(int from, int to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int red = Math.round(FastColor.ARGB32.red(from)
                + (FastColor.ARGB32.red(to) - FastColor.ARGB32.red(from)) * clamped);
        int green = Math.round(FastColor.ARGB32.green(from)
                + (FastColor.ARGB32.green(to) - FastColor.ARGB32.green(from)) * clamped);
        int blue = Math.round(FastColor.ARGB32.blue(from)
                + (FastColor.ARGB32.blue(to) - FastColor.ARGB32.blue(from)) * clamped);
        return FastColor.ARGB32.color(255, red, green, blue);
    }
}
