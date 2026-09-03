package com.fish.mirebound.client;

import com.fish.mirebound.mud.MudPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class TenderFleshHudRenderer {
    private static final float RELEASE_WINDOW_EXIT_SCALE = 0.82F;
    private static int releaseWindowPlayerId = Integer.MIN_VALUE;
    private static boolean releaseWindowOpen;

    private TenderFleshHudRenderer() {
    }

    static boolean render(GuiGraphics graphics, Minecraft minecraft, float charge) {
        if (!MudPhysics.isClientPlayerInTenderFlesh(minecraft.player)) {
            releaseWindowOpen = false;
            releaseWindowPlayerId = Integer.MIN_VALUE;
            return false;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int x = StruggleHudLayout.barX(width);
        int y = StruggleHudLayout.barY(minecraft);
        float opportunity = MudPhysics.clientTenderFleshEscapeOpportunity(minecraft.player);
        float releaseThreshold = Mth.clamp(
                MudPhysics.clientTenderFleshReleaseThreshold(minecraft.player),
                0.01F, 1.0F);
        float contraction = MudPhysics.clientTenderFleshContraction(minecraft.player);
        float wrap = MudPhysics.clientTenderFleshWrap(minecraft.player);
        float pressure = MudPhysics.clientTenderFleshPressure(minecraft.player);
        float calmness = MudPhysics.clientTenderFleshCalmness(minecraft.player);
        float clampedCharge = Mth.clamp(charge, 0.0F, 1.0F);
        if (releaseWindowPlayerId != minecraft.player.getId()) {
            releaseWindowPlayerId = minecraft.player.getId();
            releaseWindowOpen = false;
        }
        releaseWindowOpen = updateReleaseWindow(
                releaseWindowOpen, opportunity, releaseThreshold);

        int outerColor = color(opportunity, 0xE014080A, 0xE04B2529);
        int frameColor = color(opportunity, 0xF04A171B, 0xF0924D54);
        int bedColor = color(opportunity, 0xF0210D10, 0xF04A2529);
        StruggleHudLayout.fillPixelRounded(graphics, x - 2, y - 1,
                StruggleHudLayout.BAR_WIDTH + 4, StruggleHudLayout.BAR_HEIGHT + 2, outerColor);
        StruggleHudLayout.fillPixelRounded(graphics, x - 1, y,
                StruggleHudLayout.BAR_WIDTH + 2, StruggleHudLayout.BAR_HEIGHT, frameColor);
        StruggleHudLayout.fillPixelRounded(graphics, x, y + 1,
                StruggleHudLayout.BAR_WIDTH, StruggleHudLayout.BAR_HEIGHT - 2, bedColor);
        drawWrapJaws(graphics, x, y, Math.max(wrap, pressure * 0.88F));
        for (int marker = 1; marker < 10; marker++) {
            int markerX = x + 2 + marker * StruggleHudLayout.INNER_WIDTH / 10;
            graphics.fill(markerX, y + 3, markerX + 1,
                    y + StruggleHudLayout.BAR_HEIGHT - 1, 0x88421317);
        }
        if (clampedCharge > 0.0F) {
            int fillX = x + 2;
            int red = Mth.lerpInt(opportunity, 174, 238);
            int green = Mth.lerpInt(opportunity, 76, 186);
            int blue = Mth.lerpInt(opportunity, 79, 157);
            int color = 0xFF000000 | red << 16 | green << 8 | blue;
            StruggleHudLayout.fillPixelRoundedProgress(
                    graphics, fillX, y + 2, StruggleHudLayout.INNER_WIDTH,
                    StruggleHudLayout.BAR_HEIGHT - 4, clampedCharge, color);
            boolean fullReleaseFlash = clampedCharge >= 0.999F && releaseWindowOpen;
            if (fullReleaseFlash) {
                // Use the exact fixed interior mask so the ready flash cannot
                // lose cap pixels or inherit a partially filled silhouette.
                StruggleHudLayout.fillPixelRoundedProgress(
                        graphics,
                        fillX,
                        y + 2,
                        StruggleHudLayout.INNER_WIDTH,
                        StruggleHudLayout.BAR_HEIGHT - 4,
                        1.0F,
                        0xFFFFE7D8);
            } else {
                StruggleHudLayout.fillPixelRoundedProgress(
                        graphics, fillX, y + 2, StruggleHudLayout.INNER_WIDTH,
                        1, clampedCharge,
                        releaseWindowOpen ? 0xFFFFD8C1 : 0xFFE48380);
            }
        }
        int pulseInset = Mth.clamp(Math.round(contraction * 7.0F), 0, 7);
        StruggleHudLayout.fillPixelRounded(graphics, x + pulseInset, y,
                StruggleHudLayout.BAR_WIDTH - pulseInset * 2, 1, 0xC98B3035);
        int windowWidth = Math.max(1, Math.round(StruggleHudLayout.INNER_WIDTH * opportunity));
        int windowX = x + StruggleHudLayout.BAR_WIDTH / 2 - windowWidth / 2;
        int windowHeight = releaseWindowOpen ? 2 : 1;
        StruggleHudLayout.fillPixelRounded(graphics, windowX,
                y + StruggleHudLayout.BAR_HEIGHT, windowWidth, windowHeight,
                releaseWindowOpen ? 0xFFF8D8C4 : 0xFF823338);
        int calmWidth = Math.round(StruggleHudLayout.INNER_WIDTH * calmness);
        if (calmWidth > 0) {
            StruggleHudLayout.fillPixelRounded(
                    graphics,
                    x + 2,
                    y + StruggleHudLayout.BAR_HEIGHT + 2,
                    calmWidth,
                    1,
                    0xFFC5A6A0);
        }
        return true;
    }

    static boolean updateReleaseWindow(boolean currentlyOpen,
            float opportunity, float threshold) {
        float sanitizedThreshold = Mth.clamp(threshold, 0.01F, 1.0F);
        return currentlyOpen
                ? opportunity >= sanitizedThreshold * RELEASE_WINDOW_EXIT_SCALE
                : opportunity >= sanitizedThreshold;
    }

    private static void drawWrapJaws(GuiGraphics graphics, int x, int y, float wrap) {
        int reach = Mth.clamp(Math.round(26.0F * wrap), 0, 26);
        if (reach <= 0) {
            return;
        }
        int left = x + 2;
        int right = x + StruggleHudLayout.BAR_WIDTH - 2;
        int color = 0xEF621B24;
        graphics.fill(left, y + 3, left + reach, y + 5, color);
        graphics.fill(right - reach, y + 3, right, y + 5, color);
        for (int offset = 2; offset < reach; offset += 5) {
            int tooth = (offset / 5 & 1) == 0 ? 1 : 2;
            graphics.fill(left + offset, y + 2, left + offset + 2, y + 2 + tooth, color);
            graphics.fill(right - offset - 2, y + 5 - tooth,
                    right - offset, y + 5, color);
        }
    }

    private static int color(float amount, int from, int to) {
        int alpha = Mth.lerpInt(amount, from >>> 24, to >>> 24);
        int red = Mth.lerpInt(amount, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int green = Mth.lerpInt(amount, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int blue = Mth.lerpInt(amount, from & 0xFF, to & 0xFF);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
