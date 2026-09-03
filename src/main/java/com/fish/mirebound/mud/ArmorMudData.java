package com.fish.mirebound.mud;

import com.fish.mirebound.coverage.MudCoveragePaintPredicate;
import com.mojang.serialization.Codec;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

/** Immutable sparse per-item mud coverage stored in an ItemStack data component. */
public final class ArmorMudData {
    private static final int LEGACY_BYTES_PER_CELL = 3;
    private static final int SOURCE_BYTES_PER_CELL = 11;
    private static final int SOURCE_V1_HEADER_SIZE = 6;
    private static final int SOURCE_HEADER_SIZE = 10;
    private static final int SOURCE_MAGIC = 0x4651414D; // FQAM
    private static final int SOURCE_VERSION = 2;
    private static final int CELL_MASK = 0x7FF;
    private static final int COVERAGE_SHIFT = 11;
    private static final int MEDIUM_SHIFT = 19;
    public static final ArmorMudData EMPTY = new ArmorMudData(new byte[0]);
    public static final Codec<ArmorMudData> CODEC = Codec.BYTE_BUFFER.xmap(
            ArmorMudData::fromBuffer,
            ArmorMudData::toBuffer);
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorMudData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ArmorMudData decode(RegistryFriendlyByteBuf buffer) {
            return new ArmorMudData(ByteBufCodecs.BYTE_ARRAY.decode(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ArmorMudData value) {
            ByteBufCodecs.BYTE_ARRAY.encode(buffer, value.packed);
        }
    };

    private final byte[] packed;
    private final int coveragePatternSeed;
    private final int maximumCoverageByte;
    private final int hash;

    public ArmorMudData(byte[] packed) {
        int sourceHeaderSize = sourceHeaderSize(packed);
        boolean sourceFormat = sourceHeaderSize > 0;
        int usableLength;
        if (sourceFormat) {
            usableLength = sourceHeaderSize
                    + (packed.length - sourceHeaderSize) / SOURCE_BYTES_PER_CELL
                            * SOURCE_BYTES_PER_CELL;
        } else {
            usableLength = packed.length - packed.length % LEGACY_BYTES_PER_CELL;
        }
        this.packed = usableLength <= 0
                || sourceFormat && usableLength == sourceHeaderSize
                ? new byte[0] : Arrays.copyOf(packed, usableLength);
        int storedSeed = sourceHeaderSize == SOURCE_HEADER_SIZE && this.packed.length > 0
                ? readInt(this.packed, SOURCE_V1_HEADER_SIZE) : 0;
        this.coveragePatternSeed = this.packed.length == 0 ? 0
                : storedSeed != 0 ? storedSeed : legacyPatternSeed(this.packed);
        this.maximumCoverageByte = computeMaximumCoverageByte();
        this.hash = Arrays.hashCode(this.packed);
    }

    public boolean isEmpty() {
        return packed.length == 0;
    }

    public int dirtyCellCount() {
        return sourceFormat()
                ? (packed.length - firstCellOffset()) / SOURCE_BYTES_PER_CELL
                : packed.length / LEGACY_BYTES_PER_CELL;
    }

    public int coveragePatternSeed() {
        return coveragePatternSeed;
    }

    public float averageCoverage() {
        if (packed.length == 0) {
            return 0.0F;
        }
        int total = 0;
        for (int offset = firstCellOffset(); offset < packed.length; offset += bytesPerCell()) {
            total += coverageByte(unpack(offset));
        }
        return total / (255.0F * dirtyCellCount());
    }

    public float maximumCoverage() {
        return maximumCoverageByte / 255.0F;
    }

    public SinkingMedium dominantMedium() {
        int[] weights = new int[SinkingMedium.COUNT];
        for (int offset = firstCellOffset(); offset < packed.length; offset += bytesPerCell()) {
            int value = unpack(offset);
            int mediumId = Math.min(SinkingMedium.COUNT - 1, mediumId(value));
            weights[mediumId] += coverageByte(value);
        }
        int best = 0;
        for (int index = 1; index < weights.length; index++) {
            if (weights[index] > weights[best]) {
                best = index;
            }
        }
        return SinkingMedium.byId(best);
    }

    public void forEach(CellConsumer consumer) {
        forEachVisual((cell, coverage, medium, visualSource) ->
                consumer.accept(cell, coverage, medium));
    }

    public void forEachVisual(VisualCellConsumer consumer) {
        for (int offset = firstCellOffset(); offset < packed.length; offset += bytesPerCell()) {
            int value = unpack(offset);
            int cell = value & CELL_MASK;
            if (cell < MudSurfaceLayout.CELL_COUNT) {
                consumer.accept(cell, coverageByte(value) / 255.0F,
                        SinkingMedium.byId(mediumId(value)), visualSource(offset));
            }
        }
    }

    public float coverageAt(int targetCell) {
        for (int offset = firstCellOffset(); offset < packed.length; offset += bytesPerCell()) {
            int value = unpack(offset);
            if ((value & CELL_MASK) == targetCell) {
                return coverageByte(value) / 255.0F;
            }
        }
        return 0.0F;
    }

    public SinkingMedium mediumAt(int targetCell) {
        for (int offset = firstCellOffset(); offset < packed.length; offset += bytesPerCell()) {
            int value = unpack(offset);
            if ((value & CELL_MASK) == targetCell) {
                return SinkingMedium.byId(mediumId(value));
            }
        }
        return SinkingMedium.MUD;
    }

    public long visualSourceAt(int targetCell) {
        for (int offset = firstCellOffset(); offset < packed.length; offset += bytesPerCell()) {
            int value = unpack(offset);
            if ((value & CELL_MASK) == targetCell) {
                return visualSource(offset);
            }
        }
        return 0L;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    private int unpack(int offset) {
        return (packed[offset] & 0xFF)
                | (packed[offset + 1] & 0xFF) << 8
                | (packed[offset + 2] & 0xFF) << 16;
    }

    private boolean sourceFormat() {
        return hasSourceHeader(packed);
    }

    private int firstCellOffset() {
        return sourceHeaderSize(packed);
    }

    private int bytesPerCell() {
        return sourceFormat() ? SOURCE_BYTES_PER_CELL : LEGACY_BYTES_PER_CELL;
    }

    private long visualSource(int offset) {
        if (!sourceFormat()) {
            return 0L;
        }
        long result = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            result |= (long) (packed[offset + LEGACY_BYTES_PER_CELL + index] & 0xFF)
                    << index * Byte.SIZE;
        }
        return result;
    }

    private int computeMaximumCoverageByte() {
        int maximum = 0;
        for (int offset = firstCellOffset(); offset < packed.length; offset += bytesPerCell()) {
            maximum = Math.max(maximum, coverageByte(unpack(offset)));
        }
        return maximum;
    }

    private static boolean hasSourceHeader(byte[] bytes) {
        return sourceHeaderSize(bytes) > 0;
    }

    private static int sourceHeaderSize(byte[] bytes) {
        if (bytes.length < SOURCE_V1_HEADER_SIZE
                || readInt(bytes, 0) != SOURCE_MAGIC
                || (bytes[5] & 0xFF) != SOURCE_BYTES_PER_CELL) {
            return 0;
        }
        int version = bytes[4] & 0xFF;
        if (version == 1) {
            return SOURCE_V1_HEADER_SIZE;
        }
        return version == SOURCE_VERSION && bytes.length >= SOURCE_HEADER_SIZE
                ? SOURCE_HEADER_SIZE : 0;
    }

    private static int legacyPatternSeed(byte[] bytes) {
        int seed = MudCoveragePatternSeed.mix(Arrays.hashCode(bytes), 0x41A55A17);
        return seed == 0 ? 0x6D2B79F5 : seed;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | (bytes[offset + 1] & 0xFF) << 8
                | (bytes[offset + 2] & 0xFF) << 16
                | (bytes[offset + 3] & 0xFF) << 24;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }

    private static void writeLong(byte[] bytes, int offset, long value) {
        for (int index = 0; index < Long.BYTES; index++) {
            bytes[offset + index] = (byte) (value >>> index * Byte.SIZE);
        }
    }

    private static int coverageByte(int packed) {
        return packed >>> COVERAGE_SHIFT & 0xFF;
    }

    private static int mediumId(int packed) {
        return packed >>> MEDIUM_SHIFT & 0x1F;
    }

    private static ArmorMudData fromBuffer(ByteBuffer source) {
        ByteBuffer copy = source.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes.length == 0 ? EMPTY : new ArmorMudData(bytes);
    }

    private ByteBuffer toBuffer() {
        return ByteBuffer.wrap(Arrays.copyOf(packed, packed.length));
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof ArmorMudData other && Arrays.equals(packed, other.packed);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int cell, float coverage, SinkingMedium medium);
    }

    @FunctionalInterface
    public interface VisualCellConsumer {
        void accept(int cell, float coverage, SinkingMedium medium, long visualSource);
    }

    public static final class Builder {
        private final byte[] coverage = new byte[MudSurfaceLayout.CELL_COUNT];
        private final byte[] medium = new byte[MudSurfaceLayout.CELL_COUNT];
        private final long[] visualSource = new long[MudSurfaceLayout.CELL_COUNT];
        private int coveragePatternSeed;
        private boolean changed;

        private Builder(ArmorMudData source) {
            coveragePatternSeed = source.coveragePatternSeed();
            source.forEachVisual((cell, strength, sinkingMedium, sourceVisual) -> {
                coverage[cell] = (byte) Mth.clamp(Math.round(strength * 255.0F), 0, 255);
                medium[cell] = (byte) sinkingMedium.id();
                visualSource[cell] = sourceVisual;
            });
        }

        public boolean mark(int cell, float target, SinkingMedium sinkingMedium) {
            return mark(cell, target, sinkingMedium, 0L);
        }

        public boolean mark(int cell, float target, SinkingMedium sinkingMedium,
                long sourceVisual) {
            int current = coverage[cell] & 0xFF;
            int targetByte = Mth.clamp(Math.round(target * 255.0F), 0, 255);
            int next = Math.max(current, targetByte);
            if (next > 0 && coveragePatternSeed == 0) {
                coveragePatternSeed = MudCoveragePatternSeed.next();
            }
            boolean replacesOwner = targetByte >= current || current == 0;
            if (next == current
                    && (current == 0 || (medium[cell] & 0xFF) == sinkingMedium.id())
                    && (!replacesOwner || visualSource[cell] == sourceVisual)) {
                return false;
            }
            coverage[cell] = (byte) next;
            if (replacesOwner) {
                medium[cell] = (byte) sinkingMedium.id();
                visualSource[cell] = sourceVisual;
            }
            changed = true;
            return true;
        }

        public boolean markSplash(int cell, float target,
                SinkingMedium sinkingMedium, long sourceVisual) {
            return markSplash(cell, target, sinkingMedium, sourceVisual, 1);
        }

        public boolean markSplash(int cell, float target,
                SinkingMedium sinkingMedium, long sourceVisual,
                int accumulationPasses) {
            float current = (coverage[cell] & 0xFF) / 255.0F;
            return mark(cell, MudCoverageRules.accumulateSplash(
                            current, target, accumulationPasses),
                    sinkingMedium, sourceVisual);
        }

        public boolean wash(int cell, float amount, float clearThreshold, int tickSalt) {
            int current = coverage[cell] & 0xFF;
            if (current == 0) {
                return false;
            }
            float noise = cellNoise(cell, tickSalt);
            int reduction = Math.max(1, Math.round(amount * (0.58F + noise * 0.72F) * 255.0F));
            int next = Math.max(0, current - reduction);
            if (next / 255.0F <= clearThreshold) {
                next = 0;
            }
            if (next == current) {
                return false;
            }
            coverage[cell] = (byte) next;
            if (next == 0) {
                medium[cell] = (byte) SinkingMedium.MUD.id();
                visualSource[cell] = 0L;
            }
            changed = true;
            return true;
        }

        public boolean fadeToFloor(int cell, float amount, float floor) {
            int current = coverage[cell] & 0xFF;
            int floorByte = Mth.clamp(Math.round(floor * 255.0F), 0, 255);
            if (current <= floorByte) {
                return false;
            }
            int reduction = Math.max(1, Math.round(amount * 255.0F));
            coverage[cell] = (byte) Math.max(floorByte, current - reduction);
            changed = true;
            return true;
        }

        public boolean fadeTransferredSurfaceEdges(BitSet transferredCells,
                IntPredicate visibleCell) {
            boolean faded = MudSurfaceFadeBlend.fade(
                    coverage, medium, visualSource, transferredCells, visibleCell);
            changed |= faded;
            return faded;
        }

        public boolean blendSurfaceEdges(EquipmentSlot slot) {
            boolean blended = MudSurfaceEdgeBlend.blend(coverage, medium, visualSource, cell -> {
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                return ArmorMudManager.slotOwnsSurface(slot, part, surface, row);
            }, (cell, mediumId) -> true);
            changed |= blended;
            return blended;
        }

        public boolean blendSurfaceEdges(EquipmentSlot slot,
                MudCoveragePaintPredicate allowsPaint) {
            boolean blended = MudSurfaceEdgeBlend.blend(coverage, medium, visualSource, cell -> {
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                return ArmorMudManager.slotOwnsSurface(slot, part, surface, row);
            }, allowsPaint::test);
            changed |= blended;
            return blended;
        }

        public boolean changed() {
            return changed;
        }

        public int coveragePatternSeed() {
            return coveragePatternSeed;
        }

        public ArmorMudData build() {
            int count = 0;
            for (byte value : coverage) {
                if ((value & 0xFF) > 0) {
                    count++;
                }
            }
            if (count == 0) {
                return EMPTY;
            }
            int bytesPerCell = SOURCE_BYTES_PER_CELL;
            int headerSize = SOURCE_HEADER_SIZE;
            byte[] result = new byte[headerSize + count * bytesPerCell];
            int offset = headerSize;
            writeInt(result, 0, SOURCE_MAGIC);
            result[4] = (byte) SOURCE_VERSION;
            result[5] = (byte) SOURCE_BYTES_PER_CELL;
            writeInt(result, SOURCE_V1_HEADER_SIZE, coveragePatternSeed);
            for (int cell = 0; cell < coverage.length; cell++) {
                int strength = coverage[cell] & 0xFF;
                if (strength == 0) {
                    continue;
                }
                int value = cell | strength << COVERAGE_SHIFT | (medium[cell] & 0x1F) << MEDIUM_SHIFT;
                result[offset++] = (byte) value;
                result[offset++] = (byte) (value >>> 8);
                result[offset++] = (byte) (value >>> 16);
                writeLong(result, offset, visualSource[cell]);
                offset += Long.BYTES;
            }
            return new ArmorMudData(result);
        }

        private static float cellNoise(int cell, int tickSalt) {
            int value = cell * 73428767 ^ tickSalt * 9122719;
            value ^= value >>> 13;
            value *= 1274126177;
            value ^= value >>> 16;
            return (value & 1023) / 1023.0F;
        }
    }
}
