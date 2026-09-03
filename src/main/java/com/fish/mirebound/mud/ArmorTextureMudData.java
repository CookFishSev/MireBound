package com.fish.mirebound.mud;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Sparse per-texture UV mud persisted on the exact armor ItemStack. */
public final class ArmorTextureMudData {
    public static final int MAX_LAYERS = 8;
    public static final int MAX_DIMENSION = 1024;
    public static final int MAX_TOTAL_PIXELS = 65536;
    private static final int BYTES_PER_PIXEL = 6;
    public static final ArmorTextureMudData EMPTY = new ArmorTextureMudData(List.of());

    private static final Codec<Layer> LAYER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(Layer::texture),
            Codec.intRange(1, MAX_DIMENSION).fieldOf("width").forGetter(Layer::width),
            Codec.intRange(1, MAX_DIMENSION).fieldOf("height").forGetter(Layer::height),
            Codec.BYTE_BUFFER.fieldOf("pixels")
                    .xmap(ArmorTextureMudData::bytesFromBuffer, ArmorTextureMudData::bufferFromBytes)
                    .forGetter(Layer::packed),
            Codec.LONG.listOf(0, MAX_TOTAL_PIXELS)
                    .optionalFieldOf("visual_sources", List.of())
                    .forGetter(Layer::visualSources)
    ).apply(instance, Layer::new));

    public static final Codec<ArmorTextureMudData> CODEC = LAYER_CODEC.listOf(0, MAX_LAYERS)
            .xmap(ArmorTextureMudData::new, ArmorTextureMudData::layers);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorTextureMudData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ArmorTextureMudData decode(RegistryFriendlyByteBuf buffer) {
            int count = Mth.clamp(buffer.readVarInt(), 0, MAX_LAYERS);
            List<Layer> layers = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                ResourceLocation texture = buffer.readResourceLocation();
                int width = Mth.clamp(buffer.readVarInt(), 1, MAX_DIMENSION);
                int height = Mth.clamp(buffer.readVarInt(), 1, MAX_DIMENSION);
                byte[] packed = ByteBufCodecs.BYTE_ARRAY.decode(buffer);
                int sourceCount = Mth.clamp(buffer.readVarInt(), 0,
                        packed.length / BYTES_PER_PIXEL);
                long[] visualSources = new long[sourceCount];
                for (int source = 0; source < sourceCount; source++) {
                    visualSources[source] = buffer.readLong();
                }
                layers.add(new Layer(texture, width, height, packed, visualSources));
            }
            return layers.isEmpty() ? EMPTY : new ArmorTextureMudData(layers);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ArmorTextureMudData value) {
            buffer.writeVarInt(value.layers.size());
            for (Layer layer : value.layers) {
                buffer.writeResourceLocation(layer.texture);
                buffer.writeVarInt(layer.width);
                buffer.writeVarInt(layer.height);
                ByteBufCodecs.BYTE_ARRAY.encode(buffer, layer.packed);
                buffer.writeVarInt(layer.visualSources.length);
                for (long source : layer.visualSources) {
                    buffer.writeLong(source);
                }
            }
        }
    };

    private final List<Layer> layers;
    private final int hash;

    private ArmorTextureMudData(List<Layer> source) {
        List<Layer> sanitized = new ArrayList<>(Math.min(source.size(), MAX_LAYERS));
        int remainingPixels = MAX_TOTAL_PIXELS;
        for (Layer layer : source) {
            if (sanitized.size() >= MAX_LAYERS || remainingPixels <= 0
                    || layer == null || layer.texture == null
                    || layer.width < 1 || layer.height < 1
                    || layer.width > MAX_DIMENSION || layer.height > MAX_DIMENSION) {
                continue;
            }
            Layer clean = layer.sanitized(remainingPixels);
            if (!clean.isEmpty()) {
                sanitized.add(clean);
                remainingPixels -= clean.dirtyPixelCount();
            }
        }
        this.layers = List.copyOf(sanitized);
        this.hash = this.layers.hashCode();
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public List<Layer> layers() {
        return layers;
    }

    public Layer layer(ResourceLocation texture, int width, int height) {
        for (Layer layer : layers) {
            if (layer.texture.equals(texture) && layer.width == width && layer.height == height) {
                return layer;
            }
        }
        return null;
    }

    public int dirtyPixelCount() {
        int count = 0;
        for (Layer layer : layers) {
            count += layer.dirtyPixelCount();
        }
        return count;
    }

    public float averageCoverage() {
        int count = 0;
        int total = 0;
        for (Layer layer : layers) {
            count += layer.dirtyPixelCount();
            total += layer.coverageTotal();
        }
        return count == 0 ? 0.0F : total / (255.0F * count);
    }

    public float coverageFraction() {
        long possible = 0L;
        int total = 0;
        for (Layer layer : layers) {
            possible += (long) layer.width * layer.height * 255L;
            total += layer.coverageTotal();
        }
        return possible == 0L ? 0.0F : Mth.clamp(total / (float) possible, 0.0F, 1.0F);
    }

    public SinkingMedium dominantMedium() {
        int[] weights = new int[SinkingMedium.COUNT];
        for (Layer layer : layers) {
            layer.forEach((pixel, coverage, medium) -> weights[medium.id()] += Math.round(coverage * 255.0F));
        }
        int best = 0;
        for (int index = 1; index < weights.length; index++) {
            if (weights[index] > weights[best]) {
                best = index;
            }
        }
        return SinkingMedium.byId(best);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static int maximumPixelsForLayer(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return 0;
        }
        return (int) Math.min((long) width * height, MAX_TOTAL_PIXELS);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof ArmorTextureMudData other && layers.equals(other.layers);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @FunctionalInterface
    public interface PixelConsumer {
        void accept(int pixel, float coverage, SinkingMedium medium);
    }

    @FunctionalInterface
    public interface VisualPixelConsumer {
        void accept(int pixel, float coverage, SinkingMedium medium, long visualSource);
    }

    public static final class Layer {
        private final ResourceLocation texture;
        private final int width;
        private final int height;
        private final byte[] packed;
        private final long[] visualSources;
        private final int hash;

        private Layer(ResourceLocation texture, int width, int height, byte[] packed) {
            this(texture, width, height, packed, new long[0]);
        }

        private Layer(ResourceLocation texture, int width, int height, byte[] packed,
                List<Long> visualSources) {
            this(texture, width, height, packed, toLongArray(visualSources));
        }

        private Layer(ResourceLocation texture, int width, int height, byte[] packed,
                long[] visualSources) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            int usable = Math.min(packed.length - packed.length % BYTES_PER_PIXEL,
                    maximumPixelsForLayer(width, height) * BYTES_PER_PIXEL);
            this.packed = usable <= 0 ? new byte[0] : Arrays.copyOf(packed, usable);
            int recordCount = this.packed.length / BYTES_PER_PIXEL;
            this.visualSources = visualSources == null || visualSources.length == 0
                    ? new long[0]
                    : Arrays.copyOf(visualSources, Math.min(recordCount, visualSources.length));
            this.hash = 31 * (31 * (31 * texture.hashCode() + width) + height)
                    + 31 * Arrays.hashCode(this.packed) + Arrays.hashCode(this.visualSources);
        }

        public ResourceLocation texture() {
            return texture;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public byte[] packed() {
            return Arrays.copyOf(packed, packed.length);
        }

        public List<Long> visualSources() {
            if (visualSources.length == 0) {
                return List.of();
            }
            List<Long> result = new ArrayList<>(visualSources.length);
            for (long source : visualSources) {
                result.add(source);
            }
            return List.copyOf(result);
        }

        public boolean isEmpty() {
            return packed.length == 0;
        }

        public int dirtyPixelCount() {
            return packed.length / BYTES_PER_PIXEL;
        }

        public SinkingMedium mediumAt(int pixel) {
            int low = 0;
            int high = packed.length / BYTES_PER_PIXEL - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int offset = middle * BYTES_PER_PIXEL;
                int storedPixel = readInt(packed, offset);
                if (storedPixel < pixel) {
                    low = middle + 1;
                } else if (storedPixel > pixel) {
                    high = middle - 1;
                } else {
                    return (packed[offset + 4] & 0xFF) == 0
                            ? SinkingMedium.MUD
                            : SinkingMedium.byId(packed[offset + 5] & 0xFF);
                }
            }
            return SinkingMedium.MUD;
        }

        public long visualSourceAt(int pixel) {
            int low = 0;
            int high = packed.length / BYTES_PER_PIXEL - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int offset = middle * BYTES_PER_PIXEL;
                int storedPixel = readInt(packed, offset);
                if (storedPixel < pixel) {
                    low = middle + 1;
                } else if (storedPixel > pixel) {
                    high = middle - 1;
                } else {
                    return middle < visualSources.length ? visualSources[middle] : 0L;
                }
            }
            return 0L;
        }

        public void forEach(PixelConsumer consumer) {
            forEachVisual((pixel, coverage, medium, ignored) ->
                    consumer.accept(pixel, coverage, medium));
        }

        public void forEachVisual(VisualPixelConsumer consumer) {
            int maximumPixel = width * height;
            for (int offset = 0, record = 0; offset < packed.length;
                    offset += BYTES_PER_PIXEL, record++) {
                int pixel = readInt(packed, offset);
                int coverage = packed[offset + 4] & 0xFF;
                if (pixel < 0 || pixel >= maximumPixel || coverage == 0) {
                    continue;
                }
                consumer.accept(pixel, coverage / 255.0F,
                        SinkingMedium.byId(packed[offset + 5] & 0xFF),
                        record < visualSources.length ? visualSources[record] : 0L);
            }
        }

        private int coverageTotal() {
            int total = 0;
            for (int offset = 0; offset < packed.length; offset += BYTES_PER_PIXEL) {
                total += packed[offset + 4] & 0xFF;
            }
            return total;
        }

        private Layer sanitized(int maximumPixels) {
            TreeMap<Integer, PixelValue> values = new TreeMap<>();
            forEachVisual((pixel, coverage, medium, visualSource) -> values.put(pixel,
                    new PixelValue(Math.round(coverage * 255.0F), medium.id(), visualSource)));
            return fromValues(texture, width, height, values, maximumPixels);
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof Layer other
                    && width == other.width && height == other.height
                    && texture.equals(other.texture) && Arrays.equals(packed, other.packed)
                    && Arrays.equals(visualSources, other.visualSources);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    public static final class Builder {
        private final LinkedHashMap<LayerKey, TreeMap<Integer, PixelValue>> values = new LinkedHashMap<>();
        private int pixelCount;
        private boolean changed;

        private Builder(ArmorTextureMudData source) {
            for (Layer layer : source.layers) {
                TreeMap<Integer, PixelValue> layerValues = new TreeMap<>();
                layer.forEachVisual((pixel, coverage, medium, visualSource) -> layerValues.put(pixel,
                        new PixelValue(Math.round(coverage * 255.0F), medium.id(), visualSource)));
                values.put(new LayerKey(layer.texture, layer.width, layer.height), layerValues);
                pixelCount += layerValues.size();
            }
        }

        public boolean mark(ResourceLocation texture, int width, int height, int pixel,
                float target, SinkingMedium medium) {
            return mark(texture, width, height, pixel, target, medium, 0L);
        }

        public boolean mark(ResourceLocation texture, int width, int height, int pixel,
                float target, SinkingMedium medium, long visualSource) {
            if (!valid(texture, width, height, pixel)) {
                return false;
            }
            if (medium == null) {
                return false;
            }
            LayerKey key = new LayerKey(texture, width, height);
            TreeMap<Integer, PixelValue> layer = values.get(key);
            if (layer == null) {
                if (values.size() >= MAX_LAYERS) {
                    return false;
                }
                layer = new TreeMap<>();
                values.put(key, layer);
            }
            PixelValue current = layer.get(pixel);
            int currentCoverage = current == null ? 0 : current.coverage;
            int targetCoverage = Mth.clamp(Math.round(target * 255.0F), 0, 255);
            if (targetCoverage <= currentCoverage && current != null
                    && current.mediumId == medium.id()
                    && current.visualSource == visualSource) {
                return false;
            }
            if (currentCoverage == 0) {
                if (layer.size() >= maximumPixelsForLayer(width, height)
                        || pixelCount >= MAX_TOTAL_PIXELS) {
                    return false;
                }
                pixelCount++;
            }
            int nextCoverage = Math.max(currentCoverage, targetCoverage);
            int nextMedium = targetCoverage >= currentCoverage ? medium.id() : current.mediumId;
            long nextVisualSource = targetCoverage >= currentCoverage
                    ? visualSource : current.visualSource;
            layer.put(pixel, new PixelValue(nextCoverage, nextMedium, nextVisualSource));
            changed = true;
            return true;
        }

        public boolean wash(ResourceLocation texture, int width, int height, int pixel,
                float amount, float clearThreshold) {
            LayerKey key = new LayerKey(texture, width, height);
            TreeMap<Integer, PixelValue> layer = values.get(key);
            if (layer == null) {
                return false;
            }
            PixelValue current = layer.get(pixel);
            if (current == null || current.coverage == 0) {
                return false;
            }
            int currentCoverage = current.coverage;
            int reduction = Math.max(1, Math.round(Math.max(0.0F, amount) * 255.0F));
            int nextCoverage = Math.max(0, currentCoverage - reduction);
            if (nextCoverage / 255.0F <= clearThreshold) {
                nextCoverage = 0;
            }
            if (nextCoverage == currentCoverage) {
                return false;
            }
            if (nextCoverage == 0) {
                layer.remove(pixel);
                pixelCount--;
                if (layer.isEmpty()) {
                    values.remove(key);
                }
            } else {
                layer.put(pixel, new PixelValue(nextCoverage, current.mediumId,
                        current.visualSource));
            }
            changed = true;
            return true;
        }

        public boolean changed() {
            return changed;
        }

        public ArmorTextureMudData build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            List<Layer> result = new ArrayList<>(values.size());
            int remainingPixels = MAX_TOTAL_PIXELS;
            for (Map.Entry<LayerKey, TreeMap<Integer, PixelValue>> entry : values.entrySet()) {
                if (!entry.getValue().isEmpty() && remainingPixels > 0) {
                    LayerKey key = entry.getKey();
                    Layer layer = fromValues(
                            key.texture, key.width, key.height, entry.getValue(), remainingPixels);
                    result.add(layer);
                    remainingPixels -= layer.dirtyPixelCount();
                }
            }
            return result.isEmpty() ? EMPTY : new ArmorTextureMudData(result);
        }

        private static boolean valid(ResourceLocation texture, int width, int height, int pixel) {
            return texture != null && width > 0 && height > 0
                    && width <= MAX_DIMENSION && height <= MAX_DIMENSION
                    && pixel >= 0 && pixel < width * height;
        }
    }

    private static Layer fromValues(ResourceLocation texture, int width, int height,
            TreeMap<Integer, PixelValue> values, int maximumPixels) {
        int count = Math.min(values.size(),
                Math.min(maximumPixelsForLayer(width, height), Math.max(0, maximumPixels)));
        byte[] packed = new byte[count * BYTES_PER_PIXEL];
        int offset = 0;
        long[] visualSources = new long[count];
        int record = 0;
        for (Map.Entry<Integer, PixelValue> entry : values.entrySet()) {
            if (offset >= packed.length) {
                break;
            }
            writeInt(packed, offset, entry.getKey());
            packed[offset + 4] = (byte) entry.getValue().coverage;
            packed[offset + 5] = (byte) entry.getValue().mediumId;
            visualSources[record++] = entry.getValue().visualSource;
            offset += BYTES_PER_PIXEL;
        }
        boolean hasVisualSource = false;
        for (long visualSource : visualSources) {
            if (visualSource != 0L) {
                hasVisualSource = true;
                break;
            }
        }
        return new Layer(texture, width, height, packed,
                hasVisualSource ? visualSources : new long[0]);
    }

    private static long[] toLongArray(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return new long[0];
        }
        long[] result = new long[values.size()];
        for (int index = 0; index < values.size(); index++) {
            Long value = values.get(index);
            result[index] = value == null ? 0L : value;
        }
        return result;
    }

    private static int readInt(byte[] bytes, int offset) {
        return bytes[offset] & 0xFF
                | (bytes[offset + 1] & 0xFF) << 8
                | (bytes[offset + 2] & 0xFF) << 16
                | (bytes[offset + 3] & 0xFF) << 24;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }

    private static byte[] bytesFromBuffer(ByteBuffer source) {
        ByteBuffer copy = source.slice();
        byte[] result = new byte[copy.remaining()];
        copy.get(result);
        return result;
    }

    private static ByteBuffer bufferFromBytes(byte[] source) {
        return ByteBuffer.wrap(Arrays.copyOf(source, source.length));
    }

    private record LayerKey(ResourceLocation texture, int width, int height) {
    }

    private record PixelValue(int coverage, int mediumId, long visualSource) {
    }
}
