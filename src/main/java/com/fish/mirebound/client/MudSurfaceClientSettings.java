package com.fish.mirebound.client;

import com.fish.mirebound.client.config.MireboundClientSettings;

/** Client-only budgets for procedural mud surface visuals. */
final class MudSurfaceClientSettings {
    private MudSurfaceClientSettings() {
    }

    static boolean enabled() {
        return MireboundClientSettings.mudSurfaceEnabled();
    }

    static boolean preciseModelGeometry() {
        return MireboundClientSettings.mudSurfacePreciseModelGeometry();
    }

    static double renderDistance() {
        return MireboundClientSettings.mudSurfaceRenderDistance();
    }

    static int maxHoles() {
        return MireboundClientSettings.mudSurfaceMaxHoles();
    }

    static int maxSurfaceCells() {
        return MireboundClientSettings.mudSurfaceMaxCells();
    }

    static int maxSideImprints() {
        return MireboundClientSettings.mudSurfaceMaxSideImprints();
    }

    static int maxSideCells() {
        return MireboundClientSettings.mudSurfaceMaxSideCells();
    }

    static int maxBubbles() {
        return MireboundClientSettings.mudSurfaceMaxBubbles();
    }

    static int ambientProbes() {
        return MireboundClientSettings.mudSurfaceAmbientProbes();
    }

    static int ambientIntervalTicks() {
        return MireboundClientSettings.mudSurfaceAmbientIntervalTicks();
    }

    static double armorRadiusBonus() {
        return MireboundClientSettings.mudSurfaceArmorRadiusBonus();
    }

    static double armorRadiusMaximum() {
        return MireboundClientSettings.mudSurfaceArmorRadiusMaximum();
    }

    static boolean insectMoundEnabled() {
        return MireboundClientSettings.insectMoundSurfaceEnabled();
    }

    static int insectMoundMaxPatches() {
        return MireboundClientSettings.insectMoundMaxPatches();
    }

    static int insectMoundScanInterval() {
        return MireboundClientSettings.insectMoundScanInterval();
    }

    static int insectMoundLarvaePerFace() {
        return MireboundClientSettings.insectMoundLarvaePerFace();
    }

    static int insectMoundMinLength() {
        return MireboundClientSettings.insectMoundMinLength();
    }

    static int insectMoundMaxLength() {
        return MireboundClientSettings.insectMoundMaxLength();
    }

    static double insectMoundBaseActivity() {
        return MireboundClientSettings.insectMoundBaseActivity();
    }

    static double insectMoundSoundVolume() {
        return MireboundClientSettings.insectMoundSoundVolume();
    }
}
