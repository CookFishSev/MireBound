package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public record SwarmStateSyncPayload(
        int strengthPermille, boolean hasProfilePos, long profilePos, int mediumId)
        implements CustomPacketPayload {
    public static final Type<SwarmStateSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "swarm_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SwarmStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SwarmStateSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    int strength = buffer.readVarInt();
                    boolean hasProfilePos = buffer.readBoolean();
                    return new SwarmStateSyncPayload(
                            strength, hasProfilePos, hasProfilePos ? buffer.readLong() : 0L,
                            buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SwarmStateSyncPayload payload) {
                    buffer.writeVarInt(payload.strengthPermille());
                    buffer.writeBoolean(payload.hasProfilePos());
                    if (payload.hasProfilePos()) {
                        buffer.writeLong(payload.profilePos());
                    }
                    buffer.writeVarInt(payload.mediumId());
                }
            };

    public float strength() {
        return Mth.clamp(strengthPermille / 1000.0F, 0.0F, 1.0F);
    }

    @Override
    public Type<SwarmStateSyncPayload> type() {
        return TYPE;
    }
}
