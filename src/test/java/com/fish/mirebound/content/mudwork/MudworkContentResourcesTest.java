package com.fish.mirebound.content.mudwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MudworkContentResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve(
            "assets/mirebound");
    private static final Path DATA = RESOURCES.resolve("data/mirebound");

    private static final List<String> BLOCKS = List.of(
            "wet_adobe",
            "adobe_bricks",
            "adobe_brick_stairs",
            "adobe_brick_slab",
            "adobe_brick_wall",
            "carved_adobe",
            "adobe_tiles",
            "adobe_tile_stairs",
            "adobe_tile_slab",
            "adobe_tile_wall");

    private static final List<String> NEW_ITEMS = List.of(
            "maggot",
            "cooked_maggot",
            "blood_clot_ball",
            "tar_blob",
            "gel_clay_ball",
            "stone_clay_ball",
            "pale_clay_ball");

    private static final List<String> RECIPES = List.of(
            "wet_adobe_brick",
            "wet_adobe",
            "dried_adobe_brick_smelting",
            "dried_adobe_brick_campfire_cooking",
            "adobe_bricks",
            "wet_adobe_smelting",
            "mud_sling",
            "adobe_brick_stairs",
            "adobe_brick_slab",
            "adobe_brick_wall",
            "carved_adobe_stonecutting",
            "adobe_tiles_stonecutting",
            "adobe_tile_stairs",
            "adobe_tile_slab",
            "adobe_tile_wall",
            "tar_from_tar_blobs",
            "gel_clay_from_balls",
            "stone_clay_from_balls",
            "pale_mire_from_balls",
            "cooked_maggot_smelting",
            "cooked_maggot_smoking",
            "cooked_maggot_campfire");

    private static final Map<String, String> MUD_BALLS = Map.of(
            "mud_ball", "mud",
            "thin_mud_ball", "thin_mud",
            "shallow_mud_ball", "shallow_mud",
            "tidal_mud_ball", "tidal_mud",
            "lime_mud_ball", "lime_mud",
            "gravel_silt_mud_ball", "gravel_silt",
            "fungal_mire_mud_ball", "fungal_mire",
            "peat_silt_mud_ball", "peat_silt",
            "mire_mud_ball", "mire",
            "peat_bog_mud_ball", "peat_bog");

    @Test
    void everyBuildingBlockHasStateItemAndLootResources()
            throws IOException {
        String english = Files.readString(ASSETS.resolve("lang/en_us.json"));
        String chinese = Files.readString(ASSETS.resolve("lang/zh_cn.json"));
        for (String block : BLOCKS) {
            assertJson(ASSETS.resolve("blockstates/" + block + ".json"));
            assertJson(ASSETS.resolve("models/item/" + block + ".json"));
            assertJson(DATA.resolve("loot_table/blocks/" + block + ".json"));
            String key = "block.mirebound." + block;
            assertTrue(english.contains(key), key + " missing from en_us");
            assertTrue(chinese.contains(key), key + " missing from zh_cn");
        }
    }

    @Test
    void recipesAndFunctionalTagsArePresent() throws IOException {
        for (String recipe : RECIPES) {
            assertJson(DATA.resolve("recipe/" + recipe + ".json"));
        }
        String walls = Files.readString(RESOURCES.resolve(
                "data/minecraft/tags/block/walls.json"));
        assertFalse(walls.contains("mirebound:adobe_brick_wall"));
        assertFalse(walls.contains("mirebound:adobe_tile_wall"));

        String pickaxe = Files.readString(RESOURCES.resolve(
                "data/minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(pickaxe.contains("mirebound:stone_clay"));
        assertFalse(pickaxe.contains("mirebound:adobe_bricks"));
        assertFalse(pickaxe.contains("mirebound:adobe_tiles"));
    }

    @Test
    void originalPixelTexturesHaveExpectedDimensions() throws IOException {
        for (String name : List.of(
                "adobe_bricks", "adobe_tiles", "carved_adobe",
                "wet_adobe_0", "wet_adobe_1", "wet_adobe_2",
                "wet_adobe_3")) {
            assertTexture(ASSETS.resolve("textures/block/" + name + ".png"),
                    16, 16);
        }
        for (String name : List.of(
                "mud_ball", "mud_sling", "wet_adobe_brick",
                "dried_adobe_brick")) {
            assertTexture(ASSETS.resolve("textures/item/" + name + ".png"),
                    16, 16);
        }
    }

    @Test
    void stairStatesAreCompleteAndMudBallNoLongerAliasesVanillaDye()
            throws IOException {
        for (String stair : List.of(
                "adobe_brick_stairs", "adobe_tile_stairs")) {
            String state = Files.readString(ASSETS.resolve(
                    "blockstates/" + stair + ".json"));
            assertEquals(40, occurrences(state, "facing="));
        }
        String mudBall = Files.readString(ASSETS.resolve(
                "models/item/mud_ball.json"));
        assertTrue(mudBall.contains("mirebound:item/mud_ball"));
        assertFalse(mudBall.contains("minecraft:item/brown_dye"));
    }

    @Test
    void everyMudBallHasAColorTextureModelNameAndFourToOneRecipe()
            throws IOException {
        String english = Files.readString(ASSETS.resolve("lang/en_us.json"));
        String chinese = Files.readString(ASSETS.resolve("lang/zh_cn.json"));
        for (Map.Entry<String, String> entry : MUD_BALLS.entrySet()) {
            String item = entry.getKey();
            String block = entry.getValue();
            assertTexture(ASSETS.resolve("textures/item/" + item + ".png"),
                    16, 16);
            String model = Files.readString(
                    ASSETS.resolve("models/item/" + item + ".json"));
            assertNotNull(JsonParser.parseString(model));
            assertTrue(model.contains("mirebound:item/" + item));
            assertTrue(english.contains("item.mirebound." + item));
            assertTrue(chinese.contains("item.mirebound." + item));

            String recipe = Files.readString(DATA.resolve(
                    "recipe/" + block + "_from_mud_balls.json"));
            assertNotNull(JsonParser.parseString(recipe));
            assertTrue(recipe.contains("\"BB\", \"BB\""));
            assertTrue(recipe.contains("mirebound:" + item));
            assertTrue(recipe.contains("mirebound:" + block));
        }
    }

    @Test
    void newMudworkItemsHaveModelsTexturesAndTranslations() throws IOException {
        String english = Files.readString(ASSETS.resolve("lang/en_us.json"));
        String chinese = Files.readString(ASSETS.resolve("lang/zh_cn.json"));
        for (String item : NEW_ITEMS) {
            assertJson(ASSETS.resolve("models/item/" + item + ".json"));
            assertTexture(ASSETS.resolve("textures/item/" + item + ".png"),
                    16, 16);
            assertTrue(english.contains("item.mirebound." + item));
            assertTrue(chinese.contains("item.mirebound." + item));
        }
    }

    private static void assertJson(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "Missing " + path);
        assertNotNull(JsonParser.parseString(Files.readString(path)));
    }

    private static void assertTexture(
            Path path, int expectedWidth, int expectedHeight)
            throws IOException {
        assertTrue(Files.isRegularFile(path), "Missing " + path);
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, "Unreadable " + path);
        assertEquals(expectedWidth, image.getWidth(), path.toString());
        assertEquals(expectedHeight, image.getHeight(), path.toString());
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length())
                / needle.length();
    }
}
