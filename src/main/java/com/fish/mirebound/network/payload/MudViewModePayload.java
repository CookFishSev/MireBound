package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Synchronizes client-only detached-camera state so it cannot create mud contact. */
public record MudViewModePayload(boolean externalCamera) implements CustomPacketPayload {
    public static final Type<MudViewModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_view_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudViewModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    MudViewModePayload::externalCamera,
                    MudViewModePayload::new);

    @Override
    public Type<MudViewModePayload> type() {
        return TYPE;
    }
}
