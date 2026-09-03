package com.fish.mirebound.client.tentacle;

import com.fish.mirebound.client.config.MireboundClientSettings;

/** Tentacle-scoped view of the shared client configuration. */
final class TentacleClientSettings {
    private TentacleClientSettings() {
    }

    static int crossSectionSides() {
        return MireboundClientSettings.crossSectionSides();
    }

    static double renderDistance() {
        return MireboundClientSettings.renderDistance();
    }

    static double lodDistance() {
        return MireboundClientSettings.lodDistance();
    }

    static int lodStride() {
        return MireboundClientSettings.lodStride();
    }

    static int interpolationTicks() {
        return MireboundClientSettings.interpolationTicks();
    }

    static double surfaceVariation() {
        return MireboundClientSettings.surfaceVariation();
    }

    static double pulseAmplitude() {
        return MireboundClientSettings.pulseAmplitude();
    }

    static double pulseSpeed() {
        return MireboundClientSettings.pulseSpeed();
    }

    static double tipTaperStart() {
        return MireboundClientSettings.tipTaperStart();
    }

    static double tipRingScale() {
        return MireboundClientSettings.tipRingScale();
    }

    static double tipLengthScale() {
        return MireboundClientSettings.tipLengthScale();
    }

    static int rootCapRings() {
        return MireboundClientSettings.rootCapRings();
    }

    static double rootCapLengthScale() {
        return MireboundClientSettings.rootCapLengthScale();
    }

    static boolean castShaderShadows() {
        return MireboundClientSettings.castShaderShadows();
    }

    static GrabCameraMode grabCameraMode() {
        return switch (MireboundClientSettings.grabCameraMode()) {
            case OFF -> GrabCameraMode.OFF;
            case SMOOTH -> GrabCameraMode.SMOOTH;
            case IMMERSIVE -> GrabCameraMode.IMMERSIVE;
        };
    }

    static double grabCameraStrength() {
        return MireboundClientSettings.grabCameraStrength();
    }

    static boolean grabWrapEnabled() {
        return MireboundClientSettings.grabWrapEnabled();
    }

    static int grabWrapSegments() {
        return MireboundClientSettings.grabWrapSegments();
    }

    static double grabWrapTurns() {
        return MireboundClientSettings.grabWrapTurns();
    }

    static double grabWrapRadiusScale() {
        return MireboundClientSettings.grabWrapRadiusScale();
    }

    static double grabWrapLengthScale() {
        return MireboundClientSettings.grabWrapLengthScale();
    }

    static double grabWrapStrandRadiusScale() {
        return MireboundClientSettings.grabWrapStrandRadiusScale();
    }

    static double grabWrapWholeBodyTurnsScale() {
        return MireboundClientSettings.grabWrapWholeBodyTurnsScale();
    }

    static int grabWrapStrands() {
        return MireboundClientSettings.grabWrapStrands();
    }

    enum GrabCameraMode {
        OFF,
        SMOOTH,
        IMMERSIVE
    }
}
