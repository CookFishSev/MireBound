package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.entitycoverage.EntityMudCoverageState;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Compact full or incremental mud-coverage state for one non-player entity. */
public record EntityMudCoveragePayload(
        int entityId, UUID entityUuid, int revision, int patternSeed,
        int primaryStrength, int primaryMediumId, long primaryVisualSource,
        int secondaryStrength, int secondaryMediumId, long secondaryVisualSource,
        int automaticFadeScale,
        boolean fullSnapshot, List<Spot> spots, List<Integer> removedSpotIds)
        implements CustomPacketPayload {
    public static final Type<EntityMudCoveragePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "entity_mud_coverage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityMudCoveragePayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public EntityMudCoveragePayload decode(RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    UUID entityUuid = buffer.readUUID();
                    int revision = buffer.readVarInt();
                    int patternSeed = buffer.readInt();
                    int primaryStrength = buffer.readUnsignedByte();
                    int primaryMediumId = buffer.readUnsignedByte();
                    long primaryVisualSource = buffer.readLong();
                    int secondaryStrength = buffer.readUnsignedByte();
                    int secondaryMediumId = buffer.readUnsignedByte();
                    long secondaryVisualSource = buffer.readLong();
                    int automaticFadeScale = buffer.readUnsignedByte();
                    boolean fullSnapshot = buffer.readBoolean();
                    int encodedSpotCount = buffer.readUnsignedByte();
                    java.util.ArrayList<Spot> spots = new java.util.ArrayList<>(
                            Math.min(encodedSpotCount,
                                    EntityMudCoverageState.MAXIMUM_SPOTS));
                    for (int index = 0; index < encodedSpotCount; index++) {
                        Spot spot = readSpot(buffer);
                        if (index < EntityMudCoverageState.MAXIMUM_SPOTS) {
                            spots.add(spot);
                        }
                    }
                    int encodedRemovedCount = buffer.readUnsignedByte();
                    java.util.ArrayList<Integer> removedSpotIds =
                            new java.util.ArrayList<>(Math.min(
                                    encodedRemovedCount,
                                    EntityMudCoverageState.MAXIMUM_SPOTS));
                    for (int index = 0; index < encodedRemovedCount; index++) {
                        int id = buffer.readVarInt();
                        if (index < EntityMudCoverageState.MAXIMUM_SPOTS) {
                            removedSpotIds.add(id);
                        }
                    }
                    return new EntityMudCoveragePayload(
                            entityId, entityUuid, revision, patternSeed,
                            primaryStrength, primaryMediumId, primaryVisualSource,
                            secondaryStrength, secondaryMediumId,
                            secondaryVisualSource, automaticFadeScale, fullSnapshot,
                            spots, removedSpotIds);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        EntityMudCoveragePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeUUID(payload.entityUuid());
                    buffer.writeVarInt(payload.revision());
                    buffer.writeInt(payload.patternSeed());
                    buffer.writeByte(payload.primaryStrength());
                    buffer.writeByte(payload.primaryMediumId());
                    buffer.writeLong(payload.primaryVisualSource());
                    buffer.writeByte(payload.secondaryStrength());
                    buffer.writeByte(payload.secondaryMediumId());
                    buffer.writeLong(payload.secondaryVisualSource());
                    buffer.writeByte(payload.automaticFadeScale());
                    buffer.writeBoolean(payload.fullSnapshot());
                    buffer.writeByte(payload.spots().size());
                    for (Spot spot : payload.spots()) {
                        writeSpot(buffer, spot);
                    }
                    buffer.writeByte(payload.removedSpotIds().size());
                    for (int id : payload.removedSpotIds()) {
                        buffer.writeVarInt(id);
                    }
                }
            };

    public EntityMudCoveragePayload {
        if (entityUuid == null) {
            throw new IllegalArgumentException("entityUuid");
        }
        entityId = Math.max(0, entityId);
        revision = Math.max(0, revision);
        primaryStrength = Mth.clamp(primaryStrength, 0, 255);
        primaryMediumId = Mth.clamp(primaryMediumId, 0, 255);
        secondaryStrength = Mth.clamp(secondaryStrength, 0, 255);
        secondaryMediumId = Mth.clamp(secondaryMediumId, 0, 255);
        automaticFadeScale = Mth.clamp(automaticFadeScale, 0, 255);
        spots = spots == null ? List.of() : List.copyOf(
                spots.subList(0, Math.min(
                        spots.size(), EntityMudCoverageState.MAXIMUM_SPOTS)));
        removedSpotIds = removedSpotIds == null ? List.of() : List.copyOf(
                removedSpotIds.subList(0, Math.min(
                        removedSpotIds.size(),
                        EntityMudCoverageState.MAXIMUM_SPOTS)));
    }

    @Override
    public Type<EntityMudCoveragePayload> type() {
        return TYPE;
    }

    private static Spot readSpot(RegistryFriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        int shapeId = buffer.readUnsignedByte();
        int x;
        int y;
        int z;
        int radius;
        if (carriesPosition(shapeId)) {
            x = buffer.readUnsignedByte();
            y = buffer.readUnsignedByte();
            z = buffer.readUnsignedByte();
            radius = buffer.readUnsignedByte();
        } else {
            x = 128;
            y = buffer.readUnsignedByte();
            z = 128;
            radius = 255;
        }
        return new Spot(
                id, shapeId, x, y, z, radius,
                buffer.readUnsignedByte(), buffer.readUnsignedByte(),
                buffer.readLong());
    }

    private static void writeSpot(
            RegistryFriendlyByteBuf buffer, Spot spot) {
        buffer.writeVarInt(spot.id());
        buffer.writeByte(spot.shapeId());
        if (carriesPosition(spot.shapeId())) {
            buffer.writeByte(spot.x());
            buffer.writeByte(spot.y());
            buffer.writeByte(spot.z());
            buffer.writeByte(spot.radius());
        } else {
            buffer.writeByte(spot.y());
        }
        buffer.writeByte(spot.strength());
        buffer.writeByte(spot.mediumId());
        buffer.writeLong(spot.visualSource());
    }

    private static boolean carriesPosition(int shapeId) {
        return shapeId == 0 || shapeId == 3 || shapeId == 4;
    }

    public record Spot(
            int id, int shapeId,
            int x, int y, int z, int radius, int strength,
            int mediumId, long visualSource) {
        public Spot {
            id = Math.max(1, id);
            shapeId = Mth.clamp(shapeId, 0, 255);
            x = Mth.clamp(x, 0, 255);
            y = Mth.clamp(y, 0, 255);
            z = Mth.clamp(z, 0, 255);
            radius = Mth.clamp(radius, 0, 255);
            strength = Mth.clamp(strength, 0, 255);
            mediumId = Mth.clamp(mediumId, 0, 255);
        }
    }
}
