package com.fish.mirebound.mixin.client.worldgen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes only the mapped grid coordinates needed to find a free insertion row. */
@Mixin(targets = "net.minecraft.client.gui.layouts.GridLayout$CellInhabitant")
public interface GridLayoutCellInhabitantAccessMixin {
    @Accessor("row")
    int mirebound$getRow();

    @Accessor("column")
    int mirebound$getColumn();

    @Accessor("occupiedRows")
    int mirebound$getOccupiedRows();

    @Accessor("occupiedColumns")
    int mirebound$getOccupiedColumns();
}
