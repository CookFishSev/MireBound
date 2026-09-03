package com.fish.mirebound.mud.harvest;

import net.minecraft.util.Mth;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Configurable vanilla-style tool families for sinking-medium harvesting. */
public enum MudHarvestTool {
    NONE,
    SHOVEL,
    HOE,
    PICKAXE,
    AXE,
    SWORD;

    public static MudHarvestTool byId(int id) {
        return values()[Mth.clamp(id, 0, values().length - 1)];
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (this) {
            case NONE -> false;
            case SHOVEL -> stack.getItem() instanceof ShovelItem;
            case HOE -> stack.getItem() instanceof HoeItem;
            case PICKAXE -> stack.getItem() instanceof PickaxeItem;
            case AXE -> stack.getItem() instanceof AxeItem;
            case SWORD -> stack.getItem() instanceof SwordItem;
        };
    }

    public float referenceDestroySpeed(ItemStack stack) {
        if (this == NONE || stack.isEmpty()) {
            return 1.0F;
        }
        return Math.max(1.0F, stack.getDestroySpeed(referenceState()));
    }

    public String translationKey() {
        return "gui.mirebound.physics.harvest_tool." + name().toLowerCase(java.util.Locale.ROOT);
    }

    private BlockState referenceState() {
        return switch (this) {
            case NONE -> Blocks.DIRT.defaultBlockState();
            case SHOVEL -> Blocks.DIRT.defaultBlockState();
            case HOE -> Blocks.SCULK.defaultBlockState();
            case PICKAXE -> Blocks.STONE.defaultBlockState();
            case AXE -> Blocks.OAK_LOG.defaultBlockState();
            case SWORD -> Blocks.COBWEB.defaultBlockState();
        };
    }
}
