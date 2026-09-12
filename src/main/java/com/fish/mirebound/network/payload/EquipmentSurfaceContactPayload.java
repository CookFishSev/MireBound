package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import com.fish.mirebound.coverage.armor.EquipmentSurfacePatch;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record EquipmentSurfaceContactPayload(EquipmentSurfaceTarget target, ResourceLocation item,
        Vec3 origin, List<Sample> samples, List<EquipmentSurfacePatch> patches,
        boolean completeSurface, int surfaceCells) implements CustomPacketPayload {
    public static final int MAX_SAMPLES = 256;
    public static final int MAX_PATCHES = 128;
    public static final Type<EquipmentSurfaceContactPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "equipment_surface_contact"));
    public record Sample(long face, int cell, float x, float y, float z) {}
    public EquipmentSurfaceContactPayload(EquipmentSurfaceTarget target, ResourceLocation item,
            Vec3 origin, List<Sample> samples, List<EquipmentSurfacePatch> patches, boolean completeSurface) {
        this(target, item, origin, samples, patches, completeSurface,
                completeSurface ? patches.stream().mapToInt(p -> p.probes().size()).sum() : 0);
    }
    public EquipmentSurfaceContactPayload(EquipmentSurfaceTarget target, ResourceLocation item,
            Vec3 origin, List<Sample> samples) {
        this(target, item, origin, samples, List.of(), false);
    }
    public EquipmentSurfaceContactPayload {
        java.util.Objects.requireNonNull(target);
        java.util.Objects.requireNonNull(item);
        java.util.Objects.requireNonNull(origin);
        samples = List.copyOf(samples);
        patches = List.copyOf(patches);
        if (samples.size() > MAX_SAMPLES) throw new IllegalArgumentException("Equipment surface samples");
        if (surfaceCells < 0 || surfaceCells > EquipmentSurfaceData.MAX_CELLS
                || completeSurface && surfaceCells < patches.stream().mapToInt(p -> p.probes().size()).sum())
            throw new IllegalArgumentException("Equipment total surface size");
        if (patches.size() > MAX_PATCHES || !samples.isEmpty() && !patches.isEmpty()
                || patches.stream().mapToInt(p -> p.probes().size()).sum() > EquipmentSurfaceData.MAX_CELLS)
            throw new IllegalArgumentException("Equipment surface patches");
        java.util.Set<Long> faces = new java.util.HashSet<>();
        for (EquipmentSurfacePatch patch : patches)
            if (!faces.add(patch.face())) throw new IllegalArgumentException("Duplicate equipment surface patch");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentSurfaceContactPayload> STREAM_CODEC = new StreamCodec<>() {
        public EquipmentSurfaceContactPayload decode(RegistryFriendlyByteBuf b) {
            EquipmentSurfaceTarget target = new EquipmentSurfaceTarget(b.readUnsignedByte(), b.readVarInt(), b.readUtf(64), b.readBoolean());
            ResourceLocation item = b.readResourceLocation();
            Vec3 origin = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
            int count = b.readVarInt();
            if (count < 0 || count > MAX_SAMPLES) throw new IllegalArgumentException("Equipment surface samples");
            List<Sample> samples = new ArrayList<>(count);
            for (int i = 0; i < count; i++) samples.add(new Sample(b.readLong(), b.readUnsignedByte(), b.readFloat(), b.readFloat(), b.readFloat()));
            int patchCount = b.readVarInt();
            if (patchCount < 0 || patchCount > MAX_PATCHES) throw new IllegalArgumentException("Equipment surface patches");
            List<EquipmentSurfacePatch> patches = new ArrayList<>(patchCount);
            int remaining = EquipmentSurfaceData.MAX_CELLS;
            for (int i = 0; i < patchCount; i++) {
                long face = b.readLong();
                int width = b.readUnsignedByte(), height = b.readUnsignedByte();
                Vec3 a = readPoint(b), second = readPoint(b), c = readPoint(b), d = readPoint(b);
                int cells = b.readVarInt();
                if (cells < 0 || cells > 256 || cells > remaining) throw new IllegalArgumentException("Equipment patch cells");
                remaining -= cells;
                List<Integer> probes = new ArrayList<>(cells);
                for (int cell = 0; cell < cells; cell++) probes.add(b.readUnsignedMedium());
                patches.add(new EquipmentSurfacePatch(face, width, height, a, second, c, d, probes));
            }
            return new EquipmentSurfaceContactPayload(target, item, origin, samples, patches, b.readBoolean(), b.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf b, EquipmentSurfaceContactPayload p) {
            for (Sample sample : p.samples) {
                if (sample.cell() < 0 || sample.cell() >= 256) throw new IllegalArgumentException("Equipment surface cell");
            }
            b.writeByte(p.target.kind()); b.writeVarInt(p.target.slot()); b.writeUtf(p.target.identifier(), 64); b.writeBoolean(p.target.cosmetic());
            b.writeResourceLocation(p.item); b.writeDouble(p.origin.x); b.writeDouble(p.origin.y); b.writeDouble(p.origin.z);
            b.writeVarInt(p.samples.size());
            for (Sample sample : p.samples) { b.writeLong(sample.face()); b.writeByte(sample.cell()); b.writeFloat(sample.x()); b.writeFloat(sample.y()); b.writeFloat(sample.z()); }
            b.writeVarInt(p.patches.size());
            for (EquipmentSurfacePatch patch : p.patches) {
                b.writeLong(patch.face()); b.writeByte(patch.width()); b.writeByte(patch.height());
                writePoint(b, patch.a()); writePoint(b, patch.b()); writePoint(b, patch.c()); writePoint(b, patch.d());
                b.writeVarInt(patch.probes().size());
                for (int probe : patch.probes()) b.writeMedium(probe);
            }
            b.writeBoolean(p.completeSurface);
            b.writeVarInt(p.surfaceCells);
        }
    };
    private static Vec3 readPoint(RegistryFriendlyByteBuf b) {
        return new Vec3(b.readFloat(), b.readFloat(), b.readFloat());
    }
    private static void writePoint(RegistryFriendlyByteBuf b, Vec3 point) {
        b.writeFloat((float) point.x); b.writeFloat((float) point.y); b.writeFloat((float) point.z);
    }
    public Type<EquipmentSurfaceContactPayload> type() { return TYPE; }
}
