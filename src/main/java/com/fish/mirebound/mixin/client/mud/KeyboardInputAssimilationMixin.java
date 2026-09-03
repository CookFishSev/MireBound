package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.ClientAssimilationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps physical key states available to the soul camera without moving the sealed body. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputAssimilationMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mirebound$suppressSealedBodyInput(boolean movingSlowly, float impulseScale,
            CallbackInfo callback) {
        if (!ClientAssimilationState.localStasisActive(Minecraft.getInstance())) {
            return;
        }
        leftImpulse = 0.0F;
        forwardImpulse = 0.0F;
        up = false;
        down = false;
        left = false;
        right = false;
        jumping = false;
        shiftKeyDown = false;
    }
}
