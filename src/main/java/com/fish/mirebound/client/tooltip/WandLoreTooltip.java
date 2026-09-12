package com.fish.mirebound.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Cached text layout drawn in the normal tooltip batch, with independent glyph offsets. */
final class WandLoreTooltip implements ClientTooltipComponent {
    private static final int TOP = 8;
    private static final int LINE_HEIGHT = 14;
    private final Component text;
    private final int maximumWidth;
    private Font layoutFont;
    private final List<Glyph> glyphs = new ArrayList<>();
    private int width;
    private int height;
    private double seconds;

    WandLoreTooltip(String text, int maximumWidth, Font font) {
        this.text = Component.literal(text);
        this.maximumWidth = maximumWidth;
        layout(font);
    }

    void setTime(double seconds) {
        this.seconds = seconds;
    }

    private void layout(Font font) {
        if (layoutFont == font) return;
        layoutFont = font;
        glyphs.clear();
        width = 0;
        List<FormattedCharSequence> lines = font.split(text, maximumWidth);
        for (int row = 0; row < lines.size(); row++) {
            final float y = TOP + row * LINE_HEIGHT;
            float[] cursor = {0};
            lines.get(row).accept((index, style, codePoint) -> {
                FormattedCharSequence character = FormattedCharSequence.forward(Character.toString(codePoint), style);
                glyphs.add(new Glyph(character, cursor[0], y));
                cursor[0] += font.getSplitter().stringWidth(character);
                return true;
            });
            width = Math.max(width, (int) Math.ceil(cursor[0]));
        }
        height = TOP + lines.size() * LINE_HEIGHT + 2;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getWidth(Font font) {
        layout(font);
        return width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffers) {
        for (int index = 0; index < glyphs.size(); index++) {
            double opacity = WandLoreAnimation.opacity(seconds, index);
            int alpha = (int) Math.round(opacity * 255);
            // Vanilla treats a near-zero alpha color as opaque text.
            if (alpha < 4) continue;
            Glyph glyph = glyphs.get(index);
            int gray = WandLoreAnimation.gray(seconds, index);
            int color = alpha << 24 | gray << 16 | gray << 8 | gray;
            float offsetY = WandLoreAnimation.floatOffset(seconds, index);
            font.drawInBatch(glyph.text, x + glyph.x - (float) ((1 - opacity) * 1.2D),
                    y + glyph.y + offsetY, color, true, pose, buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.hLine(x, x + Math.max(0, width - 1), y + 2, 0x604C5055);
    }

    private record Glyph(FormattedCharSequence text, float x, float y) {
    }
}
