package com.fish.mirebound.mud.container;

import com.fish.mirebound.mud.SinkingMedium;

/** Shared eligibility rules for finite mud containers. */
public final class MudContainerRules {
    private MudContainerRules() {
    }

    public static boolean isBucketable(SinkingMedium medium) {
        return switch (medium) {
            case INSECT_MOUND, TENDER_FLESH -> false;
            default -> true;
        };
    }
}
