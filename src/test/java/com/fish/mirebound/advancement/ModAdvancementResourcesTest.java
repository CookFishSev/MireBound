package com.fish.mirebound.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModAdvancementResourcesTest {
    private static final Path ADVANCEMENTS =
            Path.of("src/main/resources/data/mirebound/advancement");

    @Test
    void gameplayAdvancementsAreValidAndShareTheRequestedRoot() throws IOException {
        JsonObject root = read("duckweed_on_a_sullied_stream");
        assertEquals("mirebound:entered_mud",
                trigger(root, "entered_mud"));

        assertChild("adhd", "restrained", "mirebound:sculk_restrained");
        assertChild("entombed_in_a_warm_nest", "enclosed",
                "mirebound:tender_flesh_enclosed");
        assertChild("one_hundred_times_the_beef", "ate_raw_maggot",
                "minecraft:consume_item");
    }

    @Test
    void bothLanguagesContainEveryAdvancementTitleAndDescription() throws IOException {
        List<String> names = List.of("duckweed", "adhd", "warm_nest", "beef_100x");
        for (String language : List.of("zh_cn", "en_us")) {
            JsonObject translations = JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/mirebound/lang/" + language + ".json")))
                    .getAsJsonObject();
            for (String name : names) {
                assertNotNull(translations.get("advancement.mirebound." + name + ".title"));
                assertNotNull(translations.get("advancement.mirebound." + name + ".description"));
            }
        }
    }

    @Test
    void advancementDescriptionsUseTheShortToastText() throws IOException {
        JsonObject zh = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/mirebound/lang/zh_cn.json")))
                .getAsJsonObject();
        assertEquals("陷入流沙", zh.get(
                "advancement.mirebound.duckweed.description").getAsString());
        assertEquals("被幽匿钳制", zh.get(
                "advancement.mirebound.adhd.description").getAsString());
        assertEquals("被嫩肉包裹", zh.get(
                "advancement.mirebound.warm_nest.description").getAsString());
        assertEquals("食用蛆", zh.get(
                "advancement.mirebound.beef_100x.description").getAsString());

        JsonObject en = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/mirebound/lang/en_us.json")))
                .getAsJsonObject();
        assertEquals("Sink into quicksand", en.get(
                "advancement.mirebound.duckweed.description").getAsString());
        assertEquals("Restrained by sculk", en.get(
                "advancement.mirebound.adhd.description").getAsString());
        assertEquals("Enclosed by tender flesh", en.get(
                "advancement.mirebound.warm_nest.description").getAsString());
        assertEquals("Eat a maggot", en.get(
                "advancement.mirebound.beef_100x.description").getAsString());
    }

    private static void assertChild(String file, String criterion, String expectedTrigger)
            throws IOException {
        JsonObject advancement = read(file);
        assertEquals("mirebound:duckweed_on_a_sullied_stream",
                advancement.get("parent").getAsString());
        assertEquals(expectedTrigger, trigger(advancement, criterion));
    }

    private static String trigger(JsonObject advancement, String criterion) {
        return advancement.getAsJsonObject("criteria")
                .getAsJsonObject(criterion).get("trigger").getAsString();
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(
                ADVANCEMENTS.resolve(name + ".json"))).getAsJsonObject();
    }
}
