package com.fish.mirebound.client.tuning;

import net.minecraft.client.Minecraft;

/** Layout constants and normalized positioning for the two wand HUD groups. */
final class MudTuningHudLayout {
    static final int MODE_TRACK_SIZE = 56;
    static final int SCREEN_MARGIN = 6;
    private static final int CENTER_HUD_BOTTOM_OFFSET = 78;
    private MudTuningHudLayout() {
    }

    static int modeY(Minecraft minecraft) {
        return Math.max(0, minecraft.getWindow().getGuiScaledHeight()
                - 78 - modeHeight(0));
    }

    static int anchoredModeY(int guiHeight, int selectorHeight) {
        return Math.max(0, guiHeight - CENTER_HUD_BOTTOM_OFFSET - selectorHeight);
    }

    static int modeWidth(int guiWidth) {
        return MODE_TRACK_SIZE * MudTuningWandMode.hudOrder().size();
    }

    static int modeHeight(int guiWidth) {
        return modeCellSize(modeTrackSize(guiWidth), true);
    }

    static int modeTrackSize(int guiWidth) {
        return MODE_TRACK_SIZE;
    }

    static int modeCellSize(int trackSize, boolean selected) {
        int inset = selected ? 1 : Math.max(3, Math.round(trackSize / 14.0F));
        return Math.max(24, trackSize - inset * 2);
    }

    static HudBounds bounds(Minecraft minecraft, MudTuningHudElement element,
            int guiWidth, int guiHeight, int baseWidth, int baseHeight) {
        double scale = Math.min(scale(element),
                maxScale(guiWidth, guiHeight, baseWidth, baseHeight));
        int width = Math.max(1, Math.round(baseWidth * (float) scale));
        int height = Math.max(1, Math.round(baseHeight * (float) scale));
        int x = horizontalPosition(element, guiWidth, width);
        int y = verticalPosition(verticalPosition(element), guiHeight, height);
        return new HudBounds(x, y, width, height, baseWidth, baseHeight, scale);
    }

    static double maxScale(int guiWidth, int guiHeight,
            int baseWidth, int baseHeight) {
        double widthScale = (guiWidth - SCREEN_MARGIN * 2.0D)
                / Math.max(1.0D, baseWidth);
        double heightScale = (guiHeight - SCREEN_MARGIN * 2.0D)
                / Math.max(1.0D, baseHeight);
        return Math.max(0.05D, Math.min(2.0D, Math.min(widthScale, heightScale)));
    }

    static int horizontalPosition(MudTuningHudElement element,
            int guiWidth, int width) {
        return horizontalPosition(x(element), guiWidth, width);
    }

    static int horizontalPosition(double normalized, int guiWidth, int width) {
        int available = Math.max(0, guiWidth - width - SCREEN_MARGIN * 2);
        return SCREEN_MARGIN + Math.round((float) (Math.max(0.0D,
                Math.min(1.0D, normalized)) * available));
    }

    static int verticalPosition(double normalized, int guiHeight, int height) {
        int available = Math.max(0, guiHeight - height - SCREEN_MARGIN * 2);
        return SCREEN_MARGIN + Math.round((float) (Math.max(0.0D,
                Math.min(1.0D, normalized)) * available));
    }

    static double x(MudTuningHudElement element) {
        return MudTuningHudEditorState.active()
                ? MudTuningHudEditorState.x(element)
                : MudTuningClientSettings.hudElementX(element);
    }

    static double verticalPosition(MudTuningHudElement element) {
        return MudTuningHudEditorState.active()
                ? MudTuningHudEditorState.y(element)
                : MudTuningClientSettings.hudElementY(element);
    }

    static double scale(MudTuningHudElement element) {
        return MudTuningHudEditorState.active()
                ? MudTuningHudEditorState.scale(element)
                : MudTuningClientSettings.hudElementScale(element);
    }

    static boolean enabled(MudTuningHudElement element) {
        return MudTuningHudEditorState.active()
                ? MudTuningHudEditorState.enabled(element)
                : MudTuningClientSettings.hudElementEnabled(element);
    }

    record HudBounds(int x, int y, int width, int height,
            int baseWidth, int baseHeight, double scale) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + height;
        }

        boolean containsResizeHandle(double mouseX, double mouseY) {
            return mouseX >= x + width - 12 && mouseX <= x + width + 2
                    && mouseY >= y + height - 12 && mouseY <= y + height + 2;
        }

        boolean overlaps(HudBounds other, int margin) {
            if (other == null) {
                return false;
            }
            int gap = Math.max(0, margin);
            return x < other.x + other.width + gap
                    && x + width + gap > other.x
                    && y < other.y + other.height + gap
                    && y + height + gap > other.y;
        }
    }
}
