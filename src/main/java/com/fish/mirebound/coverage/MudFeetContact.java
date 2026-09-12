package com.fish.mirebound.coverage;

import net.minecraft.world.phys.Vec3;

/** Keeps animated skin and equipment probes above the authoritative feet plane. */
public final class MudFeetContact {
    public static final double ENTRY_INSET = 0.020D;

    private MudFeetContact() {
    }

    public static Vec3 entryPoint(double feetY, Vec3 point) {
        double minimumY = entryHeight(feetY);
        return point.y < minimumY ? new Vec3(point.x, minimumY, point.z) : point;
    }

    public static double entryHeight(double feetY) {
        return feetY + ENTRY_INSET;
    }
}
