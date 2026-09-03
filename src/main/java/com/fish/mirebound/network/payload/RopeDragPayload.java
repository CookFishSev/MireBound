package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.rope.RopeFrame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Client drag intent; the server remains authoritative for the rope pose. */
public record RopeDragPayload(
        boolean dragging,
        int ropeId,
        int segmentIndex,
        RopeFrame frame,
        Vec3 viewOrigin,
        Vec3 viewDirection,
        long inputSession,
        long inputSequence) implements CustomPacketPayload {
    public static final Type<RopeDragPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "rope_drag"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RopeDragPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RopeDragPayload decode(RegistryFriendlyByteBuf buffer) {
                    boolean dragging = buffer.readBoolean();
                    int ropeId = buffer.readVarInt();
                    int segmentIndex = buffer.readVarInt();
                    RopeFrame frame = readFrame(buffer);
                    Vec3 origin = readVector(buffer);
                    Vec3 direction = readVector(buffer);
                    long session = buffer.readLong();
                    long sequence = buffer.readLong();
                    return new RopeDragPayload(dragging, ropeId, segmentIndex, frame,
                            origin, direction, session, sequence);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        RopeDragPayload payload) {
                    buffer.writeBoolean(payload.dragging());
                    buffer.writeVarInt(payload.ropeId());
                    buffer.writeVarInt(payload.segmentIndex());
                    writeFrame(buffer, payload.frame());
                    writeVector(buffer, payload.viewOrigin());
                    writeVector(buffer, payload.viewDirection());
                    buffer.writeLong(payload.inputSession());
                    buffer.writeLong(payload.inputSequence());
                }
            };

    /** Compatibility constructor for tests and callers that only provide orientation. */
    public RopeDragPayload(boolean dragging, int ropeId, int segmentIndex, RopeFrame frame) {
        this(dragging, ropeId, segmentIndex, frame, Vec3.ZERO, Vec3.ZERO, 0L, 0L);
    }

    public static RopeDragPayload release(int ropeId, int segmentIndex) {
        return new RopeDragPayload(false, ropeId, segmentIndex, RopeFrame.IDENTITY);
    }

    public static RopeDragPayload release(int ropeId, int segmentIndex,
            long inputSession, long inputSequence) {
        return new RopeDragPayload(false, ropeId, segmentIndex, RopeFrame.IDENTITY,
                Vec3.ZERO, Vec3.ZERO, inputSession, inputSequence);
    }

    private static RopeFrame readFrame(RegistryFriendlyByteBuf buffer) {
        return RopeFrame.from(
                readVector(buffer, true), readVector(buffer, true), readVector(buffer, true));
    }

    private static Vec3 readVector(RegistryFriendlyByteBuf buffer) {
        return readVector(buffer, false);
    }

    private static Vec3 readVector(RegistryFriendlyByteBuf buffer, boolean floats) {
        return floats
                ? new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
                : new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static void writeFrame(RegistryFriendlyByteBuf buffer, RopeFrame frame) {
        RopeFrame used = frame == null ? RopeFrame.IDENTITY : frame;
        writeVector(buffer, used.x(), true);
        writeVector(buffer, used.y(), true);
        writeVector(buffer, used.z(), true);
    }

    private static void writeVector(RegistryFriendlyByteBuf buffer, Vec3 vector) {
        writeVector(buffer, vector, false);
    }

    private static void writeVector(RegistryFriendlyByteBuf buffer, Vec3 vector, boolean floats) {
        Vec3 used = finite(vector) ? vector : Vec3.ZERO;
        if (floats) {
            buffer.writeFloat((float) used.x);
            buffer.writeFloat((float) used.y);
            buffer.writeFloat((float) used.z);
        } else {
            buffer.writeDouble(used.x);
            buffer.writeDouble(used.y);
            buffer.writeDouble(used.z);
        }
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    @Override
    public Type<RopeDragPayload> type() {
        return TYPE;
    }
}
