package com.fish.mirebound.mud;

import net.minecraft.util.StringRepresentable;

/** Per-block visual shape selection stored in the chunk block-state palette. */
public enum MudBlockVariant implements StringRepresentable {
    DEFAULT("default"),
    HEIGHT("height"),
    SPECIAL("special"),
    NATURAL_DEPTH("natural_depth"),
    NATURAL_DEPTH_END("natural_depth_end");

    private final String serializedName;

    MudBlockVariant(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public boolean naturalDepth() { return this == NATURAL_DEPTH || this == NATURAL_DEPTH_END; }

    public static MudBlockVariant byId(int id) {
        MudBlockVariant[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, id))];
    }
}
