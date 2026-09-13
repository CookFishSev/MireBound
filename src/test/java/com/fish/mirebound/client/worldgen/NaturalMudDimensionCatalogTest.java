package com.fish.mirebound.client.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NaturalMudDimensionCatalogTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation MOON = ResourceLocation.parse("example:moon");
    private static final ResourceLocation MARSH = ResourceLocation.parse("example:marsh");

    @Test void registeredVanillaDimensionIdsResolveToBundledDisplayNames() throws Exception {
        var chinese = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/mirebound/lang/zh_cn.json"))).getAsJsonObject();
        assertEquals("\u4e3b\u4e16\u754c", chinese.get(NaturalMudDimensionCatalog.translationKey(OVERWORLD)).getAsString());
        assertEquals("\u4e0b\u754c", chinese.get(NaturalMudDimensionCatalog.translationKey(
                ResourceLocation.parse("minecraft:the_nether"))).getAsString());
        assertEquals("\u672b\u5730", chinese.get(NaturalMudDimensionCatalog.translationKey(
                ResourceLocation.parse("minecraft:the_end"))).getAsString());
        assertEquals("dimension.example.the_end", NaturalMudDimensionCatalog.translationKey(
                ResourceLocation.parse("example:the_end")));
    }

    @Test void explicitModDimensionMembershipTakesPriorityOverTags() {
        var entries = List.of(new NaturalMudDimensionCatalog.Entry(MOON, Set.of(MARSH)));
        assertTrue(NaturalMudDimensionCatalog.matches(entries, MOON, MARSH, Set.of(OVERWORLD)));
        assertFalse(NaturalMudDimensionCatalog.matches(entries, OVERWORLD, MARSH, Set.of(OVERWORLD)));
    }

    @Test void lateInjectedBiomesRemainSelectableUsingDimensionTags() {
        var entries = List.of(new NaturalMudDimensionCatalog.Entry(OVERWORLD,
                Set.of(ResourceLocation.parse("minecraft:plains"))));
        assertTrue(NaturalMudDimensionCatalog.matches(entries, OVERWORLD, MARSH, Set.of(OVERWORLD)));
        assertFalse(NaturalMudDimensionCatalog.matches(entries, MOON, MARSH, Set.of(OVERWORLD)));
        assertTrue(NaturalMudDimensionCatalog.matches(entries, MOON, MARSH, Set.of()));
    }
}
