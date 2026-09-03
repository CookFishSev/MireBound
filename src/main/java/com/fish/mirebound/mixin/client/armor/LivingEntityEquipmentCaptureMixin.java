package com.fish.mirebound.mixin.client.armor;

import com.fish.mirebound.client.ArmorAccessoryRenderContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// LivingEntity#getItemBySlot is abstract in 1.21.1. The concrete player method is
// the authoritative hook for custom wearable items placed in HEAD/CHEST/LEGS/FEET.
@Mixin(Player.class)
public abstract class LivingEntityEquipmentCaptureMixin {
    @Inject(
            method = "getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private void mirebound$captureCustomArmorSlot(EquipmentSlot slot,
            CallbackInfoReturnable<ItemStack> callback) {
        ArmorAccessoryRenderContext.recordEquipmentSlot(
                (Player) (Object) this, slot, callback.getReturnValue());
    }
}
