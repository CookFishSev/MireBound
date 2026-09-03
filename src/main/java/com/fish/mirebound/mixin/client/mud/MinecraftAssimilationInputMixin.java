package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.ClientAssimilationState;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents number keys from changing the anchored body's selected hotbar slot. */
@Mixin(Minecraft.class)
public abstract class MinecraftAssimilationInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void mirebound$consumeSoulHotbarKeys(CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!ClientAssimilationState.localStasisActive(minecraft)) {
            return;
        }
        while (minecraft.options.keyTogglePerspective.consumeClick()) {
            // The detached soul always uses its own first-person camera.
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        for (KeyMapping hotbarKey : minecraft.options.keyHotbarSlots) {
            while (hotbarKey.consumeClick()) {
                // Consume every queued number-key click before vanilla changes the selected slot.
            }
        }
    }
}
