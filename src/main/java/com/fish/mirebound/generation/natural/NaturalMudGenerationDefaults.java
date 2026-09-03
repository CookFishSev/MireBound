package com.fish.mirebound.generation.natural;

import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile.Rule;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Curated ecology and rarity table for vanilla dimensions. */
final class NaturalMudGenerationDefaults {
    private static final String RIVER = "#minecraft:is_river";
    private static final String BEACH = "#minecraft:is_beach";
    private static final String OCEAN = "#minecraft:is_ocean";
    private static final String BADLANDS = "#minecraft:is_badlands";
    private static final String JUNGLE = "#minecraft:is_jungle";
    private static final String TAIGA = "#minecraft:is_taiga";
    private static final String END = "#minecraft:is_end";

    private NaturalMudGenerationDefaults() {
    }

    static NaturalMudGenerationProfile create() {
        List<Rule> rules = new ArrayList<>();
        rules.add(rule(SinkingMedium.MUD, 650, 4, 7, 1, 2, true,
                forms(NaturalMudDepositForm.RIVERBANK_CRESCENT,
                        NaturalMudDepositForm.MARSH_MOSAIC,
                        NaturalMudDepositForm.SURFACE_LAKE),
                RIVER, "minecraft:swamp", "minecraft:mangrove_swamp",
                "minecraft:plains", "minecraft:forest", "minecraft:meadow",
                "minecraft:taiga"));
        rules.add(rule(SinkingMedium.SOFT_QUICKSAND, 350, 4, 8, 1, 3, false,
                forms(NaturalMudDepositForm.DUNE_BLOWOUT,
                        NaturalMudDepositForm.MARSH_MOSAIC),
                "minecraft:desert", BEACH, "#minecraft:is_savanna"));
        rules.add(rule(SinkingMedium.SILT, 450, 4, 8, 1, 3, false,
                forms(NaturalMudDepositForm.RIVERBED_RIBBON,
                        NaturalMudDepositForm.RIVERBANK_CRESCENT),
                RIVER, OCEAN, BEACH));
        rules.add(rule(SinkingMedium.THIN_MUD, 350, 4, 7, 1, 2, false,
                forms(NaturalMudDepositForm.RIVERBANK_CRESCENT,
                        NaturalMudDepositForm.MARSH_MOSAIC),
                RIVER, "minecraft:swamp", "minecraft:mangrove_swamp"));
        rules.add(rule(SinkingMedium.SHALLOW_MUD, 400, 4, 8, 1, 2, false,
                forms(NaturalMudDepositForm.MARSH_MOSAIC,
                        NaturalMudDepositForm.RIVERBANK_CRESCENT,
                        NaturalMudDepositForm.SURFACE_LAKE),
                RIVER, "minecraft:swamp", "minecraft:mangrove_swamp"));
        rules.add(rule(SinkingMedium.TIDAL_MUD, 90, 4, 8, 1, 3, false,
                forms(NaturalMudDepositForm.RIVERBANK_CRESCENT,
                        NaturalMudDepositForm.RIVERBED_RIBBON),
                BEACH, OCEAN, "minecraft:mangrove_swamp"));
        rules.add(rule(SinkingMedium.PEAT_BOG, 50, 4, 8, 2, 4, false,
                forms(NaturalMudDepositForm.MARSH_MOSAIC),
                TAIGA, "minecraft:swamp", "minecraft:mangrove_swamp",
                "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga"));
        rules.add(rule(SinkingMedium.LIVING_SLIME, 20, 3, 6, 1, 2, false,
                forms(NaturalMudDepositForm.ORGANIC_NEST,
                        NaturalMudDepositForm.CAVE_SEEP),
                "minecraft:swamp", "minecraft:lush_caves"));
        rules.add(rule(SinkingMedium.TAR, 18, 3, 7, 2, 4, false,
                forms(NaturalMudDepositForm.CAVE_SEEP,
                        NaturalMudDepositForm.DUNE_BLOWOUT,
                        NaturalMudDepositForm.UNDERGROUND_LAKE),
                BADLANDS, "minecraft:desert", "minecraft:dripstone_caves"));
        rules.add(rule(SinkingMedium.JUNGLE_QUICKSAND, 80, 4, 8, 2, 4, false,
                forms(NaturalMudDepositForm.RIVERBANK_CRESCENT,
                        NaturalMudDepositForm.MARSH_MOSAIC),
                JUNGLE));
        rules.add(rule(SinkingMedium.INSECT_MOUND, 16, 3, 6, 1, 2, true,
                forms(NaturalMudDepositForm.ORGANIC_NEST),
                JUNGLE, "minecraft:mangrove_swamp", "minecraft:swamp"));
        rules.add(rule(SinkingMedium.RED_QUICKSAND, 90, 5, 9, 2, 4, false,
                forms(NaturalMudDepositForm.DUNE_BLOWOUT),
                BADLANDS, "minecraft:desert"));
        rules.add(rule(SinkingMedium.ASH_QUICKSAND, 100, 4, 8, 2, 4, false,
                forms(NaturalMudDepositForm.VOLCANIC_FISSURE,
                        NaturalMudDepositForm.CAVE_SEEP,
                        NaturalMudDepositForm.UNDERGROUND_LAKE),
                "minecraft:basalt_deltas", "minecraft:nether_wastes",
                "minecraft:soul_sand_valley"));
        rules.add(rule(SinkingMedium.SOUL_SILT, 120, 4, 8, 2, 4, false,
                forms(NaturalMudDepositForm.VOLCANIC_FISSURE,
                        NaturalMudDepositForm.CAVE_SEEP),
                "minecraft:soul_sand_valley"));
        rules.add(rule(SinkingMedium.GEL_CLAY, 35, 3, 6, 1, 3, false,
                forms(NaturalMudDepositForm.CAVE_SEEP,
                        NaturalMudDepositForm.RIVERBANK_CRESCENT),
                "minecraft:lush_caves", RIVER));
        rules.add(rule(SinkingMedium.LIME_MUD, 28, 3, 6, 1, 3, false,
                forms(NaturalMudDepositForm.CAVE_SEEP,
                        NaturalMudDepositForm.RIVERBANK_CRESCENT),
                "minecraft:dripstone_caves", BEACH));
        rules.add(rule(SinkingMedium.END_SILT, 650, 5, 9, 2, 4, false,
                forms(NaturalMudDepositForm.END_IMPACT_RING),
                "minecraft:end_highlands", "minecraft:end_midlands",
                "minecraft:end_barrens", "minecraft:small_end_islands", END));
        rules.add(rule(SinkingMedium.SCULK_MIRE, 18, 3, 6, 2, 4, false,
                forms(NaturalMudDepositForm.CAVE_SEEP,
                        NaturalMudDepositForm.UNDERGROUND_LAKE),
                "minecraft:deep_dark"));
        rules.add(rule(SinkingMedium.GRAVEL_SILT, 75, 4, 8, 1, 3, false,
                forms(NaturalMudDepositForm.RIVERBED_RIBBON,
                        NaturalMudDepositForm.RIVERBANK_CRESCENT),
                RIVER, OCEAN, "minecraft:stony_shore"));
        rules.add(rule(SinkingMedium.FUNGAL_MIRE, 80, 3, 7, 1, 3, false,
                forms(NaturalMudDepositForm.ORGANIC_NEST,
                        NaturalMudDepositForm.VOLCANIC_FISSURE),
                "minecraft:crimson_forest", "minecraft:warped_forest"));
        rules.add(rule(SinkingMedium.STONE_CLAY, 32, 3, 6, 1, 3, false,
                forms(NaturalMudDepositForm.CAVE_SEEP),
                "minecraft:dripstone_caves", "minecraft:stony_peaks",
                "minecraft:jagged_peaks"));
        rules.add(rule(SinkingMedium.PALE_MIRE, 28, 4, 7, 1, 3, false,
                forms(NaturalMudDepositForm.MARSH_MOSAIC,
                        NaturalMudDepositForm.RIVERBED_RIBBON),
                "minecraft:snowy_plains", "minecraft:grove",
                "minecraft:frozen_river", "minecraft:ice_spikes"));
        rules.add(rule(SinkingMedium.PEAT_SILT, 38, 4, 7, 1, 3, false,
                forms(NaturalMudDepositForm.RIVERBED_RIBBON,
                        NaturalMudDepositForm.MARSH_MOSAIC),
                "minecraft:swamp", TAIGA));
        rules.add(rule(SinkingMedium.TENDER_FLESH, 18, 3, 6, 1, 3, true,
                forms(NaturalMudDepositForm.ORGANIC_NEST),
                "minecraft:crimson_forest"));
        rules.add(rule(SinkingMedium.MIRE, 45, 4, 8, 2, 4, false,
                forms(NaturalMudDepositForm.MARSH_MOSAIC,
                        NaturalMudDepositForm.CAVE_SEEP),
                "minecraft:swamp", "minecraft:mangrove_swamp",
                "minecraft:dark_forest"));
        rules.add(rule(SinkingMedium.ASSIMILATION_SLIME, 10, 3, 5, 1, 2, true,
                forms(NaturalMudDepositForm.ORGANIC_NEST),
                "minecraft:crimson_forest", "minecraft:nether_wastes"));
        return new NaturalMudGenerationProfile(rules);
    }

    private static Rule rule(
            SinkingMedium medium, int chance,
            int minimumRadius, int maximumRadius,
            int minimumDepth, int maximumDepth,
            boolean fullHeightTop,
            List<NaturalMudDepositForm> forms,
            String... biomeSelectors) {
        Set<String> selectors = new LinkedHashSet<>(Arrays.asList(biomeSelectors));
        return new Rule(medium, true, chance, selectors, forms,
                minimumRadius, maximumRadius, minimumDepth, maximumDepth,
                fullHeightTop);
    }

    private static List<NaturalMudDepositForm> forms(
            NaturalMudDepositForm... forms) {
        return List.of(forms);
    }

}
