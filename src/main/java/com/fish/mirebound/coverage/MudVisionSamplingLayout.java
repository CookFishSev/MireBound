package com.fish.mirebound.coverage;

import net.minecraft.util.Mth;

/** Shared face-grid dimensions used by authoritative sampling and client mapping. */
public final class MudVisionSamplingLayout {
    private static final double HEAD_RADIUS = 0.205D;
    private static final double BASE_WIDTH_PIXELS = 8.0D;
    private static final double DEFAULT_WIDTH_PIXELS = 8.0D;
    private static final double DEFAULT_HEIGHT_PIXELS = 8.0D;
    private static final double DEFAULT_BOTTOM_OFFSET_PIXELS = 0.0D;
    private static final double CENTER_HEIGHT_FACTOR = 0.8975D;
    private static final double BASE_HALF_HEIGHT_FACTOR = 0.1375D;
    private static final double PIXEL_HEIGHT_FACTOR = BASE_HALF_HEIGHT_FACTOR * 2.0D / 8.0D;
    private static final double BASE_BOTTOM_HEIGHT_FACTOR =
            CENTER_HEIGHT_FACTOR - BASE_HALF_HEIGHT_FACTOR;
    private static double widthPixels = DEFAULT_WIDTH_PIXELS;
    private static double heightPixels = DEFAULT_HEIGHT_PIXELS;
    private static double bottomOffsetPixels = DEFAULT_BOTTOM_OFFSET_PIXELS;

    private MudVisionSamplingLayout() {
    }

    public static double widthPixels() {
        return widthPixels;
    }

    public static double heightPixels() {
        return heightPixels;
    }

    public static double bottomOffsetPixels() {
        return bottomOffsetPixels;
    }

    public static void setWidthPixels(double value) {
        widthPixels = Mth.clamp(value, 2.0D, 16.0D);
    }

    public static void setHeightPixels(double value) {
        heightPixels = Mth.clamp(value, 1.0D, 12.0D);
    }

    public static void setBottomOffsetPixels(double value) {
        bottomOffsetPixels = Mth.clamp(value, -4.0D, 8.0D);
    }

    public static void reset() {
        widthPixels = DEFAULT_WIDTH_PIXELS;
        heightPixels = DEFAULT_HEIGHT_PIXELS;
        bottomOffsetPixels = DEFAULT_BOTTOM_OFFSET_PIXELS;
    }

    public static double minHeightFactor() {
        return BASE_BOTTOM_HEIGHT_FACTOR + PIXEL_HEIGHT_FACTOR * bottomOffsetPixels;
    }

    public static double maxHeightFactor() {
        return minHeightFactor() + PIXEL_HEIGHT_FACTOR * heightPixels;
    }

    public static double faceRadius() {
        return HEAD_RADIUS * (widthPixels / BASE_WIDTH_PIXELS);
    }

    public static double frontOffset() {
        return HEAD_RADIUS + 0.012D;
    }
}
