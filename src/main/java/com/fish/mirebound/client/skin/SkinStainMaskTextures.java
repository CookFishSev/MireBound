package com.fish.mirebound.client.skin;

import com.fish.mirebound.coverage.skin.SkinStainMask;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** A white alpha stencil lets the normal entity shader clip HD UV pixels without subdividing geometry. */
public final class SkinStainMaskTextures {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int MAX_PIXELS = 32 * 1024 * 1024;
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(16, .75F, true);
    private static int retainedPixels;

    private SkinStainMaskTextures() {}

    public static ResourceLocation texture(int entityId, int skinWidth, int skinHeight) {
        SkinStainMask mask = ClientSkinStainRules.forEntity(entityId);
        if (mask == null) return WHITE;
        int height = Math.max(1, Math.min(SkinStainMask.MAX_DIMENSION,
                (int) ((long) mask.width() * skinHeight / Math.max(1, skinWidth))));
        Key key = new Key(mask, height);
        Entry existing = CACHE.get(key);
        if (existing != null) return existing.texture;
        int pixels = mask.width() * height;
        var iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext() && (CACHE.size() >= 32 || retainedPixels + pixels > MAX_PIXELS)) {
            Entry entry = iterator.next().getValue();
            Minecraft.getInstance().getTextureManager().release(entry.texture);
            retainedPixels -= entry.pixels;
            iterator.remove();
        }
        NativeImage image = new NativeImage(mask.width(), height, true);
        image.fillRect(0, 0, mask.width(), height, -1);
        mask.forEachBlocked(mask.width(), height, index -> image.setPixelRGBA(index % mask.width(), index / mask.width(), 0));
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        ResourceLocation location = Minecraft.getInstance().getTextureManager().register("mirebound_skin_stencil", texture);
        CACHE.put(key, new Entry(location, pixels));
        retainedPixels += pixels;
        return location;
    }

    public static void reset() {
        for (Entry entry : CACHE.values()) Minecraft.getInstance().getTextureManager().release(entry.texture);
        CACHE.clear();
        retainedPixels = 0;
    }

    private record Key(SkinStainMask mask, int height) {}
    private record Entry(ResourceLocation texture, int pixels) {}
}
