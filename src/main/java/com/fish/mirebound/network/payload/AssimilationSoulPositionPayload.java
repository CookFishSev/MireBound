package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Low-frequency owner soul-camera position used only for server QTE range authority. */
public record AssimilationSoulPositionPayload(
        float x, float y, float z) implements CustomPacketPayload {
    public static final Type<AssimilationSoulPositionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "assimilation_soul_position"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssimilationSoulPositionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AssimilationSoulPositionPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new AssimilationSoulPositionPayload(
                            buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        AssimilationSoulPositionPayload payload) {
                    buffer.writeFloat(payload.x());
                    buffer.writeFloat(payload.y());
                    buffer.writeFloat(payload.z());
                }
            };

    @Override
    public Type<AssimilationSoulPositionPayload> type() {
        return TYPE;
    }
}
