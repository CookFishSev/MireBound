package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sparse follow-up to {@link MudCoverageSyncPayload}; indices are sorted and absolute. */
public record MudCoverageDeltaPayload(
        int entityId,
        int coveragePatternSeed,
        int coveragePermille,
        int mediumId,
        int visionPermille,
        int[] surfaceIndices,
        byte[] surfaceCoverage,
        byte[] surfaceMedium,
        int[] surfaceAppearance,
        long[] surfaceVisualSource,
        int[] capeIndices,
        byte[] capeCoverage,
        byte[] capeMedium,
        int[] capeAppearance,
        long[] capeVisualSource,
        int[] visionIndices,
        byte[] visionCoverage,
        byte[] visionMedium,
        long[] visionVisualSource) implements CustomPacketPayload {
    public static final Type<MudCoverageDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_coverage_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MudCoverageDeltaPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudCoverageDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    int coveragePatternSeed = buffer.readInt();
                    int coveragePermille = buffer.readVarInt();
                    int mediumId = buffer.readVarInt();
                    int visionPermille = buffer.readVarInt();
                    CellChanges surfaces = readChanges(buffer, MudSurfaceLayout.CELL_COUNT, true);
                    CellChanges capes = readChanges(buffer, MudCapeLayout.CELL_COUNT, true);
                    CellChanges vision = readChanges(buffer, MudBodyPart.VISION_COUNT, false);
                    return new MudCoverageDeltaPayload(
                            entityId, coveragePatternSeed, coveragePermille, mediumId, visionPermille,
                            surfaces.indices, surfaces.coverage, surfaces.medium,
                            surfaces.appearance, surfaces.visualSource,
                            capes.indices, capes.coverage, capes.medium,
                            capes.appearance, capes.visualSource,
                            vision.indices, vision.coverage, vision.medium,
                            vision.visualSource);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudCoverageDeltaPayload payload) {
                    buffer.writeVarInt(payload.entityId);
                    buffer.writeInt(payload.coveragePatternSeed);
                    buffer.writeVarInt(payload.coveragePermille);
                    buffer.writeVarInt(payload.mediumId);
                    buffer.writeVarInt(payload.visionPermille);
                    writeChanges(buffer, payload.surfaceIndices, payload.surfaceCoverage,
                            payload.surfaceMedium, payload.surfaceAppearance,
                            payload.surfaceVisualSource, MudSurfaceLayout.CELL_COUNT, true);
                    writeChanges(buffer, payload.capeIndices, payload.capeCoverage,
                            payload.capeMedium, payload.capeAppearance,
                            payload.capeVisualSource, MudCapeLayout.CELL_COUNT, true);
                    writeChanges(buffer, payload.visionIndices, payload.visionCoverage,
                            payload.visionMedium, null, payload.visionVisualSource,
                            MudBodyPart.VISION_COUNT, false);
                }
            };

    public MudCoverageDeltaPayload(
            int entityId, int coveragePermille, int mediumId, int visionPermille,
            int[] surfaceIndices, byte[] surfaceCoverage, byte[] surfaceMedium,
            int[] surfaceAppearance,
            int[] capeIndices, byte[] capeCoverage, byte[] capeMedium,
            int[] capeAppearance,
            int[] visionIndices, byte[] visionCoverage, byte[] visionMedium) {
        this(entityId, 0, coveragePermille, mediumId, visionPermille,
                surfaceIndices, surfaceCoverage, surfaceMedium, surfaceAppearance,
                capeIndices, capeCoverage, capeMedium, capeAppearance,
                visionIndices, visionCoverage, visionMedium);
    }

    public MudCoverageDeltaPayload(
            int entityId, int coveragePatternSeed,
            int coveragePermille, int mediumId, int visionPermille,
            int[] surfaceIndices, byte[] surfaceCoverage, byte[] surfaceMedium,
            int[] surfaceAppearance,
            int[] capeIndices, byte[] capeCoverage, byte[] capeMedium,
            int[] capeAppearance,
            int[] visionIndices, byte[] visionCoverage, byte[] visionMedium) {
        this(entityId, coveragePatternSeed, coveragePermille, mediumId, visionPermille,
                surfaceIndices, surfaceCoverage, surfaceMedium, surfaceAppearance,
                new long[surfaceIndices.length],
                capeIndices, capeCoverage, capeMedium, capeAppearance,
                new long[capeIndices.length],
                visionIndices, visionCoverage, visionMedium,
                new long[visionIndices.length]);
    }

    public SinkingMedium medium() {
        return SinkingMedium.byId(mediumId);
    }

    private static CellChanges readChanges(
            RegistryFriendlyByteBuf buffer, int maximum, boolean hasAppearance) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid mud coverage delta count " + count + "/" + maximum);
        }
        int[] indices = new int[count];
        byte[] coverage = new byte[count];
        byte[] medium = new byte[count];
        int[] appearance = hasAppearance ? new int[count] : new int[0];
        long[] visualSource = new long[count];
        int previous = -1;
        for (int offset = 0; offset < count; offset++) {
            int index = buffer.readVarInt();
            if (index <= previous || index < 0 || index >= maximum) {
                throw new IllegalArgumentException("Invalid mud coverage delta index " + index);
            }
            indices[offset] = index;
            coverage[offset] = buffer.readByte();
            int mediumId = buffer.readUnsignedByte();
            medium[offset] = (byte) (mediumId < SinkingMedium.COUNT
                    ? mediumId : SinkingMedium.MUD.id());
            if (hasAppearance) {
                appearance[offset] = buffer.readVarInt();
            }
            visualSource[offset] = buffer.readLong();
            previous = index;
        }
        return new CellChanges(indices, coverage, medium, appearance, visualSource);
    }

    private static void writeChanges(
            RegistryFriendlyByteBuf buffer,
            int[] indices,
            byte[] coverage,
            byte[] medium,
            int[] appearance,
            long[] visualSource,
            int maximum,
            boolean hasAppearance) {
        int count = Math.min(indices.length, Math.min(coverage.length, medium.length));
        if (hasAppearance) {
            count = Math.min(count, appearance == null ? 0 : appearance.length);
        }
        count = Math.min(count, visualSource == null ? 0 : visualSource.length);
        if (count > maximum) {
            throw new IllegalArgumentException("Too many mud coverage deltas " + count + "/" + maximum);
        }
        buffer.writeVarInt(count);
        int previous = -1;
        for (int offset = 0; offset < count; offset++) {
            int index = indices[offset];
            if (index <= previous || index < 0 || index >= maximum) {
                throw new IllegalArgumentException("Unsorted mud coverage delta index " + index);
            }
            buffer.writeVarInt(index);
            buffer.writeByte(coverage[offset]);
            buffer.writeByte(medium[offset]);
            if (hasAppearance) {
                buffer.writeVarInt(appearance[offset]);
            }
            buffer.writeLong(visualSource[offset]);
            previous = index;
        }
    }

    private record CellChanges(int[] indices, byte[] coverage, byte[] medium,
            int[] appearance, long[] visualSource) {
    }

    @Override
    public Type<MudCoverageDeltaPayload> type() {
        return TYPE;
    }
}
