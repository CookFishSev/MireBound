package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Position-backed adaptive source states needed after a client rejoins. */
public record MudVisualSourceCatalogPayload(int entityId, long[] sources, int[] stateIds)
        implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 2048;
    public static final Type<MudVisualSourceCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "mud_visual_sources"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MudVisualSourceCatalogPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MudVisualSourceCatalogPayload decode(RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_ENTRIES) {
                        throw new IllegalArgumentException("Invalid visual source catalog size " + count);
                    }
                    long[] sources = new long[count];
                    int[] stateIds = new int[count];
                    for (int index = 0; index < count; index++) {
                        sources[index] = buffer.readLong();
                        stateIds[index] = buffer.readVarInt();
                    }
                    return new MudVisualSourceCatalogPayload(entityId, sources, stateIds);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        MudVisualSourceCatalogPayload payload) {
                    int count = Math.min(MAX_ENTRIES, Math.min(
                            payload.sources == null ? 0 : payload.sources.length,
                            payload.stateIds == null ? 0 : payload.stateIds.length));
                    buffer.writeVarInt(payload.entityId);
                    buffer.writeVarInt(count);
                    for (int index = 0; index < count; index++) {
                        buffer.writeLong(payload.sources[index]);
                        buffer.writeVarInt(Math.max(0, payload.stateIds[index]));
                    }
                }
            };

    public MudVisualSourceCatalogPayload {
        int count = Math.min(MAX_ENTRIES, Math.min(
                sources == null ? 0 : sources.length,
                stateIds == null ? 0 : stateIds.length));
        sources = sources == null ? new long[0] : Arrays.copyOf(sources, count);
        stateIds = stateIds == null ? new int[0] : Arrays.copyOf(stateIds, count);
    }

    @Override
    public Type<MudVisualSourceCatalogPayload> type() {
        return TYPE;
    }
}
