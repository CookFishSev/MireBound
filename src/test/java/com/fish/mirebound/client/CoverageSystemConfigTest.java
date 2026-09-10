package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.*;
import com.fish.mirebound.client.config.MireboundClientSettings;
import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

class CoverageSystemConfigTest {
    @Test void existingClientConfigsDefaultToTheStableSystem() throws Exception {
        var field = MireboundClientSettings.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        ModConfigSpec spec = (ModConfigSpec) field.get(null);
        ModConfigSpec.BooleanValue option = spec.getValues()
                .get("visual_effects.independent_surface_coverage");
        assertNotNull(option);
        assertFalse(option.getDefault());
        assertFalse(ClientOption.INDEPENDENT_SURFACE_COVERAGE.defaultEnabled());
        assertTrue(ClientOption.PLAYER_COVERAGE.defaultEnabled());
    }

    @Test void allShippedLanguagesExplainTheAlternativeSystem() throws Exception {
        try (var paths = Files.list(Path.of("src/main/resources/assets/mirebound/lang"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String key = "gui.mirebound.client.option.independent_surface_coverage";
                assertTrue(json.has(key), path.toString());
                assertTrue(json.has(key + ".desc"), path.toString());
                assertFalse(json.get(key + ".desc").getAsString().isBlank(), path.toString());
            }
        }
    }
}
