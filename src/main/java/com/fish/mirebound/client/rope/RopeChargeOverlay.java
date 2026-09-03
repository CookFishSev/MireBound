package com.fish.mirebound.client.rope;

import com.fish.mirebound.client.ClientEvents;
import com.fish.mirebound.client.StruggleHudLayout;
import com.fish.mirebound.rope.RopeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Throw charge indicator for the rope item. */
public final class RopeChargeOverlay {
    private RopeChargeOverlay() {
    }

    public static boolean isCharging(Minecraft minecraft) {
        return minecraft != null && minecraft.player != null
                && !minecraft.options.hideGui && minecraft.screen == null
                && minecraft.player.isUsingItem()
                && minecraft.player.getUseItem().getItem() instanceof RopeItem;
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isCharging(minecraft)) {
            return;
        }
        ItemStack stack = minecraft.player.getUseItem();
        int ticks = stack.getUseDuration(minecraft.player)
                - minecraft.player.getUseItemRemainingTicks();
        GuiGraphics graphics = event.getGuiGraphics();
        int width = StruggleHudLayout.BAR_WIDTH;
        int x = StruggleHudLayout.barX(graphics.guiWidth());
        int baseY = StruggleHudLayout.barY(minecraft);
        int y = ClientEvents.isStruggleHudVisible(minecraft)
                ? StruggleHudLayout.barYAbove(baseY) : baseY;
        float charge = RopeItem.charge(ticks);
        boolean rescue = ClientRopes.isRescueCastArmed();
        int background = rescue ? 0xD3132427 : 0xD33A1719;
        int fillColor = rescue ? 0xFF55C7D8 : 0xFFE7473C;
        int highlight = rescue ? 0xFFA7F3FA : 0xFFFF9A8F;
        int fill = Mth.clamp(Math.round(StruggleHudLayout.INNER_WIDTH * charge),
                0, StruggleHudLayout.INNER_WIDTH);
        StruggleHudLayout.fillPixelRounded(graphics, x - 1, y - 1,
                width + 2, StruggleHudLayout.BAR_HEIGHT + 2, 0xD51B1011);
        StruggleHudLayout.fillPixelRounded(graphics, x, y, width,
                StruggleHudLayout.BAR_HEIGHT, background);
        if (fill > 0) {
            StruggleHudLayout.fillPixelRoundedProgress(graphics, x + 2, y + 2,
                    StruggleHudLayout.INNER_WIDTH, StruggleHudLayout.BAR_HEIGHT - 4,
                    charge, fillColor);
            StruggleHudLayout.fillPixelRoundedProgress(graphics, x + 2, y + 2,
                    StruggleHudLayout.INNER_WIDTH, 1, charge, highlight);
        }
    }
}
