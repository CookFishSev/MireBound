package com.fish.mirebound.assimilation;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.Arrays;
import net.minecraft.util.Mth;

/** Allocation-free operations for one player's fixed-size medium contribution palette. */
public final class AssimilationContributions {
    public static final int PERSISTENCE_SCALE = 10_000;

    private AssimilationContributions() {
    }

    public static float total(float[] values) {
        float total = 0.0F;
        int length = Math.min(values == null ? 0 : values.length, SinkingMedium.COUNT);
        for (int i = 0; i < length; i++) {
            total += Math.max(0.0F, values[i]);
        }
        return Mth.clamp(total, 0.0F, 1.0F);
    }

    public static SinkingMedium dominant(float[] values, SinkingMedium fallback) {
        int best = fallback == null ? SinkingMedium.ASSIMILATION_SLIME.id() : fallback.id();
        float strength = 0.0F;
        int length = Math.min(values == null ? 0 : values.length, SinkingMedium.COUNT);
        for (int i = 0; i < length; i++) {
            if (values[i] > strength) {
                strength = values[i];
                best = i;
            }
        }
        return SinkingMedium.byId(best);
    }

    public static float add(float[] values, SinkingMedium medium, float amount) {
        if (values == null || values.length < SinkingMedium.COUNT
                || medium == null || amount <= 0.0F) {
            return 0.0F;
        }
        float accepted = Math.min(amount, 1.0F - total(values));
        if (accepted <= 0.0F) {
            return 0.0F;
        }
        values[medium.id()] = Mth.clamp(values[medium.id()] + accepted, 0.0F, 1.0F);
        return accepted;
    }

    /** Removes a total amount while preserving the current multi-medium mixture. */
    public static float removeProportional(float[] values, float amount) {
        float total = total(values);
        if (values == null || total <= 0.0F || amount <= 0.0F) {
            return 0.0F;
        }
        float removed = Math.min(total, amount);
        float scale = (total - removed) / total;
        int length = Math.min(values.length, SinkingMedium.COUNT);
        for (int index = 0; index < length; index++) {
            values[index] = Math.max(0.0F, values[index] * scale);
        }
        return removed;
    }

    public static void setSingle(float[] values, SinkingMedium medium, float progress) {
        Arrays.fill(values, 0.0F);
        if (medium != null) {
            values[medium.id()] = Mth.clamp(progress, 0.0F, 1.0F);
        }
    }

    public static int[] packPersistent(float[] values) {
        int[] packed = new int[SinkingMedium.COUNT];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = Mth.clamp(Math.round(values[i] * PERSISTENCE_SCALE),
                    0, PERSISTENCE_SCALE);
        }
        return packed;
    }

    public static void unpackPersistent(int[] packed, float[] values) {
        Arrays.fill(values, 0.0F);
        int length = Math.min(packed == null ? 0 : packed.length, SinkingMedium.COUNT);
        float total = 0.0F;
        for (int i = 0; i < length; i++) {
            values[i] = Mth.clamp(packed[i] / (float) PERSISTENCE_SCALE, 0.0F, 1.0F);
            total += values[i];
        }
        if (total > 1.0F) {
            float scale = 1.0F / total;
            for (int i = 0; i < length; i++) {
                values[i] *= scale;
            }
        }
    }

    public static byte[] packNetwork(float[] values) {
        byte[] packed = new byte[SinkingMedium.COUNT];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = (byte) Mth.clamp(Math.round(values[i] * 255.0F), 0, 255);
        }
        return packed;
    }

    public static void unpackNetwork(byte[] packed, float[] values) {
        Arrays.fill(values, 0.0F);
        int length = Math.min(packed == null ? 0 : packed.length, SinkingMedium.COUNT);
        for (int i = 0; i < length; i++) {
            values[i] = (packed[i] & 0xFF) / 255.0F;
        }
    }

    /** Weighted rendezvous selection keeps each canonical cell stable as the mix changes. */
    public static SinkingMedium mediumForCell(int seed, int cell, float[] values,
            SinkingMedium fallback) {
        SinkingMedium selected = fallback == null ? SinkingMedium.ASSIMILATION_SLIME : fallback;
        float total = total(values);
        if (total <= 0.0001F) {
            return selected;
        }
        int hash = mix(seed ^ cell * 0x632BE5AB);
        float target = (float) ((Integer.toUnsignedLong(hash) + 0.5D)
                / 4294967296.0D * total);
        float cumulative = 0.0F;
        int length = Math.min(values == null ? 0 : values.length, SinkingMedium.COUNT);
        for (int i = 0; i < length; i++) {
            float weight = values[i];
            if (weight <= 0.0001F) {
                continue;
            }
            cumulative += weight;
            selected = SinkingMedium.byId(i);
            if (target <= cumulative) {
                return selected;
            }
        }
        return selected;
    }

    public static SinkingMedium mediumForCell(int seed, int cell,
            int[] mediumIds, float[] cumulativeWeights, int count,
            SinkingMedium fallback) {
        if (count <= 0) {
            return fallback == null ? SinkingMedium.ASSIMILATION_SLIME : fallback;
        }
        float total = cumulativeWeights[count - 1];
        int hash = mix(seed ^ cell * 0x632BE5AB);
        float target = (float) ((Integer.toUnsignedLong(hash) + 0.5D)
                / 4294967296.0D * total);
        for (int i = 0; i < count; i++) {
            if (target <= cumulativeWeights[i]) {
                return SinkingMedium.byId(mediumIds[i]);
            }
        }
        return SinkingMedium.byId(mediumIds[count - 1]);
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }
}
