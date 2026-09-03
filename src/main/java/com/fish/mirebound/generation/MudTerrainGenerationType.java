package com.fish.mirebound.generation;

import com.fish.mirebound.generation.natural.NaturalMudDepositForm;
import java.util.List;
import java.util.Locale;

/** Shape family selected by the tuning wand's terrain generator. */
public enum MudTerrainGenerationType {
    SURFACE_DEPOSIT(null),
    LAKE_POOL(null),
    LAKE_SURFACE(null),
    RIVERBANK_CRESCENT(NaturalMudDepositForm.RIVERBANK_CRESCENT),
    RIVERBED_RIBBON(NaturalMudDepositForm.RIVERBED_RIBBON),
    DUNE_BLOWOUT(NaturalMudDepositForm.DUNE_BLOWOUT),
    MARSH_MOSAIC(NaturalMudDepositForm.MARSH_MOSAIC),
    CAVE_SEEP(NaturalMudDepositForm.CAVE_SEEP),
    VOLCANIC_FISSURE(NaturalMudDepositForm.VOLCANIC_FISSURE),
    END_IMPACT_RING(NaturalMudDepositForm.END_IMPACT_RING),
    ORGANIC_NEST(NaturalMudDepositForm.ORGANIC_NEST);

    private static final List<MudTerrainGenerationType> SELECTABLE =
            List.of(LAKE_POOL, LAKE_SURFACE,
                    RIVERBANK_CRESCENT, RIVERBED_RIBBON,
                    DUNE_BLOWOUT, MARSH_MOSAIC,
                    CAVE_SEEP, VOLCANIC_FISSURE,
                    END_IMPACT_RING, ORGANIC_NEST);
    private final NaturalMudDepositForm naturalForm;

    MudTerrainGenerationType(NaturalMudDepositForm naturalForm) {
        this.naturalForm = naturalForm;
    }

    public MudTerrainGenerationType cycle(int direction) {
        int index = SELECTABLE.indexOf(this);
        if (index < 0) {
            return defaultType();
        }
        return SELECTABLE.get(Math.floorMod(index + direction, SELECTABLE.size()));
    }

    public String translationKey() {
        if (naturalForm != null) {
            return naturalForm.translationKey();
        }
        return "hud.mirebound.tuning.generation.type."
                + name().toLowerCase(Locale.ROOT);
    }

    public boolean isLake() {
        return this == LAKE_POOL || this == LAKE_SURFACE;
    }

    public boolean isNaturalDeposit() {
        return naturalForm != null;
    }

    public boolean usesDepositSettings() {
        return this == SURFACE_DEPOSIT || isNaturalDeposit();
    }

    public NaturalMudDepositForm naturalForm() {
        return naturalForm;
    }

    public static MudTerrainGenerationType byId(int id) {
        return validId(id) && values()[id] != SURFACE_DEPOSIT
                ? values()[id] : defaultType();
    }

    public static boolean validId(int id) {
        return id >= 0 && id < values().length;
    }

    public static List<MudTerrainGenerationType> selectableValues() {
        return SELECTABLE;
    }

    public static MudTerrainGenerationType defaultType() {
        return LAKE_POOL;
    }
}
