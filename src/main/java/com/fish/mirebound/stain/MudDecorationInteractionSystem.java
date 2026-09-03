package com.fish.mirebound.stain;

import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Lets air-only item interactions replace hidden decorative stain containers. */
public final class MudDecorationInteractionSystem {
    private MudDecorationInteractionSystem() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().canPerformAction(ItemAbilities.FIRESTARTER_LIGHT)) {
            return;
        }
        UseOnContext context = new UseOnContext(
                event.getEntity(), event.getHand(), event.getHitVec());
        BlockState clicked = event.getLevel().getBlockState(event.getPos());
        if (clicked.getToolModifiedState(
                context, ItemAbilities.FIRESTARTER_LIGHT, true) != null) {
            return;
        }
        BlockPos target = event.getPos().relative(event.getFace());
        Level level = event.getLevel();
        if (level.getBlockState(target).getBlock() != ModBlocks.MUD_FOOTPRINT.get()) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            MudDecalAccess.removeContainer(serverLevel, target);
        } else {
            level.setBlock(target, Blocks.AIR.defaultBlockState(),
                    MudDecalAccess.DECORATION_UPDATE_FLAGS);
        }
    }
}
