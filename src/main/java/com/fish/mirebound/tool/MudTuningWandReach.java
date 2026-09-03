package com.fish.mirebound.tool;

import com.fish.mirebound.Mirebound;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Applies the server-synchronized block reach used only while holding the tuning wand. */
public final class MudTuningWandReach {
    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            Mirebound.MOD_ID, "mud_tuning_wand_reach");

    private MudTuningWandReach() {
    }

    /** Recovers the configured range from our synced modifier past vanilla's 64-block cap. */
    public static double configuredInteractionRange(Player player) {
        if (player == null) {
            return MudPhysicsSettings.MUD_TUNING_WAND_DEFAULT_INTERACTION_RANGE;
        }
        AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        AttributeModifier modifier = reach == null ? null : reach.getModifier(MODIFIER_ID);
        return configuredInteractionRange(
                player.blockInteractionRange(),
                modifier == null ? 0.0D : modifier.amount(),
                modifier != null && modifier.operation()
                        == AttributeModifier.Operation.ADD_VALUE);
    }

    static double configuredInteractionRange(
            double visibleRange, double modifierAmount, boolean modifierPresent) {
        double range = modifierPresent
                ? Player.DEFAULT_BLOCK_INTERACTION_RANGE + modifierAmount
                : visibleRange;
        return Math.max(
                MudPhysicsSettings.MUD_TUNING_WAND_MINIMUM_INTERACTION_RANGE,
                Math.min(MudPhysicsSettings.MUD_TUNING_WAND_MAXIMUM_INTERACTION_RANGE,
                        range));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach == null) {
            return;
        }
        boolean holding = player.getMainHandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()
                || player.getOffhandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get();
        AttributeModifier current = reach.getModifier(MODIFIER_ID);
        if (!holding) {
            if (current != null) {
                reach.removeModifier(MODIFIER_ID);
            }
            return;
        }

        double amount = Math.max(0.0D,
                MudPhysicsSettings.mudTuningWandInteractionRange()
                        - Player.DEFAULT_BLOCK_INTERACTION_RANGE);
        if (current != null
                && current.operation() == AttributeModifier.Operation.ADD_VALUE
                && Math.abs(current.amount() - amount) <= 1.0E-9D) {
            return;
        }
        reach.addOrUpdateTransientModifier(new AttributeModifier(
                MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE));
    }
}
