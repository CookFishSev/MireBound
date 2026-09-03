package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client climb intent; rope contact and movement are validated by the server. */
public record RopeClimbInputPayload(boolean active, boolean jumping, boolean crouching)
        implements CustomPacketPayload {
    public static final Type<RopeClimbInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_climb_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeClimbInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RopeClimbInputPayload::active,
                    ByteBufCodecs.BOOL,
                    RopeClimbInputPayload::jumping,
                    ByteBufCodecs.BOOL,
                    RopeClimbInputPayload::crouching,
                    RopeClimbInputPayload::new);

    @Override
    public Type<RopeClimbInputPayload> type() {
        return TYPE;
    }
}
