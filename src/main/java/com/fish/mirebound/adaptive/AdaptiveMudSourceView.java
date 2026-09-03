package com.fish.mirebound.adaptive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/** World view in which the proxy position is temporarily exposed as its source. */
final class AdaptiveMudSourceView implements BlockGetter {
    private final BlockGetter delegate;
    private final BlockPos sourcePos;
    private final BlockState sourceState;
    @Nullable
    private final BlockEntity sourceEntity;

    private AdaptiveMudSourceView(
            BlockGetter delegate, BlockPos sourcePos, BlockState sourceState) {
        this.delegate = delegate;
        this.sourcePos = sourcePos.immutable();
        this.sourceState = sourceState;
        BlockEntity current = delegate.getBlockEntity(sourcePos);
        this.sourceEntity = current instanceof AdaptiveMudBlockEntity adaptiveEntity
                ? adaptiveEntity.virtualSourceBlockEntity() : null;
    }

    static BlockGetter wrap(
            BlockGetter delegate, BlockPos sourcePos, BlockState sourceState) {
        return new AdaptiveMudSourceView(delegate, sourcePos, sourceState);
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return sourcePos.equals(pos) ? sourceEntity : delegate.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return sourcePos.equals(pos) ? sourceState : delegate.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return sourcePos.equals(pos)
                ? sourceState.getFluidState() : delegate.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }
}
