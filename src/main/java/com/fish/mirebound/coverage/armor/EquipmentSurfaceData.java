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
    public static final Codec<EquipmentSurfaceData> CODEC = CELL_CODEC.listOf(0, MAX_CELLS)
            .xmap(EquipmentSurfaceData::new, data -> data.cells);
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentSurfaceData> STREAM_CODEC = new StreamCodec<>() {
        public EquipmentSurfaceData decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_CELLS) throw new IllegalArgumentException("Equipment surface count");
            List<Cell> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) cells.add(new Cell(buffer.readLong(), buffer.readUnsignedByte(),
                    buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readLong()));
            return new EquipmentSurfaceData(cells);
        }
        public void encode(RegistryFriendlyByteBuf buffer, EquipmentSurfaceData data) {
            buffer.writeVarInt(data.cells.size());
            for (Cell cell : data.cells) {
                buffer.writeLong(cell.face()); buffer.writeByte(cell.index());
                buffer.writeByte(cell.strength()); buffer.writeByte(cell.medium()); buffer.writeLong(cell.source());
            }
        }
    };
    private final List<Cell> cells;
    private final Map<Key, Cell> index;
    public EquipmentSurfaceData(List<Cell> cells) {
        Map<Key, Cell> distinct = new LinkedHashMap<>();
        for (Cell cell : cells) {
            if (distinct.size() >= MAX_CELLS) break;
            if (cell != null && cell.face() != 0 && cell.index() >= 0 && cell.index() < 256 && cell.strength() > 0
                    && cell.strength() <= 255 && cell.medium() >= 0 && cell.medium() < SinkingMedium.COUNT)
                distinct.put(new Key(cell.face(), cell.index()), cell);
        }
        this.cells = List.copyOf(distinct.values());
        index = Map.copyOf(distinct);
    }
    public Cell cell(long face, int pixel) { return index.get(new Key(face, pixel)); }
    public List<Cell> cells() { return cells; }
    public boolean isEmpty() { return cells.isEmpty(); }
    public Builder toBuilder() { return new Builder(this); }
    @Override public int hashCode() { return cells.hashCode(); }
    @Override public boolean equals(Object other) { return other instanceof EquipmentSurfaceData data && cells.equals(data.cells); }
    public static final class Builder {
        private final Map<Key, Cell> cells;
        private boolean changed;
        private Builder(EquipmentSurfaceData data) {
            cells = new LinkedHashMap<>();
            for (Cell cell : data.cells) cells.put(new Key(cell.face(), cell.index()), cell);
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
        public boolean changed() { return changed; }
        public EquipmentSurfaceData build() { return new EquipmentSurfaceData(List.copyOf(cells.values())); }
    }
}
