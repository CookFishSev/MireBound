package com.fish.mirebound.mixin.client.tuning;

import com.fish.mirebound.client.MudTuningSectionHighlightCache;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockVariant;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelTuningHighlightMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void mirebound$invalidateTuningHighlight(BlockPos pos, BlockState state,
            int flags, int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && (Object) this instanceof ClientLevel
                && !(state.getBlock() instanceof MudBlock
                && MudBlock.variant(state) != MudBlockVariant.SPECIAL)) {
            MudTuningSectionHighlightCache.invalidate(pos);
        }
    }
}
