package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class SculkMireHudRenderer {
    private static final int SEGMENT_WIDTH = 8;
    private static final int SEGMENT_GAP = 1;

    private SculkMireHudRenderer() {
    }

    static boolean isVisible(Minecraft minecraft, float partialTick) {
        if (minecraft.player == null) {
            return false;
        }
        return ClientSculkClampManager.hudProgress(minecraft.player.getId(), partialTick) >= 0.0F
                || MudPhysics.clientSculkEscapeProgress(minecraft.player) >= 0.0F;
    }

    static boolean render(GuiGraphics graphics, Minecraft minecraft, float partialTick) {
        float clampProgress = ClientSculkClampManager.hudProgress(
                minecraft.player.getId(), partialTick);
        Mode mode;
        float progress;
        if (clampProgress >= 0.0F) {
            mode = Mode.RESTRAINT;
            progress = clampProgress;
        } else {
            progress = MudPhysics.clientSculkEscapeProgress(minecraft.player);
            if (progress < 0.0F) {
                return false;
            }
            mode = Mode.ESCAPE;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int x = StruggleHudLayout.barX(width);
        int y = StruggleHudLayout.barY(minecraft);
        drawFrame(graphics, x, y);
        drawFill(graphics, minecraft, x, y, progress, mode);
        return true;
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y) {
        StruggleHudLayout.fillPixelRounded(graphics, x - 2, y - 1,
                StruggleHudLayout.BAR_WIDTH + 4, StruggleHudLayout.BAR_HEIGHT + 2, 0xD9060A0E);
        StruggleHudLayout.fillPixelRounded(graphics, x - 1, y,
                StruggleHudLayout.BAR_WIDTH + 2, StruggleHudLayout.BAR_HEIGHT, 0xF00B2730);
        StruggleHudLayout.fillPixelRounded(graphics, x, y + 1,
                StruggleHudLayout.BAR_WIDTH, StruggleHudLayout.BAR_HEIGHT - 2, 0xF0061118);
        StruggleHudLayout.fillPixelRounded(graphics, x - 4, y + 2, 3,
                StruggleHudLayout.BAR_HEIGHT - 3, 0xE0147980);
        StruggleHudLayout.fillPixelRounded(graphics, x + StruggleHudLayout.BAR_WIDTH + 1,
                y + 2, 3, StruggleHudLayout.BAR_HEIGHT - 3, 0xE0147980);
    }

    private static void drawFill(GuiGraphics graphics, Minecraft minecraft,
            int x, int y, float progress, Mode mode) {
        int fillWidth = Mth.clamp(Math.round(StruggleHudLayout.INNER_WIDTH * progress),
                0, StruggleHudLayout.INNER_WIDTH);
        if (fillWidth > 0) {
            int fillX = x + 2;
            int fillEnd = fillX + fillWidth;
            int base = mode == Mode.ESCAPE ? 0xFF0B6872 : 0xFF12525F;
            int light = mode == Mode.ESCAPE ? 0xFF28AEB2 : 0xFF39C5B8;
            int shadow = mode == Mode.ESCAPE ? 0xFF07434C : 0xFF0B3542;
            StruggleHudLayout.fillPixelRounded(graphics, fillX, y + 2, fillWidth,
                    StruggleHudLayout.BAR_HEIGHT - 4, base);
            int capInset = fillWidth > 2 ? 1 : 0;
            int endInset = fillWidth == StruggleHudLayout.INNER_WIDTH ? 1 : 0;
            graphics.fill(fillX + capInset, y + 2, fillEnd - endInset, y + 3, light);
            graphics.fill(fillX + capInset, y + StruggleHudLayout.BAR_HEIGHT - 2,
                    fillEnd - endInset,
                    y + StruggleHudLayout.BAR_HEIGHT - 1, shadow);

            int pulse = Math.floorMod(minecraft.player.tickCount / 4, 7);
            for (int segment = 0;
                    segment < StruggleHudLayout.INNER_WIDTH / (SEGMENT_WIDTH + SEGMENT_GAP);
                    segment++) {
                int segmentX = fillX + segment * (SEGMENT_WIDTH + SEGMENT_GAP);
                if (segmentX >= fillEnd) {
                    break;
                }
                if (segment > 0) {
                    graphics.fill(segmentX - 1, y + 2,
                            Math.min(segmentX, fillEnd),
                            y + StruggleHudLayout.BAR_HEIGHT - 1, 0xB905222B);
                }
                if (Math.floorMod(segment * 3 + pulse, 7) == 0) {
                    int glintX = Math.min(segmentX + 2, fillEnd - 1);
                    graphics.fill(glintX, y + 3, glintX + 1, y + 5, 0xFF68DED0);
                }
            }
        }

        for (int cell = 1; cell < 9; cell++) {
            int markerX = x + 2 + cell * StruggleHudLayout.INNER_WIDTH / 9;
            graphics.fill(markerX, y + StruggleHudLayout.BAR_HEIGHT - 1, markerX + 1,
                    y + StruggleHudLayout.BAR_HEIGHT, 0xA20E4F59);
        }
    }

    private enum Mode {
        ESCAPE,
        RESTRAINT
    }
}
