package com.fish.mirebound.adaptive;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Carries the source block-entity render snapshot while a block is proxied. */
public final class AdaptiveMudBlockEntity extends BlockEntity {
    private static final String SOURCE_STATE_TAG = "SourceState";
    private static final String SOURCE_BLOCK_ENTITY_TAG = "SourceBlockEntity";

    private BlockState sourceState;
    private CompoundTag sourceBlockEntityData;
    private BlockEntity virtualSourceBlockEntity;

    public AdaptiveMudBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ADAPTIVE_MUD_ENTITY.get(), pos, state);
    }

    public void configure(BlockState sourceState, @Nullable CompoundTag sourceBlockEntityData) {
        this.sourceState = sourceState;
        this.sourceBlockEntityData = sourceBlockEntityData == null
                ? null : AdaptiveMudSourceStore.boundedBlockEntityData(sourceBlockEntityData);
        virtualSourceBlockEntity = null;
        setChanged();
    }

    @Nullable
    public BlockEntity virtualSourceBlockEntity() {
        if (level == null || sourceState == null) {
            return null;
        }
        if (virtualSourceBlockEntity == null) {
            CompoundTag data = sourceBlockEntityData;
            if (level instanceof ServerLevel serverLevel) {
                CompoundTag stored = AdaptiveMudSourceStore.get(serverLevel)
                        .sourceBlockEntityData(worldPosition);
                if (stored != null) {
                    data = stored;
                }
            }
            if (data == null) {
                return null;
            }
            if (level instanceof ServerLevel) {
                virtualSourceBlockEntity = BlockEntity.loadStatic(
                        worldPosition, sourceState, data.copy(),
                        level.registryAccess());
            } else {
                CompoundTag identity = new CompoundTag();
                identity.putString("id", data.getString("id"));
                identity.putInt("x", worldPosition.getX());
                identity.putInt("y", worldPosition.getY());
                identity.putInt("z", worldPosition.getZ());
                virtualSourceBlockEntity = BlockEntity.loadStatic(
                        worldPosition, sourceState, identity,
                        level.registryAccess());
            }
            if (virtualSourceBlockEntity != null) {
                virtualSourceBlockEntity.setLevel(level);
                if (!(level instanceof ServerLevel)) {
                    virtualSourceBlockEntity.handleUpdateTag(
                            data.copy(), level.registryAccess());
                }
            }
        }
        return virtualSourceBlockEntity;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (sourceState != null) {
            tag.put(SOURCE_STATE_TAG, NbtUtils.writeBlockState(sourceState));
        }
        if (sourceBlockEntityData != null) {
            tag.put(SOURCE_BLOCK_ENTITY_TAG, sourceBlockEntityData.copy());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sourceState = null;
        sourceBlockEntityData = null;
        virtualSourceBlockEntity = null;
        if (tag.contains(SOURCE_STATE_TAG, Tag.TAG_COMPOUND)) {
            try {
                BlockState loaded = NbtUtils.readBlockState(
                        registries.lookupOrThrow(Registries.BLOCK),
                        tag.getCompound(SOURCE_STATE_TAG));
                if (!loaded.isAir() && !(loaded.getBlock() instanceof AdaptiveMudBlock)) {
                    sourceState = loaded;
                }
            } catch (RuntimeException exception) {
                Mirebound.LOGGER.warn("Ignoring invalid adaptive mud source state at {}",
                        worldPosition);
            }
        }
        if (sourceState != null
                && tag.contains(SOURCE_BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) {
            sourceBlockEntityData = AdaptiveMudSourceStore.boundedBlockEntityData(
                    tag.getCompound(SOURCE_BLOCK_ENTITY_TAG));
        }
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
