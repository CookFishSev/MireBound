package com.fish.mirebound.content.mudwork;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A bulk batch of wet adobe that dries outdoors without a block entity. */
public final class WetAdobeBlock extends Block {
    public static final IntegerProperty DRYNESS = IntegerProperty.create(
            "dryness", 0, WetAdobeDrying.MAXIMUM_DRYNESS);

    private final Supplier<? extends Block> driedBlock;

    public WetAdobeBlock(
            Properties properties, Supplier<? extends Block> driedBlock) {
        super(properties);
        this.driedBlock = driedBlock;
        registerDefaultState(stateDefinition.any().setValue(DRYNESS, 0));
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(
            BlockState state, ServerLevel level,
            BlockPos pos, RandomSource random) {
        BlockPos exposedPos = pos.above();
        WetAdobeDrying.Result result = WetAdobeDrying.update(
                state.getValue(DRYNESS),
                level.canSeeSky(exposedPos),
                level.isRainingAt(exposedPos),
                random.nextDouble());
        if (result.complete()) {
            level.setBlock(pos, driedBlock.get().defaultBlockState(),
                    Block.UPDATE_ALL);
        } else if (result.dryness() != state.getValue(DRYNESS)) {
            level.setBlock(pos, state.setValue(DRYNESS, result.dryness()),
                    Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRYNESS);
    }
}
