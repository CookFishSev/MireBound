package com.fish.mirebound.mixin.mud;

import com.fish.mirebound.assimilation.AssimilationSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Accept the final server teleport destination, never a client movement request. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerAssimilationTeleportMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"), require = 1)
    private void mirebound$moveFrozenBodyAnchor(CallbackInfo ci) {
        AssimilationSystem.onBodyTeleported(player);
    }
}
