package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.coverage.armor.EquipmentSurfaceTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record EquipmentSurfaceContactPayload(EquipmentSurfaceTarget target, ResourceLocation item,
        Vec3 origin, List<Sample> samples) implements CustomPacketPayload {
    public static final int MAX_SAMPLES = 256;
    public static final Type<EquipmentSurfaceContactPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "equipment_surface_contact"));
    public record Sample(long face, int cell, float x, float y, float z) {}
    public EquipmentSurfaceContactPayload {
        java.util.Objects.requireNonNull(target);
        java.util.Objects.requireNonNull(item);
        java.util.Objects.requireNonNull(origin);
        samples = List.copyOf(samples);
        if (samples.size() > MAX_SAMPLES) throw new IllegalArgumentException("Equipment surface samples");
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
            return new EquipmentSurfaceContactPayload(target, item, origin, samples);
        }
        public void encode(RegistryFriendlyByteBuf b, EquipmentSurfaceContactPayload p) {
            for (Sample sample : p.samples) {
                if (sample.cell() < 0 || sample.cell() >= 256) throw new IllegalArgumentException("Equipment surface cell");
            }
            b.writeByte(p.target.kind()); b.writeVarInt(p.target.slot()); b.writeUtf(p.target.identifier(), 64); b.writeBoolean(p.target.cosmetic());
            b.writeResourceLocation(p.item); b.writeDouble(p.origin.x); b.writeDouble(p.origin.y); b.writeDouble(p.origin.z);
            b.writeVarInt(p.samples.size());
            for (Sample sample : p.samples) { b.writeLong(sample.face()); b.writeByte(sample.cell()); b.writeFloat(sample.x()); b.writeFloat(sample.y()); b.writeFloat(sample.z()); }
        }
    };
    public Type<EquipmentSurfaceContactPayload> type() { return TYPE; }
}
