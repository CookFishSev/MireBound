package com.fish.mirebound.mixin.client.worldgen;

import com.fish.mirebound.client.worldgen.NaturalMudWorldCreationClient;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stages the selected per-world profile only when a new world is confirmed. */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenNaturalMudMixin {
    @Inject(method = "onCreate", at = @At("HEAD"))
    private void mirebound$stageNaturalMudProfile(CallbackInfo callback) {
        NaturalMudWorldCreationClient.stage(
                (CreateWorldScreen) (Object) this);
    }

    @Inject(method = "popScreen", at = @At("HEAD"))
    private void mirebound$discardNaturalMudProfile(CallbackInfo callback) {
        NaturalMudWorldCreationClient.discard(
                (CreateWorldScreen) (Object) this);
    }
}
