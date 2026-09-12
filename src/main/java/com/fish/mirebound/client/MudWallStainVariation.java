package com.fish.mirebound.client;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Static spatial variation; the two-pixel halo prevents artificial block seams. */
final class MudWallStainVariation {
    static final int HALO = 2;
    static final int ROWS = 16 + HALO * 2;

    private MudWallStainVariation() {
    }

    static boolean occupied(int[] rows, int x, int y) {
        return (rows[y + HALO] & 1 << (x + HALO)) != 0;
    }

    static void addRow(int[] rows, int bits, int row, int du, int dv) {
        int y = row + dv + HALO;
        if (y < 0 || y >= ROWS) return;
        int shift = du + HALO;
        int translated = shift >= 0 ? bits << shift : bits >>> -shift;
        rows[y] |= translated & ((1 << ROWS) - 1);
    }

    static int apply(int color, int x, int y, int worldU, int worldV, int seed, int[] rows) {
        if (FastColor.ABGR32.alpha(color) == 0) return color;
        int depth = 3;
        for (int distance = 1; distance <= HALO; distance++) {
            if (!occupied(rows, x - distance, y) || !occupied(rows, x + distance, y)
                    || !occupied(rows, x, y - distance) || !occupied(rows, x, y + distance)) {
                depth = distance;
                break;
            }
        }
        float clusters = noise(Math.floorDiv(worldU, 3), Math.floorDiv(worldV, 3), seed);
        float grain = noise(worldU, worldV, seed ^ 0x632be5ab);
        float variation = clusters * 0.75F + grain * 0.25F;
        float edge = depth == 3 ? 1 : Mth.clamp(depth - variation * 2.2F, 0, 1);
        int alpha = FastColor.ABGR32.alpha(color);
        // Opaque cores remain opaque; faded and translucent areas gain mottling.
        float opacity = alpha >= 245 ? 1 : 0.78F + variation * 0.22F;
        alpha = Math.round(alpha * edge * opacity);
        if (alpha == 0) return 0;
        float shade = 0.91F + clusters * 0.08F + grain * 0.05F;
        return FastColor.ABGR32.color(alpha,
                Math.min(255, Math.round(FastColor.ABGR32.blue(color) * shade)),
                Math.min(255, Math.round(FastColor.ABGR32.green(color) * shade)),
                Math.min(255, Math.round(FastColor.ABGR32.red(color) * shade)));
    }

    private static float noise(int x, int y, int seed) {
        int value = seed ^ x * 0x1f123bb5 ^ y * 0x5f356495;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;
        return (value & 65535) / 65535.0F;
    }
}
