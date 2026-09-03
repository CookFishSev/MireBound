package com.fish.mirebound.client.swarm;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import com.fish.mirebound.mud.SinkingMedium;

public final class ClientSwarmState {
    private static float target;
    private static float displayed;
    private static BlockPos profilePos;
    private static SinkingMedium medium = SinkingMedium.INSECT_MOUND;

    private ClientSwarmState() {
    }

    public static void setTarget(
            float strength, BlockPos sourceProfilePos, SinkingMedium sourceMedium) {
        target = Mth.clamp(strength, 0.0F, 1.0F);
        profilePos = sourceProfilePos;
        medium = sourceMedium == null ? SinkingMedium.INSECT_MOUND : sourceMedium;
    }

    public static void tick() {
        float rate = target > displayed ? 0.16F : 0.08F;
        displayed += (target - displayed) * rate;
        if (displayed < 0.001F && target <= 0.0F) {
            displayed = 0.0F;
        }
    }

    public static float displayed() {
        return displayed;
    }

    public static BlockPos profilePos() {
        return profilePos;
    }

    public static SinkingMedium medium() {
        return medium;
    }

    public static void reset() {
        target = 0.0F;
        displayed = 0.0F;
        profilePos = null;
        medium = SinkingMedium.INSECT_MOUND;
    }
}
