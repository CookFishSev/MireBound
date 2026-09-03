package com.fish.mirebound.mud.container;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudVolumeState;
import com.fish.mirebound.mud.flow.MudFlowSystem;
import com.fish.mirebound.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Empty-bucket collection entry point for finite mud volumes. */
public final class MudVolumeContainerSystem {
    private MudVolumeContainerSystem() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Items.BUCKET)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof MudBlock mudBlock)
                || state.getBlock() instanceof AdaptiveMudBlock
                || !MudContainerRules.isBucketable(mudBlock.medium())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(state.getBlock() instanceof MudBlock)) {
            return;
        }

        collect(level, event.getPos(), state, mudBlock, event.getEntity(),
                event.getItemStack(), event.getHand(), null,
                event.getEntity().isShiftKeyDown());
    }

    /** Lets a partially filled bucket collect more of the same medium before placement. */
    static InteractionResult tryCollect(UseOnContext context, MudVolumeData stored) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof MudBlock mudBlock)
                || state.getBlock() instanceof AdaptiveMudBlock
                || !MudContainerRules.isBucketable(mudBlock.medium())
                || mudBlock.medium() != stored.medium()
                || collectionAmount(stored.pixels(), MudVolumeState.pixels(state),
                        context.isSecondaryUseActive()) <= 0) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            Player player = context.getPlayer();
            if (player == null || !collect(serverLevel, context.getClickedPos(), state,
                    mudBlock, player, context.getItemInHand(), context.getHand(), stored,
                    context.isSecondaryUseActive())) {
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean collect(ServerLevel level, BlockPos pos, BlockState state,
            MudBlock mudBlock, Player player, ItemStack stack,
            net.minecraft.world.InteractionHand hand, MudVolumeData stored, boolean singlePixel) {
        int storedPixels = stored == null ? 0 : stored.pixels();
        int available = MudVolumeState.pixels(state);
        int taken = collectionAmount(storedPixels, available, singlePixel);
        if (taken <= 0) {
            return false;
        }
        int remaining = available - taken;
        BlockState next = remaining <= 0
                ? state.getFluidState().createLegacyBlock()
                : MudVolumeState.withPixels(state, remaining, MudBlock.storedFacing(state));
        if (!level.setBlock(pos, next, Block.UPDATE_ALL)) {
            return false;
        }
        if (stored == null) {
            giveFilledBucket(player, stack, hand,
                    MudBucketItem.create(mudBlock.medium(), taken));
        } else {
            stack.set(ModDataComponents.MUD_VOLUME.get(),
                    new MudVolumeData(stored.medium(), storedPixels + taken));
        }
        float amountScale = taken / 16.0F;
        level.playSound(null, pos, SoundEvents.MUD_BREAK, SoundSource.BLOCKS,
                0.72F + amountScale * 0.35F,
                0.78F + level.random.nextFloat() * 0.1F - amountScale * 0.08F);
        MudFlowSystem.wakeNeighbors(level, pos);
        return true;
    }

    static int collectionAmount(int storedPixels, int availablePixels, boolean singlePixel) {
        int capacity = Math.max(0, 16 - storedPixels);
        int available = Math.max(0, availablePixels);
        return Math.min(capacity, singlePixel ? Math.min(1, available) : available);
    }

    private static void giveFilledBucket(Player player, ItemStack empty,
            net.minecraft.world.InteractionHand hand, ItemStack filled) {
        if (player.isCreative()) {
            player.getInventory().placeItemBackInInventory(filled);
            return;
        }
        if (empty.getCount() == 1) {
            player.setItemInHand(hand, filled);
            return;
        }
        empty.shrink(1);
        player.getInventory().placeItemBackInInventory(filled);
    }
}
