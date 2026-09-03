package com.fish.mirebound.mud;

final class LivingSlimeRuntimeState {
    private static final int DETACH_GRACE_TICKS = 12;

    double anchorX;
    double anchorY;
    double anchorZ;
    double impactEnergy;
    int detachedTicks;
    boolean anchorActive;
    boolean localFrame;

    boolean touch(double x, double y, double z) {
        boolean newContact = !anchorActive;
        if (!anchorActive) {
            anchorX = x;
            anchorY = y;
            anchorZ = z;
            anchorActive = true;
        }
        detachedTicks = 0;
        return newContact;
    }

    void follow(double x, double y, double z, double amount) {
        anchorX += (x - anchorX) * amount;
        anchorY += (y - anchorY) * amount;
        anchorZ += (z - anchorZ) * amount;
    }

    void detach() {
        if (anchorActive && ++detachedTicks > DETACH_GRACE_TICKS) {
            reset();
        }
    }

    void reset() {
        anchorX = 0.0D;
        anchorY = 0.0D;
        anchorZ = 0.0D;
        impactEnergy = 0.0D;
        detachedTicks = 0;
        anchorActive = false;
        localFrame = false;
    }
}
