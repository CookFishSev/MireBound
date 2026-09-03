package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.water.WaterGunProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Server parameters used by local water-gun trajectory prediction and particles. */
public record WaterGunProfileSyncPayload(
        int capacity,
        int waterPerTick,
        float pressure,
        float gravity,
        float maximumRange,
        float segmentLength,
        float streamWidth,
        float firingMovementScale,
        float recoilDegrees,
        float baseWashRadius,
        float distanceSpread,
        float maximumWashRadius,
        int sableMaximumBlockSamples) implements CustomPacketPayload {
    public static final Type<WaterGunProfileSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "water_gun_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaterGunProfileSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WaterGunProfileSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new WaterGunProfileSyncPayload(
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readFloat(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, WaterGunProfileSyncPayload payload) {
                    buffer.writeVarInt(payload.capacity());
                    buffer.writeVarInt(payload.waterPerTick());
                    buffer.writeFloat(payload.pressure());
                    buffer.writeFloat(payload.gravity());
                    buffer.writeFloat(payload.maximumRange());
                    buffer.writeFloat(payload.segmentLength());
                    buffer.writeFloat(payload.streamWidth());
                    buffer.writeFloat(payload.firingMovementScale());
                    buffer.writeFloat(payload.recoilDegrees());
                    buffer.writeFloat(payload.baseWashRadius());
                    buffer.writeFloat(payload.distanceSpread());
                    buffer.writeFloat(payload.maximumWashRadius());
                    buffer.writeVarInt(payload.sableMaximumBlockSamples());
                }
            };

    public WaterGunProfileSyncPayload {
        capacity = Mth.clamp(capacity, 1, 1_000_000);
        waterPerTick = Mth.clamp(waterPerTick, 1, 1000);
        pressure = Mth.clamp(pressure, 0.05F, 8.0F);
        gravity = Mth.clamp(gravity, 0.0F, 1.0F);
        maximumRange = Mth.clamp(maximumRange, 1.0F, 64.0F);
        segmentLength = Mth.clamp(segmentLength, 0.10F, 2.0F);
        streamWidth = Mth.clamp(streamWidth, 0.005F, 0.25F);
        firingMovementScale = Mth.clamp(firingMovementScale, 0.10F, 1.0F);
        recoilDegrees = Mth.clamp(recoilDegrees, 0.0F, 12.0F);
        baseWashRadius = Mth.clamp(baseWashRadius, 0.05F, 4.0F);
        distanceSpread = Mth.clamp(distanceSpread, 0.0F, 0.5F);
        maximumWashRadius = Mth.clamp(maximumWashRadius, baseWashRadius, 4.0F);
        sableMaximumBlockSamples = Mth.clamp(sableMaximumBlockSamples, 32, 1024);
    }

    public static WaterGunProfileSyncPayload from(WaterGunProfile profile) {
        return new WaterGunProfileSyncPayload(
                profile.capacity(),
                profile.waterPerTick(),
                (float) profile.pressure(),
                (float) profile.gravity(),
                (float) profile.maximumRange(),
                (float) profile.segmentLength(),
                (float) profile.streamWidth(),
                (float) profile.firingMovementScale(),
                (float) profile.recoilDegrees(),
                profile.baseWashRadius(),
                profile.distanceSpread(),
                profile.maximumWashRadius(),
                profile.sableMaximumBlockSamples());
    }

    public WaterGunProfile applyTo(WaterGunProfile profile) {
        return profile.withVisualSettings(
                capacity,
                waterPerTick,
                pressure, gravity, maximumRange, segmentLength, streamWidth,
                firingMovementScale, recoilDegrees,
                baseWashRadius, distanceSpread, maximumWashRadius, sableMaximumBlockSamples);
    }

    @Override
    public Type<WaterGunProfileSyncPayload> type() {
        return TYPE;
    }
}
