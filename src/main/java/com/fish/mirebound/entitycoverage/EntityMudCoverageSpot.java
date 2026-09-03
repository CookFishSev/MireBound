package com.fish.mirebound.entitycoverage;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.util.Mth;

/** One compact entity-local stain used to project mud onto a living model. */
public record EntityMudCoverageSpot(
        int id,
        Shape shape,
        float localX, float localY, float localZ,
        float radius, float strength,
        SinkingMedium medium, long visualSource) {
    public EntityMudCoverageSpot {
        id = Math.max(1, id);
        shape = shape == null ? Shape.RADIAL : shape;
        localX = Mth.clamp(finite(localX), -1.0F, 1.0F);
        localY = Mth.clamp(finite(localY), 0.0F, 1.0F);
        localZ = Mth.clamp(finite(localZ), -1.0F, 1.0F);
        radius = Mth.clamp(finite(radius), 0.01F, 1.0F);
        strength = Mth.clamp(finite(strength), 0.0F, 1.0F);
        if (medium == null) {
            throw new IllegalArgumentException("medium");
        }
    }

    public boolean sameSource(SinkingMedium otherMedium, long otherVisualSource) {
        return medium == otherMedium && visualSource == otherVisualSource;
    }

    public float distanceSquared(float x, float y, float z) {
        float dx = localX - x;
        float dy = localY - y;
        float dz = localZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float horizontalDistanceSquared(float x, float z) {
        float dx = localX - x;
        float dz = localZ - z;
        return dx * dx + dz * dz;
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    public enum Shape {
        RADIAL,
        LOWER_VOLUME,
        UPPER_VOLUME,
        LOWER_CONTACT_VOLUME,
        UPPER_CONTACT_VOLUME;

        public boolean localized() {
            return this == RADIAL
                    || this == LOWER_CONTACT_VOLUME
                    || this == UPPER_CONTACT_VOLUME;
        }

        public boolean volume() {
            return this != RADIAL;
        }

        public boolean lowerVolume() {
            return this == LOWER_VOLUME || this == LOWER_CONTACT_VOLUME;
        }

        public static Shape byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : RADIAL;
        }
    }
}
