package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** One compact spawn message for a deterministic, non-entity mud splash batch. */
public record MudSplashPayload(
        double originX,
        double originY,
        double originZ,
        int sourceEntityId,
        SinkingMedium medium,
        long visualSource,
        float playerHitRadius,
        float gravity,
        float drag,
        int lifetimeTicks,
        long seed,
        List<Droplet> droplets) implements CustomPacketPayload {
    private static final int MAX_DROPLETS = 64;
    public static final Type<MudSplashPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_splash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudSplashPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudSplashPayload decode(RegistryFriendlyByteBuf buffer) {
                    double x = buffer.readDouble();
                    double y = buffer.readDouble();
                    double z = buffer.readDouble();
                    int sourceEntityId = buffer.readVarInt();
                    SinkingMedium medium = SinkingMedium.byId(buffer.readUnsignedByte());
                    long visualSource = buffer.readLong();
                    float playerHitRadius = buffer.readFloat();
                    float gravity = buffer.readFloat();
                    float drag = buffer.readFloat();
                    int lifetime = buffer.readUnsignedByte();
                    long seed = buffer.readLong();
                    int count = buffer.readUnsignedByte();
                    if (count < 1 || count > MAX_DROPLETS) {
                        throw new IllegalArgumentException("Invalid mud splash droplet count: " + count);
                    }
                    List<Droplet> droplets = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        droplets.add(new Droplet(
                                buffer.readFloat(),
                                buffer.readFloat(),
                                buffer.readFloat(),
                                buffer.readFloat(),
                                buffer.readBoolean(),
                                buffer.readFloat(),
                                buffer.readUnsignedByte(),
                                buffer.readUnsignedByte()));
                    }
                    return new MudSplashPayload(x, y, z, sourceEntityId, medium,
                            visualSource,
                            playerHitRadius, gravity, drag, lifetime, seed, droplets);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MudSplashPayload payload) {
                    buffer.writeDouble(payload.originX());
                    buffer.writeDouble(payload.originY());
                    buffer.writeDouble(payload.originZ());
                    buffer.writeVarInt(payload.sourceEntityId());
                    buffer.writeByte(payload.medium().id());
                    buffer.writeLong(payload.visualSource());
                    buffer.writeFloat(payload.playerHitRadius());
                    buffer.writeFloat(payload.gravity());
                    buffer.writeFloat(payload.drag());
                    buffer.writeByte(Mth.clamp(payload.lifetimeTicks(), 1, 255));
                    buffer.writeLong(payload.seed());
                    buffer.writeByte(payload.droplets().size());
                    for (Droplet droplet : payload.droplets()) {
                        buffer.writeFloat(droplet.velocityX());
                        buffer.writeFloat(droplet.velocityY());
                        buffer.writeFloat(droplet.velocityZ());
                        buffer.writeFloat(droplet.size());
                        buffer.writeBoolean(droplet.fountain());
                        buffer.writeFloat(droplet.breakupTriggerVelocityY());
                        buffer.writeByte(Mth.clamp(droplet.breakupDurationTicks(), 1, 12));
                        buffer.writeByte(Mth.clamp(droplet.columnTrailTicks(), 1, 40));
                    }
                }
            };

    public MudSplashPayload {
        droplets = List.copyOf(droplets);
    }

    public Vec3 origin() {
        return new Vec3(originX, originY, originZ);
    }

    @Override
    public Type<MudSplashPayload> type() {
        return TYPE;
    }

    public record Droplet(
            float velocityX,
            float velocityY,
            float velocityZ,
            float size,
            boolean fountain,
            float breakupTriggerVelocityY,
            int breakupDurationTicks,
            int columnTrailTicks) {
        public Droplet(float velocityX, float velocityY, float velocityZ, float size) {
            this(velocityX, velocityY, velocityZ, size,
                    false, -Float.MAX_VALUE, 1, 1);
        }

        public Vec3 velocity() {
            return new Vec3(velocityX, velocityY, velocityZ);
        }

    }
}
