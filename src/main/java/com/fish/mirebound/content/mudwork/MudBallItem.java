package com.fish.mirebound.content.mudwork;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A hand-thrown mud clod backed by the bounded splash simulation. */
public final class MudBallItem extends Item {
    private static final float HAND_THROW_POWER = 0.28F;
    private final SinkingMedium medium;

    public MudBallItem(SinkingMedium medium, Properties properties) {
        super(properties);
        this.medium = medium;
    }

    public SinkingMedium medium() {
        return medium;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel
                && player instanceof ServerPlayer serverPlayer) {
            boolean launched = MudClodLauncher.launch(
                    serverPlayer, stack, HAND_THROW_POWER, false);
            if (launched) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.getCooldowns().addCooldown(this,
                        MudSlingMechanics.cooldownTicks(
                                HAND_THROW_POWER, false));
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                        0.72F, 0.72F + level.random.nextFloat() * 0.12F);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                "item.mirebound.mud_ball.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
