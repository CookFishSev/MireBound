package com.fish.mirebound.mud;

public enum MudTuningScope {
    SINGLE,
    RANGE,
    WORLD;

    public static MudTuningScope byId(int id) {
        MudTuningScope[] values = values();
        return id >= 0 && id < values.length ? values[id] : SINGLE;
    }
}
