package com.fish.mirebound.mixin.compat.sable;

import com.fish.mirebound.compat.sable.SableTrackingSync;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem", remap = false)
public abstract class SableTrackingSyncMixin {
    @Inject(
            method = "sendFullSync(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private void mirebound$syncPositionData(
            ServerPlayer player, @Coerce Object subLevel,
            CustomPacketPayload extraPacket, CallbackInfo callback) {
        SableTrackingSync.onFullSync(player, subLevel);
    }
}
