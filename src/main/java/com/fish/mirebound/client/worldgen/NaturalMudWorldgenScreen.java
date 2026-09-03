package com.fish.mirebound.client.worldgen;

import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowEditBox;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.client.gui.MireflowToggleButton;
import com.fish.mirebound.client.tuning.MudTuningWandUiSounds;
import com.fish.mirebound.generation.MudTerrainGenerationType;
import com.fish.mirebound.generation.MudTerrainLakeSettings;
import com.fish.mirebound.generation.MudTerrainLakeShape;
import com.fish.mirebound.generation.natural.NaturalMudDepositForm;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape;
import com.fish.mirebound.generation.natural.NaturalMudDepositShape.Cell;
import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile;
import com.fish.mirebound.generation.natural.NaturalMudGenerationProfile.Rule;
import com.fish.mirebound.mud.MudBlock;
import com.fish.mirebound.mud.MudBlockVariant;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.registry.ModBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.fml.ModList;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Per-world natural sinking-medium editor opened from the normal world preset. */
public final class NaturalMudWorldgenScreen extends Screen {
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 22;
    private static final int MEDIUM_LIST_TOP = HEADER_HEIGHT + 28;
    private static final int GENERATED_TOP_HEIGHT_PIXELS = 14;
    private static final int HEADER = MireflowGuiTheme.FULLSCREEN_HEADER;
    private static final int ROW_A = MireflowGuiTheme.FULLSCREEN_ROW_A;
    private static final int ROW_B = MireflowGuiTheme.FULLSCREEN_ROW_B;
    private static final int DIVIDER = MireflowGuiTheme.DIVIDER;
    private static final int TEXT = MireflowGuiTheme.TEXT;
    private static final int MUTED = MireflowGuiTheme.MUTED;
    private static final int FORM_ENABLED = 0xFF594A2E;
    private static final int FORM_ENABLED_HOVER = 0xFF6D5A37;
    private static final int PRESET_VISIBLE_ROWS = 8;

    private final CreateWorldScreen parent;
    private final List<BiomeEntry> allBiomes;
    private final Map<String, String> biomeSourceNames;
    private final Set<ResourceLocation> selectedBiomes = new LinkedHashSet<>();
    private final List<FormBounds> renderedFormBounds = new ArrayList<>();
    private final List<BiomeGroupHeader> renderedBiomeGroupHeaders =
            new ArrayList<>();
    private NaturalMudGenerationProfile profile;
    private SinkingMedium selected = SinkingMedium.SOFT_QUICKSAND;
    private NaturalMudDepositForm previewForm;
    private DimensionFilter dimension = DimensionFilter.ALL;
    private BiomeSourceFilter biomeSource = BiomeSourceFilter.ALL;
    private int mediumScroll;
    private int biomeScroll;
    private EditBox chanceField;
    private EditBox averageRadiusField;
    private EditBox radiusVariationField;
    private EditBox searchField;
    private String query = "";
    private Component error = Component.empty();
    private boolean ruleEditorWasFocused;
    private long previewSignature = Long.MIN_VALUE;
    private List<PreviewBlock> previewBlocks = List.of();
    private int previewActualWidth;
    private int previewActualLength;
    private int previewActualDepth;
    private int previewActualRadius;
    private int previewSample;
    private float previewYaw = 45.0F;
    private float previewPitch = 28.0F;
    private float previewZoom = 1.0F;
    private boolean rotatingPreview;
    private List<NaturalMudGenerationPresetStore.Preset> presets = List.of();
    private String selectedPresetName;
    private NaturalMudGenerationProfile selectedPresetProfile;
    private String selectedPresetFileName;
    private boolean presetMenuOpen;
    private boolean presetSaveDialog;
    private int presetScroll;
    private EditBox presetNameField;
    private Button presetCancelButton;
    private Button presetConfirmButton;
    private Button presetModifyButton;
    private Component presetError = Component.empty();

    NaturalMudWorldgenScreen(
            CreateWorldScreen parent, NaturalMudGenerationProfile profile) {
        super(Component.translatable("gui.mirebound.worldgen.title"));
        this.parent = parent;
        this.profile = profile;
        Registry<Biome> registry = parent.getUiState().getSettings()
                .worldgenLoadContext().registryOrThrow(Registries.BIOME);
        allBiomes = registry.holders()
                .map(holder -> new BiomeEntry(holder,
                        holder.unwrapKey().map(ResourceKey::location)
                                .orElseGet(() -> registry.getKey(holder.value()))))
                .filter(entry -> entry.id != null)
                .sorted(Comparator
                        .comparing((BiomeEntry entry) -> !entry.isVanilla())
                        .thenComparing(entry -> entry.id.toString()))
                .toList();
        Map<String, String> sourceNames = new HashMap<>();
        for (BiomeEntry entry : allBiomes) {
            if (!entry.isVanilla()) {
                sourceNames.computeIfAbsent(entry.id.getNamespace(),
                        NaturalMudWorldgenScreen::resolveModDisplayName);
            }
        }
        biomeSourceNames = Map.copyOf(sourceNames);
        presets = NaturalMudGenerationPresetStore.load();
        loadSelectedRuleState();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        String retainedQuery = searchField == null ? query : searchField.getValue();
        clearWidgets();
        presetNameField = null;
        query = retainedQuery;
        addPresetControls();
        addMediumButtons();
        addRuleEditors();
        addBiomeWidgets();
        addPreviewEditors();
        addFooter();
        if (presetSaveDialog) {
            addPresetDialogWidgets();
        }
    }

    private void addPresetControls() {
        int saveWidth = presetSaveWidth();
        int modifyWidth = presetModifyWidth();
        int selectWidth = presetSelectWidth();
        int deleteWidth = presetDeleteWidth();
        int gap = presetHeaderGap();
        int right = presetHeaderRight();
        int deleteLeft = right - deleteWidth;
        int modifyLeft = deleteLeft - gap - modifyWidth;
        int saveLeft = modifyLeft - gap - saveWidth;
        int selectLeft = saveLeft - gap - selectWidth;
        Component selected = !hasSelectedPreset()
                ? Component.translatable("gui.mirebound.worldgen.preset.custom")
                : Component.literal(selectedPresetName);
        Button choose = MireflowButton.builder(fit(selected, selectWidth - 10),
                        ignored -> {
                            if (presetSaveDialog) {
                                return;
                            }
                            presetMenuOpen = !presetMenuOpen;
                            presetScroll = 0;
                            rebuildWidgets();
                        })
                .tone(MireflowButton.Tone.INFO)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.mirebound.worldgen.preset.select_hint")))
                .bounds(selectLeft, 4, selectWidth, 20).build();
        choose.active = !presetSaveDialog;
        addRenderableWidget(choose);
        Button save = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.preset.save"),
                        ignored -> openPresetSaveDialog())
                .tone(MireflowButton.Tone.POSITIVE)
                .bounds(saveLeft, 4, saveWidth, 20).build();
        save.active = !presetSaveDialog;
        addRenderableWidget(save);
        presetModifyButton = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.preset.modify"),
                        ignored -> updateSelectedPreset())
                .tone(MireflowButton.Tone.POSITIVE)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.mirebound.worldgen.preset.modify_hint")))
                .bounds(modifyLeft, 4, modifyWidth, 20).build();
        presetModifyButton.active = !presetSaveDialog && isPresetDirty();
        addRenderableWidget(presetModifyButton);
        Button delete = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.preset.delete"),
                        ignored -> deleteSelectedPreset())
                .tone(MireflowButton.Tone.DANGER)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.mirebound.worldgen.preset.delete_hint")))
                .bounds(deleteLeft, 4, deleteWidth, 20).build();
        delete.active = !presetSaveDialog && hasSelectedPreset();
        addRenderableWidget(delete);

        if (!presetMenuOpen || presetSaveDialog) {
            return;
        }
        int popupWidth = Math.min(260, Math.max(160, width - 12));
        int popupLeft = width - popupWidth - 6;
        int popupTop = HEADER_HEIGHT + 1;
        int count = presets.size() + 1;
        int visible = Math.min(PRESET_VISIBLE_ROWS, count);
        presetScroll = Mth.clamp(presetScroll, 0, Math.max(0, count - visible));
        for (int slot = 0; slot < visible; slot++) {
            int choice = presetScroll + slot;
            boolean custom = choice == 0;
            NaturalMudGenerationPresetStore.Preset preset = custom
                    ? null : presets.get(choice - 1);
            Component label = custom
                    ? Component.translatable("gui.mirebound.worldgen.preset.custom")
                    : Component.literal(preset.name());
            Button button = MireflowButton.builder(fit(label, popupWidth - 18),
                            ignored -> applyPreset(preset))
                    .tone(custom ? MireflowButton.Tone.NORMAL
                            : MireflowButton.Tone.NATIVE)
                    .flat()
                    .selected(custom == !hasSelectedPreset()
                            && (custom || isSelectedPreset(preset)))
                    .bounds(popupLeft + 4, popupTop + 4 + slot * 21,
                            popupWidth - 8, 19).build();
            addRenderableWidget(button);
        }
    }

    private void addPresetDialogWidgets() {
        int dialogWidth = presetDialogWidth();
        int left = presetDialogLeft();
        int top = presetDialogTop();
        presetNameField = new MireflowEditBox(font, left + 12, top + 28,
                dialogWidth - 24, 20,
                Component.translatable("gui.mirebound.worldgen.preset.name"));
        presetNameField.setMaxLength(64);
        presetNameField.setFilter(value -> !value.contains("\n")
                && !value.contains("\r"));
        presetNameField.setResponder(ignored -> presetError = Component.empty());
        presetNameField.setValue(selectedPresetName == null ? ""
                : selectedPresetName);
        addRenderableWidget(presetNameField);
        presetCancelButton = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.preset.cancel"),
                        ignored -> {
                            MudTuningWandUiSounds.playPresetCancel(
                                    Minecraft.getInstance());
                            closePresetSaveDialog();
                        })
                .bounds(left + 12, top + 74, 88, 20).build();
        addRenderableWidget(presetCancelButton);
        presetConfirmButton = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.preset.confirm"),
                        ignored -> {
                            MudTuningWandUiSounds.playPresetConfirm(
                                    Minecraft.getInstance());
                            savePreset();
                        })
                .tone(MireflowButton.Tone.POSITIVE)
                .bounds(left + dialogWidth - 100, top + 74, 88, 20).build();
        addRenderableWidget(presetConfirmButton);
    }

    private void addMediumButtons() {
        List<Rule> rules = profile.rules();
        int visible = visibleMediumRows();
        mediumScroll = Mth.clamp(mediumScroll, 0,
                Math.max(0, rules.size() - visible));
        int sidebar = sidebarWidth();
        int gap = 3;
        int half = Math.max(20, (sidebar - 13) / 2);
        addRenderableWidget(MireflowButton.builder(fit(Component.translatable(
                        "gui.mirebound.worldgen.enable_all_short"), half - 4),
                         ignored -> setAllEnabled(true))
                .tone(MireflowButton.Tone.POSITIVE)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.mirebound.worldgen.enable_all")))
                .bounds(5, HEADER_HEIGHT + 3, half, 20)
                .build());
        addRenderableWidget(MireflowButton.builder(fit(Component.translatable(
                        "gui.mirebound.worldgen.disable_all_short"),
                         Math.max(1, sidebar - 10 - half - gap - 4)),
                         ignored -> setAllEnabled(false))
                .tone(MireflowButton.Tone.DANGER)
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.mirebound.worldgen.disable_all")))
                .bounds(5 + half + gap, HEADER_HEIGHT + 3,
                        Math.max(20, sidebar - 10 - half - gap), 20)
                .build());
        for (int row = 0; row < visible && row + mediumScroll < rules.size(); row++) {
            Rule rule = rules.get(row + mediumScroll);
            addRenderableWidget(MireflowButton.builder(
                            fit(Component.translatable("block.mirebound."
                                            + rule.medium().serializedName()),
                                    sidebar - 18),
                            ignored -> select(rule.medium()))
                    .tone(rule.enabled()
                            ? MireflowButton.Tone.POSITIVE
                            : MireflowButton.Tone.DANGER)
                    .selected(rule.medium() == selected)
                    .bounds(5, MEDIUM_LIST_TOP + row * ROW_HEIGHT,
                            sidebar - 10, 20)
                    .build());
        }
    }

    private void select(SinkingMedium medium) {
        if (medium == selected || !commitRuleEditors()) {
            return;
        }
        selected = medium;
        error = Component.empty();
        biomeScroll = 0;
        previewSample = 0;
        loadSelectedRuleState();
        rebuildWidgets();
    }

    private void loadSelectedRuleState() {
        Rule rule = profile.rule(selected);
        selectedBiomes.clear();
        if (rule == null) {
            previewForm = null;
            return;
        }
        for (BiomeEntry entry : allBiomes) {
            for (String selector : rule.biomeSelectors()) {
                if (NaturalMudGenerationProfile.matchesSelector(
                        entry.holder, selector)) {
                    selectedBiomes.add(entry.id);
                    break;
                }
            }
        }
        previewForm = rule.forms().isEmpty()
                ? NaturalMudDepositForm.MARSH_MOSAIC : rule.forms().getFirst();
        dimension = inferredDimension(rule);
        invalidatePreview();
    }

    private void addRuleEditors() {
        Rule rule = profile.rule(selected);
        if (rule == null) {
            return;
        }
        int left = settingsLeft() + 5;
        int width = Math.max(42, settingsWidth() - 10);
        int top = settingsTop();
        addRenderableWidget(new MireflowToggleButton(left, top, width, 20,
                rule.enabled(), ignored -> {
                    if (!commitRuleEditors()) {
                        return;
                    }
                    Rule current = profile.rule(selected);
                    Rule updated = current.withEnabled(!current.enabled());
                    profile = profile.withRule(updated);
                    rebuildWidgets();
                }));

        Button minus = MireflowButton.builder(Component.literal("-"), ignored -> {
            if (!commitRuleEditors()) {
                return;
            }
            Rule current = profile.rule(selected);
            Rule updated = current.withChance(Math.max(0,
                    current.chancePerHundredThousandChunks() - 10));
            profile = profile.withRule(updated);
            rebuildWidgets();
        }).bounds(left, top + 40, 22, 20).build();
        minus.active = rule.enabled()
                && rule.chancePerHundredThousandChunks() > 0;
        addRenderableWidget(minus);

        chanceField = new MireflowEditBox(font, left + 25, top + 40,
                Math.max(20, width - 50), 20, Component.empty());
        chanceField.setFilter(NaturalMudWorldgenScreen::validNumberInput);
        chanceField.setValue(formatPercent(rule.chancePerHundredThousandChunks()));
        chanceField.active = rule.enabled();
        addRenderableWidget(chanceField);

        Button plus = MireflowButton.builder(Component.literal("+"), ignored -> {
            if (!commitRuleEditors()) {
                return;
            }
            Rule current = profile.rule(selected);
            Rule updated = current.withChance(Math.min(
                    NaturalMudGenerationProfile.MAXIMUM_CHANCE,
                    current.chancePerHundredThousandChunks() + 10));
            profile = profile.withRule(updated);
            rebuildWidgets();
        }).bounds(left + width - 22, top + 40, 22, 20).build();
        plus.active = rule.enabled()
                && rule.chancePerHundredThousandChunks()
                < NaturalMudGenerationProfile.MAXIMUM_CHANCE;
        addRenderableWidget(plus);

        Button reset = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.reset_medium"), ignored -> {
                    profile = profile.reset(selected);
                    error = Component.empty();
                    loadSelectedRuleState();
                    rebuildWidgets();
                }).tone(MireflowButton.Tone.DANGER)
                .bounds(left, top + 70, width, 20).build();
        reset.active = rule.enabled();
        addRenderableWidget(reset);
    }

    private void addPreviewEditors() {
        Rule rule = profile.rule(selected);
        PreviewControlLayout layout = previewControlLayout();
        if (rule == null || layout == null) {
            averageRadiusField = null;
            radiusVariationField = null;
            return;
        }
        Button random = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.random_sample"), ignored -> {
                    previewSample++;
                    invalidatePreview();
                }).bounds(layout.randomLeft, layout.controlTop,
                        layout.randomWidth, 20)
                .tone(MireflowButton.Tone.INFO)
                .build();
        random.active = rule.enabled();
        addRenderableWidget(random);

        addRadiusEditor(layout.averageLeft, layout.controlTop,
                layout.averageWidth, true, rule);
        addRadiusEditor(layout.variationLeft, layout.controlTop,
                layout.variationWidth, false, rule);
    }

    private void addRadiusEditor(
            int left, int top, int width, boolean average, Rule rule) {
        Button minus = MireflowButton.builder(Component.literal("-"), ignored -> {
            if (!commitRuleEditors()) {
                return;
            }
            Rule current = profile.rule(selected);
            Rule changed = average
                    ? current.withRadiusRange(
                            current.minimumRadius() - 1,
                            current.maximumRadius() - 1)
                    : current.withRadiusRange(
                            current.minimumRadius() + 1,
                            current.maximumRadius() - 1);
            profile = profile.withRule(changed);
            invalidatePreview();
            rebuildWidgets();
        }).bounds(left, top, 22, 20).build();
        minus.active = rule.enabled() && (average
                ? rule.minimumRadius() > 2
                : rule.maximumRadius() - rule.minimumRadius() >= 2);
        addRenderableWidget(minus);

        EditBox field = new MireflowEditBox(font, left + 25, top,
                Math.max(20, width - 50), 20, Component.empty());
        field.setFilter(NaturalMudWorldgenScreen::validNumberInput);
        field.setValue(formatRadiusMetric(average
                ? rule.averageRadius() : rule.radiusVariation()));
        field.active = rule.enabled();
        addRenderableWidget(field);
        if (average) {
            averageRadiusField = field;
        } else {
            radiusVariationField = field;
        }

        Button plus = MireflowButton.builder(Component.literal("+"), ignored -> {
            if (!commitRuleEditors()) {
                return;
            }
            Rule current = profile.rule(selected);
            Rule changed = average
                    ? current.withRadiusRange(
                            current.minimumRadius() + 1,
                            current.maximumRadius() + 1)
                    : current.withRadiusRange(
                            current.minimumRadius() - 1,
                            current.maximumRadius() + 1);
            profile = profile.withRule(changed);
            invalidatePreview();
            rebuildWidgets();
        }).bounds(left + width - 22, top, 22, 20).build();
        plus.active = rule.enabled() && (average
                ? rule.maximumRadius() < 12
                : rule.minimumRadius() > 2 && rule.maximumRadius() < 12);
        addRenderableWidget(plus);
    }

    private void addBiomeWidgets() {
        renderedBiomeGroupHeaders.clear();
        int left = biomeLeft() + 5;
        int width = Math.max(42, biomeWidth() - 10);
        searchField = new MireflowEditBox(font, left, 46, width, 20,
                Component.translatable("gui.mirebound.worldgen.search"));
        searchField.setValue(query);
        searchField.setHint(Component.translatable(
                "gui.mirebound.worldgen.search"));
        Rule rule = profile.rule(selected);
        boolean enabled = rule != null && rule.enabled();
        searchField.active = enabled;
        addRenderableWidget(searchField);

        int gap = 3;
        int half = Math.max(20, (width - gap) / 2);
        Button dimensionButton = MireflowButton.builder(fit(Component.translatable(
                        dimension.translationKey()), half - 8), ignored -> {
                    if (!commitRuleEditors()) {
                        return;
                    }
                    dimension = dimension.next();
                    biomeScroll = 0;
                    rebuildWidgets();
                }).tone(MireflowButton.Tone.INFO)
                .bounds(left, 69, half, 20).build();
        dimensionButton.active = enabled;
        addRenderableWidget(dimensionButton);
        Button sourceButton = MireflowButton.builder(fit(Component.translatable(
                        biomeSource.translationKey()), half - 8), ignored -> {
                    if (!commitRuleEditors()) {
                        return;
                    }
                    biomeSource = biomeSource.next();
                    biomeScroll = 0;
                    rebuildWidgets();
                }).tone(MireflowButton.Tone.CONVERTED)
                .bounds(left + half + gap, 69,
                        Math.max(20, width - half - gap), 20).build();
        sourceButton.active = enabled;
        addRenderableWidget(sourceButton);

        Button selectVisible = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.select_visible"), ignored -> {
                    if (!commitRuleEditors()) {
                        return;
                    }
                    filteredBiomes().forEach(entry -> selectedBiomes.add(entry.id));
                    commitBiomeSelection();
                    rebuildWidgets();
                }).tone(MireflowButton.Tone.NATIVE)
                .bounds(left, 92, half, 20).build();
        selectVisible.active = enabled;
        addRenderableWidget(selectVisible);
        Button clearVisible = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.clear_visible"), ignored -> {
                    if (!commitRuleEditors()) {
                        return;
                    }
                    filteredBiomes().forEach(entry -> selectedBiomes.remove(entry.id));
                    commitBiomeSelection();
                    rebuildWidgets();
                }).tone(MireflowButton.Tone.INCOMPATIBLE)
                .bounds(left + half + gap, 92,
                        Math.max(20, width - half - gap), 20).build();
        clearVisible.active = enabled;
        addRenderableWidget(clearVisible);

        List<BiomeListRow> visible = biomeListRows();
        int rows = visibleBiomeRows();
        biomeScroll = Mth.clamp(biomeScroll, 0,
                Math.max(0, visible.size() - rows));
        for (int row = 0; row < rows && row + biomeScroll < visible.size(); row++) {
            BiomeListRow listRow = visible.get(row + biomeScroll);
            int rowTop = biomeRowsTop() + row * ROW_HEIGHT;
            if (listRow.groupTitle != null) {
                renderedBiomeGroupHeaders.add(new BiomeGroupHeader(
                        rowTop, listRow.groupTitle));
                continue;
            }
            BiomeEntry entry = listRow.biome;
            boolean selectedBiome = selectedBiomes.contains(entry.id);
            Component label = fit(displayName(entry.id), width - 12);
            Button biomeButton = MireflowButton.builder(label, ignored -> {
                        if (!commitRuleEditors()) {
                            return;
                        }
                        if (!selectedBiomes.add(entry.id)) {
                            selectedBiomes.remove(entry.id);
                        }
                        commitBiomeSelection();
                        rebuildWidgets();
                    }).tone(selectedBiome
                            ? MireflowButton.Tone.POSITIVE
                            : MireflowButton.Tone.NORMAL)
                    .selected(selectedBiome)
                    .bounds(left, rowTop, width, 20)
                    .build();
            biomeButton.active = rule != null && rule.enabled();
            addRenderableWidget(biomeButton);
        }
    }

    private void commitBiomeSelection() {
        Rule rule = profile.rule(selected);
        if (rule == null) {
            return;
        }
        Set<String> selectors = new LinkedHashSet<>();
        selectedBiomes.stream().map(ResourceLocation::toString)
                .sorted().forEach(selectors::add);
        Rule updated = rule.withBiomeSelectors(selectors);
        if (!updated.equals(rule)) {
            profile = profile.withRule(updated);
        }
    }

    private void addFooter() {
        int y = height - 26;
        addRenderableWidget(MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.reset_all"), ignored -> {
                    profile = NaturalMudGenerationProfile.defaults();
                    error = Component.empty();
                    loadSelectedRuleState();
                    rebuildWidgets();
                }).tone(MireflowButton.Tone.DANGER)
                .bounds(7, y, 92, 20).build());
        addRenderableWidget(MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.cancel"), ignored -> cancel())
                .bounds(width - 199, y, 92, 20).build());
        addRenderableWidget(MireflowButton.builder(Component.translatable(
                        "gui.mirebound.worldgen.apply"), ignored -> apply())
                .tone(MireflowButton.Tone.POSITIVE)
                .bounds(width - 99, y, 92, 20).build());
    }

    private void setAllEnabled(boolean enabled) {
        if (!commitRuleEditors()) {
            return;
        }
        profile = profile.withAllEnabled(enabled);
        rebuildWidgets();
    }

    private boolean commitRuleEditors() {
        if (chanceField == null) {
            return true;
        }
        Rule current = profile.rule(selected);
        if (current == null) {
            return true;
        }
        try {
            double percent = Double.parseDouble(chanceField.getValue());
            int value = (int) Math.round(percent * 1000.0D);
            if (!Double.isFinite(percent) || value < 0
                    || value > NaturalMudGenerationProfile.MAXIMUM_CHANCE) {
                throw new NumberFormatException();
            }
            Rule updated = current.withChance(value);
            boolean radiusChanged = false;
            if (averageRadiusField != null && radiusVariationField != null) {
                double average = parseRadiusMetric(averageRadiusField);
                double variation = parseRadiusMetric(radiusVariationField);
                double minimum = average - variation;
                double maximum = average + variation;
                int minimumRadius = Mth.clamp((int) Math.round(minimum), 2, 12);
                int maximumRadius = Mth.clamp((int) Math.round(maximum),
                        minimumRadius, 12);
                if (!Double.isFinite(average) || !Double.isFinite(variation)
                        || variation < 0.0D || minimum < 2.0D
                        || maximum > 12.0D) {
                    throw new InvalidRadiusException();
                }
                updated = updated.withRadiusRange(
                        minimumRadius, maximumRadius);
                radiusChanged = minimumRadius != current.minimumRadius()
                        || maximumRadius != current.maximumRadius();
            }
            profile = profile.withRule(updated);
            chanceField.setValue(formatPercent(updated.chancePerHundredThousandChunks()));
            if (averageRadiusField != null && radiusVariationField != null) {
                averageRadiusField.setValue(formatRadiusMetric(updated.averageRadius()));
                radiusVariationField.setValue(formatRadiusMetric(updated.radiusVariation()));
            }
            error = Component.empty();
            if (radiusChanged) {
                invalidatePreview();
            }
            return true;
        } catch (InvalidRadiusException ignored) {
            error = Component.translatable(
                    "gui.mirebound.worldgen.invalid_scale");
            if (averageRadiusField != null) {
                averageRadiusField.setFocused(true);
            }
            return false;
        } catch (NumberFormatException ignored) {
            error = Component.translatable(
                    "gui.mirebound.worldgen.invalid_probability");
            chanceField.setFocused(true);
            return false;
        }
    }

    private static double parseRadiusMetric(EditBox field) {
        try {
            return Double.parseDouble(field.getValue());
        } catch (NumberFormatException ignored) {
            throw new InvalidRadiusException();
        }
    }

    private void markProfileCustom() {
        selectedPresetName = null;
        selectedPresetProfile = null;
        selectedPresetFileName = null;
    }

    private boolean hasSelectedPreset() {
        return selectedPresetName != null && selectedPresetProfile != null
                && selectedPresetFileName != null;
    }

    private boolean isSelectedPreset(
            NaturalMudGenerationPresetStore.Preset preset) {
        return preset != null && selectedPresetFileName != null
                && selectedPresetFileName.equals(preset.fileName());
    }

    private boolean isPresetDirty() {
        if (!hasSelectedPreset()) {
            return false;
        }
        if (!profile.rules().equals(selectedPresetProfile.rules())) {
            return true;
        }
        Rule rule = profile.rule(selected);
        return rule != null && hasUncommittedRuleEditorChanges(rule);
    }

    private boolean hasUncommittedRuleEditorChanges(Rule rule) {
        if (chanceField != null) {
            try {
                double percent = Double.parseDouble(chanceField.getValue());
                int value = (int) Math.round(percent * 1000.0D);
                if (Double.isFinite(percent) && value >= 0
                        && value <= NaturalMudGenerationProfile.MAXIMUM_CHANCE
                        && value != rule.chancePerHundredThousandChunks()) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (averageRadiusField == null || radiusVariationField == null) {
            return false;
        }
        try {
            double average = Double.parseDouble(averageRadiusField.getValue());
            double variation = Double.parseDouble(radiusVariationField.getValue());
            double minimum = average - variation;
            double maximum = average + variation;
            if (!Double.isFinite(average) || !Double.isFinite(variation)
                    || variation < 0.0D || minimum < 2.0D || maximum > 12.0D) {
                return false;
            }
            int minimumRadius = Mth.clamp((int) Math.round(minimum), 2, 12);
            int maximumRadius = Mth.clamp((int) Math.round(maximum),
                    minimumRadius, 12);
            return minimumRadius != rule.minimumRadius()
                    || maximumRadius != rule.maximumRadius();
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void openPresetSaveDialog() {
        if (!commitRuleEditors()) {
            return;
        }
        presetMenuOpen = false;
        presetSaveDialog = true;
        presetError = Component.empty();
        rebuildWidgets();
        if (presetNameField != null) {
            setFocused(presetNameField);
            presetNameField.setFocused(true);
        }
    }

    private void closePresetSaveDialog() {
        presetSaveDialog = false;
        presetError = Component.empty();
        presetNameField = null;
        presetCancelButton = null;
        presetConfirmButton = null;
        rebuildWidgets();
    }

    private void savePreset() {
        if (presetNameField == null) {
            return;
        }
        String name = presetNameField.getValue().trim();
        if (name.isEmpty()) {
            presetError = Component.translatable(
                    "gui.mirebound.worldgen.preset.empty_name");
            setFocused(presetNameField);
            presetNameField.setFocused(true);
            return;
        }
        if (!commitRuleEditors()) {
            return;
        }
        NaturalMudGenerationPresetStore.SaveResult result =
                NaturalMudGenerationPresetStore.save(name, profile);
        if (result != NaturalMudGenerationPresetStore.SaveResult.SAVED) {
            presetError = Component.translatable(result
                    == NaturalMudGenerationPresetStore.SaveResult.INVALID_NAME
                            ? "gui.mirebound.worldgen.preset.invalid_name"
                            : "gui.mirebound.worldgen.preset.save_failed");
            return;
        }
        presets = NaturalMudGenerationPresetStore.load();
        NaturalMudGenerationPresetStore.Preset saved = presets.stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst().orElse(null);
        if (saved == null) {
            presetError = Component.translatable(
                    "gui.mirebound.worldgen.preset.save_failed");
            return;
        }
        selectedPresetName = saved.name();
        selectedPresetProfile = saved.profile();
        selectedPresetFileName = saved.fileName();
        closePresetSaveDialog();
    }

    private void deleteSelectedPreset() {
        if (!hasSelectedPreset()) {
            return;
        }
        NaturalMudGenerationPresetStore.Preset preset = presets.stream()
                .filter(this::isSelectedPreset)
                .findFirst().orElse(null);
        if (preset == null) {
            preset = new NaturalMudGenerationPresetStore.Preset(
                    selectedPresetName, selectedPresetProfile,
                    selectedPresetFileName);
        }
        NaturalMudGenerationPresetStore.DeleteResult result =
                NaturalMudGenerationPresetStore.delete(preset);
        if (result != NaturalMudGenerationPresetStore.DeleteResult.DELETED) {
            error = Component.translatable(result
                    == NaturalMudGenerationPresetStore.DeleteResult.NOT_FOUND
                            ? "gui.mirebound.worldgen.preset.delete_missing"
                            : "gui.mirebound.worldgen.preset.delete_failed");
            presets = NaturalMudGenerationPresetStore.load();
            rebuildWidgets();
            return;
        }
        presets = NaturalMudGenerationPresetStore.load();
        markProfileCustom();
        presetMenuOpen = false;
        error = Component.empty();
        rebuildWidgets();
    }

    private void applyPreset(NaturalMudGenerationPresetStore.Preset preset) {
        if (!commitRuleEditors()) {
            return;
        }
        presetMenuOpen = false;
        if (preset != null) {
            profile = preset.profile();
            selectedPresetName = preset.name();
            selectedPresetProfile = preset.profile();
            selectedPresetFileName = preset.fileName();
        } else {
            markProfileCustom();
        }
        error = Component.empty();
        loadSelectedRuleState();
        rebuildWidgets();
    }

    private void updateSelectedPreset() {
        if (!hasSelectedPreset() || !isPresetDirty()
                || !commitRuleEditors()) {
            return;
        }
        NaturalMudGenerationPresetStore.Preset preset = presets.stream()
                .filter(this::isSelectedPreset)
                .findFirst()
                .orElse(new NaturalMudGenerationPresetStore.Preset(
                        selectedPresetName, selectedPresetProfile,
                        selectedPresetFileName));
        NaturalMudGenerationPresetStore.UpdateResult result =
                NaturalMudGenerationPresetStore.update(preset, profile);
        if (result != NaturalMudGenerationPresetStore.UpdateResult.UPDATED) {
            error = Component.translatable(
                    "gui.mirebound.worldgen.preset.modify_failed");
            presets = NaturalMudGenerationPresetStore.load();
            rebuildWidgets();
            return;
        }
        presets = NaturalMudGenerationPresetStore.load();
        NaturalMudGenerationPresetStore.Preset updated = presets.stream()
                .filter(this::isSelectedPreset)
                .findFirst().orElse(null);
        if (updated != null) {
            selectedPresetName = updated.name();
            selectedPresetProfile = updated.profile();
            selectedPresetFileName = updated.fileName();
        } else {
            selectedPresetProfile = profile;
        }
        error = Component.empty();
        rebuildWidgets();
    }

    private void apply() {
        if (!commitRuleEditors()) {
            return;
        }
        NaturalMudWorldCreationClient.update(parent, profile);
        Minecraft.getInstance().setScreen(parent);
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public void tick() {
        super.tick();
        if (presetSaveDialog) {
            return;
        }
        if (searchField != null && !searchField.getValue().equals(query)) {
            if (!commitRuleEditors()) {
                return;
            }
            query = searchField.getValue();
            biomeScroll = 0;
            rebuildWidgets();
            if (searchField != null) {
                setFocused(searchField);
            }
        }
        boolean editorFocused = isRuleEditorFocused();
        if (ruleEditorWasFocused && !editorFocused) {
            commitRuleEditors();
            editorFocused = isRuleEditorFocused();
        }
        ruleEditorWasFocused = editorFocused;
        if (!editorFocused) {
            syncRuleEditorFields();
        }
        updatePresetModifyButtonState();
    }

    private void updatePresetModifyButtonState() {
        if (presetModifyButton != null) {
            presetModifyButton.active = !presetSaveDialog && isPresetDirty();
        }
    }

    private boolean isRuleEditorFocused() {
        return (chanceField != null && chanceField.isFocused())
                || (averageRadiusField != null && averageRadiusField.isFocused())
                || (radiusVariationField != null && radiusVariationField.isFocused());
    }

    private void syncRuleEditorFields() {
        Rule rule = profile.rule(selected);
        if (rule == null) {
            return;
        }
        if (chanceField != null && !chanceField.isFocused()) {
            chanceField.setValue(formatPercent(rule.chancePerHundredThousandChunks()));
        }
        if (averageRadiusField != null && !averageRadiusField.isFocused()) {
            averageRadiusField.setValue(formatRadiusMetric(rule.averageRadius()));
        }
        if (radiusVariationField != null && !radiusVariationField.isFocused()) {
            radiusVariationField.setValue(formatRadiusMetric(rule.radiusVariation()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (presetSaveDialog) {
            if (!insidePresetDialog(mouseX, mouseY)) {
                return true;
            }
            if (button == 0 && insideDialogButton(presetCancelButton, mouseX, mouseY)) {
                presetCancelButton.onPress();
                return true;
            }
            if (button == 0 && insideDialogButton(presetConfirmButton, mouseX, mouseY)) {
                presetConfirmButton.onPress();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (presetMenuOpen && !insidePresetMenu(mouseX, mouseY)
                && !insidePresetHeader(mouseX, mouseY)) {
            presetMenuOpen = false;
            rebuildWidgets();
            return true;
        }
        if (presetMenuOpen && button == 0
                && clickPresetMenuEntry(mouseX, mouseY)) {
            return true;
        }
        if (button == 1 && mouseX >= 3 && mouseX < sidebarWidth()
                && mouseY >= MEDIUM_LIST_TOP) {
            int row = (int) ((mouseY - MEDIUM_LIST_TOP) / ROW_HEIGHT)
                    + mediumScroll;
            List<Rule> rules = profile.rules();
            int rowTop = MEDIUM_LIST_TOP
                    + (row - mediumScroll) * ROW_HEIGHT;
            if (row >= 0 && row < rules.size()
                    && mouseY >= rowTop && mouseY < rowTop + 20
                    && row - mediumScroll < visibleMediumRows()) {
                toggleMedium(rules.get(row).medium());
                return true;
            }
        }
        Rule rule = profile.rule(selected);
        for (FormBounds bounds : renderedFormBounds) {
            if (!bounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (rule == null || !rule.enabled()) {
                return true;
            }
            if (button == 0) {
                previewForm = bounds.form;
                invalidatePreview();
                return true;
            }
            if (button == 1 && commitRuleEditors()) {
                toggleForm(bounds.form);
                return true;
            }
        }
        if (button == 0 && rule != null && rule.enabled()
                && insideShapePreview(mouseX, mouseY)) {
            rotatingPreview = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (presetSaveDialog) {
                closePresetSaveDialog();
                return true;
            }
            if (presetMenuOpen) {
                presetMenuOpen = false;
                rebuildWidgets();
                return true;
            }
        }
        if (presetSaveDialog) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        Rule rule = profile.rule(selected);
        if (button == 0 && rotatingPreview
                && rule != null && rule.enabled()) {
            previewYaw = (previewYaw + (float) dragX * 0.8F) % 360.0F;
            previewPitch = Mth.clamp(
                    previewPitch + (float) dragY * 0.65F,
                    -75.0F, 75.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && rotatingPreview) {
            rotatingPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void toggleForm(NaturalMudDepositForm form) {
        Rule rule = profile.rule(selected);
        if (rule == null || !rule.enabled()) {
            return;
        }
        EnumSet<NaturalMudDepositForm> forms = rule.forms().isEmpty()
                ? EnumSet.noneOf(NaturalMudDepositForm.class)
                : EnumSet.copyOf(rule.forms());
        if (!forms.add(form)) {
            forms.remove(form);
        }
        profile = profile.withRule(rule.withForms(List.copyOf(forms)));
        invalidatePreview();
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY,
            double scrollX, double scrollY) {
        if (Math.abs(scrollY) <= 1.0E-6D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (presetSaveDialog) {
            return true;
        }
        if (presetMenuOpen) {
            if (insidePresetMenu(mouseX, mouseY)) {
                int count = presets.size() + 1;
                int visible = Math.min(PRESET_VISIBLE_ROWS, count);
                presetScroll = Mth.clamp(presetScroll
                        - (int) Math.signum(scrollY), 0,
                        Math.max(0, count - visible));
                rebuildWidgets();
                return true;
            }
            presetMenuOpen = false;
            rebuildWidgets();
            return true;
        }
        Rule rule = profile.rule(selected);
        if (insideShapePreview(mouseX, mouseY)
                && rule != null && rule.enabled()) {
            previewZoom = Mth.clamp((float) (previewZoom
                    * Math.pow(1.12D, scrollY)), 0.5F, 2.5F);
            return true;
        }
        if (mouseX < sidebarWidth()) {
            mediumScroll -= (int) Math.signum(scrollY);
            rebuildWidgets();
            return true;
        }
        if (mouseX >= biomeLeft() && mouseX < formLeft()) {
            if (rule == null || !rule.enabled()) {
                return true;
            }
            biomeScroll -= (int) Math.signum(scrollY);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The custom translucent surface deliberately avoids the vanilla blur pass.
    }

    @Override
    public void render(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MireflowGuiTheme.drawTranslucentSurface(graphics, 0, 0, width, height);
        graphics.fill(0, 0, width, HEADER_HEIGHT, HEADER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, HEADER);
        graphics.hLine(0, width - 1, HEADER_HEIGHT - 1, DIVIDER);
        graphics.hLine(0, width - 1, height - FOOTER_HEIGHT, DIVIDER);
        graphics.vLine(sidebarWidth(), HEADER_HEIGHT - 1,
                height - FOOTER_HEIGHT, DIVIDER);
        graphics.vLine(biomeLeft(), HEADER_HEIGHT - 1,
                height - FOOTER_HEIGHT, DIVIDER);
        graphics.vLine(formLeft(), HEADER_HEIGHT - 1,
                height - FOOTER_HEIGHT, DIVIDER);
        graphics.vLine(previewLeft(), HEADER_HEIGHT - 1,
                height - FOOTER_HEIGHT, DIVIDER);
        graphics.drawString(font, title, 8, 9, TEXT, false);
        if (!error.getString().isEmpty()) {
            Component fitted = fit(error,
                    Math.max(20, width - font.width(title) - 24));
            graphics.drawString(font, fitted,
                    width - font.width(fitted) - 8, 9, 0xFFFF8974, false);
        }

        renderSidebarRows(graphics);
        renderSectionLabels(graphics);
        renderFormControls(graphics, mouseX, mouseY);
        renderBlockPreview(graphics);
        renderShapePreview(graphics);
        renderBiomeGroupHeaders(graphics);
        renderPresetMenu(graphics);
        Rule rule = profile.rule(selected);
        if (rule != null && !rule.enabled()) {
            graphics.fill(settingsLeft(), HEADER_HEIGHT,
                    width, height - FOOTER_HEIGHT, 0x48000000);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFormTooltip(graphics, mouseX, mouseY);
        if (presetSaveDialog) {
            renderPresetSaveDialog(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderPresetMenu(GuiGraphics graphics) {
        if (!presetMenuOpen || presetSaveDialog) {
            return;
        }
        int left = presetPopupLeft();
        int top = presetPopupTop();
        int right = left + presetPopupWidth();
        int bottom = top + presetPopupHeight();
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, DIVIDER);
        graphics.fill(left, top, right, bottom, 0xF0141C17);
    }

    private boolean clickPresetMenuEntry(double mouseX, double mouseY) {
        int top = presetPopupTop();
        int row = (int) ((mouseY - top - 4) / 21);
        int visible = Math.min(PRESET_VISIBLE_ROWS, presets.size() + 1);
        if (row < 0 || row >= visible) {
            return false;
        }
        int rowTop = top + 4 + row * 21;
        if (mouseY < rowTop || mouseY >= rowTop + 19
                || mouseX < presetPopupLeft() + 4
                || mouseX >= presetPopupLeft() + presetPopupWidth() - 4) {
            return false;
        }
        int choice = presetScroll + row;
        if (choice < 0 || choice > presets.size()) {
            return false;
        }
        applyPreset(choice == 0 ? null : presets.get(choice - 1));
        return true;
    }

    private static boolean insideDialogButton(
            Button button, double mouseX, double mouseY) {
        if (button == null) {
            return false;
        }
        return mouseX >= button.getX() - 3
                && mouseX < button.getX() + button.getWidth() + 3
                && mouseY >= button.getY() - 3
                && mouseY < button.getY() + button.getHeight() + 3;
    }

    private void renderPresetSaveDialog(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int dialogWidth = presetDialogWidth();
        int left = presetDialogLeft();
        int top = presetDialogTop();
        MireflowGuiTheme.Panel panel = new MireflowGuiTheme.Panel(
                left, top, dialogWidth, presetDialogHeight(), width, height);
        MireflowGuiTheme.drawPanel(graphics, panel);
        graphics.drawString(font, Component.translatable(
                "gui.mirebound.worldgen.preset.save_title"),
                left + 12, top + 9, TEXT, false);
        if (!presetError.getString().isEmpty()) {
            graphics.drawString(font, fit(presetError, dialogWidth - 24),
                    left + 12, top + 52, MireflowGuiTheme.ERROR, false);
        } else {
            graphics.drawString(font, Component.translatable(
                    "gui.mirebound.worldgen.preset.name_hint"),
                    left + 12, top + 52, MUTED, false);
        }
        if (presetNameField != null) {
            presetNameField.render(graphics, mouseX, mouseY, partialTick);
        }
        if (presetCancelButton != null) {
            presetCancelButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (presetConfirmButton != null) {
            presetConfirmButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderSidebarRows(GuiGraphics graphics) {
        for (int row = 0; row < visibleMediumRows(); row++) {
            int y = MEDIUM_LIST_TOP - 1 + row * ROW_HEIGHT;
            graphics.fill(3, y, sidebarWidth() - 3, y + ROW_HEIGHT - 1,
                    row % 2 == 0 ? ROW_A : ROW_B);
        }
        for (int row = 0; row < visibleBiomeRows(); row++) {
            int y = biomeRowsTop() - 1 + row * ROW_HEIGHT;
            graphics.fill(biomeLeft() + 3, y, formLeft() - 3,
                    y + ROW_HEIGHT - 1, row % 2 == 0 ? ROW_A : ROW_B);
        }
    }

    private void renderSectionLabels(GuiGraphics graphics) {
        Rule rule = profile.rule(selected);
        if (rule == null) {
            return;
        }
        int settingsLeft = settingsLeft() + 5;
        graphics.drawString(font,
                fit(Component.translatable("block.mirebound."
                                + selected.serializedName()), settingsWidth() - 10),
                settingsLeft, settingsTop() - 15, TEXT, false);
        graphics.drawString(font,
                fit(Component.translatable("gui.mirebound.worldgen.probability"),
                        settingsWidth() - 10),
                settingsLeft, settingsTop() + 29, MUTED, false);

        Component biomes = Component.translatable(
                "gui.mirebound.worldgen.biomes", selectedBiomes.size());
        graphics.drawString(font, fit(biomes, biomeWidth() - 10),
                biomeLeft() + 5, 34, TEXT, false);
        graphics.drawString(font,
                Component.translatable("gui.mirebound.worldgen.forms"),
                formLeft() + 5, 34, TEXT, false);
        PreviewControlLayout layout = previewControlLayout();
        if (layout != null) {
            graphics.drawString(font, fit(Component.translatable(
                            "gui.mirebound.worldgen.average_scale"),
                            layout.averageWidth),
                    layout.averageLeft, layout.labelTop, MUTED, false);
            graphics.drawString(font, fit(Component.translatable(
                            "gui.mirebound.worldgen.scale_variation"),
                            layout.variationWidth),
                    layout.variationLeft, layout.labelTop, MUTED, false);
        }
    }

    private void renderBiomeGroupHeaders(GuiGraphics graphics) {
        int left = biomeLeft() + 9;
        int right = formLeft() - 9;
        for (BiomeGroupHeader header : renderedBiomeGroupHeaders) {
            Component title = fit(header.title, Math.max(0, right - left - 24));
            int center = (left + right) / 2;
            int halfText = font.width(title) / 2;
            int lineY = header.top + 10;
            int leftEnd = center - halfText - 5;
            int rightStart = center + halfText + 5;
            if (leftEnd > left) {
                graphics.hLine(left, leftEnd, lineY, DIVIDER);
            }
            if (rightStart < right) {
                graphics.hLine(rightStart, right, lineY, DIVIDER);
            }
            graphics.drawCenteredString(font, title,
                    center, header.top + 6, MUTED);
        }
    }

    private void renderFormControls(
            GuiGraphics graphics, int mouseX, int mouseY) {
        renderedFormBounds.clear();
        Rule rule = profile.rule(selected);
        if (rule == null || formAreaWidth() < 24) {
            return;
        }
        List<FormBounds> bounds = formBounds();
        for (FormBounds area : bounds) {
            boolean enabled = rule.forms().contains(area.form);
            boolean previewed = area.form == previewForm;
            boolean hovered = area.contains(mouseX, mouseY);
            boolean ruleEnabled = rule.enabled();
            int border = ruleEnabled && previewed
                    ? MireflowGuiTheme.ACCENT : DIVIDER;
            int fill = !ruleEnabled ? 0xFF202622 : enabled
                    ? hovered ? FORM_ENABLED_HOVER : FORM_ENABLED
                    : hovered ? MireflowGuiTheme.CONTROL_HOVER
                            : MireflowGuiTheme.CONTROL;
            graphics.fill(area.left, area.top, area.right, area.bottom, border);
            graphics.fill(area.left + 1, area.top + 1,
                    area.right - 1, area.bottom - 1, fill);
            Component name = fit(Component.translatable(area.form.translationKey()),
                    area.right - area.left - 6);
            graphics.drawCenteredString(font, name,
                    (area.left + area.right) / 2,
                    area.top + (area.bottom - area.top - 8) / 2,
                    ruleEnabled && enabled ? TEXT : MUTED);
        }
        renderedFormBounds.addAll(bounds);
    }

    private void renderFormTooltip(
            GuiGraphics graphics, int mouseX, int mouseY) {
        for (FormBounds bounds : renderedFormBounds) {
            if (bounds.contains(mouseX, mouseY)) {
                Rule rule = profile.rule(selected);
                if (rule != null && !rule.enabled()) {
                    graphics.renderTooltip(font, Component.translatable(
                                    "gui.mirebound.worldgen.disabled_hint"),
                            mouseX, mouseY);
                    return;
                }
                graphics.renderTooltip(font, Component.translatable(
                                "gui.mirebound.worldgen.form_hint"),
                        mouseX, mouseY);
                return;
            }
        }
    }

    private void renderBlockPreview(GuiGraphics graphics) {
        Rule rule = profile.rule(selected);
        if (rule == null || settingsWidth() < 44) {
            return;
        }
        int areaTop = HEADER_HEIGHT + 8;
        int areaBottom = settingsTop() - 22;
        if (areaBottom - areaTop < 24) {
            return;
        }
        float scale = Mth.clamp(settingsWidth() / 68.0F, 1.5F, 3.0F);
        int size = Math.round(16.0F * scale);
        int x = settingsLeft() + (settingsWidth() - size) / 2;
        int y = areaTop + (areaBottom - areaTop - size) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 80.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(new ItemStack(ModBlocks.blockFor(rule.medium())), 0, 0);
        graphics.pose().popPose();
    }

    private void renderShapePreview(GuiGraphics graphics) {
        Rule rule = profile.rule(selected);
        if (rule == null || previewForm == null || previewWidth() < 72) {
            return;
        }
        int left = previewLeft() + 5;
        int right = width - 5;
        int top = previewTop();
        int bottom = previewShapeBottom();
        if (right - left < 32 || bottom - top < 32) {
            return;
        }
        graphics.fill(left, top, right, bottom, 0x64101512);
        graphics.hLine(left, right - 1, top, DIVIDER);
        graphics.hLine(left, right - 1, bottom - 1, DIVIDER);
        graphics.vLine(left, top, bottom - 1, DIVIDER);
        graphics.vLine(right - 1, top, bottom - 1, DIVIDER);

        List<PreviewBlock> blocks = previewBlocks(rule);
        if (blocks.isEmpty()) {
            return;
        }
        int minimumX = blocks.stream().mapToInt(PreviewBlock::x).min().orElse(0);
        int maximumX = blocks.stream().mapToInt(PreviewBlock::x).max().orElse(0);
        int minimumZ = blocks.stream().mapToInt(PreviewBlock::z).min().orElse(0);
        int maximumZ = blocks.stream().mapToInt(PreviewBlock::z).max().orElse(0);
        int minimumY = blocks.stream().mapToInt(PreviewBlock::y).min().orElse(0);
        int maximumY = blocks.stream().mapToInt(PreviewBlock::y).max().orElse(0);
        int footprintWidth = previewActualWidth;
        int footprintLength = previewActualLength;
        int diameter = Math.max(footprintWidth, footprintLength);
        int depth = previewActualDepth;
        float scale = (float) Math.min(
                (right - left) * 0.48D / Math.max(1.0D, diameter),
                (bottom - top) * 0.55D
                        / Math.max(1.0D, diameter * 0.72D + depth));
        scale = Mth.clamp(scale, 2.0F, 13.0F) * previewZoom;
        double centerX = (minimumX + maximumX + 1) * 0.5D;
        double centerY = (minimumY + maximumY + 1) * 0.5D;
        double centerZ = (minimumZ + maximumZ + 1) * 0.5D;

        BlockState full = ModBlocks.blockFor(rule.medium()).defaultBlockState();
        BlockState topState = rule.fullHeightTop() ? full
                : full.setValue(MudBlock.VARIANT, MudBlockVariant.HEIGHT)
                        .setValue(MudBlock.HEIGHT, GENERATED_TOP_HEIGHT_PIXELS);
        BlockState hostState = previewForm == NaturalMudDepositForm.SURFACE_LAKE
                ? Blocks.DIRT.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
        graphics.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        graphics.flush();
        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance()
                .renderBuffers().bufferSource();
        graphics.pose().pushPose();
        graphics.pose().translate((left + right) * 0.5D,
                top + (bottom - top) * 0.59D, 180.0D);
        graphics.pose().scale(scale, -scale, scale);
        graphics.pose().mulPose(Axis.XP.rotationDegrees(previewPitch));
        graphics.pose().mulPose(Axis.YP.rotationDegrees(previewYaw));
        for (PreviewBlock block : blocks) {
            graphics.pose().pushPose();
            graphics.pose().translate(
                    block.x - centerX, block.y - centerY, block.z - centerZ);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    block.terrain ? hostState : block.top ? topState : full,
                    graphics.pose(), buffers, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
        buffers.endBatch();
        RenderSystem.disableDepthTest();
        graphics.disableScissor();
        Component actualSize = Component.translatable(
                "gui.mirebound.worldgen.preview_actual_size",
                footprintWidth, footprintLength, depth, previewActualRadius);
        graphics.drawString(font, fit(actualSize, right - left - 10),
                left + 5, top + 5, MUTED, false);
        graphics.drawCenteredString(font,
                Component.translatable(previewForm.translationKey()),
                (left + right) / 2, bottom - 12, MUTED);
    }

    private List<PreviewBlock> previewBlocks(Rule rule) {
        rule = previewRule(rule);
        long sampleSeed = previewSeed(rule);
        int previewRadius = previewRadius(rule, sampleSeed);
        long signature = rule.medium().id();
        signature = signature * 31L + previewForm.ordinal();
        signature = signature * 31L + previewRadius;
        signature = signature * 31L + previewSample;
        signature = signature * 31L + rule.minimumDepth();
        signature = signature * 31L + rule.maximumDepth();
        if (signature == previewSignature) {
            return previewBlocks;
        }
        if (previewForm.lake()) {
            return buildLakePreview(
                    rule, sampleSeed, previewRadius, signature);
        }
        List<Cell> cells = NaturalMudDepositShape.build(
                previewForm, sampleSeed, previewRadius);
        Map<Long, Integer> depths = new HashMap<>();
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        int maximumDepth = 0;
        for (Cell cell : cells) {
            int columnDepth = NaturalMudDepositShape.columnDepth(rule, cell);
            depths.put(cellKey(cell.dx(), cell.dz()), columnDepth);
            minimumX = Math.min(minimumX, cell.dx());
            maximumX = Math.max(maximumX, cell.dx());
            minimumZ = Math.min(minimumZ, cell.dz());
            maximumZ = Math.max(maximumZ, cell.dz());
            maximumDepth = Math.max(maximumDepth, columnDepth);
        }
        List<PreviewBlock> next = new ArrayList<>();
        for (Cell cell : cells) {
            int columnDepth = depths.get(cellKey(cell.dx(), cell.dz()));
            next.add(new PreviewBlock(
                    cell.dx(), 0, cell.dz(), true, false));
            for (int layer = 1; layer < columnDepth; layer++) {
                if (exposedLayer(depths, cell.dx(), cell.dz(), layer)) {
                    next.add(new PreviewBlock(
                            cell.dx(), -layer, cell.dz(), false, false));
                }
            }
        }
        previewActualWidth = cells.isEmpty()
                ? 0 : maximumX - minimumX + 1;
        previewActualLength = cells.isEmpty()
                ? 0 : maximumZ - minimumZ + 1;
        previewActualDepth = Math.max(1, maximumDepth);
        previewActualRadius = previewRadius;
        previewSignature = signature;
        previewBlocks = List.copyOf(next);
        return previewBlocks;
    }

    private List<PreviewBlock> buildLakePreview(
            Rule rule, long seed, int radius, long signature) {
        MudTerrainLakeSettings settings =
                NaturalMudDepositShape.lakeSettings(seed, radius);
        MudTerrainLakeShape.Shape shape = MudTerrainLakeShape.build(settings);
        MudTerrainGenerationType type =
                previewForm == NaturalMudDepositForm.SURFACE_LAKE
                        ? MudTerrainGenerationType.LAKE_SURFACE
                        : MudTerrainGenerationType.LAKE_POOL;
        Set<Long> surfaces = MudTerrainLakeShape.surfaceInterior(shape);
        List<PreviewBlock> next = new ArrayList<>();
        for (net.minecraft.core.BlockPos pos : shape.interior()) {
            next.add(new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(),
                    surfaces.contains(pos.asLong()), false));
        }
        for (net.minecraft.core.BlockPos pos : shape.shell()) {
            if (!MudTerrainLakeShape.includesShell(type, pos)) {
                continue;
            }
            boolean cutaway = type == MudTerrainGenerationType.LAKE_POOL
                    && pos.getX() < 0 && pos.getZ() < 0;
            if (!cutaway) {
                next.add(new PreviewBlock(pos.getX(), pos.getY(), pos.getZ(),
                        false, true));
            }
        }

        List<net.minecraft.core.BlockPos> extent = new ArrayList<>(
                shape.interior().size() + shape.cavity().size()
                        + shape.shell().size());
        extent.addAll(shape.interior());
        extent.addAll(shape.cavity());
        shape.shell().stream()
                .filter(pos -> MudTerrainLakeShape.includesShell(type, pos))
                .forEach(extent::add);
        int minimumX = extent.stream().mapToInt(
                net.minecraft.core.BlockPos::getX).min().orElse(0);
        int maximumX = extent.stream().mapToInt(
                net.minecraft.core.BlockPos::getX).max().orElse(0);
        int minimumY = extent.stream().mapToInt(
                net.minecraft.core.BlockPos::getY).min().orElse(0);
        int maximumY = extent.stream().mapToInt(
                net.minecraft.core.BlockPos::getY).max().orElse(0);
        int minimumZ = extent.stream().mapToInt(
                net.minecraft.core.BlockPos::getZ).min().orElse(0);
        int maximumZ = extent.stream().mapToInt(
                net.minecraft.core.BlockPos::getZ).max().orElse(0);
        previewActualWidth = maximumX - minimumX + 1;
        previewActualLength = maximumZ - minimumZ + 1;
        previewActualDepth = maximumY - minimumY + 1;
        previewActualRadius = radius;
        previewSignature = signature;
        previewBlocks = List.copyOf(next);
        return previewBlocks;
    }

    private Rule previewRule(Rule rule) {
        if (averageRadiusField == null || radiusVariationField == null) {
            return rule;
        }
        try {
            double average = parseRadiusMetric(averageRadiusField);
            double variation = parseRadiusMetric(radiusVariationField);
            double minimum = average - variation;
            double maximum = average + variation;
            int minimumRadius = Mth.clamp((int) Math.round(minimum), 2, 12);
            int maximumRadius = Mth.clamp((int) Math.round(maximum),
                    minimumRadius, 12);
            if (!Double.isFinite(average) || !Double.isFinite(variation)
                    || variation < 0.0D || minimum < 2.0D
                    || maximum > 12.0D) {
                return rule;
            }
            return rule.withRadiusRange(minimumRadius, maximumRadius);
        } catch (InvalidRadiusException ignored) {
            return rule;
        }
    }

    private long previewSeed(Rule rule) {
        long value = rule.medium().id() * 0x9E3779B97F4A7C15L
                + previewForm.ordinal() * 0xC2B2AE3D27D4EB4FL
                + previewSample * 0x165667B19E3779F9L
                + 7L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private int previewRadius(Rule rule, long seed) {
        if (previewSample == 0) {
            return Mth.clamp((int) Math.round(rule.averageRadius()), 2, 12);
        }
        int range = rule.maximumRadius() - rule.minimumRadius() + 1;
        return rule.minimumRadius() + Math.floorMod((int) seed, range);
    }

    private static boolean exposedLayer(
            Map<Long, Integer> depths, int x, int z, int layer) {
        return depths.getOrDefault(cellKey(x - 1, z), 0) <= layer
                || depths.getOrDefault(cellKey(x + 1, z), 0) <= layer
                || depths.getOrDefault(cellKey(x, z - 1), 0) <= layer
                || depths.getOrDefault(cellKey(x, z + 1), 0) <= layer;
    }

    private static long cellKey(int x, int z) {
        return (long) x << 32 ^ z & 0xFFFFFFFFL;
    }

    private void invalidatePreview() {
        previewSignature = Long.MIN_VALUE;
        previewBlocks = List.of();
        previewActualWidth = 0;
        previewActualLength = 0;
        previewActualDepth = 0;
        previewActualRadius = 0;
    }

    private List<FormBounds> formBounds() {
        int left = formLeft() + 4;
        int right = previewLeft() - 4;
        NaturalMudDepositForm[] forms = NaturalMudDepositForm.values();
        int availableHeight = Math.max(forms.length * 12,
                height - FOOTER_HEIGHT - 47);
        int rowStep = Mth.clamp(availableHeight / forms.length, 13, 22);
        int cellHeight = Math.max(12, rowStep - 2);
        List<FormBounds> result = new ArrayList<>();
        for (int index = 0; index < forms.length; index++) {
            int y = 47 + index * rowStep;
            result.add(new FormBounds(
                    forms[index], left, y, right, y + cellHeight));
        }
        return result;
    }

    private List<BiomeEntry> filteredBiomes() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<BiomeEntry> result = new ArrayList<>();
        for (BiomeEntry entry : allBiomes) {
            if (!dimension.matches(entry.holder)
                    || !biomeSource.matches(entry)) {
                continue;
            }
            Component name = displayName(entry.id);
            if (!needle.isEmpty()
                    && !entry.id.toString().toLowerCase(Locale.ROOT).contains(needle)
                    && !name.getString().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private List<BiomeListRow> biomeListRows() {
        List<BiomeEntry> biomes = filteredBiomes();
        if (biomeSource == BiomeSourceFilter.VANILLA) {
            return biomes.stream().map(BiomeListRow::biome).toList();
        }
        List<BiomeListRow> rows = new ArrayList<>();
        String previousNamespace = null;
        for (BiomeEntry biome : biomes) {
            String namespace = biome.id.getNamespace();
            if (!namespace.equals(previousNamespace)) {
                Component sourceName = biome.isVanilla()
                        ? Component.literal("Minecraft")
                        : Component.literal(biomeSourceNames.getOrDefault(
                                namespace, namespace));
                rows.add(BiomeListRow.header(sourceName));
                previousNamespace = namespace;
            }
            rows.add(BiomeListRow.biome(biome));
        }
        return rows;
    }

    private int sidebarWidth() {
        return Mth.clamp(width / 9, 56, 145);
    }

    private int presetSelectWidth() {
        return scaledPresetWidth(112);
    }

    private int presetSaveWidth() {
        return scaledPresetWidth(82);
    }

    private int presetModifyWidth() {
        return scaledPresetWidth(82);
    }

    private int presetDeleteWidth() {
        return scaledPresetWidth(82);
    }

    private int presetHeaderGap() {
        return width < 420 ? 3 : 4;
    }

    private int scaledPresetWidth(int preferred) {
        int preferredTotal = 112 + 82 * 3;
        int gapTotal = presetHeaderGap() * 3;
        int available = Math.max(1, width - 14);
        if (available >= preferredTotal + gapTotal) {
            return preferred;
        }
        double scale = (available - gapTotal) / (double) preferredTotal;
        return Math.max(42, (int) Math.floor(preferred * scale));
    }

    private int presetHeaderRight() {
        return width - 7;
    }

    private int presetHeaderLeft() {
        int right = presetHeaderRight();
        int gap = presetHeaderGap();
        return right - presetDeleteWidth() - gap - presetModifyWidth()
                - gap - presetSaveWidth() - gap - presetSelectWidth();
    }

    private boolean insidePresetHeader(double mouseX, double mouseY) {
        return mouseX >= presetHeaderLeft() && mouseX < presetHeaderRight()
                && mouseY >= 4 && mouseY < 24;
    }

    private int presetPopupWidth() {
        return Math.min(260, Math.max(160, width - 12));
    }

    private int presetPopupLeft() {
        return width - presetPopupWidth() - 6;
    }

    private int presetPopupTop() {
        return HEADER_HEIGHT + 1;
    }

    private int presetPopupHeight() {
        int visible = Math.min(PRESET_VISIBLE_ROWS, presets.size() + 1);
        return 6 + visible * 21;
    }

    private boolean insidePresetMenu(double mouseX, double mouseY) {
        return mouseX >= presetPopupLeft() - 2
                && mouseX < presetPopupLeft() + presetPopupWidth() + 2
                && mouseY >= presetPopupTop() - 2
                && mouseY < presetPopupTop() + presetPopupHeight() + 2;
    }

    private int presetDialogWidth() {
        return Math.min(380, Math.max(180, width - 20));
    }

    private int presetDialogHeight() {
        return 104;
    }

    private int presetDialogLeft() {
        return (width - presetDialogWidth()) / 2;
    }

    private int presetDialogTop() {
        return Math.max(8, (height - presetDialogHeight()) / 2);
    }

    private boolean insidePresetDialog(double mouseX, double mouseY) {
        return mouseX >= presetDialogLeft()
                && mouseX < presetDialogLeft() + presetDialogWidth()
                && mouseY >= presetDialogTop()
                && mouseY < presetDialogTop() + presetDialogHeight();
    }

    private int settingsLeft() {
        return sidebarWidth() + 1;
    }

    private int settingsWidth() {
        int available = Math.max(1, width - sidebarWidth());
        return Mth.clamp(Math.round(available * 0.16F), 52, 160);
    }

    private int biomeLeft() {
        return settingsLeft() + settingsWidth();
    }

    private int biomeWidth() {
        int available = Math.max(1, width - sidebarWidth());
        return Mth.clamp(Math.round(available * 0.25F), 64, 240);
    }

    private int formLeft() {
        return biomeLeft() + biomeWidth();
    }

    private int formWidth() {
        int available = Math.max(1, width - sidebarWidth());
        return Mth.clamp(Math.round(available * 0.18F), 58, 132);
    }

    private int previewLeft() {
        return Math.min(width - 1, formLeft() + formWidth());
    }

    private int previewWidth() {
        return Math.max(0, width - previewLeft());
    }

    private int formAreaWidth() {
        return Math.max(0, formWidth() - 8);
    }

    private int previewTop() {
        return 45;
    }

    private int settingsTop() {
        return Math.max(47, height - FOOTER_HEIGHT - 106);
    }

    private int previewControlsTop() {
        return height - FOOTER_HEIGHT - 39;
    }

    private int previewShapeBottom() {
        return previewControlLayout() == null
                ? height - FOOTER_HEIGHT - 5
                : previewControlsTop() - 5;
    }

    private boolean insideShapePreview(double mouseX, double mouseY) {
        return mouseX >= previewLeft() + 5 && mouseX < width - 5
                && mouseY >= previewTop() && mouseY < previewShapeBottom();
    }

    private PreviewControlLayout previewControlLayout() {
        if (previewWidth() < 250) {
            return null;
        }
        int left = previewLeft() + 6;
        int right = width - 6;
        int available = right - left;
        int gap = 6;
        int randomWidth = Mth.clamp(available / 6, 62, 96);
        int groupWidth = Math.max(70,
                (available - randomWidth - gap * 2) / 2);
        int averageLeft = left + randomWidth + gap;
        int variationLeft = averageLeft + groupWidth + gap;
        return new PreviewControlLayout(
                left, randomWidth,
                averageLeft, groupWidth,
                variationLeft, Math.max(70, right - variationLeft),
                previewControlsTop(), previewControlsTop() + 13);
    }

    private int biomeRowsTop() {
        return 116;
    }

    private int visibleMediumRows() {
        return Math.max(1,
                (height - MEDIUM_LIST_TOP - FOOTER_HEIGHT - 3) / ROW_HEIGHT);
    }

    private void toggleMedium(SinkingMedium medium) {
        if (!commitRuleEditors()) {
            return;
        }
        Rule rule = profile.rule(medium);
        if (rule == null) {
            return;
        }
        profile = profile.withRule(rule.withEnabled(!rule.enabled()));
        rebuildWidgets();
    }

    private int visibleBiomeRows() {
        return Math.max(1,
                (height - FOOTER_HEIGHT - biomeRowsTop() - 3) / ROW_HEIGHT);
    }

    private static boolean validNumberInput(String value) {
        return value.isEmpty() || value.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d*)");
    }

    private static String formatPercent(int chance) {
        return String.format(Locale.ROOT, "%.3f", chance / 1000.0D);
    }

    private static String formatRadiusMetric(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-6D
                ? Integer.toString((int) Math.rint(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private Component displayName(ResourceLocation id) {
        String key = id.toLanguageKey("biome");
        return I18n.exists(key) ? Component.translatable(key)
                : Component.literal(id.toString());
    }

    private static String resolveModDisplayName(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .filter(name -> !name.isBlank())
                .orElse(namespace);
    }

    private Component fit(Component text, int available) {
        if (font.width(text) <= available) {
            return text;
        }
        return Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(0, available - font.width("..."))) + "...");
    }

    private static DimensionFilter inferredDimension(Rule rule) {
        for (String selector : rule.biomeSelectors()) {
            if (selector.contains("nether") || selector.contains("crimson")
                    || selector.contains("warped") || selector.contains("soul_sand")
                    || selector.contains("basalt")) {
                return DimensionFilter.NETHER;
            }
            if (selector.contains("end_")) {
                return DimensionFilter.END;
            }
        }
        return DimensionFilter.OVERWORLD;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record BiomeEntry(Holder<Biome> holder, ResourceLocation id) {
        private boolean isVanilla() {
            return "minecraft".equals(id.getNamespace());
        }
    }

    private record BiomeListRow(BiomeEntry biome, Component groupTitle) {
        private static BiomeListRow biome(BiomeEntry biome) {
            return new BiomeListRow(biome, null);
        }

        private static BiomeListRow header(Component title) {
            return new BiomeListRow(null, title);
        }
    }

    private record BiomeGroupHeader(int top, Component title) {
    }

    private record PreviewBlock(
            int x, int y, int z, boolean top, boolean terrain) {
    }

    private record FormBounds(
            NaturalMudDepositForm form,
            int left, int top, int right, int bottom) {
        private boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private record PreviewControlLayout(
            int randomLeft, int randomWidth,
            int averageLeft, int averageWidth,
            int variationLeft, int variationWidth,
            int labelTop, int controlTop) {
    }

    private static final class InvalidRadiusException
            extends RuntimeException {
    }

    private enum DimensionFilter {
        ALL,
        OVERWORLD,
        NETHER,
        END;

        private boolean matches(Holder<Biome> biome) {
            return switch (this) {
                case ALL -> true;
                case OVERWORLD -> biome.is(BiomeTags.IS_OVERWORLD);
                case NETHER -> biome.is(BiomeTags.IS_NETHER);
                case END -> biome.is(BiomeTags.IS_END);
            };
        }

        private DimensionFilter next() {
            DimensionFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private String translationKey() {
            return "gui.mirebound.worldgen.dimension."
                    + name().toLowerCase(Locale.ROOT);
        }
    }

    private enum BiomeSourceFilter {
        ALL,
        VANILLA,
        MODDED;

        private boolean matches(BiomeEntry biome) {
            return switch (this) {
                case ALL -> true;
                case VANILLA -> biome.isVanilla();
                case MODDED -> !biome.isVanilla();
            };
        }

        private BiomeSourceFilter next() {
            BiomeSourceFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private String translationKey() {
            return "gui.mirebound.worldgen.source."
                    + name().toLowerCase(Locale.ROOT);
        }
    }
}
