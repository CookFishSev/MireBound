package com.fish.mirebound.coverage.skin;

import java.util.BitSet;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Immutable UV mask. Scaling follows skin width, so legacy 64x32 skins keep the same head UVs. */
public final class SkinStainMask {
    public static final int MAX_DIMENSION = 4096;
    private final int width;
    private final int height;
    private final BitSet blocked;
    private final int hash;

    public SkinStainMask(int width, int height, BitSet blocked) {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                || blocked.length() > width * height) throw new IllegalArgumentException("Invalid skin mask dimensions");
        this.width = width;
        this.height = height;
        this.blocked = (BitSet) blocked.clone();
        this.hash = Objects.hash(width, height, this.blocked);
    }

    public static SkinStainMask empty(int width, int height) {
        return new SkinStainMask(width, height, new BitSet());
    }

    public int width() { return width; }
    public int height() { return height; }
    public int count() { return blocked.cardinality(); }
    public boolean isEmpty() { return blocked.isEmpty(); }
    public BitSet copyBits() { return (BitSet) blocked.clone(); }
    public int storageBytes() { return (blocked.length() + 7) / 8; }

    public boolean blocked(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && blocked.get(y * width + x);
    }

    public boolean allows(int x, int y, int skinWidth) {
        return skinWidth <= 0 || !blocked((int) ((long) x * width / skinWidth),
                (int) ((long) y * width / skinWidth));
    }

    public void forEachBlocked(int targetWidth, int targetHeight, IntConsumer consumer) {
        for (int start = blocked.nextSetBit(0); start >= 0;) {
            int row = start / width;
            int end = Math.min((row + 1) * width, blocked.nextClearBit(start));
            int x0 = ceilScale(start % width, targetWidth, width);
            int x1 = ceilScale(end - row * width, targetWidth, width);
            int y0 = Math.min(targetHeight, ceilScale(row, targetWidth, width));
            int y1 = Math.min(targetHeight, ceilScale(row + 1, targetWidth, width));
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) consumer.accept(y * targetWidth + x);
            start = blocked.nextSetBit(end);
        }
    }

    public SkinStainMask resized(int targetWidth, int targetHeight) {
        if (width == targetWidth && height == targetHeight) return this;
        BitSet result = new BitSet();
        forEachBlocked(targetWidth, targetHeight, result::set);
        return new SkinStainMask(targetWidth, targetHeight, result);
    }

    private static int ceilScale(int value, int numerator, int denominator) {
        return (int) (((long) value * numerator + denominator - 1) / denominator);
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        return other instanceof SkinStainMask mask && width == mask.width && height == mask.height
                && blocked.equals(mask.blocked);
    }
}
