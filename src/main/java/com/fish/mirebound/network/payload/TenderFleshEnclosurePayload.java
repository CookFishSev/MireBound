package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Compact server-authoritative state for one player's procedural flesh enclosure. */
public record TenderFleshEnclosurePayload(
        int entityId,
        boolean active,
        boolean retreating,
        int brokenMask,
        int pillarDamagePacked,
        int pillarRequiredHitsPacked,
        int cooldownTicks,
        float progress,
        double anchorX,
        double anchorY,
        double anchorZ,
        double playerX,
        double playerZ) implements CustomPacketPayload {
    public static final Type<TenderFleshEnclosurePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "tender_flesh_enclosure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TenderFleshEnclosurePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TenderFleshEnclosurePayload decode(RegistryFriendlyByteBuf buffer) {
                    return new TenderFleshEnclosurePayload(
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readUnsignedByte(),
                            buffer.readUnsignedShort(),
                            buffer.readUnsignedShort(),
                            buffer.readVarInt(),
                            buffer.readUnsignedByte() / 255.0F,
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        TenderFleshEnclosurePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.active());
                    buffer.writeBoolean(payload.retreating());
                    buffer.writeByte(payload.brokenMask());
                    buffer.writeShort(payload.pillarDamagePacked());
                    buffer.writeShort(payload.pillarRequiredHitsPacked());
                    buffer.writeVarInt(payload.cooldownTicks());
                    buffer.writeByte(Math.round(payload.progress() * 255.0F));
                    buffer.writeDouble(payload.anchorX());
                    buffer.writeDouble(payload.anchorY());
                    buffer.writeDouble(payload.anchorZ());
                    buffer.writeDouble(payload.playerX());
                    buffer.writeDouble(payload.playerZ());
                }
            };

    public TenderFleshEnclosurePayload {
        brokenMask &= 0x0F;
        pillarDamagePacked &= 0x0FFF;
        pillarRequiredHitsPacked &= 0x0FFF;
        cooldownTicks = Math.max(0, cooldownTicks);
        progress = Mth.clamp(progress, 0.0F, 1.0F);
    }

    @Override
    public Type<TenderFleshEnclosurePayload> type() {
        return TYPE;
    }
}
