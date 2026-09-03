package com.fish.mirebound.mud.harvest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the drop table against the regression it was written to fix: a medium that silently drops
 * nothing when broken.
 *
 * <p>The table is asserted through source text rather than by loading {@link MudDropRules}, because
 * that class resolves vanilla {@code Items} and the mod's deferred registries, neither of which is
 * available without a running game. Source-level assertions are the same technique
 * {@code ModCreativeTabsTest} uses for registry-backed content.
 */
class MudDropRulesCoverageTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/fish/mirebound/mud/harvest/MudDropRules.java");
    private static final Path DROP_SYSTEM = Path.of(
            "src/main/java/com/fish/mirebound/mud/harvest/MudVolumeDropSystem.java");

    @Test
    void everyMediumHasExactlyOneDropRule() throws Exception {
        String source = Files.readString(SOURCE);
        List<String> missing = new ArrayList<>();
        for (SinkingMedium medium : SinkingMedium.values()) {
            String key = "rules.put(SinkingMedium." + medium.name() + ",";
            int occurrences = count(source, key);
            if (occurrences == 0) {
                missing.add(medium.name());
            } else {
                assertEquals(1, occurrences,
                        medium.name() + " must have exactly one drop rule");
            }
        }
        assertTrue(missing.isEmpty(),
                "media with no drop rule would be destroyed with no drop: " + missing);
    }

    @Test
    void tableSizeMatchesTheMediumCount() throws Exception {
        String source = Files.readString(SOURCE);
        assertEquals(SinkingMedium.values().length, count(source, "rules.put(SinkingMedium."),
                "the table must stay exactly total over SinkingMedium");
    }

    @Test
    void dropSystemNoLongerSpecialCasesPlainMud() throws Exception {
        String source = Files.readString(DROP_SYSTEM);
        // This was the whole bug: every medium except MUD returned before any drop was added.
        assertFalse(source.contains("medium() != SinkingMedium.MUD"),
                "the plain-mud special case must stay removed");
        assertTrue(source.contains("MudDropRules.ruleFor("),
                "drops must resolve through the standardized table");
    }

    @Test
    void rulesDoNotBypassVanillaExplorationRewards() throws Exception {
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("Items.ECHO_SHARD"));
        assertFalse(source.contains("Items.NAUTILUS_SHELL"));
    }

    @Test
    void everyChineseMudMediumUsesItsOwnScaledMudBall() throws Exception {
        String source = Files.readString(SOURCE);
        Map<SinkingMedium, String> expected = Map.of(
                SinkingMedium.MUD, "MUD_BALL",
                SinkingMedium.THIN_MUD, "THIN_MUD_BALL",
                SinkingMedium.SHALLOW_MUD, "SHALLOW_MUD_BALL",
                SinkingMedium.TIDAL_MUD, "TIDAL_MUD_BALL",
                SinkingMedium.LIME_MUD, "LIME_MUD_BALL",
                SinkingMedium.GRAVEL_SILT, "GRAVEL_SILT_MUD_BALL",
                SinkingMedium.FUNGAL_MIRE, "FUNGAL_MIRE_MUD_BALL",
                SinkingMedium.PEAT_SILT, "PEAT_SILT_MUD_BALL",
                SinkingMedium.MIRE, "MIRE_MUD_BALL",
                SinkingMedium.PEAT_BOG, "PEAT_BOG_MUD_BALL");
        for (Map.Entry<SinkingMedium, String> entry : expected.entrySet()) {
            int start = source.indexOf(
                    "rules.put(SinkingMedium." + entry.getKey().name() + ",");
            int end = source.indexOf("));", start);
            assertTrue(start >= 0 && end > start, entry.getKey().name());
            String rule = source.substring(start, end);
            assertTrue(rule.contains("MudDropRule.of(ModBlocks."
                            + entry.getValue() + ", MudDropYield.SCALED"),
                    entry.getKey().name() + " must use " + entry.getValue());
        }
        assertEquals(expected.size(), expected.values().stream().distinct().count(),
                "each native mud medium needs a distinct mud-ball item");
    }

    private static int count(String text, String needle) {
        int total = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            total++;
            index += needle.length();
        }
        return total;
    }
}
