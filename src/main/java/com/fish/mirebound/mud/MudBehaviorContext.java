package com.fish.mirebound.mud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Position-aware behavior switches shared by native and converted sinking blocks. */
public final class MudBehaviorContext {
    private MudBehaviorContext() {
    }

    public static boolean coverage(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.COVERAGE_ENABLED);
    }

    public static boolean coverage(Level level, SinkingMedium medium) {
        return medium != null && MudMediumRuntime.value(
                level, medium, MudPhysicsParameter.COVERAGE_ENABLED) >= 0.5D;
    }

    public static boolean adhesion(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.ADHESION_STRANDS_ENABLED)
                || enabled(level, pos, medium, MudPhysicsParameter.ADHESION_SHEET_ENABLED);
    }

    public static boolean assimilation(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.ASSIMILATION_ENABLED);
    }

    public static boolean tentacle(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.TENTACLE_ENABLED);
    }

    public static boolean swarm(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.SWARM_ENABLED);
    }

    public static boolean sculk(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.SCULK_ENABLED);
    }

    public static boolean tenderFlesh(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.FLESH_ENABLED);
    }

    public static boolean eruption(Level level, BlockPos pos, SinkingMedium medium) {
        return enabled(level, pos, medium, MudPhysicsParameter.ERUPTION_ENABLED);
    }

    private static boolean enabled(Level level, BlockPos pos, SinkingMedium medium,
            MudPhysicsParameter parameter) {
        return medium != null && MudMediumRuntime.value(level, pos, medium, parameter) >= 0.5D;
    }
}
