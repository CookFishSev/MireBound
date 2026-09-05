package com.fish.mirebound.mixin.client.worldgen;

import com.fish.mirebound.client.worldgen.NaturalMudWorldCreationClient;
import com.fish.mirebound.client.gui.MireflowButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a separate Mirebound world-generation entry to the vanilla world tab. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
abstract class CreateWorldScreenWorldTabMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mirebound$bindNaturalMudEditor(
            CreateWorldScreen outer, CallbackInfo callback) {
        Button button = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.open"),
                        ignored -> NaturalMudWorldCreationClient.open(outer))
                .tone(MireflowButton.Tone.INFO)
                .bounds(0, 0, 310, 20)
                .build();
        button.active = NaturalMudWorldCreationClient.supports(outer)
                && !outer.getUiState().isDebug();
        outer.getUiState().addListener(ignored -> button.active =
                NaturalMudWorldCreationClient.supports(outer)
                        && !outer.getUiState().isDebug());
        ((GridLayoutTabAccessMixin) (Object) this)
                .mirebound$getLayout().addChild(button, 4, 0, 1, 2);
    }

}
