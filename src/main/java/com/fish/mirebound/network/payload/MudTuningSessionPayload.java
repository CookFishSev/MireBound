package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.mud.tuning.MudTuningObjectId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudTuningSessionPayload(MudTuningScope scope, boolean editable, MudTuningAnchor first,
        MudTuningAnchor second, List<MediumProfile> profiles) implements CustomPacketPayload {
    public static final Type<MudTuningSessionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_tuning_session"));
    public static final int MAX_OBJECTS = 1024;
    private static final int MAX_VECTOR_PALETTE = MAX_OBJECTS;
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningSessionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudTuningSessionPayload decode(RegistryFriendlyByteBuf buffer) {
                    MudTuningScope scope = MudTuningScope.byId(buffer.readVarInt());
                    boolean editable = buffer.readBoolean();
                    MudTuningAnchor first = MudTuningAnchor.read(buffer);
                    MudTuningAnchor second = MudTuningAnchor.read(buffer);
                    List<double[]> baselines = readBaselinePalette(buffer);
                    List<double[]> values = readValuePalette(buffer, baselines);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_OBJECTS) {
                        throw new IllegalArgumentException("Invalid mud tuning profile count " + count);
                    }
                    List<MediumProfile> profiles = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        MudTuningObjectId objectId = MudTuningObjectId.read(buffer);
                        int blockCount = buffer.readVarInt();
                        boolean anyLocal = buffer.readBoolean();
                        boolean allLocal = buffer.readBoolean();
                        int variant = buffer.readVarInt();
                        int height = buffer.readVarInt();
                        boolean shapeMixed = buffer.readBoolean();
                        int representativeStateId = buffer.readVarInt();
                        int capabilities = buffer.readVarInt();
                        double[] profileValues = paletteEntry(values, buffer.readVarInt(), "value");
                        double[] resetValues = paletteEntry(
                                baselines, buffer.readVarInt(), "baseline");
                        profiles.add(new MediumProfile(objectId, blockCount, anyLocal, allLocal,
                                variant, height, shapeMixed, representativeStateId, capabilities,
                                profileValues, resetValues));
                    }
                    return new MudTuningSessionPayload(scope, editable, first, second, List.copyOf(profiles));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudTuningSessionPayload payload) {
                    buffer.writeVarInt(payload.scope.ordinal());
                    buffer.writeBoolean(payload.editable);
                    MudTuningAnchor.write(buffer, payload.first);
                    MudTuningAnchor.write(buffer, payload.second);
                    Palette palette = Palette.create(payload.profiles);
                    writeBaselinePalette(buffer, palette.baselines);
                    writeValuePalette(buffer, palette.values);
                    buffer.writeVarInt(payload.profiles.size());
                    for (MediumProfile profile : payload.profiles) {
                        profile.objectId.write(buffer);
                        buffer.writeVarInt(profile.blockCount);
                        buffer.writeBoolean(profile.anyLocal);
                        buffer.writeBoolean(profile.allLocal);
                        buffer.writeVarInt(profile.blockVariant);
                        buffer.writeVarInt(profile.blockHeight);
                        buffer.writeBoolean(profile.shapeMixed);
                        buffer.writeVarInt(profile.representativeStateId);
                        buffer.writeVarInt(profile.capabilities);
                        buffer.writeVarInt(palette.valueIndex.get(VectorKey.of(profile.values)));
                        buffer.writeVarInt(palette.baselineIndex.get(VectorKey.of(profile.resetValues)));
                    }
                }
            };

    private static List<double[]> readBaselinePalette(RegistryFriendlyByteBuf buffer) {
        int count = checkedPaletteCount(buffer.readVarInt(), "baseline");
        List<double[]> result = new ArrayList<>(count);
        for (int paletteIndex = 0; paletteIndex < count; paletteIndex++) {
            double[] vector = new double[MudPhysicsParameter.COUNT];
            for (int parameterIndex = 0; parameterIndex < vector.length; parameterIndex++) {
                vector[parameterIndex] = buffer.readDouble();
            }
            result.add(vector);
        }
        return List.copyOf(result);
    }

    private static List<double[]> readValuePalette(
            RegistryFriendlyByteBuf buffer, List<double[]> baselines) {
        int count = checkedPaletteCount(buffer.readVarInt(), "value");
        List<double[]> result = new ArrayList<>(count);
        for (int paletteIndex = 0; paletteIndex < count; paletteIndex++) {
            double[] vector = Arrays.copyOf(
                    paletteEntry(baselines, buffer.readVarInt(), "baseline"),
                    MudPhysicsParameter.COUNT);
            int changedCount = buffer.readVarInt();
            if (changedCount < 0 || changedCount > MudPhysicsParameter.COUNT) {
                throw new IllegalArgumentException(
                        "Invalid mud tuning changed value count " + changedCount);
            }
            int previousIndex = -1;
            for (int changed = 0; changed < changedCount; changed++) {
                int parameterIndex = buffer.readVarInt();
                if (parameterIndex <= previousIndex || parameterIndex >= vector.length) {
                    throw new IllegalArgumentException(
                            "Invalid mud tuning parameter index " + parameterIndex);
                }
                vector[parameterIndex] = buffer.readDouble();
                previousIndex = parameterIndex;
            }
            result.add(vector);
        }
        return List.copyOf(result);
    }

    private static void writeBaselinePalette(
            RegistryFriendlyByteBuf buffer, List<double[]> baselines) {
        buffer.writeVarInt(baselines.size());
        for (double[] vector : baselines) {
            requireVector(vector);
            for (double value : vector) {
                buffer.writeDouble(value);
            }
        }
    }

    private static void writeValuePalette(
            RegistryFriendlyByteBuf buffer, List<ValueVector> values) {
        buffer.writeVarInt(values.size());
        for (ValueVector entry : values) {
            buffer.writeVarInt(entry.baselineIndex);
            double[] baseline = entry.baseline;
            double[] vector = entry.values;
            int changedCount = 0;
            for (int index = 0; index < MudPhysicsParameter.COUNT; index++) {
                if (Double.doubleToLongBits(vector[index])
                        != Double.doubleToLongBits(baseline[index])) {
                    changedCount++;
                }
            }
            buffer.writeVarInt(changedCount);
            for (int index = 0; index < MudPhysicsParameter.COUNT; index++) {
                if (Double.doubleToLongBits(vector[index])
                        != Double.doubleToLongBits(baseline[index])) {
                    buffer.writeVarInt(index);
                    buffer.writeDouble(vector[index]);
                }
            }
        }
    }

    private static int checkedPaletteCount(int count, String kind) {
        if (count < 0 || count > MAX_VECTOR_PALETTE) {
            throw new IllegalArgumentException(
                    "Invalid mud tuning " + kind + " palette count " + count);
        }
        return count;
    }

    private static double[] paletteEntry(List<double[]> palette, int index, String kind) {
        if (index < 0 || index >= palette.size()) {
            throw new IllegalArgumentException(
                    "Invalid mud tuning " + kind + " palette index " + index);
        }
        return palette.get(index);
    }

    private static void requireVector(double[] vector) {
        if (vector == null || vector.length != MudPhysicsParameter.COUNT) {
            throw new IllegalArgumentException("Invalid mud tuning vector length");
        }
    }

    @Override
    public Type<MudTuningSessionPayload> type() {
        return TYPE;
    }

    public record MediumProfile(MudTuningObjectId objectId, int blockCount,
            boolean anyLocal, boolean allLocal,
            int blockVariant, int blockHeight, boolean shapeMixed,
            int representativeStateId, int capabilities,
            double[] values, double[] resetValues) {
    }

    private record VectorKey(long[] bits) {
        private static VectorKey of(double[] vector) {
            requireVector(vector);
            long[] bits = new long[vector.length];
            for (int index = 0; index < vector.length; index++) {
                bits[index] = Double.doubleToLongBits(vector[index]);
            }
            return new VectorKey(bits);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof VectorKey key && Arrays.equals(bits, key.bits);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bits);
        }
    }

    private record ValueVector(int baselineIndex, double[] baseline, double[] values) {
    }

    private record Palette(List<double[]> baselines, Map<VectorKey, Integer> baselineIndex,
            List<ValueVector> values, Map<VectorKey, Integer> valueIndex) {
        private static Palette create(List<MediumProfile> profiles) {
            if (profiles.size() > MAX_OBJECTS) {
                throw new IllegalArgumentException(
                        "Invalid mud tuning profile count " + profiles.size());
            }
            Map<VectorKey, Integer> baselineIndex = new LinkedHashMap<>();
            List<double[]> baselines = new ArrayList<>();
            for (MediumProfile profile : profiles) {
                addVector(profile.resetValues, baselines, baselineIndex);
            }

            Map<VectorKey, Integer> valueIndex = new LinkedHashMap<>();
            List<ValueVector> values = new ArrayList<>();
            for (MediumProfile profile : profiles) {
                VectorKey key = VectorKey.of(profile.values);
                if (valueIndex.containsKey(key)) {
                    continue;
                }
                int baseline = baselineIndex.get(VectorKey.of(profile.resetValues));
                valueIndex.put(key, values.size());
                values.add(new ValueVector(
                        baseline, baselines.get(baseline), profile.values));
            }
            return new Palette(List.copyOf(baselines), Map.copyOf(baselineIndex),
                    List.copyOf(values), Map.copyOf(valueIndex));
        }

        private static void addVector(double[] vector, List<double[]> vectors,
                Map<VectorKey, Integer> index) {
            VectorKey key = VectorKey.of(vector);
            if (!index.containsKey(key)) {
                index.put(key, vectors.size());
                vectors.add(vector);
            }
        }
    }
}
