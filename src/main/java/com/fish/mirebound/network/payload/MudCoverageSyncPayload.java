package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCapeLayout;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public record MudCoverageSyncPayload(int entityId, int coveragePatternSeed,
        int coveragePermille, int mediumId, int visionPermille,
        byte[] packedVisionCoverage, byte[] packedVisionMedium,
        long[] packedVisionVisualSource,
        byte[] packedSurfaceCoverage, byte[] packedSurfaceMedium, int[] packedSurfaceAppearance,
        long[] packedSurfaceVisualSource,
        byte[] packedCapeCoverage, byte[] packedCapeMedium, int[] packedCapeAppearance,
        long[] packedCapeVisualSource) implements CustomPacketPayload {
    public static final Type<MudCoverageSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_coverage"));
    private static final int COVERAGE_LEVEL_MAX = 255;
    private static final int VISION_COVERAGE_PACKED_LENGTH = MudBodyPart.VISION_COUNT;
    private static final int COVERAGE_PACKED_LENGTH = MudSurfaceLayout.CELL_COUNT;
    public static final StreamCodec<RegistryFriendlyByteBuf, MudCoverageSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MudCoverageSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            return new MudCoverageSyncPayload(
                    buffer.readVarInt(),
                    buffer.readInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readByteArray(VISION_COVERAGE_PACKED_LENGTH),
                    buffer.readByteArray(VISION_COVERAGE_PACKED_LENGTH),
                    buffer.readLongArray(new long[0], VISION_COVERAGE_PACKED_LENGTH),
                    buffer.readByteArray(COVERAGE_PACKED_LENGTH),
                    buffer.readByteArray(COVERAGE_PACKED_LENGTH),
                    buffer.readVarIntArray(COVERAGE_PACKED_LENGTH),
                    buffer.readLongArray(new long[0], COVERAGE_PACKED_LENGTH),
                    buffer.readByteArray(MudCapeLayout.CELL_COUNT),
                    buffer.readByteArray(MudCapeLayout.CELL_COUNT),
                    buffer.readVarIntArray(MudCapeLayout.CELL_COUNT),
                    buffer.readLongArray(new long[0], MudCapeLayout.CELL_COUNT));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MudCoverageSyncPayload payload) {
            buffer.writeVarInt(payload.entityId);
            buffer.writeInt(payload.coveragePatternSeed);
            buffer.writeVarInt(payload.coveragePermille);
            buffer.writeVarInt(payload.mediumId);
            buffer.writeVarInt(payload.visionPermille);
            buffer.writeByteArray(payload.packedVisionCoverage);
            buffer.writeByteArray(payload.packedVisionMedium);
            buffer.writeLongArray(payload.packedVisionVisualSource);
            buffer.writeByteArray(payload.packedSurfaceCoverage);
            buffer.writeByteArray(payload.packedSurfaceMedium);
            buffer.writeVarIntArray(payload.packedSurfaceAppearance);
            buffer.writeLongArray(payload.packedSurfaceVisualSource);
            buffer.writeByteArray(payload.packedCapeCoverage);
            buffer.writeByteArray(payload.packedCapeMedium);
            buffer.writeVarIntArray(payload.packedCapeAppearance);
            buffer.writeLongArray(payload.packedCapeVisualSource);
        }
    };

    public MudCoverageSyncPayload(int entityId, int coveragePermille, int mediumId, int visionPermille,
            byte[] packedVisionCoverage, byte[] packedVisionMedium,
            byte[] packedSurfaceCoverage, byte[] packedSurfaceMedium,
            byte[] packedCapeCoverage, byte[] packedCapeMedium) {
        this(entityId, 0, coveragePermille, mediumId, visionPermille,
                packedVisionCoverage, packedVisionMedium,
                packedSurfaceCoverage, packedSurfaceMedium,
                packedCapeCoverage, packedCapeMedium);
    }

    public MudCoverageSyncPayload(int entityId, int coveragePatternSeed,
            int coveragePermille, int mediumId, int visionPermille,
            byte[] packedVisionCoverage, byte[] packedVisionMedium,
            byte[] packedSurfaceCoverage, byte[] packedSurfaceMedium,
            byte[] packedCapeCoverage, byte[] packedCapeMedium) {
        this(entityId, coveragePatternSeed, coveragePermille, mediumId, visionPermille,
                packedVisionCoverage, packedVisionMedium,
                new long[VISION_COVERAGE_PACKED_LENGTH],
                packedSurfaceCoverage, packedSurfaceMedium, new int[COVERAGE_PACKED_LENGTH],
                new long[COVERAGE_PACKED_LENGTH],
                packedCapeCoverage, packedCapeMedium, new int[MudCapeLayout.CELL_COUNT],
                new long[MudCapeLayout.CELL_COUNT]);
    }

    public SinkingMedium medium() {
        return SinkingMedium.byId(mediumId);
    }

    public static byte[] packSurfaces(float[] surfaceCoverage) {
        byte[] packed = new byte[COVERAGE_PACKED_LENGTH];
        for (int i = 0; i < COVERAGE_PACKED_LENGTH; i++) {
            packed[i] = (byte) packCoverageLevel(i < surfaceCoverage.length ? surfaceCoverage[i] : 0.0F);
        }
        return packed;
    }

    public static byte[] packVision(float[] visionCoverage) {
        byte[] packed = new byte[VISION_COVERAGE_PACKED_LENGTH];
        for (int i = 0; i < VISION_COVERAGE_PACKED_LENGTH; i++) {
            packed[i] = (byte) packCoverageLevel(i < visionCoverage.length ? visionCoverage[i] : 0.0F);
        }
        return packed;
    }

    public static byte[] packCape(float[] capeCoverage) {
        byte[] packed = new byte[MudCapeLayout.CELL_COUNT];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = (byte) packCoverageLevel(i < capeCoverage.length ? capeCoverage[i] : 0.0F);
        }
        return packed;
    }

    public static byte[] packSurfaceMedium(byte[] surfaceMedium) {
        return packMediumIds(surfaceMedium, MudSurfaceLayout.CELL_COUNT);
    }

    public static byte[] packVisionMedium(byte[] visionMedium) {
        return packMediumIds(visionMedium, VISION_COVERAGE_PACKED_LENGTH);
    }

    public static byte[] packCapeMedium(byte[] capeMedium) {
        return packMediumIds(capeMedium, MudCapeLayout.CELL_COUNT);
    }

    public int surfacePixelAppearance(MudBodyPart part, MudSurface surface, int row, int column) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        return index < packedSurfaceAppearance.length ? packedSurfaceAppearance[index] : 0;
    }

    public long surfacePixelVisualSource(
            MudBodyPart part, MudSurface surface, int row, int column) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        return index < packedSurfaceVisualSource.length
                ? packedSurfaceVisualSource[index] : 0L;
    }

    public int capePixelAppearance(MudCapeLayout.Side side, int row, int column) {
        int index = MudCapeLayout.index(side, row, column);
        return index < packedCapeAppearance.length ? packedCapeAppearance[index] : 0;
    }

    public long capePixelVisualSource(MudCapeLayout.Side side, int row, int column) {
        int index = MudCapeLayout.index(side, row, column);
        return index < packedCapeVisualSource.length
                ? packedCapeVisualSource[index] : 0L;
    }

    public int capePixelCoveragePermille(int row, int column) {
        int front = capePixelCoveragePermille(MudCapeLayout.Side.OUTER, row, column);
        int back = capePixelCoveragePermille(MudCapeLayout.Side.INNER, row, column);
        return Math.max(front, back);
    }

    public int capePixelCoveragePermille(MudCapeLayout.Side side, int row, int column) {
        int index = MudCapeLayout.index(side, row, column);
        return coveragePermille(index < packedCapeCoverage.length ? packedCapeCoverage[index] & 0xFF : 0);
    }

    public SinkingMedium capePixelMedium(int row, int column) {
        MudCapeLayout.Side side = capePixelCoveragePermille(MudCapeLayout.Side.INNER, row, column)
                > capePixelCoveragePermille(MudCapeLayout.Side.OUTER, row, column)
                        ? MudCapeLayout.Side.INNER
                        : MudCapeLayout.Side.OUTER;
        return capePixelMedium(side, row, column);
    }

    public SinkingMedium capePixelMedium(MudCapeLayout.Side side, int row, int column) {
        int index = MudCapeLayout.index(side, row, column);
        return SinkingMedium.byId(index < packedCapeMedium.length
                ? packedCapeMedium[index] & 0xFF : SinkingMedium.MUD.id());
    }

    private static byte[] packMediumIds(byte[] mediumIds, int count) {
        byte[] packed = new byte[count];
        for (int i = 0; i < count; i++) {
            int mediumId = i < mediumIds.length ? mediumIds[i] & 0xFF : SinkingMedium.MUD.id();
            packed[i] = (byte) (mediumId < SinkingMedium.COUNT ? mediumId : SinkingMedium.MUD.id());
        }
        return packed;
    }

    public int visionCoveragePermille(int band, int lane) {
        return coveragePermille(visionCoverageLevel(band, lane));
    }

    public SinkingMedium visionMedium(int band, int lane) {
        return SinkingMedium.byId(visionMediumId(band, lane));
    }

    public long visionVisualSource(int band, int lane) {
        int index = visionIndex(band, lane);
        return index >= 0 && index < packedVisionVisualSource.length
                ? packedVisionVisualSource[index] : 0L;
    }

    public int partCoveragePermille(MudBodyPart part) {
        int max = 0;
        for (int band = 0; band < MudBodyPart.BANDS; band++) {
            max = Math.max(max, bandCoverageLevel(part, band));
        }
        return coveragePermille(max);
    }

    public int bandCoveragePermille(MudBodyPart part, int band) {
        return coveragePermille(bandCoverageLevel(part, band));
    }

    public int surfaceCoveragePermille(MudBodyPart part, int band, MudSurface surface) {
        return coveragePermille(surfaceCoverageLevel(part, band, surface));
    }

    private int bandCoverageLevel(MudBodyPart part, int band) {
        int max = 0;
        for (MudSurface surface : MudSurface.values()) {
            MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
            for (int row = 0; row < face.height(); row++) {
                if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                    continue;
                }
                for (int column = 0; column < face.width(); column++) {
                    max = Math.max(max, surfacePixelCoverageLevel(part, surface, row, column));
                }
            }
        }
        return max;
    }

    public int surfaceCoveragePermille(MudBodyPart part, int band, MudSurface surface, int lane) {
        return coveragePermille(surfaceCoverageLevel(part, band, surface, lane));
    }

    public SinkingMedium surfaceMedium(MudBodyPart part, int band, MudSurface surface, int lane) {
        return SinkingMedium.byId(surfaceMediumId(part, band, surface, lane));
    }

    private int surfaceCoverageLevel(MudBodyPart part, int band, MudSurface surface) {
        int max = 0;
        for (int lane = 0; lane < MudBodyPart.SURFACE_LANES; lane++) {
            max = Math.max(max, surfaceCoverageLevel(part, band, surface, lane));
        }
        return max;
    }

    private int surfaceCoverageLevel(MudBodyPart part, int band, MudSurface surface, int lane) {
        int max = 0;
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        for (int row = 0; row < face.height(); row++) {
            if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                continue;
            }
            for (int column = 0; column < face.width(); column++) {
                if (MudSurfaceLayout.legacyLane(part, surface, column) == lane) {
                    max = Math.max(max, surfacePixelCoverageLevel(part, surface, row, column));
                }
            }
        }
        return max;
    }

    private int visionCoverageLevel(int band, int lane) {
        int index = visionIndex(band, lane);
        if (index < 0 || index >= VISION_COVERAGE_PACKED_LENGTH || packedVisionCoverage.length <= index) {
            return 0;
        }

        return packedVisionCoverage[index] & 0xFF;
    }

    private int surfaceMediumId(MudBodyPart part, int band, MudSurface surface, int lane) {
        int bestLevel = 0;
        int bestMedium = SinkingMedium.MUD.id();
        MudSurfaceLayout.Face face = MudSurfaceLayout.face(part, surface);
        for (int row = 0; row < face.height(); row++) {
            if (MudSurfaceLayout.legacyBand(part, surface, row) != band) {
                continue;
            }
            for (int column = 0; column < face.width(); column++) {
                if (MudSurfaceLayout.legacyLane(part, surface, column) != lane) {
                    continue;
                }
                int level = surfacePixelCoverageLevel(part, surface, row, column);
                if (level > bestLevel) {
                    bestLevel = level;
                    bestMedium = surfacePixelMediumId(part, surface, row, column);
                }
            }
        }
        return bestMedium;
    }

    private int visionMediumId(int band, int lane) {
        int index = visionIndex(band, lane);
        if (index < 0 || index >= VISION_COVERAGE_PACKED_LENGTH || packedVisionMedium.length <= index) {
            return SinkingMedium.MUD.id();
        }
        return packedVisionMedium[index] & 0xFF;
    }

    public int surfacePixelCoveragePermille(MudBodyPart part, MudSurface surface, int row, int column) {
        return coveragePermille(surfacePixelCoverageLevel(part, surface, row, column));
    }

    public SinkingMedium surfacePixelMedium(MudBodyPart part, MudSurface surface, int row, int column) {
        return SinkingMedium.byId(surfacePixelMediumId(part, surface, row, column));
    }

    private int surfacePixelCoverageLevel(MudBodyPart part, MudSurface surface, int row, int column) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        return index < packedSurfaceCoverage.length ? packedSurfaceCoverage[index] & 0xFF : 0;
    }

    private int surfacePixelMediumId(MudBodyPart part, MudSurface surface, int row, int column) {
        int index = MudSurfaceLayout.cellIndex(part, surface, row, column);
        if (packedSurfaceMedium.length <= index) {
            return SinkingMedium.MUD.id();
        }
        return packedSurfaceMedium[index] & 0xFF;
    }

    private static int visionIndex(int band, int lane) {
        return Mth.clamp(band, 0, MudBodyPart.VISION_BANDS - 1) * MudBodyPart.VISION_LANES
                + Mth.clamp(lane, 0, MudBodyPart.VISION_LANES - 1);
    }

    public static int packCoverageLevel(float coverage) {
        float normalized = Mth.clamp(coverage, 0.0F, 1.0F);
        return Mth.clamp(Math.round(Mth.sqrt(normalized) * COVERAGE_LEVEL_MAX), 0, COVERAGE_LEVEL_MAX);
    }

    public static float unpackCoverageLevel(int level) {
        float normalized = Mth.clamp(level, 0, COVERAGE_LEVEL_MAX) / (float) COVERAGE_LEVEL_MAX;
        return normalized * normalized;
    }

    private static int coveragePermille(int level) {
        return Math.round(unpackCoverageLevel(level) * 1000.0F);
    }

    @Override
    public Type<MudCoverageSyncPayload> type() {
        return TYPE;
    }
}
