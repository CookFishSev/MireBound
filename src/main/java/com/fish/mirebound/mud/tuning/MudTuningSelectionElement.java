package com.fish.mirebound.mud.tuning;

/** Client-selected part of the server-owned tuning-wand range. */
public enum MudTuningSelectionElement {
    NONE,
    FIRST,
    SECOND,
    BODY;

    public static MudTuningSelectionElement byId(int id) {
        MudTuningSelectionElement[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }

    public static MudTuningSelectionElement next(
            MudTuningSelectionElement current, boolean firstSet, boolean secondSet) {
        if (!firstSet) {
            return NONE;
        }
        if (!secondSet) {
            return FIRST;
        }
        return switch (current) {
            case FIRST -> SECOND;
            case SECOND -> BODY;
            case NONE, BODY -> FIRST;
        };
    }
}
