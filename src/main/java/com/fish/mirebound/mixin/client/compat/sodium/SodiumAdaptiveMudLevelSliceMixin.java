package com.fish.mirebound.mixin.client.compat.sodium;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.client.AdaptiveMudClientCache;
import com.fish.mirebound.mud.MudBlock;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Makes Sodium's render-only world slice see the stored source block state. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumAdaptiveMudLevelSliceMixin {
    @ModifyReturnValue(
            method = "getBlockState",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private BlockState mirebound$useAdaptiveSourceState(
            BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof AdaptiveMudBlock adaptive)
                || MudBlock.heightPixels(state, adaptive.medium()) != 16) {
            return state;
        }
        Level level = Minecraft.getInstance().level;
        BlockState source = AdaptiveMudClientCache.sourceState(level, pos);
        return source == null || source.getBlock() instanceof MudBlock ? state : source;
    }
}
