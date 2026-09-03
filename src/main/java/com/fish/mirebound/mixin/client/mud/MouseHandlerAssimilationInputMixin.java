package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.AssimilationQteClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 2000)
public abstract class MouseHandlerAssimilationInputMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void mirebound$captureAssimilationMouseInput(long windowPointer, int button,
            int action, int modifiers, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (windowPointer == minecraft.getWindow().getWindow()
                && AssimilationQteClient.handleRawMouseButton(minecraft, button, action)) {
            callback.cancel();
        }
    }
}
