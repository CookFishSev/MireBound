package com.fish.mirebound.content.mudwork;

import com.fish.mirebound.registry.ModBlocks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Chargeable sling that turns one mud ball into a longer-ranged clod burst. */
public final class MudSlingItem extends Item {
    private static final int USE_DURATION = 72_000;
    private static final float MINIMUM_POWER = 0.10F;

    public MudSlingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)
                || (!player.getAbilities().instabuild
                && findAmmo(player).isEmpty())) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(
            ItemStack stack, Level level,
            LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }
        float power = MudSlingMechanics.chargePower(
                getUseDuration(stack, livingEntity) - timeLeft);
        if (power < MINIMUM_POWER) {
            return;
        }
        ItemStack ammo = findAmmo(player);
        if (ammo.isEmpty() && !player.getAbilities().instabuild) {
            return;
        }
        if (ammo.isEmpty()) {
            ammo = new ItemStack(ModBlocks.MUD_BALL.get());
        }
        boolean launched = MudClodLauncher.launch(player, ammo, power, true);
        if (!launched) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            ammo.shrink(1);
        }
        player.getCooldowns().addCooldown(this,
                MudSlingMechanics.cooldownTicks(power, true));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                1.0F, 0.58F + power * 0.34F);
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
    public void appendHoverText(
            ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "item.mirebound.mud_sling.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "item.mirebound.mud_sling.tooltip.2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static ItemStack findAmmo(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof MudBallItem) {
            return offhand;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.getItem() instanceof MudBallItem) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }
}
