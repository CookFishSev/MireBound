package com.fish.mirebound.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fish.mirebound.mud.SinkingMedium;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModCreativeTabsTest {
    @Test
    void publicSectionsUseTheConsolidatedOrderAndCounts() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fish/mirebound/registry/ModCreativeTabs.java"));
        String publicMedia = between(source,
                "private static final List<SinkingMedium> PUBLIC_MEDIA",
                "private static final List<CreativeEntry> QUICKSAND_CONTENT");
        assertEquals(SinkingMedium.COUNT,
                count(publicMedia, "SinkingMedium."));

        String bucketContent = between(source,
                "private static final List<CreativeEntry> BUCKET_CONTENT",
                "private static final List<Supplier<? extends ItemLike>> TOOL_CONTENT");
        assertTrue(bucketContent.contains(".filter(MudContainerRules::isBucketable)"));

        String tools = between(source,
                "private static final List<Supplier<? extends ItemLike>> TOOL_CONTENT",
                "private static final List<Supplier<? extends ItemLike>> ITEM_CONTENT");
        assertEquals(3, count(tools, "ModBlocks."));
        assertFalse(tools.contains("MUD_BUCKET"));

        String items = between(source,
                "private static final List<Supplier<? extends ItemLike>> ITEM_CONTENT",
                "private static final List<CreativeEntry> ENCHANTMENT_CONTENT");
        assertEquals(10, count(items, "ModBlocks."));
        for (String mudBall : new String[] {
                "MUD_BALL", "THIN_MUD_BALL", "SHALLOW_MUD_BALL",
                "TIDAL_MUD_BALL", "LIME_MUD_BALL",
                "GRAVEL_SILT_MUD_BALL", "FUNGAL_MIRE_MUD_BALL",
                "PEAT_SILT_MUD_BALL", "MIRE_MUD_BALL",
                "PEAT_BOG_MUD_BALL"}) {
            assertTrue(items.contains(mudBall), mudBall);
        }
        for (String item : new String[] {
                "MAGGOT", "COOKED_MAGGOT", "BLOOD_CLOT_BALL",
                "TAR_BLOB", "GEL_CLAY_BALL", "STONE_CLAY_BALL",
                "PALE_CLAY_BALL"}) {
            assertTrue(items.contains("ModMudworkContent." + item), item);
        }
        assertTrue(source.contains("ModMudworkContent"));

        String sections = between(source,
                "private static final List<Section> SECTIONS",
                "private static SectionLayout cachedLayout");
        int previous = -1;
        for (String name : new String[] {
                "quicksand", "buckets", "tools",
                "enchantments", "items"}) {
            int index = sections.indexOf('"' + name + '"');
            assertTrue(index > previous, "Section order for " + name);
            previous = index;
        }
        assertFalse(sections.contains("\"building\""));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static int count(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
