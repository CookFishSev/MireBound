package com.fish.mirebound.mixin.client.worldgen;

import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the inherited tab layout without taking over any vanilla widget. */
@Mixin(GridLayoutTab.class)
public interface GridLayoutTabAccessMixin {
    @Accessor("layout")
    GridLayout mirebound$getLayout();
}
