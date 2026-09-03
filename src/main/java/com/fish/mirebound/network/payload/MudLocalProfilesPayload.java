package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Chunk-scoped local mud profiles used by client prediction and surface effects. */
public record MudLocalProfilesPayload(ResourceLocation dimension, int chunkX, int chunkZ,
        boolean replaceChunk, List<Palette> palettes, List<Entry> entries)
        implements CustomPacketPayload {
    public static final int MAX_PALETTES = 64;
    public static final int MAX_ENTRIES = 4096;
    public static final Type<MudLocalProfilesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_local_profiles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudLocalProfilesPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudLocalProfilesPayload decode(RegistryFriendlyByteBuf buffer) {
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int chunkX = buffer.readVarInt();
                    int chunkZ = buffer.readVarInt();
                    boolean replaceChunk = buffer.readBoolean();
                    int paletteCount = buffer.readVarInt();
                    if (paletteCount < 0 || paletteCount > MAX_PALETTES) {
                        throw new IllegalArgumentException("Invalid local mud palette count " + paletteCount);
                    }
                    List<Palette> palettes = new ArrayList<>(paletteCount);
                    for (int paletteIndex = 0; paletteIndex < paletteCount; paletteIndex++) {
                        int valueCount = buffer.readVarInt();
                        if (valueCount != MudPhysicsParameter.COUNT) {
                            throw new IllegalArgumentException("Invalid local mud value count " + valueCount);
                        }
                        float[] values = new float[valueCount];
                        for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                            values[valueIndex] = buffer.readFloat();
                        }
                        palettes.add(new Palette(values));
                    }
                    int entryCount = buffer.readVarInt();
                    if (entryCount < 0 || entryCount > MAX_ENTRIES) {
                        throw new IllegalArgumentException("Invalid local mud entry count " + entryCount);
                    }
                    List<Entry> entries = new ArrayList<>(entryCount);
                    for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                        entries.add(new Entry(
                                buffer.readLong(), buffer.readVarInt(), buffer.readVarInt()));
                    }
                    return new MudLocalProfilesPayload(dimension, chunkX, chunkZ, replaceChunk,
                            List.copyOf(palettes), List.copyOf(entries));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudLocalProfilesPayload payload) {
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeVarInt(payload.chunkX);
                    buffer.writeVarInt(payload.chunkZ);
                    buffer.writeBoolean(payload.replaceChunk);
                    buffer.writeVarInt(payload.palettes.size());
                    for (Palette palette : payload.palettes) {
                        float[] values = palette.values();
                        buffer.writeVarInt(values.length);
                        for (float value : values) {
                            buffer.writeFloat(value);
                        }
                    }
                    buffer.writeVarInt(payload.entries.size());
                    for (Entry entry : payload.entries) {
                        buffer.writeLong(entry.blockPos);
                        buffer.writeVarInt(entry.mediumId);
                        buffer.writeVarInt(entry.paletteIndex);
                    }
                }
            };

    @Override
    public Type<MudLocalProfilesPayload> type() {
        return TYPE;
    }

    public record Palette(float[] values) {
        public Palette {
            if (values == null || values.length != MudPhysicsParameter.COUNT) {
                throw new IllegalArgumentException("Invalid local mud palette value count");
            }
            for (float value : values) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException("Local mud palette values must be finite");
                }
            }
            values = values.clone();
        }

        @Override
        public float[] values() {
            return values.clone();
        }
    }

    public record Entry(long blockPos, int mediumId, int paletteIndex) {
    }

    public MudLocalProfilesPayload {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension");
        }
        palettes = palettes == null ? List.of() : List.copyOf(palettes);
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (palettes.size() > MAX_PALETTES) {
            throw new IllegalArgumentException("Too many local mud palettes");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many local mud entries");
        }
        for (Entry entry : entries) {
            if (entry == null || entry.mediumId() < 0
                    || entry.mediumId() >= SinkingMedium.COUNT
                    || entry.paletteIndex() < 0
                    || entry.paletteIndex() >= palettes.size()) {
                throw new IllegalArgumentException("Invalid local mud profile entry");
            }
        }
    }
}
