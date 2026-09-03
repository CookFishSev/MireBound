package com.fish.mirebound.client.tuning;

import java.util.Locale;
import java.util.List;

public enum MudTuningWandMode {
    RANGE,
    SINGLE,
    CONVERT,
    SUMMON,
    SETTINGS,
    GENERATION;

    private static final List<MudTuningWandMode> HUD_ORDER = List.of(
            RANGE, SINGLE, CONVERT, SUMMON, GENERATION, SETTINGS);

    public MudTuningWandMode cycle(int direction) {
        int index = HUD_ORDER.indexOf(this);
        return HUD_ORDER.get(Math.floorMod(index + direction, HUD_ORDER.size()));
    }

    public static List<MudTuningWandMode> hudOrder() {
        return HUD_ORDER;
    }

    public String translationKey() {
        return "hud.mirebound.tuning.mode." + name().toLowerCase(Locale.ROOT);
    }

    public boolean usesSpatialPlacement() {
        return this == SUMMON;
    }
}
