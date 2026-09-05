package com.fish.mirebound.stain;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MudFootprintBlock extends Block implements EntityBlock {
    static final PushReaction DECORATION_PUSH_REACTION = PushReaction.DESTROY;

    public MudFootprintBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MudFootprintBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return DECORATION_PUSH_REACTION;
    }

    @Override
    public void onDestroyedByPushReaction(
            BlockState state, Level level, BlockPos pos, Direction pushDirection, FluidState fluid) {
        if (level instanceof ServerLevel serverLevel) {
            MudDecalAccess.removeContainer(serverLevel, pos);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), MudDecalAccess.DECORATION_UPDATE_FLAGS);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos supportPos = pos.relative(direction);
            if (isValidSupport(level.getBlockState(supportPos), level, supportPos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidSupport(
            BlockState support, BlockGetter level, BlockPos supportPos) {
        boolean adaptive = support.getBlock() instanceof AdaptiveMudBlock;
        return isValidSupport(
                support.isAir(),
                support.getBlock() == ModBlocks.MUD_FOOTPRINT.get(),
                ModBlocks.isSinkingBlock(support.getBlock()) && !adaptive,
                supportShape(support, level, supportPos).isEmpty());
    }

    public static VoxelShape supportShape(
            BlockState support, BlockGetter level, BlockPos supportPos) {
        return support.getBlock() instanceof AdaptiveMudBlock
                ? AdaptiveMudBlock.sourceShape(level, supportPos, support)
                : support.getCollisionShape(level, supportPos);
    }

    static boolean isValidSupport(
            boolean air, boolean footprintContainer,
            boolean sinkingBlock, boolean collisionEmpty) {
        return !air && !footprintContainer && !sinkingBlock && !collisionEmpty;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MudFootprintBlockEntity blockEntity) {
            blockEntity.serverCheck(level);
        } else {
            MudDecalAccess.removeContainer(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level instanceof ServerLevel serverLevel
                && serverLevel.getBlockEntity(pos)
                        instanceof MudFootprintBlockEntity blockEntity
                && !isValidSupport(
                        serverLevel.getBlockState(neighborPos),
                        serverLevel, neighborPos)) {
            blockEntity.removeEntriesSupportedBy(serverLevel, neighborPos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState nextState, boolean movedByPiston) {
        if (!state.is(nextState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MudFootprintBlockEntity blockEntity) {
            blockEntity.unregisterAll(serverLevel);
        }
        super.onRemove(state, level, pos, nextState, movedByPiston);
    }
}
