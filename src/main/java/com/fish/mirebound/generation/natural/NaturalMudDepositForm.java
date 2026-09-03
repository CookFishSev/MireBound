package com.fish.mirebound.generation.natural;

import java.util.Locale;

/** Distinct terrain silhouettes used by natural sinking-medium deposits. */
public enum NaturalMudDepositForm {
    RIVERBANK_CRESCENT(false, false),
    RIVERBED_RIBBON(false, false),
    DUNE_BLOWOUT(false, false),
    MARSH_MOSAIC(false, false),
    CAVE_SEEP(true, false),
    VOLCANIC_FISSURE(true, false),
    END_IMPACT_RING(false, false),
    ORGANIC_NEST(false, false),
    SURFACE_LAKE(false, true),
    UNDERGROUND_LAKE(true, true);

    private final boolean underground;
    private final boolean lake;

    NaturalMudDepositForm(boolean underground, boolean lake) {
        this.underground = underground;
        this.lake = lake;
    }

    public boolean underground() {
        return underground;
    }

    public boolean lake() {
        return lake;
    }

    public String translationKey() {
        return "gui.mirebound.worldgen.form."
                + name().toLowerCase(Locale.ROOT);
    }
}
