package com.fish.mirebound.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

final class SkinPixelCache {
    private static final long RETRY_DELAY_MILLIS = 3000L;
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final long MAX_CACHED_PIXELS = 16L * 1024L * 1024L;
    private static final Field HTTP_TEXTURE_FILE = findField(HttpTexture.class, "file");
    private static final Map<ResourceLocation, Entry> CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, Entry> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private SkinPixelCache() {
    }

    static void reset() {
        CACHE.clear();
    }

    static boolean isOpaque(ResourceLocation texture, float u, float v) {
        SkinPixels pixels = pixels(texture);
        if (pixels == null) {
            return false;
        }

        int x = Mth.clamp((int) (u * pixels.width()), 0, pixels.width() - 1);
        int y = Mth.clamp((int) (v * pixels.height()), 0, pixels.height() - 1);
        return pixels.alpha(x, y) > 0;
    }

    static boolean hasPixels(ResourceLocation texture) {
        return pixels(texture) != null;
    }

    static int pixel(ResourceLocation texture, int x, int y) {
        SkinPixels pixels = pixels(texture);
        if (pixels == null) {
            return 0;
        }

        return pixels.pixel(Mth.clamp(x, 0, pixels.width() - 1), Mth.clamp(y, 0, pixels.height() - 1));
    }

    static int width(ResourceLocation texture) {
        SkinPixels pixels = pixels(texture);
        return pixels == null ? 64 : pixels.width();
    }

    static int height(ResourceLocation texture) {
        SkinPixels pixels = pixels(texture);
        return pixels == null ? 64 : pixels.height();
    }

    static int opaquePixelCount(ResourceLocation texture, int width, int height) {
        SkinPixels pixels = pixels(texture);
        return pixels == null || pixels.width() != width || pixels.height() != height
                ? 0
                : pixels.opaquePixelCount();
    }

    static int alpha(ResourceLocation texture, int x, int y) {
        SkinPixels pixels = pixels(texture);
        return pixels == null || x < 0 || y < 0 || x >= pixels.width() || y >= pixels.height()
                ? 0
                : pixels.alpha(x, y);
    }

    static long alphaTotal(ResourceLocation texture, int width, int height) {
        SkinPixels pixels = pixels(texture);
        if (pixels == null || pixels.width() != width || pixels.height() != height) {
            return 0L;
        }
        long total = 0L;
        for (int pixel : pixels.pixels()) {
            total += pixel >>> 24;
        }
        return total;
    }

    static boolean cacheTexturePixels(ResourceLocation texture, AbstractTexture sourceTexture) {
        return cacheTexturePixelsDebug(texture, sourceTexture).cached();
    }

    static CacheDebugResult cacheTexturePixelsDebug(ResourceLocation texture, AbstractTexture sourceTexture) {
        if (texture == null || sourceTexture == null) {
            return new CacheDebugResult(false, "cacheTexturePixels skipped: texture=" + texture
                    + ", sourceTexture=" + describeTextureObject(sourceTexture));
        }

        TextureCopyDebug copy = copyTexturePixelsDebug(sourceTexture);
        SkinPixels pixels = copy.pixels();
        if (pixels == null) {
            return new CacheDebugResult(false, "cacheTexturePixels failed: " + copy.detail());
        }

        Entry entry = CACHE.computeIfAbsent(texture, ignored -> new Entry());
        entry.pixels = pixels;
        entry.nextRetryAtMillis = 0L;
        trimPixelBudget();
        return new CacheDebugResult(true, "cacheTexturePixels ok: width=" + pixels.width()
                + ", height=" + pixels.height()
                + ", source=" + describeTextureObject(sourceTexture));
    }

    static String debugTextureState(ResourceLocation texture) {
        if (texture == null) {
            return "texture=null";
        }

        Entry entry = CACHE.get(texture);
        String cacheState;
        if (entry == null) {
            cacheState = "cacheEntry=absent";
        } else if (entry.pixels != null) {
            cacheState = "cacheEntry=pixels(" + entry.pixels.width() + "x" + entry.pixels.height() + ")";
        } else {
            long waitMillis = Math.max(0L, entry.nextRetryAtMillis - System.currentTimeMillis());
            cacheState = "cacheEntry=noPixels retryInMs=" + waitMillis;
        }

        return "texture=" + texture
                + ", " + cacheState
                + ", textureManager=" + debugTextureManagerState(texture)
                + ", resourceManager=" + debugResourceManagerState(texture);
    }

    static String debugTextureManagerState(ResourceLocation texture) {
        if (texture == null) {
            return "textureManagerTexture=null";
        }
        try {
            AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
            return describeTextureObject(abstractTexture);
        } catch (RuntimeException exception) {
            return "getTexture failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
    }

    static String describeTextureObject(AbstractTexture texture) {
        if (texture == null) {
            return "null";
        }

        StringBuilder detail = new StringBuilder(texture.getClass().getName());
        detail.append("@").append(Integer.toHexString(System.identityHashCode(texture)));
        if (texture instanceof DynamicTexture dynamicTexture) {
            NativeImage pixels = dynamicTexture.getPixels();
            detail.append(" DynamicTexture.pixels=");
            detail.append(pixels == null ? "null" : pixels.getWidth() + "x" + pixels.getHeight());
        }
        detail.append(" nativeFields=").append(describeNativeImageFields(texture));
        return detail.toString();
    }

    private static SkinPixels pixels(ResourceLocation texture) {
        Entry entry = CACHE.computeIfAbsent(texture, ignored -> new Entry());
        long now = System.currentTimeMillis();
        if (entry.pixels != null) {
            return entry.pixels;
        }
        if (now < entry.nextRetryAtMillis) {
            return null;
        }

        entry.pixels = loadPixels(texture);
        if (entry.pixels == null) {
            entry.nextRetryAtMillis = now + RETRY_DELAY_MILLIS;
        } else {
            trimPixelBudget();
        }
        return entry.pixels;
    }

    private static void trimPixelBudget() {
        long cachedPixels = 0L;
        for (Entry entry : CACHE.values()) {
            if (entry.pixels != null) {
                cachedPixels += entry.pixels.pixels().length;
            }
        }
        if (cachedPixels <= MAX_CACHED_PIXELS) {
            return;
        }
        Iterator<Map.Entry<ResourceLocation, Entry>> iterator = CACHE.entrySet().iterator();
        while (cachedPixels > MAX_CACHED_PIXELS && CACHE.size() > 1 && iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.pixels != null) {
                cachedPixels -= entry.pixels.pixels().length;
            }
            iterator.remove();
        }
    }

    private static SkinPixels loadPixels(ResourceLocation texture) {
        SkinPixels fromTextureManager = loadFromTextureManager(texture);
        if (fromTextureManager != null) {
            return fromTextureManager;
        }

        SkinPixels fromHttpCache = loadFromHttpTexture(texture);
        if (fromHttpCache != null) {
            return fromHttpCache;
        }

        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture);
            try (InputStream stream = resource.open()) {
                return readPixels(stream);
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static SkinPixels loadFromTextureManager(ResourceLocation texture) {
        try {
            AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
            return copyTexturePixels(abstractTexture);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static SkinPixels copyTexturePixels(AbstractTexture abstractTexture) {
        if (abstractTexture instanceof DynamicTexture dynamicTexture) {
            return copyNativeImage(dynamicTexture.getPixels());
        }
        SkinPixels reflected = loadNativeImageField(abstractTexture);
        return reflected != null ? reflected : downloadTexturePixels(abstractTexture);
    }

    private static TextureCopyDebug copyTexturePixelsDebug(AbstractTexture abstractTexture) {
        if (abstractTexture == null) {
            return new TextureCopyDebug(null, "sourceTexture=null");
        }
        if (abstractTexture instanceof DynamicTexture dynamicTexture) {
            NativeImage image = dynamicTexture.getPixels();
            SkinPixels pixels = copyNativeImage(image);
            return new TextureCopyDebug(pixels, "DynamicTexture pixels="
                    + (image == null ? "null" : image.getWidth() + "x" + image.getHeight())
                    + ", copied=" + (pixels != null));
        }

        NativeImageFieldDebug nativeField = loadNativeImageFieldDebug(abstractTexture);
        if (nativeField.pixels() != null) {
            return new TextureCopyDebug(nativeField.pixels(), nativeField.detail());
        }

        TextureCopyDebug downloaded = downloadTexturePixelsDebug(abstractTexture);
        if (downloaded.pixels() != null) {
            return downloaded;
        }
        return new TextureCopyDebug(null, nativeField.detail() + "; " + downloaded.detail());
    }

    private static SkinPixels loadFromHttpTexture(ResourceLocation texture) {
        if (HTTP_TEXTURE_FILE == null) {
            return null;
        }

        try {
            AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
            if (!(abstractTexture instanceof HttpTexture)) {
                return null;
            }

            Object value = HTTP_TEXTURE_FILE.get(abstractTexture);
            if (!(value instanceof File file) || !file.isFile()) {
                return null;
            }

            try (InputStream stream = new FileInputStream(file)) {
                return readPixels(stream);
            }
        } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static SkinPixels loadNativeImageField(AbstractTexture texture) {
        if (texture == null) {
            return null;
        }

        Class<?> type = texture.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!NativeImage.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(texture);
                    if (value instanceof NativeImage image) {
                        SkinPixels pixels = copyNativeImage(image);
                        if (pixels != null) {
                            return pixels;
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Renderer-owned textures may expose several implementation fields.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static NativeImageFieldDebug loadNativeImageFieldDebug(AbstractTexture texture) {
        if (texture == null) {
            return new NativeImageFieldDebug(null, "sourceTexture=null");
        }

        StringBuilder detail = new StringBuilder("reflectNativeImageFields ");
        boolean sawField = false;
        Class<?> type = texture.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!NativeImage.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                sawField = true;
                try {
                    field.setAccessible(true);
                    Object value = field.get(texture);
                    if (value instanceof NativeImage image) {
                        SkinPixels pixels = copyNativeImage(image);
                        detail.append(type.getName()).append("#").append(field.getName())
                                .append("=").append(image.getWidth()).append("x").append(image.getHeight())
                                .append(" copied=").append(pixels != null);
                        return new NativeImageFieldDebug(pixels, detail.toString());
                    }
                    detail.append(type.getName()).append("#").append(field.getName()).append("=null; ");
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    detail.append(type.getName()).append("#").append(field.getName()).append(" failed=")
                            .append(exception.getClass().getSimpleName()).append("; ");
                }
            }
            type = type.getSuperclass();
        }

        if (!sawField) {
            detail.append("none");
        }
        return new NativeImageFieldDebug(null, detail.toString());
    }

    private static SkinPixels readPixels(InputStream stream) throws IOException {
        try (NativeImage image = BoundedResourceReader.readImage(stream)) {
            return copyNativeImage(image);
        }
    }

    private static SkinPixels copyNativeImage(NativeImage image) {
        if (image == null) {
            return null;
        }

            int width = image.getWidth();
            int height = image.getHeight();
            if (!BoundedResourceReader.isImageSizeAllowed(width, height)) {
                return null;
            }
            int[] pixels = new int[width * height];
            int opaquePixelCount = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    pixels[y * width + x] = pixel;
                    if (pixel >>> 24 != 0) {
                        opaquePixelCount++;
                    }
                }
            }
            return new SkinPixels(width, height, pixels, opaquePixelCount);
    }

    private static SkinPixels downloadTexturePixels(AbstractTexture texture) {
        return downloadTexturePixelsDebug(texture).pixels();
    }

    private static TextureCopyDebug downloadTexturePixelsDebug(AbstractTexture texture) {
        if (texture == null) {
            return new TextureCopyDebug(null, "gpuDownload skipped: texture=null");
        }
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            return new TextureCopyDebug(null, "gpuDownload skipped: notRenderThread");
        }

        int textureId;
        try {
            textureId = texture.getId();
        } catch (RuntimeException exception) {
            return new TextureCopyDebug(null, "gpuDownload getId failed="
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        if (textureId <= 0) {
            return new TextureCopyDebug(null, "gpuDownload skipped: textureId=" + textureId);
        }

        int previousTexture = RenderSystem.getShaderTexture(0);
        try {
            RenderSystem.bindTexture(textureId);
            int width = GlStateManager._getTexLevelParameter(3553, 0, 4096);
            int height = GlStateManager._getTexLevelParameter(3553, 0, 4097);
            if (!BoundedResourceReader.isImageSizeAllowed(width, height)) {
                return new TextureCopyDebug(null, "gpuDownload invalidSize=" + width + "x" + height
                        + ", textureId=" + textureId);
            }

            try (NativeImage image = new NativeImage(width, height, true)) {
                image.downloadTexture(0, false);
                SkinPixels pixels = copyNativeImage(image);
                return new TextureCopyDebug(pixels, "gpuDownload ok: textureId=" + textureId
                        + ", size=" + width + "x" + height
                        + ", copied=" + (pixels != null));
            }
        } catch (RuntimeException exception) {
            return new TextureCopyDebug(null, "gpuDownload failed="
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage()
                    + ", textureId=" + textureId);
        } finally {
            if (previousTexture > 0) {
                RenderSystem.bindTexture(previousTexture);
            }
        }
    }

    private static String describeNativeImageFields(AbstractTexture texture) {
        StringJoiner joiner = new StringJoiner(";");
        boolean sawField = false;
        Class<?> type = texture.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!NativeImage.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                sawField = true;
                try {
                    field.setAccessible(true);
                    Object value = field.get(texture);
                    if (value instanceof NativeImage image) {
                        joiner.add(type.getSimpleName() + "#" + field.getName() + "="
                                + image.getWidth() + "x" + image.getHeight());
                    } else {
                        joiner.add(type.getSimpleName() + "#" + field.getName() + "=null");
                    }
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    joiner.add(type.getSimpleName() + "#" + field.getName() + "=failed:"
                            + exception.getClass().getSimpleName());
                }
            }
            type = type.getSuperclass();
        }
        return sawField ? joiner.toString() : "none";
    }

    private static String debugResourceManagerState(ResourceLocation texture) {
        try {
            Minecraft.getInstance().getResourceManager().getResourceOrThrow(texture);
            return "present";
        } catch (IOException | RuntimeException exception) {
            return "missing:" + exception.getClass().getSimpleName();
        }
    }

    private static Field findField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static final class Entry {
        private SkinPixels pixels;
        private long nextRetryAtMillis;
    }

    record CacheDebugResult(boolean cached, String detail) {
    }

    private record TextureCopyDebug(SkinPixels pixels, String detail) {
    }

    private record NativeImageFieldDebug(SkinPixels pixels, String detail) {
    }

    private record SkinPixels(int width, int height, int[] pixels, int opaquePixelCount) {
        private int pixel(int x, int y) {
            return pixels[y * width + x];
        }

        private int alpha(int x, int y) {
            return pixels[y * width + x] >>> 24;
        }
    }
}
