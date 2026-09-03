package com.fish.mirebound.mixin.client.creative;

import com.fish.mirebound.registry.ModCreativeTabs;
import java.util.Collection;
import java.util.Set;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeTab.class)
public class CreativeModeTabSectionsMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void mirebound$buildSectionRows(
            CreativeModeTab.ItemDisplayParameters parameters,
            CallbackInfo callbackInfo) {
        if ((Object) this != ModCreativeTabs.MAIN.get()) {
            return;
        }

        ModCreativeTabs.SectionLayout layout = ModCreativeTabs.sectionLayout(parameters.holders());
        displayItems = layout.displayItems();
        displayItemsSearchTab = layout.searchItems();
    }
}
