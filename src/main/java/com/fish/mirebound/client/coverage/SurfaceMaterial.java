package com.fish.mirebound.client.coverage;

/** A view of the material currently drawn; mutable textures are read without copying them. */
public interface SurfaceMaterial {
    int width();
    int height();
    int pixel(int x, int y);

    /** Identity of the backing pixels, independent of short-lived view wrappers. */
    default Object pixelSource() { return this; }

    default int sample(float u, float v) {
        if (!Float.isFinite(u) || !Float.isFinite(v) || width() <= 0 || height() <= 0) return 0;
        return pixel(Math.max(0, Math.min(width() - 1, (int) (u * width()))),
                Math.max(0, Math.min(height() - 1, (int) (v * height()))));
    }
}
