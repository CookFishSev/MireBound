package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;

public record MudPhysicsProfileSyncPayload(
        int mediumId,
        boolean openScreen,
        boolean editable,
        boolean customized,
        BlockPos blockPos,
        int blockVariant,
        int blockHeight,
        double[] values) implements CustomPacketPayload {
    public MudPhysicsProfileSyncPayload {
        if (blockPos == null) {
            throw new IllegalArgumentException("Physics profile block position cannot be null");
        }
        if (values == null || values.length != MudPhysicsParameter.COUNT) {
            throw new IllegalArgumentException("Physics profile must contain "
                    + MudPhysicsParameter.COUNT + " values");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Physics profile contains a non-finite value");
            }
        }
        values = Arrays.copyOf(values, values.length);
    }

    public static final Type<MudPhysicsProfileSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "physics_profile_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudPhysicsProfileSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudPhysicsProfileSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    int mediumId = buffer.readVarInt();
                    boolean openScreen = buffer.readBoolean();
                    boolean editable = buffer.readBoolean();
                    boolean customized = buffer.readBoolean();
                    BlockPos blockPos = buffer.readBlockPos();
                    int blockVariant = buffer.readVarInt();
                    int blockHeight = buffer.readVarInt();
                    int length = buffer.readVarInt();
                    if (length != MudPhysicsParameter.COUNT) {
                        throw new IllegalArgumentException("Invalid physics profile length " + length);
                    }
                    double[] values = new double[length];
                    for (int i = 0; i < length; i++) {
                        values[i] = buffer.readDouble();
                    }
                    return new MudPhysicsProfileSyncPayload(
                            mediumId, openScreen, editable, customized,
                            blockPos, blockVariant, blockHeight, values);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudPhysicsProfileSyncPayload payload) {
                    buffer.writeVarInt(payload.mediumId);
                    buffer.writeBoolean(payload.openScreen);
                    buffer.writeBoolean(payload.editable);
                    buffer.writeBoolean(payload.customized);
                    buffer.writeBlockPos(payload.blockPos);
                    buffer.writeVarInt(payload.blockVariant);
                    buffer.writeVarInt(payload.blockHeight);
                    buffer.writeVarInt(payload.values.length);
                    for (double value : payload.values) {
                        buffer.writeDouble(value);
                    }
                }
            };

    public SinkingMedium medium() {
        return SinkingMedium.byId(mediumId);
    }

    @Override
    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }

    @Override
    public Type<MudPhysicsProfileSyncPayload> type() {
        return TYPE;
    }
}
