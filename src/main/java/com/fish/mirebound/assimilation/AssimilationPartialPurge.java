package com.fish.mirebound.assimilation;

import net.minecraft.util.Mth;

/** Pure timing and safety rules for the pre-stasis active rejection minigame. */
public final class AssimilationPartialPurge {
    public static final byte RESULT_NONE = 0;
    public static final byte RESULT_SUCCESS = 1;
    public static final byte RESULT_FAILURE = 2;

    private AssimilationPartialPurge() {
    }

    public static Cursor advance(float position, boolean forward, int oneWayTicks) {
        float step = 1.0F / Math.max(1, oneWayTicks);
        float next = position + (forward ? step : -step);
        boolean nextForward = forward;
        while (next > 1.0F || next < 0.0F) {
            if (next > 1.0F) {
                next = 2.0F - next;
                nextForward = false;
            } else {
                next = -next;
                nextForward = true;
            }
        }
        return new Cursor(Mth.clamp(next, 0.0F, 1.0F), nextForward);
    }

    public static boolean succeeds(float cursor, float zoneStart, float zoneEnd) {
        float minimum = Math.min(zoneStart, zoneEnd);
        float maximum = Math.max(zoneStart, zoneEnd);
        return cursor >= minimum && cursor <= maximum;
    }

    public static float nonLethalHealth(float health, float damage) {
        return Math.max(1.0F, health - Math.max(0.0F, damage));
    }

    public record Cursor(float position, boolean forward) {
    }
}
