package com.fish.mirebound.water;

import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class WaterGunItem extends Item {
    private static volatile int displayCapacity = WaterGunProfile.DEFAULT.capacity();

    public WaterGunItem(Properties properties) {
        super(properties);
    }

    public static int water(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WATER_GUN_WATER.get(), 0);
    }

    public static void setWater(ItemStack stack, int amount) {
        int clamped = Math.max(0, Math.min(MudPhysicsSettings.waterGunCapacity(), amount));
        if (clamped == 0) {
            stack.remove(ModDataComponents.WATER_GUN_WATER.get());
        } else {
            stack.set(ModDataComponents.WATER_GUN_WATER.get(), clamped);
        }
    }

    public static int displayCapacity() {
        return displayCapacity;
    }

    public static void setDisplayCapacity(int capacity) {
        displayCapacity = Math.max(1, capacity);
    }

    public static void resetDisplayCapacity() {
        displayCapacity = WaterGunProfile.DEFAULT.capacity();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return fillFrom(context.getLevel(), context.getItemInHand(), context.getClickedPos());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        InteractionResult result = fillFrom(level, stack, hit.getBlockPos());
        return result.consumesAction()
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.pass(stack);
    }

    public static boolean canFillFromView(Level level, Player player, ItemStack stack) {
        if (water(stack) >= displayCapacity()) {
            return false;
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        return isFillSource(level, hit.getBlockPos());
    }

    private static InteractionResult fillFrom(Level level, ItemStack stack, BlockPos pos) {
        int current = water(stack);
        int capacity = MudPhysicsSettings.waterGunCapacity();
        if (current >= capacity) {
            return InteractionResult.PASS;
        }
        BlockState state = level.getBlockState(pos);
        int added;
        boolean cauldron = state.is(Blocks.WATER_CAULDRON);
        if (cauldron) {
            added = Math.min(capacity - current, Math.max(1, (capacity + 2) / 3));
        } else if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource()) {
            added = capacity - current;
        } else {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            setWater(stack, current + added);
            if (cauldron) {
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            }
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.75F, 1.18F);
        }
        return InteractionResult.CONSUME;
    }

    private static boolean isFillSource(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER_CAULDRON)
                || level.getFluidState(pos).is(FluidTags.WATER)
                        && level.getFluidState(pos).isSource();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return water(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * water(stack) / displayCapacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x28B8E8;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mirebound.water_gun.water",
                water(stack), displayCapacity()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.mirebound.water_gun.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mirebound.water_gun.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
