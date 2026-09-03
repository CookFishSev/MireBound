package com.fish.mirebound.compat.curios;

import com.fish.mirebound.mud.ArmorMudManager;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Optional Curios access isolated so the mod still loads without Curios. */
public final class CuriosCompat {
    private static final String MOD_ID = "curios";

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static ItemStack stack(LivingEntity entity, String identifier, int index, boolean cosmetic) {
        if (!isLoaded() || entity == null || !validAddress(identifier, index)) {
            return ItemStack.EMPTY;
        }
        return Access.stack(entity, identifier, index, cosmetic);
    }

    public static void commit(LivingEntity entity, String identifier, int index, boolean cosmetic,
            ItemStack stack) {
        if (isLoaded() && entity != null && !stack.isEmpty() && validAddress(identifier, index)) {
            Access.commit(entity, identifier, index, cosmetic, stack);
        }
    }

    public static boolean hasTextureMud(LivingEntity entity) {
        if (!isLoaded() || entity == null) {
            return false;
        }
        final boolean[] found = {false};
        Access.forEachStack(entity, stack -> found[0] |= !ArmorMudManager.textureData(stack).isEmpty());
        return found[0];
    }

    public static boolean validAddress(String identifier, int index) {
        return identifier != null && !identifier.isBlank() && identifier.length() <= 64
                && index >= 0 && index < 128;
    }

    private static final class Access {
        private Access() {
        }

        private static ItemStack stack(LivingEntity entity, String identifier, int index, boolean cosmetic) {
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
            if (inventory == null) {
                return ItemStack.EMPTY;
            }
            ICurioStacksHandler slots = inventory.getCurios().get(identifier);
            if (slots == null) {
                return ItemStack.EMPTY;
            }
            IDynamicStackHandler handler = cosmetic ? slots.getCosmeticStacks() : slots.getStacks();
            return index < handler.getSlots() ? handler.getStackInSlot(index) : ItemStack.EMPTY;
        }

        private static void commit(LivingEntity entity, String identifier, int index, boolean cosmetic,
                ItemStack stack) {
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
            if (inventory == null) {
                return;
            }
            ICurioStacksHandler slots = inventory.getCurios().get(identifier);
            if (slots == null) {
                return;
            }
            IDynamicStackHandler handler = cosmetic ? slots.getCosmeticStacks() : slots.getStacks();
            if (index < handler.getSlots() && handler.getStackInSlot(index) == stack) {
                handler.setStackInSlot(index, stack);
            }
        }

        private static void forEachStack(LivingEntity entity, Consumer<ItemStack> consumer) {
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
            if (inventory == null) {
                return;
            }
            for (ICurioStacksHandler slots : inventory.getCurios().values()) {
                accept(slots.getStacks(), consumer);
                accept(slots.getCosmeticStacks(), consumer);
            }
        }

        private static void accept(IDynamicStackHandler handler, Consumer<ItemStack> consumer) {
            for (int index = 0; index < handler.getSlots(); index++) {
                ItemStack stack = handler.getStackInSlot(index);
                if (!stack.isEmpty()) {
                    consumer.accept(stack);
                }
            }
        }
    }
}
