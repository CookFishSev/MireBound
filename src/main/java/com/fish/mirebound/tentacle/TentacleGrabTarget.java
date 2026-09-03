package com.fish.mirebound.tentacle;

public enum TentacleGrabTarget {
    NONE,
    HEAD,
    TORSO,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_HAND,
    RIGHT_HAND,
    LEFT_LEG,
    RIGHT_LEG,
    LEFT_FOOT,
    RIGHT_FOOT,
    WHOLE_BODY;

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static TentacleGrabTarget byName(String name) {
        if (name == null || name.equalsIgnoreCase("auto") || name.equalsIgnoreCase("none")) {
            return NONE;
        }
        for (TentacleGrabTarget target : values()) {
            if (target != NONE && target.serializedName().equalsIgnoreCase(name)) {
                return target;
            }
        }
        return null;
    }
}
