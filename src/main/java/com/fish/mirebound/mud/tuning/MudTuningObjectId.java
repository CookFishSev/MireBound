package com.fish.mirebound.mud.tuning;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Stable identity for one server-validated tuning object. */
public record MudTuningObjectId(Kind kind, int mediumId, ResourceLocation sourceBlockId) {
    private static final ResourceLocation NO_SOURCE = ResourceLocation.withDefaultNamespace("air");

    public MudTuningObjectId {
        Objects.requireNonNull(kind, "kind");
        sourceBlockId = sourceBlockId == null ? NO_SOURCE : sourceBlockId;
        if (kind == Kind.NATIVE_MEDIUM
                && (mediumId < 0 || mediumId >= SinkingMedium.COUNT)) {
            throw new IllegalArgumentException("Invalid native sinking medium " + mediumId);
        }
        if (kind != Kind.NATIVE_MEDIUM) {
            mediumId = -1;
        }
    }

    public static MudTuningObjectId nativeMedium(SinkingMedium medium) {
        return new MudTuningObjectId(Kind.NATIVE_MEDIUM, medium.id(), NO_SOURCE);
    }

    public static MudTuningObjectId sourceBlock(ResourceLocation blockId) {
        return new MudTuningObjectId(Kind.SOURCE_BLOCK, -1, blockId);
    }

    public static MudTuningObjectId convertedBlock(ResourceLocation blockId) {
        return new MudTuningObjectId(Kind.CONVERTED_BLOCK, -1, blockId);
    }

    public static MudTuningObjectId incompatibleBlock(ResourceLocation blockId) {
        return new MudTuningObjectId(Kind.INCOMPATIBLE_BLOCK, -1, blockId);
    }

    public static MudTuningObjectId adaptiveDefault() {
        return new MudTuningObjectId(Kind.ADAPTIVE_DEFAULT, -1, NO_SOURCE);
    }

    public static MudTuningObjectId tentacle() {
        return new MudTuningObjectId(Kind.TENTACLE, -1, NO_SOURCE);
    }

    public SinkingMedium nativeMedium() {
        return kind == Kind.NATIVE_MEDIUM ? SinkingMedium.byId(mediumId) : null;
    }

    public boolean hasSourceBlock() {
        return kind == Kind.SOURCE_BLOCK || kind == Kind.CONVERTED_BLOCK
                || kind == Kind.INCOMPATIBLE_BLOCK;
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(kind.ordinal());
        buffer.writeVarInt(mediumId + 1);
        buffer.writeResourceLocation(sourceBlockId);
    }

    public static MudTuningObjectId read(RegistryFriendlyByteBuf buffer) {
        int kindId = buffer.readVarInt();
        Kind[] kinds = Kind.values();
        if (kindId < 0 || kindId >= kinds.length) {
            throw new IllegalArgumentException("Invalid mud tuning object kind " + kindId);
        }
        return new MudTuningObjectId(
                kinds[kindId], buffer.readVarInt() - 1, buffer.readResourceLocation());
    }

    public enum Kind {
        NATIVE_MEDIUM,
        SOURCE_BLOCK,
        CONVERTED_BLOCK,
        INCOMPATIBLE_BLOCK,
        ADAPTIVE_DEFAULT,
        TENTACLE
    }
}
