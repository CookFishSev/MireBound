package com.fish.mirebound.mud;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Identifies a tuning position in either the main level or one stable Sable sub-level. */
public record MudTuningAnchor(UUID subLevelId, BlockPos pos) {
    public static final UUID WORLD_SUB_LEVEL_ID = new UUID(0L, 0L);
    public static final MudTuningAnchor WORLD_ORIGIN = world(BlockPos.ZERO);

    public MudTuningAnchor {
        Objects.requireNonNull(subLevelId, "subLevelId");
        pos = Objects.requireNonNull(pos, "pos").immutable();
    }

    public static MudTuningAnchor world(BlockPos pos) {
        return new MudTuningAnchor(WORLD_SUB_LEVEL_ID, pos);
    }

    public static MudTuningAnchor sable(UUID subLevelId, BlockPos pos) {
        if (WORLD_SUB_LEVEL_ID.equals(subLevelId)) {
            throw new IllegalArgumentException("Sable anchor requires a non-world sub-level ID");
        }
        return new MudTuningAnchor(subLevelId, pos);
    }

    public boolean isSable() {
        return !WORLD_SUB_LEVEL_ID.equals(subLevelId);
    }

    public boolean sameDomain(MudTuningAnchor other) {
        return other != null && subLevelId.equals(other.subLevelId);
    }

    public MudTuningAnchor withPos(BlockPos replacement) {
        return new MudTuningAnchor(subLevelId, replacement);
    }

    public static MudTuningAnchor read(RegistryFriendlyByteBuf buffer) {
        return new MudTuningAnchor(buffer.readUUID(), buffer.readBlockPos());
    }

    public static void write(RegistryFriendlyByteBuf buffer, MudTuningAnchor anchor) {
        buffer.writeUUID(anchor.subLevelId);
        buffer.writeBlockPos(anchor.pos);
    }
}
