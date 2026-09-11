package com.fish.mirebound.client;

import net.minecraft.util.Mth;

/** Shared aspect-preserving layout for pixel-art screen overlays. */
public final class ScreenOverlayLayout {
    private ScreenOverlayLayout() {
    }

    public static CoverRect cover(int viewportWidth, int viewportHeight,
            int textureWidth, int textureHeight) {
        double scale = Math.max(
                viewportWidth / (double) textureWidth,
                viewportHeight / (double) textureHeight);
        int drawWidth = Math.max(viewportWidth, (int) Math.ceil(textureWidth * scale));
        int drawHeight = Math.max(viewportHeight, (int) Math.ceil(textureHeight * scale));
        return new CoverRect(
                Math.floorDiv(viewportWidth - drawWidth, 2),
                Math.floorDiv(viewportHeight - drawHeight, 2),
                drawWidth,
                drawHeight);
    }

    public record CoverRect(int x, int y, int width, int height) {
    }

    /** Converts a point in the fixed overlay canvas back to viewport NDC. */
    static float textureXToNdc(CoverRect cover, int viewportWidth,
            float textureX) {
        if (viewportWidth <= 0 || cover.width() <= 0) {
            return 0.0F;
        }
        float screenX = cover.x() + Mth.clamp(textureX, 0.0F, 1.0F)
                * cover.width();
        return screenX / viewportWidth * 2.0F - 1.0F;
    }

    /** Converts a point in the fixed overlay canvas back to viewport NDC. */
    static float textureYToNdc(CoverRect cover, int viewportHeight,
            float textureY) {
        if (viewportHeight <= 0 || cover.height() <= 0) {
            return 0.0F;
        }
        float screenY = cover.y() + Mth.clamp(textureY, 0.0F, 1.0F)
                * cover.height();
        return 1.0F - screenY / viewportHeight * 2.0F;
    }
}
