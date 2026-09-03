package com.fish.mirebound.mud;

import net.minecraft.util.StringRepresentable;

/** Per-block visual shape selection stored in the chunk block-state palette. */
public enum MudBlockVariant implements StringRepresentable {
    DEFAULT("default"),
    HEIGHT("height"),
    SPECIAL("special");

    private final String serializedName;

    MudBlockVariant(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static MudBlockVariant byId(int id) {
        MudBlockVariant[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, id))];
    }
}
