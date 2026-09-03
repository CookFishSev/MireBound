package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.generation.MudTerrainGenerationRequest;
import com.fish.mirebound.generation.MudTerrainGenerationSettings;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.MudTerrainRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Operator intent for the tuning wand's experimental terrain generator. */
public record MudTerrainGenerationPayload(
        Action action,
        MudTerrainGenerationRequest request) implements CustomPacketPayload {
    public static final Type<MudTerrainGenerationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Mirebound.MOD_ID, "mud_terrain_generation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
            MudTerrainGenerationPayload> STREAM_CODEC = new StreamCodec<>() {
                @Override
                public MudTerrainGenerationPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MudTerrainGenerationPayload(
                            Action.byId(buffer.readVarInt()),
                            decodeRequest(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        MudTerrainGenerationPayload payload) {
                    buffer.writeVarInt(payload.action.ordinal());
                    encodeRequest(buffer, payload.request);
                }
            };

    public MudTerrainGenerationPayload(
            Action action, BlockPos center, int radius, int thickness,
            double edgeRoughness, int heightTolerance, int seed,
            boolean sameSourceOnly) {
        this(action, new MudTerrainGenerationRequest(
                MudTerrainGenerationType.SURFACE_DEPOSIT, center, false,
                new MudTerrainGenerationSettings(
                        radius, thickness, edgeRoughness,
                        heightTolerance, seed, sameSourceOnly),
                defaultLakeSettings()));
    }

    public static MudTerrainGenerationPayload cancel() {
        return new MudTerrainGenerationPayload(Action.CANCEL, defaultRequest());
    }

    public static MudTerrainGenerationPayload undo() {
        return new MudTerrainGenerationPayload(Action.UNDO, defaultRequest());
    }

    private static MudTerrainGenerationRequest decodeRequest(
            RegistryFriendlyByteBuf buffer) {
        int typeId = buffer.readVarInt();
        MudTerrainGenerationType type = MudTerrainGenerationType.byId(typeId);
        BlockPos center = buffer.readBlockPos();
        boolean spatialPlacement = buffer.readBoolean();
        int radius = buffer.readVarInt();
        int thickness = buffer.readVarInt();
        double edgeRoughness = buffer.readDouble();
        int heightTolerance = buffer.readVarInt();
        int depositSeed = buffer.readVarInt();
        boolean sameSourceOnly = buffer.readBoolean();
        MudTerrainGenerationSettings deposit = new MudTerrainGenerationSettings(
                radius, thickness, edgeRoughness,
                heightTolerance, depositSeed, sameSourceOnly);
        int horizontalRadius = buffer.readVarInt();
        int verticalRadius = buffer.readVarInt();
        int lakeSeed = buffer.readVarInt();
        ResourceLocation shellBlockId = buffer.readResourceLocation();
        ResourceLocation innerBlockId = buffer.readResourceLocation();
        int surfaceHeightPixels = buffer.readVarInt();
        boolean clearUpperCavity = buffer.readBoolean();
        MudTerrainLakeSettings lake = new MudTerrainLakeSettings(
                horizontalRadius, verticalRadius, lakeSeed,
                shellBlockId, innerBlockId, surfaceHeightPixels,
                clearUpperCavity);
        int localXId = buffer.readVarInt();
        int localYId = buffer.readVarInt();
        int localZId = buffer.readVarInt();
        boolean directionsAccepted = validDirectionId(localXId)
                && validDirectionId(localYId)
                && validDirectionId(localZId);
        MudTerrainRotation rotation = new MudTerrainRotation(
                safeDirection(localXId), safeDirection(localYId), safeDirection(localZId));
        boolean wireValuesAccepted = MudTerrainGenerationType.validId(typeId)
                && MudTerrainGenerationSettings.validWireValues(
                        radius, thickness, edgeRoughness,
                        heightTolerance, depositSeed)
                && MudTerrainLakeSettings.validWireValues(
                        horizontalRadius, verticalRadius, lakeSeed,
                        shellBlockId, innerBlockId, surfaceHeightPixels)
                && directionsAccepted
                && rotation.valid();
        return new MudTerrainGenerationRequest(
                type, center, spatialPlacement, deposit, lake,
                rotation, wireValuesAccepted);
    }

    private static void encodeRequest(
            RegistryFriendlyByteBuf buffer, MudTerrainGenerationRequest request) {
        buffer.writeVarInt(request.type().ordinal());
        buffer.writeBlockPos(request.center());
        buffer.writeBoolean(request.spatialPlacement());
        MudTerrainGenerationSettings deposit = request.depositSettings();
        buffer.writeVarInt(deposit.radius());
        buffer.writeVarInt(deposit.thickness());
        buffer.writeDouble(deposit.edgeRoughness());
        buffer.writeVarInt(deposit.heightTolerance());
        buffer.writeVarInt(deposit.seed());
        buffer.writeBoolean(deposit.sameSourceOnly());
        MudTerrainLakeSettings lake = request.lakeSettings();
        buffer.writeVarInt(lake.horizontalRadius());
        buffer.writeVarInt(lake.verticalRadius());
        buffer.writeVarInt(lake.seed());
        buffer.writeResourceLocation(lake.shellBlockId());
        buffer.writeResourceLocation(lake.innerBlockId());
        buffer.writeVarInt(lake.surfaceHeightPixels());
        buffer.writeBoolean(lake.clearUpperCavity());
        buffer.writeVarInt(request.rotation().localX().get3DDataValue());
        buffer.writeVarInt(request.rotation().localY().get3DDataValue());
        buffer.writeVarInt(request.rotation().localZ().get3DDataValue());
    }

    private static boolean validDirectionId(int id) {
        return id >= 0 && id < Direction.values().length;
    }

    private static Direction safeDirection(int id) {
        return validDirectionId(id) ? Direction.from3DDataValue(id) : Direction.UP;
    }

    private static MudTerrainGenerationRequest defaultRequest() {
        return new MudTerrainGenerationRequest(
                MudTerrainGenerationType.defaultType(),
                BlockPos.ZERO, false,
                new MudTerrainGenerationSettings(12, 3, 0.55D, 6, 92821, false),
                defaultLakeSettings());
    }

    private static MudTerrainLakeSettings defaultLakeSettings() {
        return new MudTerrainLakeSettings(
                8, 4, 92821,
                MudTerrainLakeSettings.AIR, MudTerrainLakeSettings.AIR);
    }

    @Override
    public Type<MudTerrainGenerationPayload> type() {
        return TYPE;
    }

    public enum Action {
        GENERATE,
        CANCEL,
        UNDO,
        INVALID;

        private static Action byId(int id) {
            Action[] values = values();
            return id >= 0 && id < INVALID.ordinal() ? values[id] : INVALID;
        }
    }
}
