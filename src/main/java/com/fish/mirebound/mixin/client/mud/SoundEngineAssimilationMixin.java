package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.AssimilationSoulPresentation;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps rescue/body sounds directional while making the distant soul world feel muted. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineAssimilationMixin {
    @ModifyReturnValue(
            method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"))
    private float mirebound$dampenDistantSoulSounds(float original, SoundInstance sound) {
        if (sound.isRelative()) {
            return original;
        }
        return original * AssimilationSoulPresentation.soundScale(Minecraft.getInstance());
    }
}
