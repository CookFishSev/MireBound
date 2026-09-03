package com.fish.mirebound.client;

import com.fish.mirebound.assimilation.AssimilationPartialPurge;
import com.fish.mirebound.assimilation.AssimilationStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** One compact bar combining assimilation amount, success zone, and cursor. */
final class AssimilationPurgeHudRenderer {
    static final int VANILLA_STATUS_LIFT = StruggleHudLayout.VANILLA_STATUS_LIFT;

    private AssimilationPurgeHudRenderer() {
    }

    static boolean visible(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(
                minecraft.player.getId());
        return view != null && view.stage() == AssimilationStage.ASSIMILATING
                && view.progress() > 0.0001F && view.partialPurgeActive();
    }

    static boolean render(GuiGraphics graphics, Minecraft minecraft, float partialTick) {
        if (!visible(minecraft)) {
            return false;
        }
        ClientAssimilationState.View view = ClientAssimilationState.view(
                minecraft.player.getId());
        int x = StruggleHudLayout.barX(minecraft.getWindow().getGuiScaledWidth());
        int y = StruggleHudLayout.barY(minecraft);
        renderCombined(graphics, minecraft, view, partialTick, x, y);
        return true;
    }

    private static void renderCombined(GuiGraphics graphics, Minecraft minecraft,
            ClientAssimilationState.View view, float partialTick, int x, int y) {
        int innerX = x + 2;
        int innerY = y + 2;
        int innerHeight = StruggleHudLayout.BAR_HEIGHT - 4;
        int base = 0xE337302D;
        if (view.partialPurgeResultTicks() > 0) {
            base = view.partialPurgeResult() == AssimilationPartialPurge.RESULT_SUCCESS
                    ? 0xE32C5A43 : 0xE36B2929;
        }
        frame(graphics, x, y, 0xD6151211, base);
        StruggleHudLayout.fillPixelRoundedProgress(
                graphics, innerX, innerY, StruggleHudLayout.INNER_WIDTH,
                innerHeight, view.progress(), 0xFFE66B93);

        int zoneStart = innerX + Math.round(
                view.partialPurgeZoneStart() * StruggleHudLayout.INNER_WIDTH);
        int zoneEnd = innerX + Math.round(
                view.partialPurgeZoneEnd() * StruggleHudLayout.INNER_WIDTH);
        if (zoneEnd > zoneStart) {
            graphics.fill(zoneStart, innerY, zoneEnd, innerY + innerHeight, 0xFF75C889);
            graphics.fill(zoneStart, innerY, zoneEnd, innerY + 1, 0xFFB9EBB6);
        }
        int cursorX = innerX + Mth.clamp(Math.round(
                view.partialPurgeCursor(partialTick) * (StruggleHudLayout.INNER_WIDTH - 1)),
                0, StruggleHudLayout.INNER_WIDTH - 1);
        graphics.fill(cursorX - 1, y, cursorX + 2,
                y + StruggleHudLayout.BAR_HEIGHT, 0xFF3A261F);
        graphics.fill(cursorX, y - 1, cursorX + 1,
                y + StruggleHudLayout.BAR_HEIGHT + 1, 0xFFFFF1B0);

        int percent = Mth.clamp(Math.round(view.progress() * 100.0F), 0, 100);
        Component label = Component.translatable(
                "hud.mirebound.assimilation.purge.value", percent);
        int labelX = minecraft.getWindow().getGuiScaledWidth() / 2
                - minecraft.font.width(label) / 2;
        graphics.drawString(minecraft.font, label, labelX, y - 9, 0xFFF3DDE6, true);
    }

    private static void frame(GuiGraphics graphics, int x, int y,
            int border, int background) {
        StruggleHudLayout.fillPixelRounded(graphics, x - 1, y - 1,
                StruggleHudLayout.BAR_WIDTH + 2, StruggleHudLayout.BAR_HEIGHT + 2, border);
        StruggleHudLayout.fillPixelRounded(graphics, x, y,
                StruggleHudLayout.BAR_WIDTH, StruggleHudLayout.BAR_HEIGHT, background);
    }
}
