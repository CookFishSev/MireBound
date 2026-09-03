package com.fish.mirebound.mixin.client.creative;

import com.fish.mirebound.client.creative.FishCreativeSections;
import com.fish.mirebound.registry.ModCreativeTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenSectionsMixin {
    @Shadow
    private static CreativeModeTab selectedTab;

    @Inject(method = "render", at = @At("TAIL"))
    private void mirebound$renderSectionBanners(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callbackInfo) {
        if (selectedTab != ModCreativeTabs.MAIN.get()) {
            return;
        }

        FishCreativeSections.render(
                (CreativeModeInventoryScreen) (Object) this,
                graphics);
    }
}
