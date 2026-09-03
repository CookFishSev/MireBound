package com.fish.mirebound.content.mudwork;

import net.minecraft.util.Mth;

/** Pure charge, speed, and payload envelope shared by mud throwing items. */
public final class MudSlingMechanics {
    private MudSlingMechanics() {
    }

    public static float chargePower(int useTicks) {
        float time = Math.max(0, useTicks) / 20.0F;
        return Mth.clamp((time * time + time * 2.0F) / 3.0F,
                0.0F, 1.0F);
    }

    public static double launchSpeed(float chargePower, boolean sling) {
        float power = Mth.clamp(chargePower, 0.0F, 1.0F);
        return sling ? 1.15D + power * 1.65D : 0.92D;
    }

    public static int fragmentCount(float chargePower, boolean sling) {
        if (!sling) {
            return 3;
        }
        return 3 + Math.round(Mth.clamp(chargePower, 0.0F, 1.0F) * 5.0F);
    }

    public static int cooldownTicks(float chargePower, boolean sling) {
        if (!sling) {
            return 10;
        }
        return 7 + Math.round(Mth.clamp(chargePower, 0.0F, 1.0F) * 5.0F);
    }
}
