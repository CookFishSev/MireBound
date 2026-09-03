package com.fish.mirebound.client.tuning;

import com.mojang.blaze3d.platform.InputConstants;
import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowEditBox;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.client.gui.MireflowToggleButton;
import com.fish.mirebound.mud.MudPhysicsSettings;
import com.fish.mirebound.network.payload.MudTuningGlobalRequestPayload;
import com.fish.mirebound.network.payload.MudTuningGlobalSettingsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Dedicated editor for client-wide wand presentation and server-wide limits. */
public final class MudTuningWandSettingsScreen extends Screen {
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 24;
    private static final int SIDEBAR_WIDTH = 98;
    private static final int HEADER = MireflowGuiTheme.FULLSCREEN_HEADER;
    private static final int ROW_A = MireflowGuiTheme.FULLSCREEN_ROW_A;
    private static final int ROW_B = MireflowGuiTheme.FULLSCREEN_ROW_B;
    private static final int DIVIDER = MireflowGuiTheme.DIVIDER;
    private static final int TEXT = MireflowGuiTheme.TEXT;
    private static final int MUTED = MireflowGuiTheme.MUTED;

    private Page page = Page.INTERFACE_WAND;
    private int maximumVents;
    private boolean entityCoverageEnabled;
    private int entityCoverageFadeSeconds;
    private double interactionRange;
    private boolean editable;
    private int keyScroll;
    private int interfaceScroll;
    private boolean saved;
    private final List<NumberEditor> numberEditors = new ArrayList<>();
    private final List<ColorEditor> colorEditors = new ArrayList<>();
    private Component inputError = Component.empty();
    private KeyMapping selectedKey;

    private MudTuningWandSettingsScreen(MudTuningGlobalSettingsPayload payload) {
        super(Component.translatable("gui.mirebound.tuning.settings.title"));
        accept(payload);
    }

    public static void open(MudTuningGlobalSettingsPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MudTuningWandSettingsScreen current) {
            current.accept(payload);
            current.saved = true;
            current.rebuildWidgets();
            return;
        }
        minecraft.setScreen(new MudTuningWandSettingsScreen(payload));
    }

    private void accept(MudTuningGlobalSettingsPayload payload) {
        maximumVents = Math.max(0, Math.min(96,
                payload.eruptionMaximumActivePerLevel()));
        entityCoverageEnabled = payload.entityCoverageEnabled();
        entityCoverageFadeSeconds = Math.max(0, Math.min(
                MudPhysicsSettings.ENTITY_COVERAGE_MAXIMUM_FADE_SECONDS,
                payload.entityCoverageAutomaticFadeSeconds()));
        interactionRange = Math.max(
                MudPhysicsSettings.MUD_TUNING_WAND_MINIMUM_INTERACTION_RANGE,
                Math.min(MudPhysicsSettings.MUD_TUNING_WAND_MAXIMUM_INTERACTION_RANGE,
                        payload.interactionRange()));
        editable = payload.editable();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        numberEditors.clear();
        colorEditors.clear();
        addPageNavigation();
        switch (page) {
            case INTERFACE_WAND -> addInterfaceControls();
            case KEYS -> addKeyControls();
            case WORLD_LIMITS -> addWorldControls();
            case EXPERIMENTAL -> addExperimentalControls();
        }
        addFooter();
    }

    private void addPageNavigation() {
        int y = HEADER_HEIGHT + 4;
        for (Page candidate : Page.values()) {
            Component label = fit(Component.translatable(candidate.translationKey()),
                    SIDEBAR_WIDTH - (candidate == page ? 28 : 20));
            Button button = MireflowButton.builder(label, ignored -> {
                if (page == Page.INTERFACE_WAND && !commitInterfaceEditors()) {
                    return;
                }
                page = candidate;
                keyScroll = 0;
                interfaceScroll = 0;
                selectedKey = null;
                saved = false;
                rebuildWidgets();
            }).selected(candidate == page)
                    .bounds(6, y, SIDEBAR_WIDTH - 12, 20).build();
            addRenderableWidget(button);
            y += 22;
        }
    }

    private void addInterfaceControls() {
        addHudEditorButton(0);
        addInterfaceNumber(1, MudTuningClientSettings.spatialPlacementDistance(),
                0.5D, 1.0D, 64.0D,
                MudTuningClientSettings::setSpatialPlacementDistance, 1);
        addInterfaceToggle(2, MudTuningClientSettings.tentacleAutoSnap(), enabled ->
                MudTuningClientSettings.setTentacleAutoSnap(enabled));
        addInterfaceNumber(3, interactionRange, 1.0D,
                MudPhysicsSettings.MUD_TUNING_WAND_MINIMUM_INTERACTION_RANGE,
                MudPhysicsSettings.MUD_TUNING_WAND_MAXIMUM_INTERACTION_RANGE,
                value -> interactionRange = value, 1);
        for (int index = 0; index < MudTuningClientSettings.HudColor.values().length;
                index++) {
            addInterfaceColor(5 + index,
                    MudTuningClientSettings.HudColor.values()[index]);
        }
    }

    private void addExperimentalControls() {
        addToggle(0, entityCoverageEnabled,
                enabled -> entityCoverageEnabled = enabled, editable);
    }

    private void addHudEditorButton(int row) {
        if (!isVisibleInterfaceRow(row)) {
            return;
        }
        int x = controlLeft();
        int y = interfaceRowY(row) + 2;
        addRenderableWidget(MireflowButton.builder(Component.translatable(
                "gui.mirebound.tuning.settings.hud_editor"), ignored ->
                MudTuningHudEditorScreen.open(this))
                .tone(MireflowButton.Tone.INFO)
                .bounds(x, y, width - x - 7, 20).build());
    }

    private void addInterfaceToggle(int row, boolean value,
            java.util.function.Consumer<Boolean> setter) {
        if (!isVisibleInterfaceRow(row)) {
            return;
        }
        addToggleAt(interfaceRowY(row), value, setter, true);
    }

    private void addInterfaceNumber(int row, double value, double step,
            double minimum, double maximum, DoubleConsumer setter, int decimals) {
        if (!isVisibleInterfaceRow(row)) {
            return;
        }
        addNumberAt(interfaceRowY(row), value, step, minimum, maximum,
                setter, true, decimals);
    }

    private void addInterfaceColor(int row, MudTuningClientSettings.HudColor color) {
        if (!isVisibleInterfaceRow(row)) {
            return;
        }
        int x = controlLeft();
        int y = interfaceRowY(row) + 2;
        MireflowEditBox field = new MireflowEditBox(font, x, y,
                Math.max(54, width - x - 7), 20, Component.empty());
        field.setMaxLength(6);
        field.setFilter(MudTuningWandSettingsScreen::validHexColorInput);
        field.setValue(color.hex());
        field.setResponder(ignored -> saved = false);
        addRenderableWidget(field);
        colorEditors.add(new ColorEditor(field, color));
    }

    private void addKeyControls() {
        List<KeyRow> rows = keyRows();
        int maximumScroll = Math.max(0, rows.size() - visibleKeyRows());
        keyScroll = Math.max(0, Math.min(maximumScroll, keyScroll));
        int visible = Math.min(visibleKeyRows(), rows.size() - keyScroll);
        for (int slot = 0; slot < visible; slot++) {
            KeyRow row = rows.get(keyScroll + slot);
            if (row.mapping == null) {
                continue;
            }
            int y = keyRowY(slot) + 2;
            int resetWidth = width < 420 ? 42 : 52;
            int resetX = width - resetWidth - 7;
            int keyX = controlLeft();
            Component keyLabel = row.mapping.getTranslatedKeyMessage();
            if (selectedKey == row.mapping) {
                keyLabel = Component.literal("> ").append(keyLabel).append(" <")
                        .withStyle(ChatFormatting.YELLOW);
            } else if (hasKeyConflict(row.mapping)) {
                keyLabel = keyLabel.copy().withStyle(ChatFormatting.RED);
            }
            addRenderableWidget(MireflowButton.builder(keyLabel, ignored -> {
                selectedKey = row.mapping;
                inputError = Component.empty();
                rebuildWidgets();
            }).bounds(keyX, y, Math.max(54, resetX - keyX - 3), 20).build());
            Button reset = MireflowButton.builder(Component.translatable(
                    "gui.mirebound.physics.reset_short"), ignored -> {
                        row.mapping.setToDefault();
                        finishKeyChange();
                    }).bounds(resetX, y, resetWidth, 20).build();
            reset.active = !row.mapping.isDefault();
            addRenderableWidget(reset);
        }
    }

    private void addWorldControls() {
        addInteger(0, maximumVents, 1, 0, 96,
                value -> maximumVents = value, editable);
        addInteger(1, entityCoverageFadeSeconds, 5, 0,
                MudPhysicsSettings.ENTITY_COVERAGE_MAXIMUM_FADE_SECONDS,
                value -> entityCoverageFadeSeconds = value, editable);
    }

    private void addToggle(int row, boolean value,
            java.util.function.Consumer<Boolean> setter) {
        addToggle(row, value, setter, true);
    }

    private void addToggle(int row, boolean value,
            java.util.function.Consumer<Boolean> setter, boolean active) {
        addToggleAt(rowY(row), value, setter, active);
    }

    private void addToggleAt(int y, boolean value,
            java.util.function.Consumer<Boolean> setter, boolean active) {
        int x = controlLeft();
        Button button = new MireflowToggleButton(x, y + 2,
                width - x - 7, 20, value, ignored -> {
                    setter.accept(!value);
                    saved = false;
                    rebuildWidgets();
                });
        button.active = active;
        addRenderableWidget(button);
    }

    private void addNumber(int row, double value, double step,
            double minimum, double maximum, DoubleConsumer setter, boolean active,
            int decimals) {
        addNumberAt(rowY(row), value, step, minimum, maximum, setter, active, decimals);
    }

    private void addNumberAt(int y, double value, double step,
            double minimum, double maximum, DoubleConsumer setter, boolean active,
            int decimals) {
        int x = controlLeft();
        int right = width - 7;
        Button minus = MireflowButton.builder(Component.literal("-"), ignored -> {
            setter.accept(Math.max(minimum, value - step));
            saved = false;
            rebuildWidgets();
        }).bounds(x, y + 2, 22, 20).build();
        minus.active = active && value > minimum + 1.0E-9D;
        addRenderableWidget(minus);
        EditBox field = new MireflowEditBox(font, x + 25, y + 2,
                Math.max(42, right - x - 50), 20, Component.empty());
        field.setFilter(MudTuningWandSettingsScreen::validNumberInput);
        field.setValue(format(value, decimals));
        field.setEditable(active);
        field.active = active;
        field.setResponder(text -> {
            Double parsed = parseDouble(text);
            if (parsed != null && parsed >= minimum && parsed <= maximum) {
                setter.accept(parsed);
            }
        });
        addRenderableWidget(field);
        numberEditors.add(new NumberEditor(field, minimum, maximum, setter));
        Button plus = MireflowButton.builder(Component.literal("+"), ignored -> {
            setter.accept(Math.min(maximum, value + step));
            saved = false;
            rebuildWidgets();
        }).bounds(right - 22, y + 2, 22, 20).build();
        plus.active = active && value < maximum - 1.0E-9D;
        addRenderableWidget(plus);
    }

    private void addInteger(int row, int value, int step,
            int minimum, int maximum, java.util.function.IntConsumer setter,
            boolean active) {
        addNumber(row, value, step, minimum, maximum,
                next -> setter.accept((int) Math.round(next)), active, 0);
    }

    private void addFooter() {
        int y = height - 26;
        if (page == Page.KEYS) {
            Button resetAll = MireflowButton.builder(Component.translatable(
                    "controls.resetAll"), ignored -> resetWandKeys())
                    .bounds(7, y, keyResetWidth(), 20).build();
            resetAll.active = keyRows().stream().anyMatch(
                    row -> row.mapping != null && !row.mapping.isDefault());
            addRenderableWidget(resetAll);
            addRenderableWidget(MireflowButton.builder(Component.translatable(
                    "gui.done"), ignored -> onClose())
                    .bounds(width - 75, y, 68, 20).build());
            return;
        }
        addRenderableWidget(MireflowButton.builder(Component.translatable(
                "gui.mirebound.physics.cancel"), ignored -> onClose())
                .bounds(width - 75, y, 68, 20).build());
        Button apply = MireflowButton.builder(Component.translatable(
                "gui.mirebound.physics.apply"), ignored -> applyServerSettings())
                .bounds(width - 147, y, 68, 20).build();
        apply.active = page != Page.WORLD_LIMITS || editable;
        addRenderableWidget(apply);
    }

    private void applyServerSettings() {
        if (page == Page.INTERFACE_WAND && !commitInterfaceEditors()) {
            return;
        }
        if (page == Page.EXPERIMENTAL && !editable) {
            saved = true;
            rebuildWidgets();
            return;
        }
        if (!editable || !commitNumberEditors()) {
            if (page == Page.INTERFACE_WAND) {
                saved = true;
                rebuildWidgets();
            }
            return;
        }
        PacketDistributor.sendToServer(new MudTuningGlobalRequestPayload(
                true, maximumVents, entityCoverageEnabled, entityCoverageFadeSeconds,
                interactionRange));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
            double scrollX, double scrollY) {
        if (page != Page.KEYS && page != Page.INTERFACE_WAND) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (Math.abs(scrollY) < 1.0E-6D
                || mouseY < HEADER_HEIGHT || mouseY >= height - FOOTER_HEIGHT) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (page == Page.INTERFACE_WAND) {
            if (!commitInterfaceEditors()) {
                return true;
            }
            int maximum = Math.max(0,
                    interfaceLabels().length - visibleInterfaceRows());
            interfaceScroll = Math.max(0, Math.min(maximum,
                    interfaceScroll + (scrollY > 0.0D ? -1 : 1)));
        } else {
            int maximum = Math.max(0, keyRows().size() - visibleKeyRows());
            keyScroll = Math.max(0, Math.min(maximum,
                    keyScroll + (scrollY > 0.0D ? -1 : 1)));
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick) {
        // The custom translucent surface intentionally skips the vanilla blur pass.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MireflowGuiTheme.drawTranslucentSurface(graphics, 0, 0, width, height);
        graphics.fill(0, 0, width, 26, HEADER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, HEADER);
        graphics.hLine(0, width - 1, 25, DIVIDER);
        graphics.hLine(0, width - 1, HEADER_HEIGHT - 1, DIVIDER);
        graphics.hLine(0, width - 1, height - FOOTER_HEIGHT, DIVIDER);
        graphics.vLine(SIDEBAR_WIDTH, HEADER_HEIGHT - 1,
                height - FOOTER_HEIGHT, DIVIDER);
        graphics.drawString(font, title, 7, 9, TEXT, false);
        if (!editable && page != Page.KEYS) {
            Component readOnly = Component.translatable(
                    "gui.mirebound.physics.read_only");
            graphics.drawString(font, readOnly,
                    width - font.width(readOnly) - 7, 9, 0xFFFF8974, false);
        }
        if (page == Page.KEYS) {
            renderKeyRows(graphics);
        } else {
            renderSettingRows(graphics);
        }
        int statusX = page == Page.KEYS
                ? 13 + keyResetWidth() : contentLeft();
        int statusWidth = page == Page.KEYS
                ? Math.max(16, width - 79 - statusX)
                : Math.max(30, width - 7 - statusX);
        if (selectedKey != null) {
            graphics.drawString(font, fit(Component.translatable(
                            "gui.mirebound.tuning.settings.keys.awaiting"),
                    statusWidth), statusX, height - 20,
                    0xFFFFD56A, false);
        } else if (saved) {
            graphics.drawString(font, fit(Component.translatable(
                            "gui.mirebound.physics.saved"), statusWidth),
                    statusX, height - 20,
                    0xFF8ED39A, false);
        } else if (!inputError.getString().isEmpty()) {
            graphics.drawString(font, fit(inputError, statusWidth),
                    statusX, height - 20, 0xFFFF8974, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSettingRows(GuiGraphics graphics) {
        String[] labels = switch (page) {
            case INTERFACE_WAND -> interfaceLabels();
            case WORLD_LIMITS -> new String[] {
                    "maximum_vents_per_dimension",
                    "entity_coverage_fade_seconds"};
            case EXPERIMENTAL -> new String[] {"entity_coverage_enabled"};
            case KEYS -> new String[0];
        };
        int offset = page == Page.INTERFACE_WAND ? interfaceScroll : 0;
        int visible = Math.min(visibleSettingRows(), labels.length - offset);
        for (int slot = 0; slot < visible; slot++) {
            int row = offset + slot;
            int y = rowY(slot);
            graphics.fill(contentLeft() - 2, y, width - 5, y + ROW_HEIGHT - 1,
                    slot % 2 == 0 ? ROW_A : ROW_B);
            int labelX = contentLeft() + 3;
            if (page == Page.INTERFACE_WAND && row >= 5) {
                int color = MudTuningClientSettings.HudColor.values()[row - 5].color();
                graphics.fill(labelX, y + 7, labelX + 10, y + 17,
                        0xFF000000 | color);
                labelX += 15;
            }
            graphics.drawString(font, fit(Component.translatable(
                            "gui.mirebound.tuning.settings." + labels[row]),
                    controlLeft() - labelX - 5),
                    labelX, y + 8, TEXT, false);
        }
    }

    private static String[] interfaceLabels() {
        return new String[] {
                "hud_editor", "spatial_placement_distance", "tentacle_auto_snap",
                "interaction_range", "color_legend", "color.target", "color.point_one", "color.point_two",
                "color.modified", "color.flow", "color.incompatible",
                "color.converted_default", "color.converted_modified"};
    }

    private void renderKeyRows(GuiGraphics graphics) {
        List<KeyRow> rows = keyRows();
        int visible = Math.min(visibleKeyRows(), rows.size() - keyScroll);
        for (int slot = 0; slot < visible; slot++) {
            KeyRow row = rows.get(keyScroll + slot);
            int y = keyRowY(slot);
            if (row.mapping == null) {
                graphics.fill(contentLeft() - 2, y, width - 5,
                        y + ROW_HEIGHT - 1, 0xD02A332E);
                graphics.hLine(contentLeft() - 2, width - 6,
                        y + ROW_HEIGHT - 1, DIVIDER);
                graphics.drawString(font, row.label, contentLeft() + 3, y + 8,
                        0xFFB8D0BD, false);
            } else {
                graphics.fill(contentLeft() - 2, y, width - 5,
                        y + ROW_HEIGHT - 1,
                        (keyScroll + slot) % 2 == 0 ? ROW_A : ROW_B);
                graphics.drawString(font, fit(row.label,
                                Math.max(48, controlLeft() - contentLeft() - 12)),
                        contentLeft() + 3, y + 8, TEXT, false);
            }
        }
        if (rows.size() > visibleKeyRows()) {
            graphics.drawString(font, Component.literal(
                    (keyScroll + 1) + "-" + (keyScroll + visible)
                            + " / " + rows.size()), 8, height - 20, MUTED, false);
        }
    }

    private List<KeyRow> keyRows() {
        List<KeyRow> rows = new ArrayList<>();
        rows.add(KeyRow.category("navigation"));
        rows.add(KeyRow.mapping(MudTuningInputController.modeKey()));
        rows.add(KeyRow.mapping(MudTuningInputController.nudgeKey()));
        rows.add(KeyRow.category("targeting"));
        rows.add(KeyRow.mapping(MudTuningInputController.openRangeKey()));
        rows.add(KeyRow.mapping(MudTuningInputController.selectElementKey()));
        rows.add(KeyRow.category("action"));
        rows.add(KeyRow.mapping(MudTuningInputController.quickSummonKey()));
        rows.add(KeyRow.category("generation"));
        rows.add(KeyRow.mapping(MudTuningInputController.generationVolumeUpKey()));
        rows.add(KeyRow.mapping(MudTuningInputController.generationVolumeDownKey()));
        rows.add(KeyRow.mapping(MudTuningInputController.generationRerollKey()));
        rows.add(KeyRow.mapping(MudTuningInputController.generationAxisKey()));
        rows.add(KeyRow.mapping(MudTuningInputController.generationRotateKey()));
        return rows;
    }

    private boolean commitNumberEditors() {
        for (NumberEditor editor : numberEditors) {
            Double value = parseDouble(editor.field.getValue());
            if (value == null) {
                inputError = Component.translatable(
                        "gui.mirebound.tuning.settings.invalid_number");
                editor.field.setFocused(true);
                return false;
            }
            editor.setter.accept(Math.max(editor.minimum,
                    Math.min(editor.maximum, value)));
        }
        inputError = Component.empty();
        return true;
    }

    private boolean commitInterfaceEditors() {
        if (!commitNumberEditors()) {
            return false;
        }
        for (ColorEditor editor : colorEditors) {
            if (MudTuningClientSettings.parseHexColor(editor.field.getValue(), -1) < 0) {
                inputError = Component.translatable(
                        "gui.mirebound.tuning.settings.invalid_color");
                editor.field.setFocused(true);
                return false;
            }
            editor.color.setHex(editor.field.getValue());
        }
        inputError = Component.empty();
        return true;
    }

    private boolean hasKeyConflict(KeyMapping mapping) {
        for (KeyMapping candidate : minecraft.options.keyMappings) {
            if (candidate != mapping && mapping.same(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void finishKeyChange() {
        KeyMapping.resetMapping();
        minecraft.options.save();
        selectedKey = null;
        saved = true;
        rebuildWidgets();
    }

    private void resetWandKeys() {
        for (KeyRow row : keyRows()) {
            if (row.mapping != null) {
                row.mapping.setToDefault();
            }
        }
        finishKeyChange();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page == Page.KEYS && selectedKey != null) {
            minecraft.options.setKey(
                    selectedKey, InputConstants.Type.MOUSE.getOrCreate(button));
            finishKeyChange();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page == Page.KEYS && selectedKey != null) {
            minecraft.options.setKey(selectedKey,
                    keyCode == GLFW.GLFW_KEY_ESCAPE
                            ? InputConstants.UNKNOWN
                            : InputConstants.getKey(keyCode, scanCode));
            finishKeyChange();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int visibleKeyRows() {
        return Math.max(1,
                (height - HEADER_HEIGHT - FOOTER_HEIGHT - 2) / ROW_HEIGHT);
    }

    private int visibleInterfaceRows() {
        return Math.max(1,
                (height - HEADER_HEIGHT - FOOTER_HEIGHT - 2) / ROW_HEIGHT);
    }

    private int visibleSettingRows() {
        return visibleInterfaceRows();
    }

    private boolean isVisibleInterfaceRow(int row) {
        return row >= interfaceScroll
                && row < interfaceScroll + visibleInterfaceRows();
    }

    private int interfaceRowY(int row) {
        return rowY(row - interfaceScroll);
    }

    private int keyResetWidth() {
        return SIDEBAR_WIDTH - 14;
    }

    private int controlLeft() {
        int contentWidth = Math.max(1, width - contentLeft() - 7);
        return Math.min(width - 58,
                contentLeft() + Math.max(88, contentWidth / 2));
    }

    private static int contentLeft() {
        return SIDEBAR_WIDTH + 7;
    }

    private int rowY(int row) {
        return HEADER_HEIGHT + 3 + row * ROW_HEIGHT;
    }

    private static int keyRowY(int row) {
        return HEADER_HEIGHT + 3 + row * ROW_HEIGHT;
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static boolean validNumberInput(String value) {
        return value.isEmpty() || value.matches("-?(?:\\d+(?:\\.\\d*)?|\\.\\d*)");
    }

    private static boolean validHexColorInput(String value) {
        return value.isEmpty() || value.matches("[0-9a-fA-F]{0,6}");
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank() || value.equals("-") || value.equals(".")) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Component fit(Component text, int available) {
        if (font.width(text) <= available) {
            return text;
        }
        return Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(0, available - font.width("..."))) + "...");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        INTERFACE_WAND,
        KEYS,
        EXPERIMENTAL,
        WORLD_LIMITS;

        private String translationKey() {
            return "gui.mirebound.tuning.settings.page."
                    + name().toLowerCase(Locale.ROOT);
        }
    }

    private record KeyRow(Component label, KeyMapping mapping) {
        private static KeyRow category(String name) {
            return new KeyRow(Component.translatable(
                    "gui.mirebound.tuning.settings.keys.category." + name), null);
        }

        private static KeyRow mapping(KeyMapping mapping) {
            return new KeyRow(Component.translatable(mapping.getName()), mapping);
        }
    }

    private record NumberEditor(
            EditBox field, double minimum, double maximum, DoubleConsumer setter) {
    }

    private record ColorEditor(EditBox field, MudTuningClientSettings.HudColor color) {
    }
}
