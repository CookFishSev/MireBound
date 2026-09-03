package com.fish.mirebound.coverage;

import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudCoverageRules;
import com.fish.mirebound.mud.MudEnchantmentEffects;
import com.fish.mirebound.mud.MudPlayerData;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Shared skin-edge blending and inner-cleanliness ownership rules. */
public final class MudSkinCoverageOperations {
    private MudSkinCoverageOperations() {
    }

    public static int innerCleanlinessMask(Player player) {
        int mask = 0;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            var stack = player.getItemBySlot(slot);
            if (ArmorMudManager.validArmor(stack, slot)
                    && MudEnchantmentEffects.protectsInnerSkin(player, stack)) {
                mask |= 1 << ArmorMudManager.armorSlotIndex(slot);
            }
        }
        return mask;
    }

    public static boolean innerSkinProtected(
            int mask,
            MudBodyPart part,
            MudSurface surface,
            int row) {
        if (mask == 0) {
            return false;
        }
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            int bit = 1 << ArmorMudManager.armorSlotIndex(slot);
            if ((mask & bit) != 0
                    && ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                return true;
            }
        }
        return false;
    }

    public static void blendSkinSurfaceEdges(
            Player player, MudPlayerData data, int innerCleanlinessMask) {
        data.blendSurfaceEdges(
                cell -> !innerSkinProtected(innerCleanlinessMask, cell)
                        && !com.fish.mirebound.assimilation.AssimilationSystem
                                .keepsCrackClear(player, cell)
                        && (!isLegBottomCell(cell) || data.surfaceContactThisTick(cell)),
                (cell, mediumId) -> MudCoverageRules.allowsPixel(
                        player.level(),
                        SinkingMedium.byId(mediumId & 0xFF),
                        MudCoverageRules.DOMAIN_SKIN,
                        cell,
                        MudSurfaceLayout.CELL_COUNT));
    }

    private static boolean innerSkinProtected(int mask, int cell) {
        return innerSkinProtected(
                mask,
                MudSurfaceLayout.part(cell),
                MudSurfaceLayout.surface(cell),
                MudSurfaceLayout.row(cell));
    }

    private static boolean isLegBottomCell(int cell) {
        MudBodyPart part = MudSurfaceLayout.part(cell);
        return (part == MudBodyPart.LEFT_LEG || part == MudBodyPart.RIGHT_LEG)
                && MudSurfaceLayout.surface(cell) == MudSurface.BOTTOM;
    }
}
