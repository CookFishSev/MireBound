package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.ClientAssimilationState;
import com.fish.mirebound.client.AssimilationSoulCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies the synchronized assimilation look resistance only to the local body. */
@Mixin(Entity.class)
public abstract class EntityAssimilationLookMixin {
    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double mirebound$scaleAssimilationYaw(double value) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((Object) this != minecraft.player) {
            return value;
        }
        if (ClientAssimilationState.localStasisActive(minecraft)) {
            if (ClientAssimilationState.localSoulActive(minecraft)) {
                AssimilationSoulCamera.turnYaw(value);
            }
            return 0.0D;
        }
        return value * ClientAssimilationState.localLookScale(minecraft);
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double mirebound$scaleAssimilationPitch(double value) {
        Minecraft minecraft = Minecraft.getInstance();
        if ((Object) this != minecraft.player) {
            return value;
        }
        if (ClientAssimilationState.localStasisActive(minecraft)) {
            if (ClientAssimilationState.localSoulActive(minecraft)) {
                AssimilationSoulCamera.turnPitch(value);
            }
            return 0.0D;
        }
        return value * ClientAssimilationState.localLookScale(minecraft);
    }
}
