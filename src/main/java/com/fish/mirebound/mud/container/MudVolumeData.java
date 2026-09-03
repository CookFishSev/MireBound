package com.fish.mirebound.mud.container;

import com.fish.mirebound.mud.SinkingMedium;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Persistent medium and 1/16-block volume carried by a mud container or block item. */
public record MudVolumeData(int mediumId, int pixels) {
    public static final int MAX_PIXELS = 16;
    public static final Codec<MudVolumeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("medium").forGetter(MudVolumeData::mediumId),
            Codec.INT.fieldOf("pixels").forGetter(MudVolumeData::pixels))
            .apply(instance, MudVolumeData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudVolumeData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudVolumeData decode(RegistryFriendlyByteBuf buffer) {
                    return new MudVolumeData(buffer.readVarInt(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudVolumeData value) {
                    buffer.writeVarInt(value.mediumId());
                    buffer.writeVarInt(value.pixels());
                }
            };

    public MudVolumeData {
        mediumId = Math.max(0, Math.min(SinkingMedium.COUNT - 1, mediumId));
        pixels = Math.max(1, Math.min(MAX_PIXELS, pixels));
    }

    public MudVolumeData(SinkingMedium medium, int pixels) {
        this(medium.id(), pixels);
    }

    public SinkingMedium medium() {
        return SinkingMedium.byId(mediumId);
    }
}
