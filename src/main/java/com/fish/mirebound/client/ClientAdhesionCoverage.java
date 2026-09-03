package com.fish.mirebound.client;

import com.fish.mirebound.mud.ArmorMudData;
import com.fish.mirebound.mud.ArmorMudManager;
import com.fish.mirebound.mud.MudBodyPart;
import com.fish.mirebound.mud.MudSurface;
import com.fish.mirebound.mud.MudSurfaceLayout;
import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Resolves the visible outer pollution source for reusable adhesion effects. */
final class ClientAdhesionCoverage {
    private ClientAdhesionCoverage() {
    }

    static Sample sample(Player player, MudBodyPart part, MudSurface surface, int row, int column) {
        int cell = MudSurfaceLayout.cellIndex(part, surface, row, column);
        EquipmentSlot outerSlot = null;
        ArmorMudData outerData = ArmorMudData.EMPTY;
        double outerOffset = Double.NEGATIVE_INFINITY;
        for (EquipmentSlot slot : ArmorMudManager.armorSlots()) {
            if (!ArmorMudManager.validArmor(player.getItemBySlot(slot), slot)
                    || !ArmorMudManager.slotOwnsSurface(slot, part, surface, row)) {
                continue;
            }
            double offset = ArmorMudManager.surfaceOffset(slot);
            if (offset > outerOffset) {
                outerSlot = slot;
                outerData = ArmorMudManager.data(player.getItemBySlot(slot));
                outerOffset = offset;
            }
        }
        if (outerSlot != null) {
            return new Sample(
                    outerData.coverageAt(cell), outerData.mediumAt(cell),
                    outerData.visualSourceAt(cell), outerOffset, outerSlot);
        }
        return new Sample(
                ClientMudState.displaySurfacePixelCoverage(
                        player.getId(), part, surface, row, column),
                ClientMudState.displaySurfacePixelMedium(
                        player.getId(), part, surface, row, column),
                ClientMudState.displaySurfacePixelVisualSource(
                        player.getId(), part, surface, row, column),
                0.0D,
                null);
    }

    record Sample(float coverage, SinkingMedium medium, long visualSource,
            double surfaceOffset, EquipmentSlot armorSlot) {
    }
}
