package com.fish.mirebound.generation.natural;

import java.util.concurrent.atomic.AtomicReference;

/** Same-JVM bridge from the create-world screen to the integrated server. */
public final class NaturalMudWorldCreationBridge {
    private static final AtomicReference<NaturalMudGenerationProfile> STAGED =
            new AtomicReference<>();

    private NaturalMudWorldCreationBridge() {
    }

    public static void stage(NaturalMudGenerationProfile profile) {
        STAGED.set(profile);
    }

    public static NaturalMudGenerationProfile consumeOrDefault() {
        NaturalMudGenerationProfile staged = STAGED.getAndSet(null);
        return staged == null ? NaturalMudGenerationProfile.defaults() : staged;
    }

    public static void clear() {
        STAGED.set(null);
    }
}
