package com.fish.mirebound.mud.tuning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Pure coordinate update shared by server validation and tests. */
final class MudTuningSelectionMovement {
    private MudTuningSelectionMovement() {
    }

    static Result move(BlockPos first, BlockPos second,
            MudTuningSelectionElement element, Direction direction) {
        if (first == null || element == null || element == MudTuningSelectionElement.NONE
                || direction == null) {
            return null;
        }
        return switch (element) {
            case FIRST -> new Result(first.relative(direction), second);
            case SECOND -> second == null
                    ? null : new Result(first, second.relative(direction));
            case BODY -> second == null
                    ? null : new Result(first.relative(direction), second.relative(direction));
            case NONE -> null;
        };
    }

    record Result(BlockPos first, BlockPos second) {
    }
}
