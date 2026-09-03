package com.fish.mirebound.tool;

import com.fish.mirebound.registry.ModBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Prevents a held tuning wand from activating or attacking world content. */
public final class MudTuningWandInteractionGuard {
    private MudTuningWandInteractionGuard() {
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isHolding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isHolding(event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isHolding(event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isHolding(event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void onEntityInteractSpecific(
            PlayerInteractEvent.EntityInteractSpecific event) {
        if (isHolding(event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (isHolding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isHolding(Player player) {
        return player.getMainHandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get()
                || player.getOffhandItem().getItem() == ModBlocks.MUD_TUNING_WAND.get();
    }
}
