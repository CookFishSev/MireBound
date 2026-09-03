package com.fish.mirebound.client;

import com.fish.mirebound.mud.ArmorTextureMudData;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Actual opaque UV pixels emitted by a custom equipment model. */
final class ArmorTextureFootprintCache {
    private static final int MAX_ENTRIES = 256;
    private static final long MAX_PIXEL_FOOTPRINT = 16L * 1024L * 1024L;
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(64, 0.75F, true);

    private ArmorTextureFootprintCache() {
    }

    static boolean claimRect(ItemStack stack, ResourceLocation texture, int width, int height,
            int x0, int y0, int x1, int y1) {
        Entry entry = entry(stack, texture, width, height);
        long rect = x0 | (long) x1 << 11 | (long) y0 << 22 | (long) y1 << 33;
        return entry.rectangles.add(rect);
    }

    static void recordPixel(ItemStack stack, ResourceLocation texture, int width, int height, int pixel) {
        if (pixel < 0 || pixel >= width * height) {
            return;
        }
        Entry entry = entry(stack, texture, width, height);
        if (entry.pixels.get(pixel)) {
            return;
        }
        int alpha = SkinPixelCache.alpha(texture, pixel % width, pixel / width);
        if (alpha <= 0) {
            return;
        }
        entry.pixels.set(pixel);
        entry.alphaTotal += alpha;
    }

    static CoverageStats coverage(ItemStack stack, ArmorTextureMudData.Layer layer) {
        Key key = key(stack, layer.texture(), layer.width(), layer.height());
        Entry entry = CACHE.get(key);
        if (entry == null || entry.alphaTotal <= 0) {
            return null;
        }
        double[] covered = {0.0D};
        layer.forEach((pixel, coverage, medium) -> {
            if (entry.pixels.get(pixel)) {
                covered[0] += coverage * SkinPixelCache.alpha(
                        layer.texture(), pixel % layer.width(), pixel / layer.width());
            }
        });
        return new CoverageStats(covered[0], entry.alphaTotal);
    }

    static void reset() {
        CACHE.clear();
    }

    private static Entry entry(ItemStack stack, ResourceLocation texture, int width, int height) {
        Entry entry = CACHE.computeIfAbsent(
                key(stack, texture, width, height), ignored -> new Entry());
        trimCache();
        return entry;
    }

    private static void trimCache() {
        long pixels = 0L;
        for (Key key : CACHE.keySet()) {
            pixels += (long) key.width * key.height;
        }
        Iterator<Map.Entry<Key, Entry>> iterator = CACHE.entrySet().iterator();
        while ((CACHE.size() > MAX_ENTRIES || pixels > MAX_PIXEL_FOOTPRINT)
                && CACHE.size() > 1 && iterator.hasNext()) {
            Key key = iterator.next().getKey();
            pixels -= (long) key.width * key.height;
            iterator.remove();
        }
    }

    private static Key key(ItemStack stack, ResourceLocation texture, int width, int height) {
        return new Key(BuiltInRegistries.ITEM.getKey(stack.getItem()), texture, width, height);
    }

    record CoverageStats(double coveredAlpha, int paintableAlpha) {
    }

    private record Key(ResourceLocation item, ResourceLocation texture, int width, int height) {
    }

    private static final class Entry {
        private final BitSet pixels = new BitSet();
        private final Set<Long> rectangles = new HashSet<>();
        private int alphaTotal;
    }
}
