package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** One lifecycle message for a mud-controlled item's anchor and optional stable pose. */
public record DroppedItemMudStatePayload(
        int entityId,
        UUID entityUuid,
        boolean anchored,
        boolean stablePresentation,
        int settleTicks,
        float maximumTiltDegrees,
        boolean sableAnchored) implements CustomPacketPayload {
    public static final Type<DroppedItemMudStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "dropped_item_mud_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DroppedItemMudStatePayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public DroppedItemMudStatePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new DroppedItemMudStatePayload(
                            buffer.readVarInt(),
                            buffer.readUUID(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            Mth.clamp(buffer.readVarInt(), 0, 40),
                            Mth.clamp(buffer.readFloat(), 0.0F, 45.0F),
                            buffer.readBoolean());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer, DroppedItemMudStatePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeUUID(payload.entityUuid());
                    buffer.writeBoolean(payload.anchored());
                    buffer.writeBoolean(payload.stablePresentation());
                    buffer.writeVarInt(Mth.clamp(payload.settleTicks(), 0, 40));
                    buffer.writeFloat(Mth.clamp(
                            payload.maximumTiltDegrees(), 0.0F, 45.0F));
                    buffer.writeBoolean(payload.sableAnchored());
                }
            };

    @Override
    public Type<DroppedItemMudStatePayload> type() {
        return TYPE;
    }
}
