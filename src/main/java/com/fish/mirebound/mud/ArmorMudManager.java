package com.fish.mirebound.mud;

import com.fish.mirebound.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

public final class ArmorMudManager {
    public static final int BOOT_SIDE_ROWS = 6;
    public static final int ARMOR_SLOT_COUNT = 4;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final int[][] OWNED_CELL_INDEX = buildOwnedCellIndices();
    private static final int[] OWNED_CELL_COUNT = buildOwnedCellCounts();

    private ArmorMudManager() {
    }

    public static ArmorMudData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ARMOR_MUD.get(), ArmorMudData.EMPTY);
    }

    public static void store(ItemStack stack, ArmorMudData data) {
        if (data.isEmpty()) {
            stack.remove(ModDataComponents.ARMOR_MUD.get());
        } else {
            stack.set(ModDataComponents.ARMOR_MUD.get(), data);
        }
    }

    public static ArmorTextureMudData textureData(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ARMOR_TEXTURE_MUD.get(), ArmorTextureMudData.EMPTY);
    }

    public static void storeTextureData(ItemStack stack, ArmorTextureMudData data) {
        if (data.isEmpty()) {
            stack.remove(ModDataComponents.ARMOR_TEXTURE_MUD.get());
        } else {
            stack.set(ModDataComponents.ARMOR_TEXTURE_MUD.get(), data);
        }
    }

    public static boolean validArmor(ItemStack stack, EquipmentSlot slot) {
        return !stack.isEmpty()
                && stack.getItem() instanceof ArmorItem armor
                && armor.getEquipmentSlot() == slot;
    }

    public static float coverageFraction(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return 0.0F;
        }
        return coverageFraction(data(stack), armor.getEquipmentSlot());
    }

    static float coverageFraction(ArmorMudData data, EquipmentSlot slot) {
        int ownedCells = ownedCellCount(slot);
        if (data.isEmpty() || ownedCells == 0) {
            return 0.0F;
        }
        float[] total = {0.0F};
        data.forEach((cell, coverage, medium) -> {
            MudBodyPart part = MudSurfaceLayout.part(cell);
            MudSurface surface = MudSurfaceLayout.surface(cell);
            int row = MudSurfaceLayout.row(cell);
            if (slotOwnsSurface(slot, part, surface, row)) {
                total[0] += coverage;
            }
        });
        return Mth.clamp(total[0] / ownedCells, 0.0F, 1.0F);
    }

    public static int ownedCellCount(EquipmentSlot slot) {
        return OWNED_CELL_COUNT[armorSlotIndex(slot)];
    }

    public static int ownedCellIndex(EquipmentSlot slot, int cell) {
        if (cell < 0 || cell >= MudSurfaceLayout.CELL_COUNT) {
            return -1;
        }
        return OWNED_CELL_INDEX[armorSlotIndex(slot)][cell];
    }

    public static boolean slotOwnsSurface(EquipmentSlot slot, MudBodyPart part, MudSurface surface, int row) {
        return switch (slot) {
            case HEAD -> part == MudBodyPart.HEAD;
            case CHEST -> part == MudBodyPart.BODY
                    || part == MudBodyPart.LEFT_ARM
                    || part == MudBodyPart.RIGHT_ARM;
            case LEGS -> part == MudBodyPart.BODY
                    || part == MudBodyPart.LEFT_LEG
                    || part == MudBodyPart.RIGHT_LEG;
            case FEET -> (part == MudBodyPart.LEFT_LEG || part == MudBodyPart.RIGHT_LEG)
                    && (surface == MudSurface.BOTTOM || surface != MudSurface.TOP && row < BOOT_SIDE_ROWS);
            default -> false;
        };
    }

    public static double surfaceOffset(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS ? 0.034D : 0.066D;
    }

    public static void applyContacts(ServerPlayer player, MudPlayerData playerData) {
        for (EquipmentSlot slot : armorSlots()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!validArmor(stack, slot)) {
                continue;
            }
            if (MudEnchantmentEffects.preventsArmorStaining(player, stack)) {
                continue;
            }
            ArmorMudData original = data(stack);
            ArmorMudData.Builder builder = original.toBuilder();
            for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
                if (com.fish.mirebound.assimilation.AssimilationSystem
                        .keepsCrackClear(player, cell)) {
                    continue;
                }
                int contact = playerData.armorContactCoverage(slot, cell);
                if (contact == 0) {
                    continue;
                }
                MudBodyPart part = MudSurfaceLayout.part(cell);
                MudSurface surface = MudSurfaceLayout.surface(cell);
                int row = MudSurfaceLayout.row(cell);
                if (!slotOwnsSurface(slot, part, surface, row)) {
                    continue;
                }
                SinkingMedium medium = playerData.armorContactMedium(slot, cell);
                if (!allowsCoveragePixel(player, slot, cell, medium)) {
                    continue;
                }
                builder.mark(cell, contact / 255.0F, medium,
                        playerData.armorContactVisualSource(slot, cell));
            }
            if (builder.changed()) {
                blendSurfaceEdges(player, slot, builder, playerData);
                store(stack, builder.build());
            }
        }
    }

    public static boolean fadeNatural(ServerPlayer player) {
        boolean changed = false;
        for (EquipmentSlot slot : armorSlots()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!validArmor(stack, slot)) {
                continue;
            }
            ArmorMudData original = data(stack);
            if (original.isEmpty()) {
                continue;
            }
            ArmorMudData.Builder builder = original.toBuilder();
            original.forEach((cell, coverage, medium) -> {
                int fadeTicks = MudMediumRuntime.coverageNaturalFadeTicks(player.level(), medium);
                if (fadeTicks > 0) {
                    builder.fadeToFloor(cell, 1.0F / fadeTicks, 0.0F);
                }
            });
            if (builder.changed()) {
                store(stack, builder.build());
                changed = true;
            }
        }
        return changed;
    }

    public static EquipmentSlot[] armorSlots() {
        return ARMOR_SLOTS;
    }

    public static int armorSlotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> throw new IllegalArgumentException("Not an armor slot: " + slot);
        };
    }

    static boolean allowsCoveragePixel(Player player, EquipmentSlot slot,
            int cell, SinkingMedium medium) {
        int ownedIndex = ownedCellIndex(slot, cell);
        return ownedIndex >= 0 && MudCoverageRules.allowsPixel(
                player.level(),
                medium,
                MudCoverageRules.armorDomain(armorSlotIndex(slot)),
                ownedIndex,
                ownedCellCount(slot));
    }

    public static boolean blendSurfaceEdges(ServerPlayer player, EquipmentSlot slot,
            ArmorMudData.Builder builder) {
        return builder.blendSurfaceEdges(slot, (cell, mediumId) ->
                !com.fish.mirebound.assimilation.AssimilationSystem
                        .keepsCrackClear(player, cell)
                && allowsCoveragePixel(player, slot, cell,
                        SinkingMedium.byId(mediumId & 0xFF)));
    }

    public static boolean blendSurfaceEdges(ServerPlayer player, EquipmentSlot slot,
            ArmorMudData.Builder builder, MudPlayerData playerData) {
        return builder.blendSurfaceEdges(slot, (cell, mediumId) -> {
            MudBodyPart part = MudSurfaceLayout.part(cell);
            boolean legBottom = (part == MudBodyPart.LEFT_LEG || part == MudBodyPart.RIGHT_LEG)
                    && MudSurfaceLayout.surface(cell) == MudSurface.BOTTOM;
            return !com.fish.mirebound.assimilation.AssimilationSystem
                            .keepsCrackClear(player, cell)
                    && (!legBottom || playerData.armorContactCoverage(slot, cell) > 0)
                    && allowsCoveragePixel(player, slot, cell,
                            SinkingMedium.byId(mediumId & 0xFF));
        });
    }

    private static int[][] buildOwnedCellIndices() {
        int[][] result = new int[ARMOR_SLOT_COUNT][MudSurfaceLayout.CELL_COUNT];
        for (int slotIndex = 0; slotIndex < ARMOR_SLOT_COUNT; slotIndex++) {
            java.util.Arrays.fill(result[slotIndex], -1);
            EquipmentSlot slot = ARMOR_SLOTS[slotIndex];
            int ownedIndex = 0;
            for (int cell = 0; cell < MudSurfaceLayout.CELL_COUNT; cell++) {
                if (slotOwnsSurface(
                        slot,
                        MudSurfaceLayout.part(cell),
                        MudSurfaceLayout.surface(cell),
                        MudSurfaceLayout.row(cell))) {
                    result[slotIndex][cell] = ownedIndex++;
                }
            }
        }
        return result;
    }

    private static int[] buildOwnedCellCounts() {
        int[] result = new int[ARMOR_SLOT_COUNT];
        for (int slotIndex = 0; slotIndex < ARMOR_SLOT_COUNT; slotIndex++) {
            for (int cell : OWNED_CELL_INDEX[slotIndex]) {
                if (cell >= 0) {
                    result[slotIndex]++;
                }
            }
        }
        return result;
    }
}
