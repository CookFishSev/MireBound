package com.fish.mirebound.mud;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Shared client/server timing rules for charged struggle releases. */
public final class MudStruggleTiming {
    public static final int MAX_CHARGE_TICKS = 20;

    private MudStruggleTiming() {
    }

    static int serverChargeTicks(int accumulatedTicks) {
        return Mth.clamp(accumulatedTicks, 0, MAX_CHARGE_TICKS);
    }

    static int configuredMaximumCooldown(
            Level level, BlockPos profilePos, SinkingMedium medium) {
        MudPhysicsParameter parameter = medium == SinkingMedium.LIVING_SLIME
                ? MudPhysicsParameter.SLIME_STRUGGLE_MAX_COOLDOWN_TICKS
                : MudPhysicsParameter.STRUGGLE_MAX_COOLDOWN_TICKS;
        return Mth.clamp((int) Math.round(
                MudMediumRuntime.value(level, profilePos, medium, parameter)), 0, 100);
    }

    public static int cooldownTicks(int chargeTicks, int maximumCooldownTicks) {
        int charge = Mth.clamp(chargeTicks, 0, MAX_CHARGE_TICKS);
        int maximum = Mth.clamp(maximumCooldownTicks, 0, 100);
        if (charge == 0 || maximum == 0) {
            return 0;
        }
        return Mth.clamp((int) Math.ceil(
                charge / (double) MAX_CHARGE_TICKS * maximum), 1, maximum);
    }
}
