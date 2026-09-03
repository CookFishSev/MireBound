package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.ArmorTextureMudData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Batched local-player UV samples; the server rechecks every world contact before storing it. */
public record ArmorTextureContactPayload(TargetType targetType, int slotIndex,
        String curiosIdentifier, int curiosIndex, boolean curiosCosmetic,
        ResourceLocation texture, int width, int height, Vec3 origin,
        int candidateCount, List<Sample> samples) implements CustomPacketPayload {
    public static final int MAX_SAMPLES = 512;
    public static final int MAX_CANDIDATES = 4096;
    public static final Type<ArmorTextureContactPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "armor_texture_contact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorTextureContactPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ArmorTextureContactPayload decode(RegistryFriendlyByteBuf buffer) {
                    TargetType targetType = TargetType.byId(buffer.readVarInt());
                    int slotIndex = targetType == TargetType.ARMOR ? buffer.readVarInt() : -1;
                    String curiosIdentifier = targetType == TargetType.CURIOS ? buffer.readUtf(64) : "";
                    int curiosIndex = targetType == TargetType.CURIOS ? buffer.readVarInt() : -1;
                    boolean curiosCosmetic = targetType == TargetType.CURIOS && buffer.readBoolean();
                    ResourceLocation texture = buffer.readResourceLocation();
                    int width = buffer.readVarInt();
                    int height = buffer.readVarInt();
                    Vec3 origin = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                    int candidateCount = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_SAMPLES) {
                        throw new IllegalArgumentException("Invalid armor texture contact sample count " + count);
                    }
                    List<Sample> samples = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        samples.add(new Sample(
                                buffer.readVarInt(),
                                buffer.readFloat(),
                                buffer.readFloat(),
                                buffer.readFloat()));
                    }
                    return new ArmorTextureContactPayload(
                            targetType, slotIndex, curiosIdentifier, curiosIndex, curiosCosmetic,
                            texture, width, height, origin, candidateCount, List.copyOf(samples));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ArmorTextureContactPayload payload) {
                    buffer.writeVarInt(payload.targetType.id);
                    if (payload.targetType == TargetType.ARMOR) {
                        buffer.writeVarInt(payload.slotIndex);
                    } else {
                        buffer.writeUtf(payload.curiosIdentifier, 64);
                        buffer.writeVarInt(payload.curiosIndex);
                        buffer.writeBoolean(payload.curiosCosmetic);
                    }
                    buffer.writeResourceLocation(payload.texture);
                    buffer.writeVarInt(payload.width);
                    buffer.writeVarInt(payload.height);
                    buffer.writeDouble(payload.origin.x);
                    buffer.writeDouble(payload.origin.y);
                    buffer.writeDouble(payload.origin.z);
                    buffer.writeVarInt(payload.candidateCount);
                    int count = Math.min(payload.samples.size(), MAX_SAMPLES);
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++) {
                        Sample sample = payload.samples.get(index);
                        buffer.writeVarInt(sample.pixel);
                        buffer.writeFloat(sample.offsetX);
                        buffer.writeFloat(sample.offsetY);
                        buffer.writeFloat(sample.offsetZ);
                    }
                }
            };

    public ArmorTextureContactPayload {
        targetType = targetType == null ? TargetType.ARMOR : targetType;
        curiosIdentifier = curiosIdentifier == null ? "" : curiosIdentifier;
        samples = samples == null ? List.of() : List.copyOf(samples);
    }

    public static ArmorTextureContactPayload armor(int slotIndex, ResourceLocation texture,
            int width, int height, Vec3 origin, List<Sample> samples) {
        return armor(slotIndex, texture, width, height, origin, samples.size(), samples);
    }

    public static ArmorTextureContactPayload armor(int slotIndex, ResourceLocation texture,
            int width, int height, Vec3 origin, int candidateCount, List<Sample> samples) {
        return new ArmorTextureContactPayload(TargetType.ARMOR, slotIndex, "", -1, false,
                texture, width, height, origin, candidateCount, samples);
    }

    public static ArmorTextureContactPayload curios(String identifier, int index, boolean cosmetic,
            ResourceLocation texture, int width, int height, Vec3 origin, List<Sample> samples) {
        return curios(identifier, index, cosmetic, texture, width, height, origin, samples.size(), samples);
    }

    public static ArmorTextureContactPayload curios(String identifier, int index, boolean cosmetic,
            ResourceLocation texture, int width, int height, Vec3 origin,
            int candidateCount, List<Sample> samples) {
        return new ArmorTextureContactPayload(TargetType.CURIOS, -1, identifier, index, cosmetic,
                texture, width, height, origin, candidateCount, samples);
    }

    public boolean validDimensions() {
        return width > 0 && height > 0
                && width <= ArmorTextureMudData.MAX_DIMENSION
                && height <= ArmorTextureMudData.MAX_DIMENSION;
    }

    public boolean validTarget() {
        return switch (targetType) {
            case ARMOR -> slotIndex >= 0 && slotIndex < 4;
            case CURIOS -> !curiosIdentifier.isBlank() && curiosIdentifier.length() <= 64
                    && curiosIndex >= 0 && curiosIndex < 128;
        };
    }

    public boolean validCandidateCount() {
        return candidateCount >= samples.size() && candidateCount <= MAX_CANDIDATES;
    }

    /**
     * Rejects non-finite origins before any range test. A NaN coordinate makes every distance
     * comparison evaluate to false, so an unchecked origin would pass a range gate rather than
     * fail it.
     */
    public boolean validOrigin() {
        return origin != null && Double.isFinite(origin.x)
                && Double.isFinite(origin.y) && Double.isFinite(origin.z);
    }

    @Override
    public Type<ArmorTextureContactPayload> type() {
        return TYPE;
    }

    public record Sample(int pixel, float offsetX, float offsetY, float offsetZ) {
    }

    public enum TargetType {
        ARMOR(0),
        CURIOS(1);

        private final int id;

        TargetType(int id) {
            this.id = id;
        }

        private static TargetType byId(int id) {
            if (id < 0 || id >= values().length) {
                throw new IllegalArgumentException("Invalid equipment texture target type " + id);
            }
            return values()[id];
        }
    }
}
