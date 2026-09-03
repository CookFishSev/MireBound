package com.fish.mirebound.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Boolean button whose whole surface communicates the current state. */
public final class MireflowToggleButton extends Button {
    private final boolean enabled;

    public MireflowToggleButton(
            int x, int y, int width, int height, boolean enabled, OnPress onPress) {
        super(x, y, width, height,
                Component.translatable(enabled
                        ? "gui.mirebound.physics.enabled"
                        : "gui.mirebound.physics.disabled"),
                onPress, DEFAULT_NARRATION);
        this.enabled = enabled;
    }

    @Override
    protected void renderWidget(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int fill = !active ? 0xFF202622
                : enabled
                        ? isHoveredOrFocused()
                                ? MireflowGuiTheme.ENABLED_HOVER
                                : MireflowGuiTheme.ENABLED_CONTROL
                        : isHoveredOrFocused()
                                ? MireflowGuiTheme.DISABLED_HOVER
                                : MireflowGuiTheme.DISABLED_CONTROL;
        int border = !active ? MireflowGuiTheme.DIVIDER
                : enabled ? MireflowGuiTheme.SUCCESS : MireflowGuiTheme.ERROR;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        graphics.fill(getX() + 1, getY() + 1,
                getX() + getWidth() - 1, getY() + getHeight() - 1, fill);
        renderString(graphics, net.minecraft.client.Minecraft.getInstance().font,
                active ? MireflowGuiTheme.TEXT : MireflowGuiTheme.DISABLED);
    }
}
