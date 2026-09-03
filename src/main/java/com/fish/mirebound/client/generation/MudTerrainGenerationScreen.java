package com.fish.mirebound.client.generation;

import com.fish.mirebound.client.tuning.MudTuningClientSettings;
import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowEditBox;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.generation.MudTerrainBlockRules;
import com.fish.mirebound.generation.MudTerrainGenerationSettings;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.network.payload.MudTuningGlobalSettingsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Client editor for the currently selected terrain generation type. */
public final class MudTerrainGenerationScreen extends Screen {
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 24;
    private static final int HEADER = MireflowGuiTheme.FULLSCREEN_HEADER;
    private static final int ROW_A = MireflowGuiTheme.FULLSCREEN_ROW_A;
    private static final int ROW_B = MireflowGuiTheme.FULLSCREEN_ROW_B;
    private static final int DIVIDER = MireflowGuiTheme.DIVIDER;
    private static final int TEXT = MireflowGuiTheme.TEXT;

    private final List<NumberEditor> numberEditors = new ArrayList<>();
    private final List<IdEditor> idEditors = new ArrayList<>();
    private boolean editable;
    private Component inputError = Component.empty();

    private MudTerrainGenerationScreen(MudTuningGlobalSettingsPayload payload) {
        super(Component.translatable("gui.mirebound.tuning.generation.title"));
        editable = payload.editable();
    }

    public static void open(MudTuningGlobalSettingsPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MudTerrainGenerationScreen current) {
            current.editable = payload.editable();
            current.rebuildWidgets();
            return;
        }
        minecraft.setScreen(new MudTerrainGenerationScreen(payload));
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        numberEditors.clear();
        idEditors.clear();
        addTypeSelectors();
        int firstParameterRow = typeSelectorRows();
        if (MudTuningClientSettings.generationType().isNaturalDeposit()) {
            addNaturalEditors(firstParameterRow);
        } else {
            addLakeEditors(firstParameterRow);
        }
        addFooter();
    }

    private void addTypeSelectors() {
        List<MudTerrainGenerationType> types =
                MudTerrainGenerationType.selectableValues();
        int x = controlLeft();
        int right = width - 7;
        MudTerrainGenerationType selected = MudTuningClientSettings.generationType();
        if (typeSelectorRows() == 1) {
            Button cycle = MireflowButton.builder(fit(
                            Component.translatable(selected.translationKey()),
                            right - x - 8), ignored ->
                            selectType(selected.cycle(1)))
                    .selected(true)
                    .bounds(x, rowY(0) + 2, Math.max(30, right - x), 20)
                    .build();
            cycle.active = editable;
            addRenderableWidget(cycle);
            return;
        }
        int gap = 3;
        int columns = 5;
        int available = right - x - gap * (columns - 1);
        int buttonWidth = Math.max(30, available / columns);
        for (int index = 0; index < types.size(); index++) {
            MudTerrainGenerationType type = types.get(index);
            int column = index % columns;
            int row = index / columns;
            int buttonX = x + column * (buttonWidth + gap);
            int actualWidth = column == columns - 1
                    ? right - buttonX : buttonWidth;
            Button button = MireflowButton.builder(fit(
                            Component.translatable(type.translationKey()),
                            actualWidth - 8), ignored -> selectType(type))
                    .selected(type == selected)
                    .bounds(buttonX, rowY(row) + 2, actualWidth, 20).build();
            button.active = editable;
            addRenderableWidget(button);
        }
    }

    private void selectType(MudTerrainGenerationType type) {
        if (type != MudTuningClientSettings.generationType()) {
            MudTuningClientSettings.setGenerationType(type);
            inputError = Component.empty();
            rebuildWidgets();
        }
    }

    private void addLakeEditors(int firstRow) {
        addInteger(firstRow,
                MudTuningClientSettings.generationLakeHorizontalRadius(), 1,
                MudTerrainLakeSettings.MINIMUM_HORIZONTAL_RADIUS,
                MudTerrainLakeSettings.MAXIMUM_HORIZONTAL_RADIUS,
                MudTuningClientSettings::setGenerationLakeHorizontalRadius);
        addInteger(firstRow + 1,
                MudTuningClientSettings.generationLakeVerticalRadius(), 1,
                MudTerrainLakeSettings.MINIMUM_VERTICAL_RADIUS,
                MudTerrainLakeSettings.MAXIMUM_VERTICAL_RADIUS,
                MudTuningClientSettings::setGenerationLakeVerticalRadius);
        addInteger(firstRow + 2,
                MudTuningClientSettings.generationLakeSurfaceHeightPixels(), 1,
                MudTerrainLakeSettings.MINIMUM_SURFACE_HEIGHT_PIXELS,
                MudTerrainLakeSettings.MAXIMUM_SURFACE_HEIGHT_PIXELS,
                MudTuningClientSettings::setGenerationLakeSurfaceHeightPixels);
        addInteger(firstRow + 3, MudTuningClientSettings.generationLakeSeed(), 1,
                0, 2_000_000_000,
                MudTuningClientSettings::setGenerationLakeSeed);
        addIdEditor(firstRow + 4,
                MudTuningClientSettings.generationLakeShellBlock(), false);
        addIdEditor(firstRow + 5,
                MudTuningClientSettings.generationLakeInnerBlock(), true);
    }

    private void addNaturalEditors(int firstRow) {
        addInteger(firstRow, MudTuningClientSettings.generationRadius(), 1,
                MudTerrainGenerationSettings.MINIMUM_RADIUS,
                MudTerrainGenerationSettings.MAXIMUM_RADIUS,
                MudTuningClientSettings::setGenerationRadius);
        addInteger(firstRow + 1,
                MudTuningClientSettings.generationThickness(), 1,
                MudTerrainGenerationSettings.MINIMUM_THICKNESS,
                MudTerrainGenerationSettings.MAXIMUM_THICKNESS,
                MudTuningClientSettings::setGenerationThickness);
        addInteger(firstRow + 2,
                MudTuningClientSettings.generationLakeSurfaceHeightPixels(), 1,
                MudTerrainLakeSettings.MINIMUM_SURFACE_HEIGHT_PIXELS,
                MudTerrainLakeSettings.MAXIMUM_SURFACE_HEIGHT_PIXELS,
                MudTuningClientSettings::setGenerationLakeSurfaceHeightPixels);
        addInteger(firstRow + 3, MudTuningClientSettings.generationSeed(), 1,
                0, 2_000_000_000,
                MudTuningClientSettings::setGenerationSeed);
        addIdEditor(firstRow + 4,
                MudTuningClientSettings.generationLakeInnerBlock(), true);
    }

    private void addNumber(int row, double value, double step,
            double minimum, double maximum, DoubleConsumer setter, int decimals) {
        int x = controlLeft();
        int right = width - 7;
        Button minus = MireflowButton.builder(Component.literal("-"), ignored -> {
            setter.accept(Math.max(minimum, value - step));
            rebuildWidgets();
        }).bounds(x, rowY(row) + 2, 22, 20).build();
        minus.active = editable && value > minimum + 1.0E-9D;
        addRenderableWidget(minus);

        EditBox field = new MireflowEditBox(font, x + 25, rowY(row) + 2,
                Math.max(42, right - x - 50), 20, Component.empty());
        field.setFilter(MudTerrainGenerationScreen::validNumberInput);
        field.setValue(format(value, decimals));
        field.setEditable(editable);
        field.setResponder(text -> {
            Double parsed = parseDouble(text);
            if (parsed != null && parsed >= minimum && parsed <= maximum) {
                setter.accept(parsed);
                inputError = Component.empty();
            }
        });
        addRenderableWidget(field);
        numberEditors.add(new NumberEditor(field, minimum, maximum, setter));

        Button plus = MireflowButton.builder(Component.literal("+"), ignored -> {
            setter.accept(Math.min(maximum, value + step));
            rebuildWidgets();
        }).bounds(right - 22, rowY(row) + 2, 22, 20).build();
        plus.active = editable && value < maximum - 1.0E-9D;
        addRenderableWidget(plus);
    }

    private void addInteger(int row, int value, int step,
            int minimum, int maximum, java.util.function.IntConsumer setter) {
        addNumber(row, value, step, minimum, maximum,
                next -> setter.accept((int) Math.round(next)), 0);
    }

    private void addIdEditor(int row, ResourceLocation value, boolean inner) {
        int x = controlLeft();
        EditBox field = new MireflowEditBox(font, x, rowY(row) + 2,
                Math.max(42, width - x - 7), 20, Component.empty());
        field.setFilter(MudTerrainGenerationScreen::validIdInput);
        field.setValue(value.toString());
        field.setEditable(editable);
        field.setResponder(text -> acceptId(text, inner));
        addRenderableWidget(field);
        idEditors.add(new IdEditor(field, inner));
    }

    private void acceptId(String text, boolean inner) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)
                || inner && !validInnerBlock(id)) {
            inputError = Component.translatable(
                    inner
                            ? "gui.mirebound.tuning.generation.invalid_inner"
                            : "gui.mirebound.tuning.generation.invalid_block");
            return;
        }
        if (inner) {
            MudTuningClientSettings.setGenerationLakeInnerBlock(id);
        } else {
            MudTuningClientSettings.setGenerationLakeShellBlock(id);
        }
        inputError = Component.empty();
    }

    private static boolean validInnerBlock(ResourceLocation id) {
        if (MudTerrainLakeSettings.AIR.equals(id)) {
            return true;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        return MudTerrainBlockRules.validInner(block.defaultBlockState());
    }

    private void addFooter() {
        int y = height - 26;
        int buttonWidth = Math.max(64, Math.min(96, width / 4));
        addRenderableWidget(MireflowButton.builder(Component.translatable(
                "gui.mirebound.tuning.generation.apply"), ignored -> onClose())
                .bounds(width - buttonWidth - 7, y, buttonWidth, 20).build());
    }

    private boolean commitEditors() {
        for (NumberEditor editor : numberEditors) {
            Double value = parseDouble(editor.field.getValue());
            if (value == null || value < editor.minimum || value > editor.maximum) {
                inputError = Component.translatable(
                        "gui.mirebound.tuning.settings.invalid_number");
                editor.field.setFocused(true);
                return false;
            }
            editor.setter.accept(value);
        }
        for (IdEditor editor : idEditors) {
            ResourceLocation id = ResourceLocation.tryParse(editor.field.getValue());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)
                    || editor.inner && !validInnerBlock(id)) {
                inputError = Component.translatable(editor.inner
                        ? "gui.mirebound.tuning.generation.invalid_inner"
                        : "gui.mirebound.tuning.generation.invalid_block");
                editor.field.setFocused(true);
                return false;
            }
            if (editor.inner) {
                MudTuningClientSettings.setGenerationLakeInnerBlock(id);
            } else {
                MudTuningClientSettings.setGenerationLakeShellBlock(id);
            }
        }
        inputError = Component.empty();
        return true;
    }

    @Override
    public void onClose() {
        if (commitEditors()) {
            super.onClose();
        }
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The custom translucent surface intentionally skips the vanilla blur pass.
    }

    @Override
    public void render(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MireflowGuiTheme.drawTranslucentSurface(graphics, 0, 0, width, height);
        graphics.fill(0, 0, width, 26, HEADER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, HEADER);
        graphics.hLine(0, width - 1, 25, DIVIDER);
        graphics.hLine(0, width - 1, HEADER_HEIGHT - 1, DIVIDER);
        graphics.hLine(0, width - 1, height - FOOTER_HEIGHT, DIVIDER);
        graphics.drawString(font, title, 7, 9, TEXT, false);

        Component status = !inputError.getString().isEmpty()
                ? inputError
                : !editable
                        ? Component.translatable("gui.mirebound.physics.read_only")
                        : Component.empty();
        if (!status.getString().isEmpty()) {
            Component fitted = fit(status, Math.max(30,
                    width - font.width(title) - 24));
            graphics.drawString(font, fitted,
                    width - font.width(fitted) - 7, 9, 0xFFFF8974, false);
        }

        List<String> labels = labels();
        for (int row = 0; row < labels.size(); row++) {
            int y = rowY(row);
            graphics.fill(5, y, width - 5, y + ROW_HEIGHT - 1,
                    row % 2 == 0 ? ROW_A : ROW_B);
            if (!labels.get(row).isEmpty()) {
                graphics.drawString(font, fit(Component.translatable(
                                "gui.mirebound.tuning.settings."
                                        + labels.get(row)),
                        controlLeft() - 17), 10, y + 8, TEXT, false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private List<String> labels() {
        List<String> labels = new ArrayList<>();
        labels.add("generation_type");
        if (typeSelectorRows() > 1) {
            labels.add("");
        }
        if (MudTuningClientSettings.generationType().isNaturalDeposit()) {
            labels.add("generation_radius");
            labels.add("generation_thickness");
            labels.add("generation_lake_surface_height");
            labels.add("generation_seed");
            labels.add("generation_lake_inner_block");
        } else {
            labels.add("generation_lake_horizontal_radius");
            labels.add("generation_lake_vertical_radius");
            labels.add("generation_lake_surface_height");
            labels.add("generation_seed");
            labels.add("generation_lake_shell_block");
            labels.add("generation_lake_inner_block");
        }
        return List.copyOf(labels);
    }

    private int typeSelectorRows() {
        return width >= 600 ? 2 : 1;
    }

    private int controlLeft() {
        return Math.min(width - 58, Math.max(112, width / 2));
    }

    private static int rowY(int row) {
        return HEADER_HEIGHT + 3 + row * ROW_HEIGHT;
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static boolean validNumberInput(String value) {
        return value.isEmpty() || value.matches("-?(?:\\d+(?:\\.\\d*)?|\\.\\d*)");
    }

    private static boolean validIdInput(String value) {
        return value.isEmpty() || value.matches("[a-z0-9_.:/-]*");
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()
                || value.equals("-") || value.equals(".")) {
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

    private record NumberEditor(
            EditBox field, double minimum, double maximum, DoubleConsumer setter) {
    }

    private record IdEditor(EditBox field, boolean inner) {
    }
}
