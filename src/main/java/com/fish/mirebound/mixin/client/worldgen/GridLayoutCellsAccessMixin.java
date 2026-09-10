package com.fish.mirebound.mixin.client.worldgen;

import java.util.List;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the layout's existing cell records without changing vanilla layout behavior. */
@Mixin(GridLayout.class)
public interface GridLayoutCellsAccessMixin {
    @Accessor("cellInhabitants")
    List<?> mirebound$getCellInhabitants();
}
