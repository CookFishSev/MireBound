package com.fish.mirebound.content.mudwork;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/** One compact tar portion, worth fifteen ordinary furnace operations. */
public final class TarFuelItem extends Item {
    public static final int BURN_TICKS = 15 * 200;

    public TarFuelItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getBurnTime(
            ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return BURN_TICKS;
    }
}
