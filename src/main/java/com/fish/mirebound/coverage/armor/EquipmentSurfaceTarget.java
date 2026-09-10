package com.fish.mirebound.coverage.armor;

import com.fish.mirebound.compat.curios.CuriosCompat;
import com.fish.mirebound.compat.sophisticated.SophisticatedBackpackCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** An address re-resolved on the server; never an arbitrary client inventory write. */
public record EquipmentSurfaceTarget(int kind, int slot, String identifier, boolean cosmetic) {
    public EquipmentSurfaceTarget { identifier = identifier == null ? "" : identifier; }
    public static EquipmentSurfaceTarget armor(EquipmentSlot slot) { return new EquipmentSurfaceTarget(0, slot.ordinal(), "", false); }
    public static EquipmentSurfaceTarget backpack() { return new EquipmentSurfaceTarget(2, 0, "", false); }
    public boolean valid() {
        return switch (kind) {
            case 0 -> identifier.isEmpty() && !cosmetic && slot >= 0 && slot < EquipmentSlot.values().length
                    && EquipmentSlot.values()[slot].getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
            case 1 -> CuriosCompat.validAddress(identifier, slot);
            case 2 -> slot == 0 && identifier.isEmpty() && !cosmetic;
            default -> false;
        };
    }
    public ItemStack resolve(Player player) {
        if (!valid()) return ItemStack.EMPTY;
        return switch (kind) {
            case 0 -> player.getItemBySlot(EquipmentSlot.values()[slot]);
            case 1 -> CuriosCompat.stack(player, identifier, slot, cosmetic);
            case 2 -> SophisticatedBackpackCompat.renderedStack(player);
            default -> ItemStack.EMPTY;
        };
    }
    public void commit(Player player, ItemStack stack) {
        if (kind == 1) CuriosCompat.commit(player, identifier, slot, cosmetic, stack);
        else player.getInventory().setChanged();
    }
}
