package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server notification that a rope interaction was released by validation. */
public record RopeInteractionReleasePayload(int ropeId, boolean rescue, int segmentIndex)
        implements CustomPacketPayload {
    public static final Type<RopeInteractionReleasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID,
                    "rope_interaction_release"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeInteractionReleasePayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    RopeInteractionReleasePayload::ropeId,
                    ByteBufCodecs.BOOL,
                    RopeInteractionReleasePayload::rescue,
                    ByteBufCodecs.VAR_INT,
                    RopeInteractionReleasePayload::segmentIndex,
                    RopeInteractionReleasePayload::new);

    @Override
    public Type<RopeInteractionReleasePayload> type() {
        return TYPE;
    }
}
