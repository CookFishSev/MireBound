package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudTuningAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MudTuningSelectionPayload(boolean hasFirst, MudTuningAnchor first, boolean hasSecond,
        MudTuningAnchor second, SelectionSummary summary,
        List<HighlightGroup> highlightGroups) implements CustomPacketPayload {
    public static final int MAX_HIGHLIGHT_GROUPS = 64;
    public static final int MAX_HIGHLIGHT_PRIMITIVES = 4_096;
    public static final Type<MudTuningSelectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_tuning_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudTuningSelectionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudTuningSelectionPayload decode(RegistryFriendlyByteBuf buffer) {
                    boolean hasFirst = buffer.readBoolean();
                    MudTuningAnchor first = MudTuningAnchor.read(buffer);
                    boolean hasSecond = buffer.readBoolean();
                    MudTuningAnchor second = MudTuningAnchor.read(buffer);
                    SelectionSummary summary = new SelectionSummary(
                            buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
                    int groupCount = buffer.readVarInt();
                    if (groupCount < 0 || groupCount > MAX_HIGHLIGHT_GROUPS) {
                        throw new IllegalArgumentException("Invalid mud tuning highlight group count " + groupCount);
                    }
                    int total = 0;
                    List<HighlightGroup> groups = new ArrayList<>(groupCount);
                    for (int index = 0; index < groupCount; index++) {
                        HighlightKind kind = HighlightKind.byId(buffer.readVarInt());
                        UUID subLevelId = buffer.readUUID();
                        int positionCount = buffer.readVarInt();
                        if (positionCount < 0 || positionCount > MAX_HIGHLIGHT_PRIMITIVES) {
                            throw new IllegalArgumentException(
                                    "Invalid mud tuning highlight position count " + positionCount);
                        }
                        long[] positions = new long[positionCount];
                        for (int positionIndex = 0; positionIndex < positionCount; positionIndex++) {
                            positions[positionIndex] = buffer.readLong();
                        }
                        int edgeCount = buffer.readVarInt();
                        if (edgeCount < 0 || edgeCount > MAX_HIGHLIGHT_PRIMITIVES) {
                            throw new IllegalArgumentException(
                                    "Invalid mud tuning highlight edge count " + edgeCount);
                        }
                        long[] edgeCorners = new long[edgeCount];
                        byte[] edgeAxes = new byte[edgeCount];
                        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                            edgeCorners[edgeIndex] = buffer.readLong();
                            edgeAxes[edgeIndex] = buffer.readByte();
                        }
                        total += positions.length + edgeCount;
                        if (total > MAX_HIGHLIGHT_PRIMITIVES) {
                            throw new IllegalArgumentException("Too many mud tuning highlight positions");
                        }
                        groups.add(new HighlightGroup(
                                kind, subLevelId, positions, edgeCorners, edgeAxes));
                    }
                    return new MudTuningSelectionPayload(
                            hasFirst, first, hasSecond, second, summary, List.copyOf(groups));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudTuningSelectionPayload payload) {
                    buffer.writeBoolean(payload.hasFirst);
                    MudTuningAnchor.write(buffer, payload.first);
                    buffer.writeBoolean(payload.hasSecond);
                    MudTuningAnchor.write(buffer, payload.second);
                    buffer.writeVarLong(payload.summary.volume);
                    buffer.writeVarInt(payload.summary.convertible);
                    buffer.writeVarInt(payload.summary.adaptive);
                    buffer.writeVarInt(payload.summary.mud);
                    buffer.writeVarInt(payload.summary.unsupported);
                    buffer.writeVarInt(payload.summary.unloaded);
                    if (payload.highlightGroups.size() > MAX_HIGHLIGHT_GROUPS) {
                        throw new IllegalArgumentException("Too many mud tuning highlight groups");
                    }
                    buffer.writeVarInt(payload.highlightGroups.size());
                    int total = 0;
                    for (HighlightGroup group : payload.highlightGroups) {
                        total += group.positions.length + group.edgeCorners.length;
                        if (total > MAX_HIGHLIGHT_PRIMITIVES) {
                            throw new IllegalArgumentException("Too many mud tuning highlight positions");
                        }
                        buffer.writeVarInt(group.kind.ordinal());
                        buffer.writeUUID(group.subLevelId);
                        buffer.writeVarInt(group.positions.length);
                        for (long position : group.positions) {
                            buffer.writeLong(position);
                        }
                        buffer.writeVarInt(group.edgeCorners.length);
                        for (int edgeIndex = 0; edgeIndex < group.edgeCorners.length; edgeIndex++) {
                            buffer.writeLong(group.edgeCorners[edgeIndex]);
                            buffer.writeByte(group.edgeAxes[edgeIndex]);
                        }
                    }
                }
            };

    @Override
    public Type<MudTuningSelectionPayload> type() {
        return TYPE;
    }

    public record HighlightGroup(HighlightKind kind, UUID subLevelId, long[] positions,
            long[] edgeCorners, byte[] edgeAxes) {
        public HighlightGroup {
            if (kind == null || subLevelId == null || positions == null
                    || edgeCorners == null || edgeAxes == null
                    || edgeCorners.length != edgeAxes.length) {
                throw new IllegalArgumentException("Mud tuning highlights require a domain and positions");
            }
            for (byte axis : edgeAxes) {
                if (axis < 0 || axis > 2) {
                    throw new IllegalArgumentException("Invalid mud tuning highlight edge axis " + axis);
                }
            }
        }
    }

    public enum HighlightKind {
        MODIFIED_NATIVE,
        INCOMPATIBLE,
        CONVERTED_DEFAULT,
        CONVERTED_MODIFIED,
        MODIFIED_NATIVE_FLOW,
        MODIFIED_NATIVE_FLOW_MIXED;

        private static HighlightKind byId(int id) {
            HighlightKind[] values = values();
            if (id < 0 || id >= values.length) {
                throw new IllegalArgumentException("Invalid mud tuning highlight kind " + id);
            }
            return values[id];
        }
    }

    public record SelectionSummary(long volume, int convertible, int adaptive,
            int mud, int unsupported, int unloaded) {
        public static final SelectionSummary EMPTY = new SelectionSummary(0L, 0, 0, 0, 0, 0);
    }
}
