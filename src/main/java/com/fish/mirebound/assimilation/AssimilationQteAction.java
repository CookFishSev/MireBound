package com.fish.mirebound.assimilation;

/** Input gesture requested by one assimilation self-rescue crack. */
public enum AssimilationQteAction {
    NONE,
    CLICK,
    HOLD,
    RAPID,
    TRACE;

    public static AssimilationQteAction byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NONE;
    }
}
