package com.fish.mirebound.client.worldgen;

import com.fish.mirebound.client.gui.MireflowGuiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Left selects, right changes the enabled state; keyboard activation selects. */
final class WorldgenChoiceButton extends Button {
    private final Runnable toggle;
    private final boolean enabledState;
    private final boolean selected;
    WorldgenChoiceButton(int x, int y, int width, Component label,
            boolean enabledState, boolean selected, Runnable choose, Runnable toggle) {
        super(x, y, width, 20, label, ignored -> choose.run(), DEFAULT_NARRATION);
        this.toggle = toggle;
        this.enabledState = enabledState;
        this.selected = selected;
    }
    @Override protected boolean isValidClickButton(int button) { return button == 0 || button == 1; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1 && active && visible && isMouseOver(x, y)) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            toggle.run();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override protected void renderWidget(GuiGraphics g, int x, int y, float partial) {
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                selected ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DIVIDER);
        g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
                active && enabledState ? 0xFF304A3A : 0xFF24272C);
        renderString(g, net.minecraft.client.Minecraft.getInstance().font,
                active ? MireflowGuiTheme.TEXT : MireflowGuiTheme.DISABLED);
    }
}
