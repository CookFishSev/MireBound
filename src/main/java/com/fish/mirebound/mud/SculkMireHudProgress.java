package com.fish.mirebound.mud;

import net.minecraft.util.Mth;

public final class SculkMireHudProgress {
    private SculkMireHudProgress() {
    }

    public static float escape(int quietTicks, int delayTicks) {
        return Mth.clamp(quietTicks / (float) Math.max(1, delayTicks), 0.0F, 1.0F);
    }

    public static float restraint(int remainingTicks, int observedMaximumTicks, float partialTick) {
        float remaining = Math.max(0.0F, remainingTicks - Mth.clamp(partialTick, 0.0F, 1.0F));
        return Mth.clamp(remaining / Math.max(1, observedMaximumTicks), 0.0F, 1.0F);
    }
}
