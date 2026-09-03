package com.fish.mirebound.client;

import java.util.Locale;

/** Selects the client source used for post-animation player contact geometry. */
public enum ContactGeometryMode {
    AUTO("auto"),
    MODEL_PART("model_part"),
    SODIUM_VERTICES("sodium_vertices");

    private final String serializedName;

    ContactGeometryMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ContactGeometryMode byName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "model", "modelpart", "model_part", "parts" -> MODEL_PART;
            case "sodium", "vertex", "vertices", "sodium_vertex", "sodium_vertices" -> SODIUM_VERTICES;
            case "auto" -> AUTO;
            default -> null;
        };
    }
}
