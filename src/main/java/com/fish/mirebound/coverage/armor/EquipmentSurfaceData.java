package com.fish.mirebound.coverage.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Sparse item-owned contact cells. A UV coordinate is never a surface identity. */
public final class EquipmentSurfaceData {
    public static final int MAX_CELLS = 8192;
    public static final int GRID_LIMIT = 16;
    public static final EquipmentSurfaceData EMPTY = new EquipmentSurfaceData(List.of());
    public record Cell(long face, int index, int strength, int medium, long source) {}
    private record Key(long face, int index) {}
    private static final Codec<Cell> CELL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("face").forGetter(Cell::face),
            Codec.intRange(0, 255).fieldOf("cell").forGetter(Cell::index),
            Codec.intRange(1, 255).fieldOf("strength").forGetter(Cell::strength),
            Codec.intRange(0, SinkingMedium.COUNT - 1).fieldOf("medium").forGetter(Cell::medium),
            Codec.LONG.optionalFieldOf("source", 0L).forGetter(Cell::source)
    ).apply(instance, Cell::new));
    private static final Codec<EquipmentSurfaceData> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CELL_CODEC.listOf(0, MAX_CELLS).fieldOf("cells").forGetter(data -> data.cells),
            Codec.intRange(0, MAX_CELLS).optionalFieldOf("surface_cells", 0).forGetter(data -> data.surfaceCells)
    ).apply(instance, EquipmentSurfaceData::new));
    public static final Codec<EquipmentSurfaceData> CODEC = Codec.withAlternative(RECORD_CODEC,
            CELL_CODEC.listOf(0, MAX_CELLS).xmap(EquipmentSurfaceData::new, data -> data.cells));
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentSurfaceData> STREAM_CODEC = new StreamCodec<>() {
        public EquipmentSurfaceData decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_CELLS) throw new IllegalArgumentException("Equipment surface count");
            List<Cell> cells = new ArrayList<>(count);
            while (cells.size() < count) {
                long face = buffer.readLong();
                int run = buffer.readVarInt();
                if (run < 1 || run > count - cells.size()) throw new IllegalArgumentException("Equipment face cell count");
                for (int i = 0; i < run; i++) cells.add(new Cell(face, buffer.readUnsignedByte(),
                        buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readVarLong()));
            }
            return new EquipmentSurfaceData(cells, buffer.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf buffer, EquipmentSurfaceData data) {
            buffer.writeVarInt(data.cells.size());
            for (int first = 0; first < data.cells.size();) {
                long face = data.cells.get(first).face();
                int end = first + 1;
                while (end < data.cells.size() && data.cells.get(end).face() == face) end++;
                buffer.writeLong(face);
                buffer.writeVarInt(end - first);
                for (int i = first; i < end; i++) {
                    Cell cell = data.cells.get(i);
                    buffer.writeByte(cell.index()); buffer.writeByte(cell.strength());
                    buffer.writeByte(cell.medium()); buffer.writeVarLong(cell.source());
                }
                first = end;
            }
            buffer.writeVarInt(data.surfaceCells);
        }
    };
    private final List<Cell> cells;
    private final Map<Key, Cell> index;
    private final int surfaceCells;
    private final float coverageFraction;
    private final SinkingMedium dominantMedium;
    private final java.util.Set<Long> dirtyFaces;
    public EquipmentSurfaceData(List<Cell> cells) {
        this(cells, 0);
    }
    public EquipmentSurfaceData(List<Cell> cells, int surfaceCells) {
        if (surfaceCells < 0 || surfaceCells > MAX_CELLS) throw new IllegalArgumentException("Equipment surface size");
        Map<Key, Cell> distinct = new LinkedHashMap<>();
        for (Cell cell : cells) {
            if (distinct.size() >= MAX_CELLS) break;
            if (cell != null && cell.face() != 0 && cell.index() >= 0 && cell.index() < 256 && cell.strength() > 0
                    && cell.strength() <= 255 && cell.medium() >= 0 && cell.medium() < SinkingMedium.COUNT)
                distinct.put(new Key(cell.face(), cell.index()), cell);
        }
        this.cells = List.copyOf(distinct.values());
        index = Map.copyOf(distinct);
        this.surfaceCells = surfaceCells;
        int sum = 0;
        int[] media = new int[SinkingMedium.COUNT];
        java.util.Set<Long> faces = new java.util.HashSet<>();
        for (Cell cell : this.cells) {
            sum += cell.strength();
            media[cell.medium()] += cell.strength();
            faces.add(cell.face());
        }
        dirtyFaces = java.util.Set.copyOf(faces);
        int denominator = surfaceCells > 0 ? surfaceCells : Math.min(MAX_CELLS, faces.size() * 256);
        coverageFraction = denominator == 0 ? 0F : Math.min(1F, sum / (255F * Math.max(denominator, this.cells.size())));
        int strongest = 0;
        for (int medium = 1; medium < media.length; medium++) if (media[medium] > media[strongest]) strongest = medium;
        dominantMedium = SinkingMedium.byId(strongest);
    }
    public Cell cell(long face, int pixel) { return index.get(new Key(face, pixel)); }
    public List<Cell> cells() { return cells; }
    public boolean isEmpty() { return cells.isEmpty(); }
    public boolean hasFace(long face) { return dirtyFaces.contains(face); }
    public int surfaceCells() { return surfaceCells; }
    public float coverageFraction() { return coverageFraction; }
    public SinkingMedium dominantMedium() { return dominantMedium; }
    public Builder toBuilder() { return new Builder(this); }
    @Override public int hashCode() { return 31 * cells.hashCode() + surfaceCells; }
    @Override public boolean equals(Object other) { return other instanceof EquipmentSurfaceData data
            && surfaceCells == data.surfaceCells && cells.equals(data.cells); }
    public static final class Builder {
        private final Map<Key, Cell> cells;
        private boolean changed;
        private int surfaceCells;
        private Builder(EquipmentSurfaceData data) {
            cells = new LinkedHashMap<>();
            for (Cell cell : data.cells) cells.put(new Key(cell.face(), cell.index()), cell);
            surfaceCells = data.surfaceCells;
        }
        public void surfaceCells(int count) {
            if (count < 0 || count > MAX_CELLS) throw new IllegalArgumentException("Equipment surface size");
            if (surfaceCells != count) { surfaceCells = count; changed = true; }
        }
        public void stain(long face, int pixel, int strength, int medium, long source) {
            if (face == 0 || pixel < 0 || pixel >= 256 || strength <= 0 || medium < 0 || medium >= SinkingMedium.COUNT) return;
            Key key = new Key(face, pixel);
            Cell old = cells.get(key);
            if (old == null && cells.size() >= MAX_CELLS) return;
            if (old != null && old.medium() == medium && old.source() == source && old.strength() >= strength) return;
            Cell replacement = new Cell(face, pixel, Math.max(1, Math.min(255, strength)), medium, source);
            if (!replacement.equals(old)) { cells.put(key, replacement); changed = true; }
        }
        public void wash(long face, int pixel, int amount) {
            Key key = new Key(face, pixel); Cell old = cells.get(key);
            if (old == null || amount <= 0) return;
            if (old.strength() <= amount) cells.remove(key);
            else cells.put(key, new Cell(face, pixel, old.strength() - amount, old.medium(), old.source()));
            changed = true;
        }
        public void transferToWall(long face, int pixel, float amount, float minimumCoverage) {
            Cell old = cells.get(new Key(face, pixel));
            if (old == null) return;
            int floor = Math.round(Math.clamp(minimumCoverage, 0F, 1F) * 255);
            int drain = Math.max(0, Math.round(amount * 255));
            if (old.strength() > floor) wash(face, pixel, Math.min(drain, old.strength() - floor));
        }
        public boolean changed() { return changed; }
        public EquipmentSurfaceData build() { return new EquipmentSurfaceData(List.copyOf(cells.values()), surfaceCells); }
    }
}
