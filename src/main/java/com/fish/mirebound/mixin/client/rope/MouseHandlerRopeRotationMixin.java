package com.fish.mirebound.mixin.client.rope;

import com.fish.mirebound.client.rope.ClientRopes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the two-axis physics-staff rotation before the player turn is committed. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerRopeRotationMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void mirebound$rotateDraggedRope(LocalPlayer player, double yaw, double pitch,
            Operation<Void> original) {
        if (!ClientRopes.onMouseTurn(yaw, pitch)) {
            original.call(player, yaw, pitch);
        }
    }
}
