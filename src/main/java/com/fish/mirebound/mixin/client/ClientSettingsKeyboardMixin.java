package com.fish.mirebound.mixin.client;

import com.fish.mirebound.client.ClientSettingsKey;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the Mirebound settings shortcut priority over vanilla fullscreen. */
@Mixin(KeyboardHandler.class)
public abstract class ClientSettingsKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mirebound$openClientSettings(
            long window, int key, int scanCode, int action, int modifiers,
            CallbackInfo callback) {
        if (ClientSettingsKey.handleKey(window, key, scanCode, action)) {
            callback.cancel();
        }
    }
}
