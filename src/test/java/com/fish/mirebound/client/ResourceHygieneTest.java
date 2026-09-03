package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ResourceHygieneTest {
    private static final Path ASSET_ROOT = Path.of(
            "src/main/resources/assets/mirebound");

    @Test
    void footprintsUseOnlyTheDynamicContainerRenderer() {
        assertFalse(Files.exists(ASSET_ROOT.resolve("models/block/footprint")));
        assertFalse(Files.exists(ASSET_ROOT.resolve("textures/block/footprint")));
        assertFalse(Files.exists(ASSET_ROOT.resolve("models/block/footprint_base.json")));
        assertFalse(Files.exists(ASSET_ROOT.resolve("models/block/mud_height_10.json")));
        assertFalse(Files.exists(ASSET_ROOT.resolve(
                "textures/gui/mud_clod_screen_splat.png")));
        assertTrue(Files.isRegularFile(ASSET_ROOT.resolve(
                "blockstates/mud_footprint.json")));
        assertTrue(Files.isRegularFile(ASSET_ROOT.resolve(
                "models/block/footprint_container.json")));
    }

    @Test
    void creativeBannerSpritesMatchCurrentSections() throws Exception {
        Path directory = ASSET_ROOT.resolve("textures/gui/sprites/creative");
        Set<String> actual;
        try (var files = Files.list(directory)) {
            actual = files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
        assertEquals(Set.of(
                "section_quicksand.png",
                "section_buckets.png",
                "section_tools.png",
                "section_enchantments.png",
                "section_items.png"), actual);
    }
}
