package com.fish.mirebound.mixin.compat.sable;

import com.fish.mirebound.assimilation.AssimilationSystem;
import com.fish.mirebound.compat.sable.SableCompat;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes stale vehicle plot tracking after Sable and Carry On finish their rider tick. */
@Mixin(value = ServerPlayer.class, priority = 900)
public abstract class SableAssimilationPlayerTrackingMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mirebound$clearFrozenPassengerTracking(CallbackInfo callback) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (AssimilationSystem.bypassSableVehicleTracking(player)) {
            SableCompat.clearEntityTracking(player);
        }
    }
}
