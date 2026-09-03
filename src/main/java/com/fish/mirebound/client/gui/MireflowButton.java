package com.fish.mirebound.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Vanilla-compatible button with the shared flat Mireflow pixel treatment. */
public final class MireflowButton extends Button {
    private final Tone tone;
    private final boolean selected;
    private final boolean flat;

    private MireflowButton(
            Button.Builder builder, Tone tone, boolean selected, boolean flat) {
        super(builder);
        this.tone = tone;
        this.selected = selected;
        this.flat = flat;
    }

    public static Builder builder(Component message, Button.OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
        protected void renderWidget(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (flat) {
            int border = selected || isHoveredOrFocused()
                    ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DIVIDER;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
            graphics.fill(getX(), getY() + getHeight() - 1,
                    getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX(), getY(), getX() + 1,
                    getY() + getHeight(), border);
            graphics.fill(getX() + getWidth() - 1, getY(),
                    getX() + getWidth(), getY() + getHeight(), border);
            renderString(graphics, net.minecraft.client.Minecraft.getInstance().font,
                    active || selected ? MireflowGuiTheme.TEXT : MireflowGuiTheme.DISABLED);
            return;
        }
        int border = selected || isHoveredOrFocused()
                ? MireflowGuiTheme.ACCENT : MireflowGuiTheme.DIVIDER;
        int fill = active
                ? isHoveredOrFocused() ? tone.hovered : tone.fill
                : selected ? tone.fill : 0xFF202622;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        graphics.fill(getX() + 1, getY() + 1,
                getX() + getWidth() - 1, getY() + getHeight() - 1, fill);
        renderString(graphics, net.minecraft.client.Minecraft.getInstance().font,
                active || selected ? MireflowGuiTheme.TEXT : MireflowGuiTheme.DISABLED);
    }

    public enum Tone {
        NORMAL(MireflowGuiTheme.CONTROL, MireflowGuiTheme.CONTROL_HOVER),
        INFO(0xFF2D4653, 0xFF385867),
        NATIVE(0xFF52482D, 0xFF665A36),
        SOURCE(0xFF304A3A, 0xFF3C5D48),
        CONVERTED(0xFF65442B, 0xFF7A5434),
        INCOMPATIBLE(0xFF58302F, 0xFF6C3A38),
        POSITIVE(MireflowGuiTheme.ENABLED_CONTROL, MireflowGuiTheme.ENABLED_HOVER),
        DANGER(MireflowGuiTheme.DISABLED_CONTROL, MireflowGuiTheme.DISABLED_HOVER);

        private final int fill;
        private final int hovered;

        Tone(int fill, int hovered) {
            this.fill = fill;
            this.hovered = hovered;
        }
    }

    public static final class Builder extends Button.Builder {
        private Tone tone = Tone.NORMAL;
        private boolean selected;
        private boolean flat;

        private Builder(Component message, Button.OnPress onPress) {
            super(message, onPress);
        }

        public Builder tone(Tone tone) {
            this.tone = tone == null ? Tone.NORMAL : tone;
            return this;
        }

        public Builder selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public Builder flat() {
            this.flat = true;
            return this;
        }

        @Override
        public Builder pos(int x, int y) {
            super.pos(x, y);
            return this;
        }

        @Override
        public Builder width(int width) {
            super.width(width);
            return this;
        }

        @Override
        public Builder size(int width, int height) {
            super.size(width, height);
            return this;
        }

        @Override
        public Builder bounds(int x, int y, int width, int height) {
            super.bounds(x, y, width, height);
            return this;
        }

        @Override
        public Builder tooltip(Tooltip tooltip) {
            super.tooltip(tooltip);
            return this;
        }

        @Override
        public MireflowButton build() {
            return new MireflowButton(this, tone, selected, flat);
        }
    }
}
