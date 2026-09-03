package com.fish.mirebound.mud;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Canonical conversion between finite mud volume and the existing height state. */
public final class MudVolumeState {
    private MudVolumeState() {
    }

    public static int pixels(BlockState state) {
        return MudBlock.storedHeight(state);
    }

    public static BlockState withPixels(BlockState state, int pixels, Direction facing) {
        int clamped = Math.max(1, Math.min(16, pixels));
        BlockState result = state;
        if (result.hasProperty(MudBlock.VARIANT)) {
            result = result.setValue(MudBlock.VARIANT,
                    clamped == 16 ? MudBlockVariant.DEFAULT : MudBlockVariant.HEIGHT);
        }
        if (result.hasProperty(MudBlock.HEIGHT)) {
            result = result.setValue(MudBlock.HEIGHT, clamped);
        }
        if (result.hasProperty(MudBlock.FACING)) {
            result = result.setValue(MudBlock.FACING, facing);
        }
        if (result.hasProperty(MudBlock.STACK_FILLED)) {
            result = result.setValue(MudBlock.STACK_FILLED, false);
        }
        return result;
    }
}
