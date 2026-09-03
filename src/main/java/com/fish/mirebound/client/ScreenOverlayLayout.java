package com.fish.mirebound.client;

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
}
