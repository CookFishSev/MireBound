package com.fish.mirebound.network.payload;

import com.fish.mirebound.Mirebound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Chunk-scoped source states used to render adaptive mud without block entities. */
public record AdaptiveMudSourcesPayload(ResourceLocation dimension, int chunkX, int chunkZ,
        boolean replaceChunk, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 4096;
    public static final Type<AdaptiveMudSourcesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Mirebound.MOD_ID, "adaptive_mud_sources"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdaptiveMudSourcesPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AdaptiveMudSourcesPayload decode(RegistryFriendlyByteBuf buffer) {
                    ResourceLocation dimension = buffer.readResourceLocation();
                    int chunkX = buffer.readVarInt();
                    int chunkZ = buffer.readVarInt();
                    boolean replaceChunk = buffer.readBoolean();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_ENTRIES) {
                        throw new IllegalArgumentException(
                                "Invalid adaptive mud source count " + count);
                    }
                    List<Entry> entries = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        entries.add(new Entry(buffer.readLong(), Block.stateById(buffer.readVarInt())));
                    }
                    return new AdaptiveMudSourcesPayload(
                            dimension, chunkX, chunkZ, replaceChunk, List.copyOf(entries));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                        AdaptiveMudSourcesPayload payload) {
                    if (payload.entries.size() > MAX_ENTRIES) {
                        throw new IllegalArgumentException("Too many adaptive mud source states");
                    }
                    buffer.writeResourceLocation(payload.dimension);
                    buffer.writeVarInt(payload.chunkX);
                    buffer.writeVarInt(payload.chunkZ);
                    buffer.writeBoolean(payload.replaceChunk);
                    buffer.writeVarInt(payload.entries.size());
                    for (Entry entry : payload.entries) {
                        buffer.writeLong(entry.blockPos);
                        buffer.writeVarInt(Block.getId(entry.sourceState));
                    }
                }
            };

    @Override
    public Type<AdaptiveMudSourcesPayload> type() {
        return TYPE;
    }

    public AdaptiveMudSourcesPayload {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many adaptive mud source states");
        }
        for (Entry entry : entries) {
            if (entry == null || entry.sourceState() == null) {
                throw new IllegalArgumentException("Invalid adaptive mud source state");
            }
        }
    }

    public record Entry(long blockPos, BlockState sourceState) {
    }
}
