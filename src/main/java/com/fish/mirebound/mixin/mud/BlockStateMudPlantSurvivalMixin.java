package com.fish.mirebound.mixin.mud;

import com.fish.mirebound.mud.MudPlantSupport;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lets custom upward-growing plants use the same mud support rule as flowers. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMudPlantSurvivalMixin {
    @ModifyReturnValue(method = "canSurvive", at = @At("RETURN"))
    private boolean mirebound$allowPlantOnMud(
            boolean original, LevelReader level, BlockPos pos) {
        if (original) {
            return true;
        }
        BlockState plant = (BlockState) (Object) this;
        return MudPlantSupport.canSustain(
                level.getBlockState(pos.below()), Direction.UP, plant);
    }
}
