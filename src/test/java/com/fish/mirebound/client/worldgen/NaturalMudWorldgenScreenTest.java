package com.fish.mirebound.client.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NaturalMudWorldgenScreenTest {
    @Test
    void biomeAndFormEditingStayOnTheMainScreen() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/fish/mirebound/client/worldgen/"
                        + "NaturalMudWorldgenScreen.java"));

        assertTrue(source.contains("addBiomeWidgets()"));
        assertTrue(source.contains("button == 0"));
        assertTrue(source.contains("button == 1"));
        assertTrue(source.contains("renderShapePreview"));
        assertTrue(source.contains("NaturalMudDepositShape.columnDepth"));
        assertTrue(source.contains("random_sample"));
        assertTrue(source.contains("averageRadiusField"));
        assertTrue(source.contains("mouseDragged"));
        assertTrue(source.contains("previewYaw"));
        assertTrue(source.contains("BiomeSourceFilter"));
        assertTrue(source.contains("biomeSource.matches(entry)"));
        assertTrue(source.contains("\"minecraft\".equals(id.getNamespace())"));
        assertTrue(source.contains("BiomeGroupHeader"));
        assertTrue(source.contains("getModContainerById"));
        assertTrue(source.contains("renderBiomeGroupHeaders"));
        assertTrue(source.contains("previewZoom"));
        assertTrue(source.contains("insideShapePreview(mouseX, mouseY)"));
        assertTrue(source.contains("preview_actual_size"));
        assertTrue(source.contains("previewActualRadius"));
        assertTrue(source.contains("Component.literal(\"Minecraft\")"));
        assertTrue(source.contains("FORM_ENABLED"));
        assertTrue(source.contains("MEDIUM_LIST_TOP"));
        assertTrue(source.contains("toggleMedium"));
        assertTrue(source.contains("setAllEnabled"));
        assertTrue(source.contains("worldgen.disable_all_short"));
        assertTrue(source.contains("worldgen.enable_all_short"));
        assertTrue(source.contains("deleteSelectedPreset"));
        assertTrue(source.contains("updateSelectedPreset"));
        assertTrue(source.contains("selectedPresetProfile"));
        assertTrue(source.contains("selectedPresetFileName"));
        assertTrue(source.contains("isPresetDirty"));
        assertTrue(source.contains("hasUncommittedRuleEditorChanges"));
        assertTrue(source.contains("NaturalMudGenerationPresetStore.update"));
        assertTrue(source.contains("gui.mirebound.worldgen.preset.modify"));
        assertTrue(source.contains("modify_failed"));
        assertTrue(source.contains("clickPresetMenuEntry"));
        assertTrue(source.contains("insideDialogButton"));
        assertTrue(source.contains(".flat()"));
        assertTrue(source.contains("playPresetConfirm"));
        assertTrue(source.contains("playPresetCancel"));
        assertTrue(source.contains("disabled_hint"));
        assertTrue(source.contains("setFocused(searchField)"));
        assertFalse(source.contains("[x]"));
        assertFalse(source.contains("[ ]"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/fish/mirebound/client/worldgen/"
                        + "NaturalMudBiomeSelectionScreen.java")));
    }
}
