package com.fish.mirebound.mud.container;

import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudVolumeState;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.flow.MudFlowSystem;
import com.fish.mirebound.registry.ModBlocks;
import com.fish.mirebound.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A finite mud container. Its data component is the sole volume authority. */
public final class MudBucketItem extends Item {
    private static final int FULL_TAR_BURN_TICKS = 100 * 200;

    public MudBucketItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(SinkingMedium medium, int pixels) {
        ItemStack stack = new ItemStack(ModBlocks.MUD_BUCKET.get());
        stack.set(ModDataComponents.MUD_VOLUME.get(), new MudVolumeData(medium, pixels));
        return stack;
    }

    @Override
    public int getBurnTime(
            ItemStack stack, @Nullable RecipeType<?> recipeType) {
        MudVolumeData data = stack.get(ModDataComponents.MUD_VOLUME.get());
        if (data == null || data.medium() != SinkingMedium.TAR) {
            return 0;
        }
        return Math.max(1,
                FULL_TAR_BURN_TICKS * data.pixels()
                        / MudVolumeData.MAX_PIXELS);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return getBurnTime(stack, null) > 0;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return hasCraftingRemainingItem(stack)
                ? new ItemStack(Items.BUCKET)
                : ItemStack.EMPTY;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        MudVolumeData data = stack.get(ModDataComponents.MUD_VOLUME.get());
        if (data == null) {
            return InteractionResult.PASS;
        }
        InteractionResult collection = MudVolumeContainerSystem.tryCollect(context, data);
        if (collection != InteractionResult.PASS) {
            return collection;
        }
        Level level = context.getLevel();
        Placement placement = placement(context, data.medium());
        if (placement == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            int added = Math.min(data.pixels(), 16 - placement.existingPixels());
            BlockState next = MudVolumeState.withPixels(
                    placement.baseState(), placement.existingPixels() + added, placement.facing());
            if (!level.setBlock(placement.pos(), next, Block.UPDATE_ALL)) {
                return InteractionResult.FAIL;
            }
            Player player = context.getPlayer();
            if (player != null && !player.isCreative()) {
                int remaining = data.pixels() - added;
                if (remaining <= 0) {
                    player.setItemInHand(context.getHand(), new ItemStack(Items.BUCKET));
                } else {
                    stack.set(ModDataComponents.MUD_VOLUME.get(),
                            new MudVolumeData(data.medium(), remaining));
                }
            }
            level.playSound(null, placement.pos(), SoundEvents.MUD_PLACE,
                    SoundSource.BLOCKS, 0.9F, 0.76F + level.random.nextFloat() * 0.08F);
            if (level instanceof ServerLevel serverLevel) {
                MudFlowSystem.wakeNow(serverLevel, placement.pos());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        MudVolumeData data = stack.get(ModDataComponents.MUD_VOLUME.get());
        return data == null
                ? super.getName(stack)
                : Component.translatable("item.mirebound.mud_bucket.filled",
                        Component.translatable("block.mirebound." + data.medium().serializedName()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        MudVolumeData data = stack.get(ModDataComponents.MUD_VOLUME.get());
        if (data != null) {
            tooltip.add(Component.translatable("item.mirebound.mud_volume", data.pixels())
                    .withStyle(ChatFormatting.GRAY));
            if (data.pixels() < 16) {
                tooltip.add(Component.translatable("item.mirebound.mud_bucket.tooltip.collect_all")
                        .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("item.mirebound.mud_bucket.tooltip.collect")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private static Placement placement(UseOnContext context, SinkingMedium medium) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clicked);
        if (clickedState.getBlock() instanceof MudBlock mudBlock
                && mudBlock.medium() == medium) {
            int pixels = MudVolumeState.pixels(clickedState);
            if (pixels < 16) {
                return new Placement(clicked, clickedState, pixels,
                        MudBlock.storedFacing(clickedState));
            }
        }
        BlockPlaceContext placementContext = new BlockPlaceContext(context);
        if (!placementContext.canPlace()) {
            return null;
        }
        BlockPos target = placementContext.getClickedPos();
        MudBlock block = ModBlocks.blockFor(medium);
        return block == null ? null : new Placement(
                target, block.defaultBlockState(), 0, context.getClickedFace());
    }

    private record Placement(BlockPos pos, BlockState baseState, int existingPixels,
            Direction facing) {
    }
}
