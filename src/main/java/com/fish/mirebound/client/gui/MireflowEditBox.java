package com.fish.mirebound.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Original EditBox behavior with a flat background matching the Mireflow panels. */
public final class MireflowEditBox extends EditBox {
    public MireflowEditBox(
            Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setBordered(false);
        setTextColor(MireflowGuiTheme.TEXT);
        setTextColorUneditable(MireflowGuiTheme.DISABLED);
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int originalX = getX();
        int originalY = getY();
        int border = isFocused()
                ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DIVIDER;
        graphics.fill(originalX, originalY,
                originalX + getWidth(), originalY + getHeight(), border);
        graphics.fill(originalX + 1, originalY + 1,
                originalX + getWidth() - 1, originalY + getHeight() - 1,
                MireflowGuiTheme.INPUT);
        setX(originalX + 4);
        setY(originalY + Math.max(1, (getHeight() - 8) / 2));
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setX(originalX);
        setY(originalY);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - 4.0D, mouseY);
    }

    @Override
    public int getInnerWidth() {
        return Math.max(1, super.getInnerWidth() - 8);
    }
}
