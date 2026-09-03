package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.fish.mirebound.mud.SinkingMedium;

/** One owner-only cue for a mud ball that directly struck the local player. */
public record MudClodScreenImpactPayload(
        float intensity, long seed, int mediumId)
        implements CustomPacketPayload {
    public static final Type<MudClodScreenImpactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_clod_screen_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            MudClodScreenImpactPayload> STREAM_CODEC = new StreamCodec<>() {
                @Override
                public MudClodScreenImpactPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new MudClodScreenImpactPayload(
                            buffer.readFloat(), buffer.readLong(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        MudClodScreenImpactPayload payload) {
                    buffer.writeFloat(payload.intensity());
                    buffer.writeLong(payload.seed());
                    buffer.writeVarInt(payload.mediumId());
                }
            };

    public MudClodScreenImpactPayload {
        intensity = Mth.clamp(intensity, 0.0F, 1.0F);
        mediumId = Mth.clamp(
                mediumId, 0, SinkingMedium.COUNT - 1);
    }

    /** Compatibility constructor for callers that use the original mud-only packet. */
    public MudClodScreenImpactPayload(float intensity, long seed) {
        this(intensity, seed, SinkingMedium.MUD.id());
    }

    public SinkingMedium medium() {
        return SinkingMedium.byId(mediumId);
    }

    @Override
    public Type<MudClodScreenImpactPayload> type() {
        return TYPE;
    }
}
