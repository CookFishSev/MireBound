package com.fish.mirebound.mud;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MudSpawnPreventionTagTest {
    @Test
    void everyNativeAndAdaptiveMudBlocksNaturalSpawningInside() {
        Set<String> values = values(
                "data/mirebound/tags/block/sinking_blocks.json");

        for (SinkingMedium medium : SinkingMedium.values()) {
            assertTrue(values.contains("mirebound:" + medium.serializedName()),
                    () -> "missing native spawn prevention for " + medium);
            if (medium != SinkingMedium.MIRE
                    && medium != SinkingMedium.MIRE) {
                assertTrue(values.contains(
                                "mirebound:adaptive_" + medium.serializedName()),
                        () -> "missing adaptive spawn prevention for " + medium);
            }
        }
    }

    @Test
    void sinkingTagIsIncludedByVanillaSpawnPrevention() {
        assertTrue(values(
                "data/minecraft/tags/block/prevent_mob_spawning_inside.json")
                .contains("#mirebound:sinking_blocks"));
    }

    private static Set<String> values(String resource) {
        InputStream stream = MudSpawnPreventionTagTest.class.getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, () -> "missing resource " + resource);
        JsonObject root = JsonParser.parseReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray values = root.getAsJsonArray("values");
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return result;
    }
}
