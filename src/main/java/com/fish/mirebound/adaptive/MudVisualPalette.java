package com.fish.mirebound.adaptive;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.Arrays;
import net.minecraft.util.Mth;

/** Fixed-capacity weighted palette for medium-specific visual sources. */
public final class MudVisualPalette {
    public static final int MAX_ENTRIES = 8;
    public static final int PERSISTENCE_SCALE = 10_000;

    private final byte[] mediumIds = new byte[MAX_ENTRIES];
    private final long[] visualSources = new long[MAX_ENTRIES];
    private final float[] weights = new float[MAX_ENTRIES];
    private int size;

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public SinkingMedium mediumAt(int index) {
        checkIndex(index);
        return SinkingMedium.byId(mediumIds[index] & 0xFF);
    }

    public long visualSourceAt(int index) {
        checkIndex(index);
        return visualSources[index];
    }

    public float weightAt(int index) {
        checkIndex(index);
        return weights[index];
    }

    public float totalWeight() {
        float total = 0.0F;
        for (int index = 0; index < size; index++) {
            total += weights[index];
        }
        return total;
    }

    public float weight(SinkingMedium medium, long visualSource) {
        int index = medium == null ? -1 : indexOf(medium, visualSource);
        return index < 0 ? 0.0F : weights[index];
    }

    public long dominantVisualSource(SinkingMedium medium) {
        int index = dominantIndex(medium, -1);
        return index < 0 ? MudVisualSource.NONE : visualSources[index];
    }

    public float add(SinkingMedium medium, long visualSource, float weight) {
        if (medium == null || !Float.isFinite(weight) || weight <= 0.0F) {
            return 0.0F;
        }
        int matching = indexOf(medium, visualSource);
        if (matching >= 0) {
            weights[matching] += weight;
            return weight;
        }
        if (size < MAX_ENTRIES) {
            set(size++, medium, visualSource, weight);
            return weight;
        }

        int weakest = weakestIndex();
        if (weight > weights[weakest]) {
            float displaced = weights[weakest];
            int receiver = dominantIndex(mediumAt(weakest), weakest);
            if (receiver < 0) {
                receiver = dominantIndex(null, weakest);
            }
            weights[receiver] += displaced;
            set(weakest, medium, visualSource, weight);
        } else {
            int receiver = dominantIndex(medium, -1);
            if (receiver < 0) {
                receiver = dominantIndex(null, -1);
            }
            weights[receiver] += weight;
        }
        return weight;
    }

    public float removeProportional(float amount) {
        float total = totalWeight();
        if (!Float.isFinite(amount) || amount <= 0.0F || total <= 0.0F) {
            return 0.0F;
        }
        float removed = Math.min(total, amount);
        scale((total - removed) / total);
        return removed;
    }

    public void scaleTo(float targetTotal) {
        float total = totalWeight();
        if (!Float.isFinite(targetTotal) || targetTotal <= 0.0F || total <= 0.0F) {
            clear();
            return;
        }
        scale(targetTotal / total);
    }

    public void setSingle(SinkingMedium medium, long visualSource, float weight) {
        clear();
        add(medium, visualSource, weight);
    }

    public void copyFrom(MudVisualPalette source) {
        clear();
        if (source == null) {
            return;
        }
        size = source.size;
        System.arraycopy(source.mediumIds, 0, mediumIds, 0, size);
        System.arraycopy(source.visualSources, 0, visualSources, 0, size);
        System.arraycopy(source.weights, 0, weights, 0, size);
    }

    public MudVisualPalette copy() {
        MudVisualPalette result = new MudVisualPalette();
        result.copyFrom(this);
        return result;
    }

    public void clear() {
        Arrays.fill(mediumIds, (byte) 0);
        Arrays.fill(visualSources, MudVisualSource.NONE);
        Arrays.fill(weights, 0.0F);
        size = 0;
    }

    public Entry select(long seed, int cell, SinkingMedium fallback) {
        if (size == 0) {
            return new Entry(fallback == null ? SinkingMedium.MUD : fallback,
                    MudVisualSource.NONE, 0.0F);
        }
        float total = totalWeight();
        if (total <= 0.0F) {
            return new Entry(fallback == null ? mediumAt(0) : fallback,
                    MudVisualSource.NONE, 0.0F);
        }
        long hash = mix(seed ^ (long) cell * 0x9E3779B97F4A7C15L);
        double unit = (hash >>> 11) * 0x1.0p-53;
        float target = (float) (unit * total);
        float cumulative = 0.0F;
        for (int index = 0; index < size; index++) {
            cumulative += weights[index];
            if (target <= cumulative) {
                return entryAt(index);
            }
        }
        return entryAt(size - 1);
    }

    public Entry selectForMedium(long seed, int cell, SinkingMedium medium) {
        SinkingMedium fallback = medium == null ? SinkingMedium.MUD : medium;
        float total = 0.0F;
        for (int index = 0; index < size; index++) {
            if ((mediumIds[index] & 0xFF) == fallback.id()) {
                total += weights[index];
            }
        }
        if (total <= 0.0F) {
            return new Entry(fallback, MudVisualSource.NONE, 0.0F);
        }
        long hash = mix(seed ^ (long) cell * 0x9E3779B97F4A7C15L);
        float target = (float) ((hash >>> 11) * 0x1.0p-53 * total);
        float cumulative = 0.0F;
        Entry selected = new Entry(fallback, MudVisualSource.NONE, 0.0F);
        for (int index = 0; index < size; index++) {
            if ((mediumIds[index] & 0xFF) != fallback.id()) {
                continue;
            }
            cumulative += weights[index];
            selected = entryAt(index);
            if (target <= cumulative) {
                return selected;
            }
        }
        return selected;
    }

    /** Packs each medium id and an unsigned 16-bit normalized weight into one int. */
    public int[] packPersistentEntries() {
        int[] packed = new int[size];
        for (int index = 0; index < size; index++) {
            int weight = Mth.clamp(Math.round(weights[index] * PERSISTENCE_SCALE),
                    0, 0xFFFF);
            packed[index] = (mediumIds[index] & 0xFF) << 16 | weight;
        }
        return packed;
    }

    public long[] packVisualSources() {
        return Arrays.copyOf(visualSources, size);
    }

    public void unpackPersistent(int[] entries, long[] sources) {
        clear();
        int count = Math.min(MAX_ENTRIES,
                Math.min(entries == null ? 0 : entries.length,
                        sources == null ? 0 : sources.length));
        for (int index = 0; index < count; index++) {
            int mediumId = entries[index] >>> 16 & 0xFF;
            float weight = (entries[index] & 0xFFFF) / (float) PERSISTENCE_SCALE;
            if (mediumId < SinkingMedium.COUNT && weight > 0.0F) {
                add(SinkingMedium.byId(mediumId), sources[index], weight);
            }
        }
    }

    /** Two bytes per entry: medium id followed by normalized unsigned weight. */
    public byte[] packNetworkEntries() {
        byte[] packed = new byte[size * 2];
        float total = totalWeight();
        for (int index = 0; index < size; index++) {
            packed[index * 2] = mediumIds[index];
            packed[index * 2 + 1] = (byte) Mth.clamp(Math.round(
                    total <= 0.0F ? 0.0F : weights[index] / total * 255.0F), 0, 255);
        }
        return packed;
    }

    public void unpackNetwork(byte[] entries, long[] sources, float targetTotal) {
        clear();
        int count = Math.min(MAX_ENTRIES,
                Math.min(entries == null ? 0 : entries.length / 2,
                        sources == null ? 0 : sources.length));
        for (int index = 0; index < count; index++) {
            int mediumId = entries[index * 2] & 0xFF;
            int packedWeight = entries[index * 2 + 1] & 0xFF;
            if (mediumId < SinkingMedium.COUNT && packedWeight > 0) {
                add(SinkingMedium.byId(mediumId), sources[index], packedWeight);
            }
        }
        scaleTo(Math.max(0.0F, targetTotal));
    }

    private Entry entryAt(int index) {
        return new Entry(mediumAt(index), visualSources[index], weights[index]);
    }

    private void scale(float scale) {
        if (scale <= 0.0F) {
            clear();
            return;
        }
        for (int index = 0; index < size; index++) {
            weights[index] *= scale;
        }
    }

    private int indexOf(SinkingMedium medium, long visualSource) {
        for (int index = 0; index < size; index++) {
            if ((mediumIds[index] & 0xFF) == medium.id()
                    && visualSources[index] == visualSource) {
                return index;
            }
        }
        return -1;
    }

    private int weakestIndex() {
        int weakest = 0;
        for (int index = 1; index < size; index++) {
            if (weights[index] < weights[weakest]) {
                weakest = index;
            }
        }
        return weakest;
    }

    private int dominantIndex(SinkingMedium medium, int excluded) {
        int dominant = -1;
        for (int index = 0; index < size; index++) {
            if (index == excluded || medium != null
                    && (mediumIds[index] & 0xFF) != medium.id()) {
                continue;
            }
            if (dominant < 0 || weights[index] > weights[dominant]) {
                dominant = index;
            }
        }
        return dominant;
    }

    private void set(int index, SinkingMedium medium, long visualSource, float weight) {
        mediumIds[index] = (byte) medium.id();
        visualSources[index] = visualSource;
        weights[index] = weight;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record Entry(SinkingMedium medium, long visualSource, float weight) {
    }
}
