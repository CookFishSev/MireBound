package com.fish.mirebound.client.tuning;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MudTuningGuiRegressionTest {
    @Test
    void descriptionRowStartsBelowPageButtons() {
        for (int width : new int[] {320, 800}) {
            MudTuningScreenLayout layout = MudTuningScreenLayout.calculate(width, 480);
            int pageButtonBottom = layout.headerHeight() + 24;
            int descriptionTop = layout.contentTop() - 15;
            assertTrue(descriptionTop > pageButtonBottom,
                    "Description overlaps page buttons at width " + width);
        }
    }

    @Test
    void rangeConversionKeepsTypeAndAllActions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fish/mirebound/client/MudPhysicsTuningScreen.java"));
        String method = between(source,
                "private void addAdaptiveActionRow()",
                "private void addConvertedRestoreControls");
        assertTrue(method.contains("mutateAdaptive(true, false)"));
        assertTrue(method.contains("mutateAdaptive(true, true)"));
        assertTrue(method.contains("gui.mirebound.tuning.convert_source_type"));
        assertTrue(method.contains("gui.mirebound.tuning.convert_all_sources"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }
}
