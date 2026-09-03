package com.fish.mirebound.client;

/** Shared visibility policy for body-owned and camera-contact screen masks. */
final class ScreenMaskCameraPolicy {
    private ScreenMaskCameraPolicy() {
    }

    static boolean showsBodyOwnedMasks(boolean firstPerson) {
        return firstPerson;
    }

    static boolean showsDynamicMudMask(boolean firstPerson, boolean cameraInsideMud) {
        return firstPerson || cameraInsideMud;
    }
}
