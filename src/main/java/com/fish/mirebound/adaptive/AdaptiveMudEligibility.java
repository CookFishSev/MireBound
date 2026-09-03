package com.fish.mirebound.adaptive;

import com.fish.mirebound.mud.MudBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Conservative compatibility gate for blocks used as adaptive mud appearances. */
public final class AdaptiveMudEligibility {
    private AdaptiveMudEligibility() {
    }

    public static Result check(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof AdaptiveMudBlock) {
            return Result.ALREADY_ADAPTIVE;
        }
        if (state.getBlock() instanceof MudBlock) {
            return Result.ALREADY_MUD;
        }
        if (state.isAir()) {
            return Result.AIR;
        }
        if (state.hasBlockEntity() || level.getBlockEntity(pos) != null) {
            return Result.BLOCK_ENTITY;
        }
        if (!state.getFluidState().isEmpty()) {
            return Result.FLUID;
        }
        if (state.getRenderShape() != RenderShape.MODEL) {
            return Result.DYNAMIC_RENDERER;
        }
        if (state.hasOffsetFunction()) {
            return Result.OFFSET_MODEL;
        }
        if (!Block.isShapeFullBlock(state.getShape(level, pos, CollisionContext.empty()))) {
            return Result.NON_FULL_MODEL;
        }
        if (!Block.isShapeFullBlock(state.getCollisionShape(level, pos, CollisionContext.empty()))) {
            return Result.NON_FULL_COLLISION;
        }
        if (state.isSignalSource()) {
            return Result.REDSTONE_COMPONENT;
        }
        return Result.SUPPORTED;
    }

    /** Shared conversion rule used by mutation, selection summaries, and object scans. */
    public static boolean canConvert(Result result, boolean forceAllBlocks) {
        return result != null && (result.supported()
                || forceAllBlocks
                        && result != Result.AIR
                        && result != Result.ALREADY_ADAPTIVE
                        && result != Result.ALREADY_MUD);
    }

    public enum Result {
        SUPPORTED("supported"),
        ALREADY_ADAPTIVE("already_adaptive"),
        ALREADY_MUD("already_mud"),
        AIR("air"),
        BLOCK_ENTITY("block_entity"),
        FLUID("fluid"),
        DYNAMIC_RENDERER("dynamic_renderer"),
        OFFSET_MODEL("offset_model"),
        NON_FULL_MODEL("non_full_model"),
        NON_FULL_COLLISION("non_full_collision"),
        REDSTONE_COMPONENT("redstone_component");

        private final String serializedName;

        Result(String serializedName) {
            this.serializedName = serializedName;
        }

        public String translationKey() {
            return "gui.mirebound.adaptive.compatibility." + serializedName;
        }

        public boolean supported() {
            return this == SUPPORTED;
        }
    }
}
