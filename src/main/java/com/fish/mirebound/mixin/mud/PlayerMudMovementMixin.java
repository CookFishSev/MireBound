package com.fish.mirebound.mixin.mud;

import com.fish.mirebound.mud.MudPhysics;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMudMovementMixin {
    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true, require = 1)
    private void mirebound$allowSneakingMovementInMud(Vec3 movement, MoverType mover,
            CallbackInfoReturnable<Vec3> callback) {
        Player player = (Player) (Object) this;
        if (MudPhysics.shouldBypassSneakEdgeBackoff(player)) {
            callback.setReturnValue(movement);
        }
    }
}
