package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client input intent only; all mire outcomes remain server-authoritative. */
public record SculkMireInputPayload(float movementStrength, boolean jumping, boolean crouching)
        implements CustomPacketPayload {
    public static final Type<SculkMireInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "sculk_mire_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SculkMireInputPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SculkMireInputPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new SculkMireInputPayload(
                            buffer.readUnsignedByte() / 255.0F,
                            buffer.readBoolean(), buffer.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SculkMireInputPayload payload) {
                    buffer.writeByte(Math.round(Math.max(0.0F,
                            Math.min(1.0F, payload.movementStrength())) * 255.0F));
                    buffer.writeBoolean(payload.jumping());
                    buffer.writeBoolean(payload.crouching());
                }
            };

    @Override
    public Type<SculkMireInputPayload> type() {
        return TYPE;
    }
}
