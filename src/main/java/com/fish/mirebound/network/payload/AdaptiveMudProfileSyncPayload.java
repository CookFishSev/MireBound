package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPhysicsParameter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Synchronizes the universal converted-block behavior baseline. */
public record AdaptiveMudProfileSyncPayload(double[] values) implements CustomPacketPayload {
    public AdaptiveMudProfileSyncPayload {
        if (values == null || values.length != MudPhysicsParameter.COUNT) {
            throw new IllegalArgumentException("Invalid adaptive mud profile length");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Adaptive mud profile values must be finite");
            }
        }
        values = values.clone();
    }

    @Override
    public double[] values() {
        return values.clone();
    }

    public static final Type<AdaptiveMudProfileSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "adaptive_mud_profile_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdaptiveMudProfileSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AdaptiveMudProfileSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    int length = buffer.readVarInt();
                    if (length != MudPhysicsParameter.COUNT) {
                        throw new IllegalArgumentException(
                                "Invalid adaptive mud profile length " + length);
                    }
                    double[] values = new double[length];
                    for (int index = 0; index < length; index++) {
                        values[index] = buffer.readDouble();
                    }
                    return new AdaptiveMudProfileSyncPayload(values);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, AdaptiveMudProfileSyncPayload payload) {
                    double[] values = payload.values;
                    buffer.writeVarInt(values.length);
                    for (double value : values) {
                        buffer.writeDouble(value);
                    }
                }
            };

    @Override
    public Type<AdaptiveMudProfileSyncPayload> type() {
        return TYPE;
    }
}
