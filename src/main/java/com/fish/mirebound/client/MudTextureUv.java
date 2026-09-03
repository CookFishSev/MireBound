package com.fish.mirebound.client;

import net.minecraft.util.Mth;

/** Stable sub-regions of a 16px material texture for procedural geometry. */
final class MudTextureUv {
    private static final float PIXEL_UV = 1.0F / 16.0F;

    private MudTextureUv() {
    }

    static Region sample(long seed, int requestedPixels) {
        int pixels = Mth.clamp(requestedPixels, 1, 16);
        int positions = 17 - pixels;
        long mixed = mix(seed);
        int x = (int) ((mixed >>> 8) % positions);
        int y = (int) ((mixed >>> 32) % positions);
        return new Region(
                x * PIXEL_UV,
                y * PIXEL_UV,
                (x + pixels) * PIXEL_UV,
                (y + pixels) * PIXEL_UV);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    record Region(float u0, float v0, float u1, float v1) {
    }
}
