package com.fish.mirebound.client;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Separates runtime-rendered physical quad ownership from shared texture pixels.
 * World-space positions remain frame-local; only immutable UV raster topology is cached.
 */
final class RuntimeUvOwnership {
    private static final int MAX_CACHED_QUADS = 2048;
    private static final int MAX_RASTER_PIXELS = 32768;
    private static final long MAX_CACHED_RASTER_PIXELS = 4L * 1024L * 1024L;
    private static final Map<RasterKey, int[]> RASTER_CACHE =
            new LinkedHashMap<>(256, 0.75F, true);
    private static long cachedRasterPixels;

    private RuntimeUvOwnership() {
    }

    static int[] rasterPixels(RuntimeUvQuadAssembler.Vertex[] quad, ResourceLocation texture,
            int width, int height) {
        RasterKey key = RasterKey.of(quad, texture, width, height);
        synchronized (RASTER_CACHE) {
            int[] cached = RASTER_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        int[] built = buildRaster(quad, texture, width, height);
        if (built.length > MAX_RASTER_PIXELS) {
            return built;
        }
        synchronized (RASTER_CACHE) {
            int[] replaced = RASTER_CACHE.put(key, built);
            cachedRasterPixels += built.length - (replaced == null ? 0 : replaced.length);
            trimRasterCache();
        }
        return built;
    }

    static float signedUvArea(RuntimeUvQuadAssembler.Vertex[] quad) {
        float area = 0.0F;
        for (int index = 0; index < quad.length; index++) {
            RuntimeUvQuadAssembler.Vertex current = quad[index];
            RuntimeUvQuadAssembler.Vertex next = quad[(index + 1) % quad.length];
            area += current.u * next.v - next.u * current.v;
        }
        return area * 0.5F;
    }

    static long physicalOwnerKey(RuntimeUvQuadAssembler.Vertex[] quad) {
        long hash = 0xCBF29CE484222325L;
        for (RuntimeUvQuadAssembler.Vertex vertex : quad) {
            hash = mix(hash, Float.floatToIntBits(vertex.u));
            hash = mix(hash, Float.floatToIntBits(vertex.v));
            hash = mix(hash, Math.round(vertex.position.x * 4096.0D));
            hash = mix(hash, Math.round(vertex.position.y * 4096.0D));
            hash = mix(hash, Math.round(vertex.position.z * 4096.0D));
        }
        return hash;
    }

    static void reset() {
        synchronized (RASTER_CACHE) {
            RASTER_CACHE.clear();
            cachedRasterPixels = 0L;
        }
    }

    private static void trimRasterCache() {
        var iterator = RASTER_CACHE.entrySet().iterator();
        while ((RASTER_CACHE.size() > MAX_CACHED_QUADS
                || cachedRasterPixels > MAX_CACHED_RASTER_PIXELS)
                && iterator.hasNext()) {
            int[] removed = iterator.next().getValue();
            cachedRasterPixels -= removed.length;
            iterator.remove();
        }
    }

    private static int[] buildRaster(RuntimeUvQuadAssembler.Vertex[] quad, ResourceLocation texture,
            int width, int height) {
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (RuntimeUvQuadAssembler.Vertex vertex : quad) {
            minU = Math.min(minU, vertex.u);
            maxU = Math.max(maxU, vertex.u);
            minV = Math.min(minV, vertex.v);
            maxV = Math.max(maxV, vertex.v);
        }
        int x0 = Mth.clamp(Mth.floor(minU * width + 0.0001F), 0, width);
        int x1 = Mth.clamp(Mth.ceil(maxU * width - 0.0001F), 0, width);
        int y0 = Mth.clamp(Mth.floor(minV * height + 0.0001F), 0, height);
        int y1 = Mth.clamp(Mth.ceil(maxV * height - 0.0001F), 0, height);
        int area = Math.max(0, (x1 - x0) * (y1 - y0));
        IntArrayList pixels = new IntArrayList(boundedRasterCapacity(area));
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                float u = (x + 0.5F) / width;
                float v = (y + 0.5F) / height;
                if (RuntimeUvQuadAssembler.containsUv(u, v, quad)
                        && SkinPixelCache.isOpaque(texture, u, v)) {
                    pixels.add(y * width + x);
                    if (pixels.size() > MAX_RASTER_PIXELS) {
                        return pixels.toIntArray();
                    }
                }
            }
        }
        return pixels.toIntArray();
    }

    static int boundedRasterCapacity(int area) {
        return Math.min(Math.max(0, area), MAX_RASTER_PIXELS + 1);
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    @FunctionalInterface
    interface ResolutionPolicy<T> {
        T resolve(int ownerCount, Map<Integer, T> valuesByOwner);
    }

    /** One bounded ownership table for one target texture capture pass. */
    static final class Frame<T> {
        private final int maxPixels;
        private final int maxCandidates;
        private final Int2IntOpenHashMap ownerCounts = new Int2IntOpenHashMap();
        private final Int2ObjectOpenHashMap<Map<Integer, T>> valuesByPixel = new Int2ObjectOpenHashMap<>();
        private final Long2IntOpenHashMap ownersByGeometry = new Long2IntOpenHashMap();
        private int nextOwner;
        private int candidateCount;
        private boolean reliable = true;

        Frame(int maxPixels, int maxCandidates) {
            this.maxPixels = maxPixels;
            this.maxCandidates = maxCandidates;
            ownersByGeometry.defaultReturnValue(-1);
        }

        int register(int[] rasterPixels) {
            return register(Long.MIN_VALUE + nextOwner, rasterPixels);
        }

        int register(long physicalOwnerKey, int[] rasterPixels) {
            if (!reliable) {
                return -1;
            }
            int existing = ownersByGeometry.get(physicalOwnerKey);
            if (existing >= 0) {
                return existing;
            }
            int owner = nextOwner++;
            ownersByGeometry.put(physicalOwnerKey, owner);
            for (int pixel : rasterPixels) {
                int ownerCount = ownerCounts.get(pixel);
                if (ownerCount == 0) {
                    if (ownerCounts.size() >= maxPixels) {
                        invalidate();
                        return -1;
                    }
                }
                ownerCounts.put(pixel, ownerCount + 1);
            }
            return owner;
        }

        void offer(int owner, int pixel, T value, BinaryOperator<T> chooser) {
            if (!reliable || owner < 0 || value == null) {
                return;
            }
            if (!ownerCounts.containsKey(pixel)) {
                return;
            }
            Map<Integer, T> valuesByOwner = valuesByPixel.get(pixel);
            T previous = valuesByOwner == null ? null : valuesByOwner.get(owner);
            if (previous == null) {
                if (candidateCount >= maxCandidates) {
                    invalidate();
                    return;
                }
                candidateCount++;
                if (valuesByOwner == null) {
                    valuesByOwner = new HashMap<>();
                    valuesByPixel.put(pixel, valuesByOwner);
                }
                valuesByOwner.put(owner, value);
            } else {
                valuesByOwner.put(owner, chooser.apply(previous, value));
            }
        }

        boolean reliable() {
            return reliable;
        }

        int ownerCount(int pixel) {
            return ownerCounts.get(pixel);
        }

        List<Resolved<T>> resolve(ResolutionPolicy<T> policy) {
            if (!reliable) {
                return List.of();
            }
            List<Resolved<T>> resolved = new ArrayList<>();
            for (Int2IntMap.Entry entry : ownerCounts.int2IntEntrySet()) {
                int pixel = entry.getIntKey();
                Map<Integer, T> valuesByOwner = valuesByPixel.get(pixel);
                T value = policy.resolve(entry.getIntValue(),
                        valuesByOwner == null ? Map.of() : valuesByOwner);
                if (value != null) {
                    resolved.add(new Resolved<>(pixel, value));
                }
            }
            return resolved;
        }

        private void invalidate() {
            reliable = false;
            ownerCounts.clear();
            valuesByPixel.clear();
            ownersByGeometry.clear();
            candidateCount = 0;
        }
    }

    record Resolved<T>(int pixel, T value) {
    }

    private record RasterKey(ResourceLocation texture, int width, int height,
            int u0, int v0, int u1, int v1, int u2, int v2, int u3, int v3) {
        private static RasterKey of(RuntimeUvQuadAssembler.Vertex[] quad, ResourceLocation texture,
                int width, int height) {
            return new RasterKey(texture, width, height,
                    Float.floatToIntBits(quad[0].u), Float.floatToIntBits(quad[0].v),
                    Float.floatToIntBits(quad[1].u), Float.floatToIntBits(quad[1].v),
                    Float.floatToIntBits(quad[2].u), Float.floatToIntBits(quad[2].v),
                    Float.floatToIntBits(quad[3].u), Float.floatToIntBits(quad[3].v));
        }
    }
}
