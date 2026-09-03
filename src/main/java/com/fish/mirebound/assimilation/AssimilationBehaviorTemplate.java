package com.fish.mirebound.assimilation;

import com.fish.mirebound.mud.SinkingMedium;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable behavior template shared by every medium bound to the same rules. */
public record AssimilationBehaviorTemplate(
        String id,
        AssimilationProfile profile,
        Set<SinkingMedium> media) {
    public static final String DEFAULT_ID = "default";

    public AssimilationBehaviorTemplate {
        id = id == null || id.isBlank() ? DEFAULT_ID : id;
        profile = profile == null ? AssimilationProfile.DEFAULT : profile;
        EnumSet<SinkingMedium> copy = media == null || media.isEmpty()
                ? EnumSet.noneOf(SinkingMedium.class)
                : EnumSet.copyOf(media);
        media = Collections.unmodifiableSet(copy);
    }

    public boolean appliesTo(SinkingMedium medium) {
        return medium != null && media.contains(medium);
    }
}
