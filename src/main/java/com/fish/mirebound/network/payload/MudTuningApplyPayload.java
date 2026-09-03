package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudTuningAnchor;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.mud.tuning.MudTuningObjectId;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudTuningApplyPayload(MudTuningScope scope, MudTuningObjectId objectId, MudTuningAnchor first,
        MudTuningAnchor second, int blockVariant, int blockHeight, boolean updateShape,
        boolean followWorld, double[] values, boolean[] changed) implements CustomPacketPayload {
    public static final Type<MudTuningApplyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_tuning_apply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningApplyPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudTuningApplyPayload decode(RegistryFriendlyByteBuf buffer) {
                    MudTuningScope scope = MudTuningScope.byId(buffer.readVarInt());
                    MudTuningObjectId objectId = MudTuningObjectId.read(buffer);
                    MudTuningAnchor first = MudTuningAnchor.read(buffer);
                    MudTuningAnchor second = MudTuningAnchor.read(buffer);
                    int variant = buffer.readVarInt();
                    int height = buffer.readVarInt();
                    boolean updateShape = buffer.readBoolean();
                    boolean followWorld = buffer.readBoolean();
                    int length = buffer.readVarInt();
                    if (length != MudPhysicsParameter.COUNT) {
                        throw new IllegalArgumentException("Invalid mud tuning update length " + length);
                    }
                    double[] values = new double[length];
                    boolean[] changed = new boolean[length];
                    for (int index = 0; index < length; index++) {
                        values[index] = buffer.readDouble();
                        changed[index] = buffer.readBoolean();
                    }
                    return new MudTuningApplyPayload(scope, objectId, first, second,
                            variant, height, updateShape, followWorld, values, changed);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudTuningApplyPayload payload) {
                    buffer.writeVarInt(payload.scope.ordinal());
                    payload.objectId.write(buffer);
                    MudTuningAnchor.write(buffer, payload.first);
                    MudTuningAnchor.write(buffer, payload.second);
                    buffer.writeVarInt(payload.blockVariant);
                    buffer.writeVarInt(payload.blockHeight);
                    buffer.writeBoolean(payload.updateShape);
                    buffer.writeBoolean(payload.followWorld);
                    buffer.writeVarInt(payload.values.length);
                    for (int index = 0; index < payload.values.length; index++) {
                        buffer.writeDouble(payload.values[index]);
                        buffer.writeBoolean(index < payload.changed.length && payload.changed[index]);
                    }
                }
            };

    @Override
    public Type<MudTuningApplyPayload> type() {
        return TYPE;
    }
}
