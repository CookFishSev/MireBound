package com.fish.mirebound.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LocalizationResourcesTest {
    private static final Path LANGUAGE_ROOT = Path.of(
            "src/main/resources/assets/mirebound/lang");
    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "ja_jp", "ko_kr", "pt_br", "ru_ru", "zh_cn");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "%((\\d+)\\$)?[a-zA-Z%]");

    @Test
    void englishAndChineseHaveTheSameCompleteKeySet() throws Exception {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");

        assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(chinese.keySet()));
        for (String key : english.keySet()) {
            assertFalse(english.get(key).getAsString().isBlank(), key + " is blank in en_us");
            assertFalse(chinese.get(key).getAsString().isBlank(), key + " is blank in zh_cn");
            assertEquals(placeholders(english.get(key).getAsString()),
                    placeholders(chinese.get(key).getAsString()),
                    key + " has mismatched format placeholders");
        }
    }

    @Test
    void publicBrandAndRenamedContentStayConsistent() throws Exception {
        JsonObject english = language("en_us");
        JsonObject chinese = language("zh_cn");

        assertEquals("Mirebound: Sinking Depths",
                english.get("itemGroup.mirebound").getAsString());
        assertEquals("泥沼缚境：沉陷深处",
                chinese.get("itemGroup.mirebound").getAsString());
        assertEquals("胶质黏土球",
                chinese.get("item.mirebound.gel_clay_ball").getAsString());
        assertEquals("石质黏土球",
                chinese.get("item.mirebound.stone_clay_ball").getAsString());
        assertEquals("灰色黏土球",
                chinese.get("item.mirebound.pale_clay_ball").getAsString());
        assertTrue(english.get("gui.mirebound.physics.category.sculk")
                .getAsString().contains("quicksand"));
        assertEquals("虫巢虫群",
                chinese.get("gui.mirebound.physics.category.swarm").getAsString());
    }

    @Test
    void everyBundledLanguageHasCompleteKeysAndJapaneseGuiDoesNotFallBack() throws Exception {
        JsonObject english = language("en_us");
        JsonObject japanese = language("ja_jp");
        for (String name : SUPPORTED_LANGUAGES) {
            JsonObject translations = language(name);
            assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(translations.keySet()), name);
            if (!name.equals("en_us")) {
                long sameEnglishGui = english.keySet().stream()
                        .filter(key -> key.startsWith("gui."))
                        .filter(key -> english.get(key).getAsString().equals(translations.get(key).getAsString()))
                        .count();
                assertTrue(sameEnglishGui <= 20,
                        name + " leaves too many GUI entries in English: " + sameEnglishGui);
            }
            for (String key : english.keySet()) {
                assertFalse(translations.get(key).getAsString().isBlank(), name + ":" + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translations.get(key).getAsString()),
                        name + ":" + key + " has mismatched format placeholders");
            }
        }
        for (String key : english.keySet()) {
            if (key.startsWith("gui.")) {
                assertFalse(english.get(key).getAsString().equals(japanese.get(key).getAsString()),
                        key + " still falls back to English in ja_jp");
            }
        }
    }

    private static JsonObject language(String name) throws Exception {
        return JsonParser.parseString(Files.readString(
                LANGUAGE_ROOT.resolve(name + ".json"))).getAsJsonObject();
    }

    private static List<String> placeholders(String value) {
        List<String> result = new ArrayList<>();
        var matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }
}
