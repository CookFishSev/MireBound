package com.fish.mirebound.mixin.client.mud;

import com.fish.mirebound.client.MudPlantSurfaceOffset;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies plant surface attachment before either vanilla or Sodium renders it. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMudPlantOffsetMixin {
    @ModifyReturnValue(method = "getOffset", at = @At("RETURN"))
    private Vec3 mirebound$fitPlantToMudSurface(
            Vec3 original, BlockGetter level, BlockPos pos) {
        return MudPlantSurfaceOffset.adjust(
                level, (net.minecraft.world.level.block.state.BlockState) (Object) this,
                pos, original);
    }
}
