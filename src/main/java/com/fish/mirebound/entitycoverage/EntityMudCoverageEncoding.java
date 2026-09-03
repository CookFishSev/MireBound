package com.fish.mirebound.entitycoverage;

import com.fish.mirebound.mud.SinkingMedium;
import net.minecraft.util.Mth;

/** Shared byte-level representation used by diffing and payload construction. */
final class EntityMudCoverageEncoding {
    private EntityMudCoverageEncoding() {
    }

    static int signed(float value) {
        return Mth.clamp(Math.round((Mth.clamp(value, -1.0F, 1.0F)
                + 1.0F) * 127.5F), 0, 255);
    }

    static int unit(float value) {
        return Mth.clamp(Math.round(Mth.clamp(value, 0.0F, 1.0F)
                * 255.0F), 0, 255);
    }

    static int mediumId(SinkingMedium medium) {
        return medium == null ? SinkingMedium.COUNT : medium.id();
    }

    static long spotSignature(EntityMudCoverageSpot spot) {
        long value = spot.id();
        value = value * 31L + spot.shape().ordinal();
        if (spot.shape().localized()) {
            value = value * 31L + signed(spot.localX());
            value = value * 31L + signed(spot.localZ());
            value = value * 31L + unit(spot.radius());
        }
        value = value * 31L + unit(spot.localY());
        value = value * 31L + unit(spot.strength());
        value = value * 37L + mediumId(spot.medium());
        return value * 31L + spot.visualSource();
    }
}
