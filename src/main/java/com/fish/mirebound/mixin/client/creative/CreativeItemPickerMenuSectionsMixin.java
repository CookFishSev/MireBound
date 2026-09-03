package com.fish.mirebound.mixin.client.creative;

import com.fish.mirebound.client.creative.FishCreativeSections;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public abstract class CreativeItemPickerMenuSectionsMixin {
    @Shadow
    protected abstract int getRowIndexForScroll(float scroll);

    @Inject(method = "scrollTo", at = @At("HEAD"))
    private void mirebound$trackVisibleRow(float scroll, CallbackInfo callbackInfo) {
        FishCreativeSections.setCurrentRow(getRowIndexForScroll(scroll));
    }
}
