package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudDebugSyncPayload(
        int entityId,
        boolean active,
        int mediumId,
        int depthMillis,
        int columnDepthMillis,
        int sinkLimitMillis,
        int remainingDepthMillis,
        int yBeforeMicros,
        int yAfterMicros,
        int horizontalSpeedMicros,
        int sinkStepMicros,
        int walkScalePermille,
        int verticalScalePermille,
        int holdTicks,
        int liftTicks,
        int stuckTicks,
        int agitationPermille,
        boolean physicalized) implements CustomPacketPayload {
    public static final Type<MudDebugSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudDebugSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MudDebugSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            return new MudDebugSyncPayload(
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MudDebugSyncPayload payload) {
            buffer.writeVarInt(payload.entityId);
            buffer.writeBoolean(payload.active);
            buffer.writeVarInt(payload.mediumId);
            buffer.writeVarInt(payload.depthMillis);
            buffer.writeVarInt(payload.columnDepthMillis);
            buffer.writeVarInt(payload.sinkLimitMillis);
            buffer.writeVarInt(payload.remainingDepthMillis);
            buffer.writeVarInt(payload.yBeforeMicros);
            buffer.writeVarInt(payload.yAfterMicros);
            buffer.writeVarInt(payload.horizontalSpeedMicros);
            buffer.writeVarInt(payload.sinkStepMicros);
            buffer.writeVarInt(payload.walkScalePermille);
            buffer.writeVarInt(payload.verticalScalePermille);
            buffer.writeVarInt(payload.holdTicks);
            buffer.writeVarInt(payload.liftTicks);
            buffer.writeVarInt(payload.stuckTicks);
            buffer.writeVarInt(payload.agitationPermille);
            buffer.writeBoolean(payload.physicalized);
        }
    };

    public SinkingMedium medium() {
        return SinkingMedium.byId(mediumId);
    }

    public double depth() {
        return depthMillis / 1000.0D;
    }

    public double columnDepth() {
        return columnDepthMillis / 1000.0D;
    }

    public double sinkLimit() {
        return sinkLimitMillis / 1000.0D;
    }

    public double remainingDepth() {
        return remainingDepthMillis / 1000.0D;
    }

    public double yBefore() {
        return yBeforeMicros / 10000.0D;
    }

    public double yAfter() {
        return yAfterMicros / 10000.0D;
    }

    public double horizontalSpeed() {
        return horizontalSpeedMicros / 10000.0D;
    }

    public double sinkStep() {
        return sinkStepMicros / 10000.0D;
    }

    public double walkScale() {
        return walkScalePermille / 1000.0D;
    }

    public double verticalScale() {
        return verticalScalePermille / 1000.0D;
    }

    public double agitation() {
        return agitationPermille / 1000.0D;
    }

    @Override
    public Type<MudDebugSyncPayload> type() {
        return TYPE;
    }
}
