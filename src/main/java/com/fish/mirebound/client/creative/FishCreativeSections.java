package com.fish.mirebound.client.creative;

import com.mojang.blaze3d.systems.RenderSystem;
import com.fish.mirebound.registry.ModCreativeTabs;
import com.fish.mirebound.registry.ModCreativeTabs.Section;
import com.fish.mirebound.registry.ModCreativeTabs.SectionLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/** Lightweight banner layout and rendering state for the mod's creative tab. */
public final class FishCreativeSections {
    private static final int BANNER_WIDTH = 162;
    private static final int BANNER_HEIGHT = 18;
    private static final int BANNER_IMAGE_HEIGHT = 17;
    private static final int CREATIVE_HIGHLIGHT = 0xFFFFFFFF;
    private static final int VISIBLE_ROWS = 5;

    private static int currentRow;

    private FishCreativeSections() {
    }

    public static void setCurrentRow(int row) {
        currentRow = Math.max(0, row);
    }

    public static void render(
            CreativeModeInventoryScreen screen,
            GuiGraphics graphics) {
        SectionLayout layout = ModCreativeTabs.sectionLayout();
        int originX = screen.getGuiLeft() + 8;
        int originY = screen.getGuiTop() + 17;
        Font font = Minecraft.getInstance().font;

        graphics.pose().pushPose();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        for (int index = 0; index < ModCreativeTabs.sections().size(); index++) {
            int visibleRow = layout.bannerRows().get(index) - currentRow;
            if (visibleRow < 0 || visibleRow >= VISIBLE_ROWS) {
                continue;
            }

            Section section = ModCreativeTabs.sections().get(index);
            int y = originY + visibleRow * BANNER_HEIGHT;
            graphics.enableScissor(
                    originX + 1,
                    y,
                    originX + BANNER_WIDTH - 1,
                    y + BANNER_IMAGE_HEIGHT);
            graphics.blitSprite(
                    section.bannerSprite(), originX, y, BANNER_WIDTH, BANNER_HEIGHT);
            graphics.disableScissor();
            graphics.fill(
                    originX + 1,
                    y + BANNER_HEIGHT - 1,
                    originX + BANNER_WIDTH - 1,
                    y + BANNER_HEIGHT,
                    CREATIVE_HIGHLIGHT);

            int textWidth = font.width(section.title());
            graphics.fill(
                    originX + 2,
                    y + 2,
                    originX + textWidth + 8,
                    y + BANNER_HEIGHT - 2,
                    section.titleBackground());
            graphics.drawString(
                    font,
                    section.title(),
                    originX + 5,
                    y + 4,
                    section.titleColor(),
                    true);
        }
        graphics.pose().popPose();
        RenderSystem.disableDepthTest();
    }
}
