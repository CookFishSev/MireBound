package com.fish.mirebound.mixin.client.worldgen;

import com.fish.mirebound.client.worldgen.NaturalMudWorldCreationClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses the vanilla customize button for the normal world's mud ecology. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
abstract class CreateWorldScreenWorldTabMixin {
    @Shadow @Final private Button customizeTypeButton;
    @Shadow @Final private CreateWorldScreen this$0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mirebound$bindNaturalMudEditor(
            CreateWorldScreen outer, CallbackInfo callback) {
        outer.getUiState().addListener(ignored -> mirebound$refreshButton());
        mirebound$refreshButton();
    }

    @Inject(method = "openPresetEditor", at = @At("HEAD"), cancellable = true)
    private void mirebound$openNaturalMudEditor(CallbackInfo callback) {
        if (!NaturalMudWorldCreationClient.supports(this$0)) {
            return;
        }
        NaturalMudWorldCreationClient.open(this$0);
        callback.cancel();
    }

    private void mirebound$refreshButton() {
        if (NaturalMudWorldCreationClient.supports(this$0)) {
            customizeTypeButton.setMessage(Component.translatable(
                    "gui.mirebound.worldgen.open"));
            customizeTypeButton.active = !this$0.getUiState().isDebug();
            return;
        }
        customizeTypeButton.setMessage(Component.translatable(
                "selectWorld.customizeType"));
        customizeTypeButton.active = !this$0.getUiState().isDebug()
                && this$0.getUiState().getPresetEditor() != null;
    }
}
