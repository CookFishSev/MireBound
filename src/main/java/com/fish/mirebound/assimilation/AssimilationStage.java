package com.fish.mirebound.assimilation;

public enum AssimilationStage {
    NORMAL,
    ASSIMILATING,
    SEALED,
    RESTORING;

    public static AssimilationStage byId(int id) {
        AssimilationStage[] values = values();
        return id >= 0 && id < values.length ? values[id] : NORMAL;
    }

    public boolean frozen() {
        return this == SEALED || this == RESTORING;
    }
}
