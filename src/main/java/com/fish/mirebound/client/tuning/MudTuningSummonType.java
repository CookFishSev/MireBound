package com.fish.mirebound.client.tuning;

/** Client-selectable world objects exposed by the wand's summon mode. */
public enum MudTuningSummonType {
    TENTACLE("tentacle");

    private final String id;

    MudTuningSummonType(String id) {
        this.id = id;
    }

    public String translationKey() {
        return "hud.mirebound.tuning.summon." + id;
    }
}
