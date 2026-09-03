package com.fish.mirebound.rope;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Chargeable item that launches the rope chain tip. */
public final class RopeItem extends Item {
    public static final int FULL_CHARGE_TICKS = 30;
    private static final int USE_DURATION = 72_000;

    public RopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level,
            LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }
        int heldTicks = getUseDuration(stack, livingEntity) - timeLeft;
        float charge = charge(heldTicks);
        if (charge < 0.08F) {
            RopeRuntime.consumeRescueCast(player, player.getUsedItemHand());
            return;
        }
        int segmentCount = stack.getCount();
        InteractionHand hand = player.getUsedItemHand();
        boolean rescueCast = RopeRuntime.consumeRescueCast(player, hand);
        if (!RopeRuntime.throwRope(
                player, charge, hand, segmentCount, rescueCast)) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(segmentCount);
        }
        player.getCooldowns().addCooldown(this, 8);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS,
                0.85F, 0.78F + charge * 0.34F);
    }

    public static float charge(int heldTicks) {
        float normalized = Mth.clamp(heldTicks / (float) FULL_CHARGE_TICKS,
                0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mirebound.rope.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mirebound.rope.tooltip.2",
                stack.getCount())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
