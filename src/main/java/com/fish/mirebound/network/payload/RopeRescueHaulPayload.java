package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative rescue-haul input. */
public record RopeRescueHaulPayload(
        Operation operation, int ropeId, int segmentIndex,
        long sessionId, long sequence) implements CustomPacketPayload {
    public enum Operation {
        START(0),
        KEEP_ALIVE(1),
        STOP(2);

        private final int id;

        Operation(int id) {
            this.id = id;
        }

        private static Operation fromId(int id) {
            for (Operation operation : values()) {
                if (operation.id == id) {
                    return operation;
                }
            }
            throw new IllegalArgumentException("Unknown rescue haul operation: " + id);
        }
    }

    public static final Type<RopeRescueHaulPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_rescue_haul"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeRescueHaulPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RopeRescueHaulPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new RopeRescueHaulPayload(
                            Operation.fromId(buffer.readUnsignedByte()),
                            buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readLong(), buffer.readLong());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        RopeRescueHaulPayload payload) {
                    Operation operation = payload.operation() == null
                            ? Operation.STOP : payload.operation();
                    buffer.writeByte(operation.id);
                    buffer.writeVarInt(payload.ropeId());
                    buffer.writeVarInt(payload.segmentIndex());
                    buffer.writeLong(payload.sessionId());
                    buffer.writeLong(payload.sequence());
                }
            };

    public static RopeRescueHaulPayload start(
            int ropeId, int segmentIndex, long sessionId, long sequence) {
        return new RopeRescueHaulPayload(
                Operation.START, ropeId, segmentIndex, sessionId, sequence);
    }

    public static RopeRescueHaulPayload keepAlive(
            int ropeId, long sessionId, long sequence) {
        return new RopeRescueHaulPayload(
                Operation.KEEP_ALIVE, ropeId, -1, sessionId, sequence);
    }

    public static RopeRescueHaulPayload stop(
            int ropeId, long sessionId, long sequence) {
        return new RopeRescueHaulPayload(
                Operation.STOP, ropeId, -1, sessionId, sequence);
    }

    @Override
    public Type<RopeRescueHaulPayload> type() {
        return TYPE;
    }
}
