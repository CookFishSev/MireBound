package com.fish.mirebound.tentacle;

import java.util.Locale;

public enum TentacleGrabMode {
    HOLD,
    WRAP,
    THRASH;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TentacleGrabMode byName(String name) {
        for (TentacleGrabMode mode : values()) {
            if (mode.serializedName().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }
}
