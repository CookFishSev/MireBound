package com.fish.mirebound.client;

import com.fish.mirebound.client.tuning.MudTuningClientSettings;
import com.fish.mirebound.client.tuning.MudTuningInputController;
import com.fish.mirebound.client.tuning.MudTuningNavigation;
import com.fish.mirebound.client.tuning.MudTuningScreenLayout;
import com.fish.mirebound.client.tuning.MudTuningSessionModel;
import com.fish.mirebound.client.tuning.MudTuningSessionModel.ObjectFilter;
import com.fish.mirebound.client.tuning.MudTuningSessionModel.ObjectModel;
import com.fish.mirebound.client.tuning.MudTuningSlider;
import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowButton.Tone;
import com.fish.mirebound.client.gui.MireflowEditBox;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.client.gui.MireflowToggleButton;
import com.fish.mirebound.mud.MudPhysicsParameter;
import com.fish.mirebound.mud.MudSinkingDepthControl;
import com.fish.mirebound.mud.MudTuningScope;
import com.fish.mirebound.mud.SinkingMedium;
import com.fish.mirebound.mud.harvest.MudHarvestTool;
import com.fish.mirebound.mud.tuning.MudTuningCapabilities;
import com.fish.mirebound.mud.tuning.MudTuningObjectId;
import com.fish.mirebound.network.payload.MudTuningApplyPayload;
import com.fish.mirebound.network.payload.AdaptiveMudActionPayload;
import com.fish.mirebound.network.payload.MudTuningRequestPayload;
import com.fish.mirebound.network.payload.MudTuningSessionPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Full-screen, server-authoritative tuning editor with semantic navigation. */
public final class MudPhysicsTuningScreen extends Screen {
    private static final int SESSION_REFRESH_INTERVAL = 20;
    private static final int GROUP_ROW_HEIGHT = 22;
    private static final int TAB_WIDTH = 132;
    private static final int TAB_HEIGHT = 22;
    private static final int COLOR_HEADER = MireflowGuiTheme.FULLSCREEN_HEADER;
    private static final int COLOR_SIDEBAR = MireflowGuiTheme.FULLSCREEN_SIDEBAR;
    private static final int COLOR_FOOTER = MireflowGuiTheme.FULLSCREEN_FOOTER;
    private static final int COLOR_DIVIDER = MireflowGuiTheme.DIVIDER;
    private static final int COLOR_TEXT = MireflowGuiTheme.TEXT;
    private static final int COLOR_MUTED = MireflowGuiTheme.MUTED;
    private static final int COLOR_DISABLED = MireflowGuiTheme.DISABLED;

    private final MudTuningScope returnScope;
    private final MudTuningSessionModel session;
    private final List<RowEditor> editors = new ArrayList<>();
    private MudTuningSlider maximumDepthSlider;
    private EditBox maximumDepthField;
    private MudTuningSlider naturalDepthSlider;
    private EditBox naturalDepthField;
    private boolean syncingDepthEditors;
    private MudTuningScreenLayout layout;
    private MudTuningObjectId selectedObject;
    private ObjectFilter objectFilter = ObjectFilter.NATIVE;
    private MudTuningNavigation.Group selectedGroup = MudTuningNavigation.Group.BASIC;
    private MudTuningNavigation.Page selectedPage = MudTuningNavigation.Page.BASIC_SHAPE;
    private int rowScroll;
    private int tabScroll;
    private int pageScroll;
    private int groupScroll;
    private int sessionRefreshTicks = SESSION_REFRESH_INTERVAL;
    private Component status = Component.empty();
    private int statusColor = 0xFFB8C3BC;
    private boolean saving;
    private boolean convertedActionView;
    private AdaptiveMudActionPayload.Action pendingAdaptiveAction;
    private net.minecraft.resources.ResourceLocation pendingAdaptiveSource;
    private LocalNavigation returnNavigation;

    private MudPhysicsTuningScreen(MudTuningSessionPayload payload, MudTuningScope returnScope) {
        super(Component.translatable("gui.mirebound.physics.title"));
        this.returnScope = returnScope;
        session = new MudTuningSessionModel(payload);
        chooseInitialObject();
    }

    public static void open(MudTuningSessionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        MudPhysicsTuningScreen previous = minecraft.screen instanceof MudPhysicsTuningScreen current
                ? current : null;
        if (previous != null && previous.matches(payload)) {
            previous.accept(payload);
            return;
        }
        MudTuningScope returnScope = payload.scope() == MudTuningScope.WORLD
                && previous != null
                        ? previous.session.scope() == MudTuningScope.WORLD
                                ? previous.returnScope : previous.session.scope()
                        : payload.scope();
        MudPhysicsTuningScreen next = new MudPhysicsTuningScreen(payload, returnScope);
        if (previous != null && payload.scope() == MudTuningScope.WORLD
                && previous.session.scope() != MudTuningScope.WORLD) {
            next.returnNavigation = previous.captureNavigation();
        } else if (previous != null && payload.scope() != MudTuningScope.WORLD
                && previous.session.scope() == MudTuningScope.WORLD
                && previous.returnNavigation != null) {
            next.restoreNavigation(previous.returnNavigation);
        }
        minecraft.setScreen(next);
    }

    private boolean matches(MudTuningSessionPayload payload) {
        return session.scope() == payload.scope()
                && session.first().equals(payload.first())
                && session.second().equals(payload.second());
    }

    private void accept(MudTuningSessionPayload payload) {
        commitEditors(false);
        MudTuningObjectId previous = selectedObject;
        session.accept(payload, saving);
        ObjectModel actionTarget = pendingAdaptiveTarget();
        if (actionTarget != null) {
            selectedObject = actionTarget.id();
            objectFilter = filterFor(actionTarget);
            convertedActionView = actionTarget.id().kind()
                    == MudTuningObjectId.Kind.CONVERTED_BLOCK;
            pendingAdaptiveAction = null;
            pendingAdaptiveSource = null;
            resetNavigationForObject();
        } else if (previous != null && session.find(previous) != null) {
            selectedObject = previous;
        } else if (convertedActionView && previous != null
                && previous.kind() == MudTuningObjectId.Kind.CONVERTED_BLOCK) {
            ObjectModel source = session.find(MudTuningObjectId.sourceBlock(
                    previous.sourceBlockId()));
            if (source != null) {
                selectedObject = source.id();
                objectFilter = ObjectFilter.SOURCE;
                convertedActionView = false;
                resetNavigationForObject();
            } else {
                chooseInitialObject();
            }
        } else {
            chooseInitialObject();
        }
        if (saving) {
            status = Component.translatable("gui.mirebound.physics.saved");
            statusColor = 0xFF8ED39A;
        } else if (session.objects().isEmpty()) {
            status = Component.translatable("gui.mirebound.tuning.targets_invalid");
            statusColor = 0xFFFF9C79;
        }
        saving = false;
        sessionRefreshTicks = SESSION_REFRESH_INTERVAL;
        clampNavigation();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        layout = tentacleSession()
                ? MudTuningScreenLayout.calculateTentacle(width, height)
                : MudTuningScreenLayout.calculate(width, height);
        clampNavigation();
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        normalizeUnfocusedEditors();
        if (session.scope() == MudTuningScope.WORLD || saving || --sessionRefreshTicks > 0) {
            return;
        }
        sessionRefreshTicks = SESSION_REFRESH_INTERVAL;
        PacketDistributor.sendToServer(new MudTuningRequestPayload(
                MudTuningRequestPayload.Action.REFRESH_SESSION, session.first()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The custom translucent surface intentionally skips the vanilla blur pass.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (layout == null) {
            layout = tentacleSession()
                    ? MudTuningScreenLayout.calculateTentacle(width, height)
                    : MudTuningScreenLayout.calculate(width, height);
        }
        MireflowGuiTheme.drawTranslucentSurface(graphics, 0, 0, width, height);
        graphics.fill(0, 0, width, layout.headerHeight(), COLOR_HEADER);
        if (!tentacleSession()) {
            graphics.fill(0, layout.headerHeight(), layout.sidebarWidth(),
                    layout.contentBottom(), COLOR_SIDEBAR);
        }
        graphics.fill(0, layout.contentBottom(), width, height, COLOR_FOOTER);
        graphics.hLine(0, width - 1, layout.headerHeight() - 1, COLOR_DIVIDER);
        graphics.hLine(0, width - 1, layout.contentBottom(), COLOR_DIVIDER);
        if (!tentacleSession()) {
            graphics.vLine(layout.sidebarWidth(), layout.headerHeight(),
                    layout.contentBottom(), COLOR_DIVIDER);
        }
        renderHeaderText(graphics);
        renderRowBackgrounds(graphics);
        renderMaximumDepthSeparator(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!tentacleSession()) {
            renderObjectTabs(graphics);
            renderIncompatibleGridIcons(graphics);
        }
        renderParameterLabels(graphics);
        renderStatus(graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = scrollY > 0.0D ? -1 : 1;
        if (mouseY < layout.headerHeight() && mouseX > layout.sidebarWidth()) {
            tabScroll = clamp(tabScroll + direction, 0,
                    Math.max(0, filteredObjects().size() - visibleTabCount()));
        } else if (mouseY < layout.contentTop() && mouseX > layout.sidebarWidth()) {
            List<MudTuningNavigation.Page> pages = availablePages(selectedGroup);
            pageScroll = clamp(pageScroll + direction, 0,
                    Math.max(0, pages.size() - visiblePageCount(pages)));
        } else if (mouseX <= layout.sidebarWidth()) {
            groupScroll = clamp(groupScroll + direction, 0,
                    Math.max(0, visibleGroups().size() - visibleGroupCount()));
        } else {
            rowScroll = clamp(rowScroll + direction, 0,
                    Math.max(0, pageRowCount() - layout.visibleRows()));
        }
        rebuildWidgets();
        return true;
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        editors.clear();
        maximumDepthSlider = null;
        maximumDepthField = null;
        naturalDepthSlider = null;
        naturalDepthField = null;
        syncingDepthEditors = false;
        if (layout == null) {
            return;
        }
        if (tentacleSession()) {
            addPageButtons();
            if (current() != null && pageEditable()) {
                addParameterRows();
            }
            addTentacleFooterButtons();
            return;
        }
        addHeaderButtons();
        addObjectFilterButtons();
        if (incompatibleView()) {
            addIncompatibleGridButtons();
        } else {
            addObjectTabButtons();
            addGroupButtons();
            addPageButtons();
            if (adaptiveActionView()) {
                addAdaptiveActionRow();
            } else if (current() != null && pageEditable()) {
                addParameterRows();
            }
        }
        addFooterButtons();
    }

    private void addHeaderButtons() {
        int worldWidth = layout.compact() ? 66 : 104;
        addRenderableWidget(MireflowButton.builder(scopeSwitchLabel(), ignored -> switchScope())
                .bounds(width - worldWidth - 6, 5, worldWidth, 20).build());
    }

    private void addObjectFilterButtons() {
        List<ObjectFilter> filters = Arrays.stream(ObjectFilter.values())
                .filter(this::hasFilter)
                .toList();
        if (filters.isEmpty()) {
            return;
        }
        int left = layout.sidebarWidth() + 7;
        int y = 27;
        int gap = 3;
        int available = width - left - 7;
        int filterWidth = Math.min(92,
                Math.max(1, (available - gap * (filters.size() - 1)) / filters.size()));
        int groupWidth = filterWidth * filters.size() + gap * (filters.size() - 1);
        int x = left + Math.max(0, (available - groupWidth) / 2);
        for (ObjectFilter filter : filters) {
            Component label = Component.translatable(filter.translationKey());
            Button button = MireflowButton.builder(label,
                    ignored -> selectFilter(filter))
                    .tone(filterTone(filter))
                    .selected(filter == objectFilter)
                    .bounds(x, y, filterWidth, 18).build();
            addRenderableWidget(button);
            x += filterWidth + gap;
        }
    }

    private void addObjectTabButtons() {
        List<ObjectModel> filtered = filteredObjects();
        int count = Math.min(visibleTabCount(), Math.max(0, filtered.size() - tabScroll));
        int startX = Math.max(layout.sidebarWidth() + 8,
                (width + layout.sidebarWidth() - count * TAB_WIDTH) / 2);
        int y = layout.headerHeight() - TAB_HEIGHT - 2;
        for (int slot = 0; slot < count; slot++) {
            ObjectModel object = filtered.get(tabScroll + slot);
            Component label = Component.literal("   ").append(fit(object.name(), TAB_WIDTH - 12));
            Button button = MireflowButton.builder(label, ignored -> selectObject(object.id()))
                    .selected(object.id().equals(selectedObject))
                    .bounds(startX + slot * TAB_WIDTH, y, TAB_WIDTH - 4, TAB_HEIGHT).build();
            addRenderableWidget(button);
        }
        if (filtered.size() > visibleTabCount()) {
            Button previous = MireflowButton.builder(Component.literal("<"), ignored -> {
                tabScroll = Math.max(0, tabScroll - visibleTabCount());
                rebuildWidgets();
            }).bounds(layout.sidebarWidth() + 5, y, 20, TAB_HEIGHT).build();
            previous.active = tabScroll > 0;
            addRenderableWidget(previous);
            Button next = MireflowButton.builder(Component.literal(">"), ignored -> {
                tabScroll = Math.min(Math.max(0, filtered.size() - visibleTabCount()),
                        tabScroll + visibleTabCount());
                rebuildWidgets();
            }).bounds(width - 25, y, 20, TAB_HEIGHT).build();
            next.active = tabScroll + count < filtered.size();
            addRenderableWidget(next);
        }
    }

    private void addGroupButtons() {
        List<MudTuningNavigation.Group> groups = visibleGroups();
        int count = Math.min(visibleGroupCount(), Math.max(0, groups.size() - groupScroll));
        for (int slot = 0; slot < count; slot++) {
            MudTuningNavigation.Group group = groups.get(groupScroll + slot);
            Component text = layout.compact() ? Component.literal(group.compactLabel())
                    : Component.translatable(group.translationKey());
            Button button = MireflowButton.builder(text, ignored -> selectGroup(group))
                    .selected(group == selectedGroup)
                    .bounds(4, layout.headerHeight() + 5 + slot * GROUP_ROW_HEIGHT,
                            layout.sidebarWidth() - 8, 20).build();
            button.setTooltip(Tooltip.create(Component.translatable(group.translationKey())));
            addRenderableWidget(button);
        }
    }

    private void addPageButtons() {
        List<MudTuningNavigation.Page> pages = availablePages(selectedGroup);
        if (pages.isEmpty()) {
            return;
        }
        int x = layout.contentLeft() + 7;
        int y = layout.headerHeight() + 4;
        int available = width - x - 7;
        int gap = 3;
        int visible = visiblePageCount(pages);
        boolean overflow = pages.size() > visible;
        int navigationWidth = overflow ? 45 : 0;
        int pageArea = Math.max(54, available - navigationWidth);
        int buttonWidth = Math.max(54, Math.min(120,
                (pageArea - gap * Math.max(0, visible - 1)) / visible));
        pageScroll = clamp(pageScroll, 0, Math.max(0, pages.size() - visible));
        for (int slot = 0; slot < visible; slot++) {
            MudTuningNavigation.Page page = pages.get(pageScroll + slot);
            Button button = MireflowButton.builder(Component.translatable(page.translationKey()),
                    ignored -> selectPage(page)).selected(page == selectedPage)
                    .bounds(x, y, buttonWidth, 20).build();
            if (page.finiteFlow() && current() != null
                    && !current().capabilities().has(MudTuningCapabilities.FINITE_FLOW)) {
                button.setTooltip(Tooltip.create(Component.translatable(
                        "gui.mirebound.tuning.sable_flow_unsupported")));
            }
            addRenderableWidget(button);
            x += buttonWidth + gap;
        }
        if (overflow) {
            Button previous = MireflowButton.builder(Component.literal("<"), ignored -> {
                if (commitEditors(true)) {
                    pageScroll = Math.max(0, pageScroll - 1);
                    rebuildWidgets();
                }
            }).bounds(width - 45, y, 19, 20).build();
            previous.active = pageScroll > 0;
            addRenderableWidget(previous);
            Button next = MireflowButton.builder(Component.literal(">"), ignored -> {
                if (commitEditors(true)) {
                    pageScroll = Math.min(pages.size() - visible, pageScroll + 1);
                    rebuildWidgets();
                }
            }).bounds(width - 23, y, 19, 20).build();
            next.active = pageScroll + visible < pages.size();
            addRenderableWidget(next);
        }
    }

    private void addParameterRows() {
        ObjectModel object = current();
        List<MudPhysicsParameter> parameters = parameterRows();
        int restoreRows = convertedRestoreRowCount();
        int depthControlRows = depthControlRowCount();
        int shapeRows = shapeRowCount();
        int count = Math.min(layout.visibleRows(), Math.max(0, pageRowCount() - rowScroll));
        for (int slot = 0; slot < count; slot++) {
            int logicalRow = rowScroll + slot;
            int y = layout.contentTop() + slot * layout.rowHeight() + 6;
            if (logicalRow < restoreRows) {
                addConvertedRestoreControls(object, y);
                continue;
            }
            int contentRow = logicalRow - restoreRows;
            if (contentRow < depthControlRows) {
                if (contentRow == 0) {
                    addDepthControlMode(object, y);
                } else if (contentRow == 1) {
                    addDepthValueControl(object, y, false);
                } else {
                    addDepthValueControl(object, y, true);
                }
                continue;
            }
            int parameterRow = contentRow - depthControlRows;
            if (parameterRow < shapeRows) {
                addShapeControl(object, y);
            } else {
                addParameterControl(object, parameters.get(parameterRow - shapeRows), y);
            }
        }
    }

    private void addDepthControlMode(ObjectModel object, int y) {
        int x = layout.contentLeft() + labelWidth() + 9;
        int right = width - 7;
        int resetWidth = layout.compact() ? 38 : 52;
        int resetX = right - resetWidth;
        int available = Math.max(108, resetX - x - 3);
        int optionWidth = Math.max(52, (available - 3) / 2);
        Tooltip tooltip = Tooltip.create(Component.translatable(
                "gui.mirebound.tuning.depth_control_mode.tooltip"));
        MudSinkingDepthControl.Mode currentMode = object.depthControlMode();

        Button simple = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.tuning.depth_control_mode.simple"), ignored -> {
                    if (commitEditors(true)) {
                        object.setDepthControlMode(MudSinkingDepthControl.Mode.SIMPLE);
                        rebuildWidgets();
                    }
                }).selected(currentMode == MudSinkingDepthControl.Mode.SIMPLE)
                .bounds(x, y, optionWidth, 20).build();
        simple.setTooltip(tooltip);
        addRenderableWidget(simple);

        Button advanced = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.tuning.depth_control_mode.advanced"), ignored -> {
                    if (commitEditors(true)) {
                        object.setDepthControlMode(MudSinkingDepthControl.Mode.ADVANCED);
                        rebuildWidgets();
                    }
                }).selected(currentMode == MudSinkingDepthControl.Mode.ADVANCED)
                .bounds(x + optionWidth + 3, y,
                        Math.max(52, available - optionWidth - 3), 20).build();
        advanced.setTooltip(tooltip);
        addRenderableWidget(advanced);

        Button reset = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.physics.reset_short"), ignored -> {
                    if (commitEditors(true)) {
                        object.resetDepthControlMode();
                        rebuildWidgets();
                    }
                }).bounds(resetX, y, resetWidth, 20).build();
        reset.active = object.depthControlModeDiffersFromBaseline();
        reset.setTooltip(tooltip);
        addRenderableWidget(reset);
    }

    private void addDepthValueControl(ObjectModel object, int y, boolean natural) {
        int x = layout.contentLeft() + labelWidth() + 9;
        int right = width - 7;
        int resetWidth = layout.compact() ? 38 : 52;
        int stepWidth = 18;
        int fieldWidth = layout.compact() ? 48 : 68;
        int resetX = right - resetWidth;
        int plusX = resetX - stepWidth - 3;
        int fieldX = plusX - fieldWidth - 3;
        int minusX = fieldX - stepWidth - 3;
        int sliderWidth = Math.max(28, minusX - x - 3);
        double value = natural
                ? object.naturalSinkingDepth() : object.maximumSinkingDepth();
        value = MudTuningSlider.snapValue(
                MudSinkingDepthControl.MINIMUM, MudSinkingDepthControl.MAXIMUM,
                MudSinkingDepthControl.STEP, value);
        boolean editable = object.depthControlMode() == MudSinkingDepthControl.Mode.SIMPLE;
        Tooltip tooltip = Tooltip.create(Component.translatable(
                natural
                        ? "gui.mirebound.tuning.natural_sinking_depth.tooltip"
                        : "gui.mirebound.tuning.maximum_sinking_depth.tooltip"));

        MudTuningSlider slider = new MudTuningSlider(
                x, y, sliderWidth, 20,
                MudSinkingDepthControl.MINIMUM,
                MudSinkingDepthControl.MAXIMUM,
                MudSinkingDepthControl.STEP,
                MudSinkingDepthControl.DECIMALS,
                value,
                next -> {
                    setDepthValue(object, natural, next);
                    rebuildWidgets();
                });
        slider.setTooltip(tooltip);
        slider.active = editable;
        addRenderableWidget(slider);
        if (natural) {
            naturalDepthSlider = slider;
        } else {
            maximumDepthSlider = slider;
        }

        Button minus = MireflowButton.builder(Component.literal("-"), ignored -> {
            setDepthValue(object, natural,
                    depthValue(object, natural) - MudSinkingDepthControl.STEP);
            rebuildWidgets();
        }).bounds(minusX, y, stepWidth, 20).build();
        minus.setTooltip(tooltip);
        minus.active = editable;
        addRenderableWidget(minus);

        EditBox field = new MireflowEditBox(font, fieldX, y, fieldWidth, 20,
                Component.translatable(natural
                        ? "gui.mirebound.tuning.natural_sinking_depth"
                        : "gui.mirebound.tuning.maximum_sinking_depth"));
        field.setFilter(MudPhysicsTuningScreen::validNumberInput);
        field.setValue(formatMaximumDepth(value));
        field.setTooltip(tooltip);
        field.setEditable(editable);
        field.active = editable;
        field.setResponder(text -> {
            if (syncingDepthEditors) {
                return;
            }
            Double parsed = parseDouble(text);
            if (parsed != null) {
                setDepthValue(object, natural, slider.snapValue(parsed));
                slider.setParameterValue(depthValue(object, natural));
                syncDepthControls(object, field);
            }
        });
        addRenderableWidget(field);
        if (natural) {
            naturalDepthField = field;
        } else {
            maximumDepthField = field;
        }

        Button plus = MireflowButton.builder(Component.literal("+"), ignored -> {
            setDepthValue(object, natural,
                    depthValue(object, natural) + MudSinkingDepthControl.STEP);
            rebuildWidgets();
        }).bounds(plusX, y, stepWidth, 20).build();
        plus.setTooltip(tooltip);
        plus.active = editable;
        addRenderableWidget(plus);

        Button reset = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.physics.reset_short"), ignored -> {
                    if (natural) {
                        object.resetNaturalSinkingDepth();
                    } else {
                        object.resetMaximumSinkingDepth();
                    }
                    rebuildWidgets();
                }).bounds(resetX, y, resetWidth, 20).build();
        reset.active = editable && (natural
                ? object.naturalSinkingDepthDiffersFromBaseline()
                : object.maximumSinkingDepthDiffersFromBaseline());
        reset.setTooltip(tooltip);
        addRenderableWidget(reset);
    }

    private static double depthValue(ObjectModel object, boolean natural) {
        return natural ? object.naturalSinkingDepth() : object.maximumSinkingDepth();
    }

    private static void setDepthValue(ObjectModel object, boolean natural, double value) {
        if (natural) {
            object.setNaturalSinkingDepth(value);
        } else {
            object.setMaximumSinkingDepth(value);
        }
    }

    private void addAdaptiveActionRow() {
        ObjectModel object = current();
        if (object == null) {
            return;
        }
        boolean convert = object.id().kind() == MudTuningObjectId.Kind.SOURCE_BLOCK;
        if (!convert) {
            addConvertedRestoreControls(object, layout.contentTop() + 6);
            return;
        }
        if (!object.capabilities().has(MudTuningCapabilities.CONVERT)) {
            return;
        }
        int x = layout.contentLeft() + 9;
        int available = Math.max(120, width - x - 9);
        boolean single = session.scope() == MudTuningScope.SINGLE;
        int typeWidth = single ? available : Math.max(72, (available * 3) / 5 - 2);
        Button type = MireflowButton.builder(fit(Component.translatable(
                        "gui.mirebound.tuning.convert_source_type", object.name()),
                        typeWidth - 8),
                ignored -> mutateAdaptive(true, false))
                .tone(Tone.CONVERTED)
                .bounds(x, layout.contentTop() + 6, typeWidth, 20).build();
        configureAdaptiveAction(type);
        addRenderableWidget(type);
        if (single) {
            return;
        }
        int allX = x + typeWidth + 4;
        Button all = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.tuning.convert_all_sources"),
                ignored -> mutateAdaptive(true, true))
                .tone(Tone.CONVERTED)
                .bounds(allX, layout.contentTop() + 6,
                        Math.max(48, x + available - allX), 20).build();
        configureAdaptiveAction(all);
        addRenderableWidget(all);
    }

    private void addConvertedRestoreControls(ObjectModel object, int y) {
        if (!object.capabilities().has(MudTuningCapabilities.RESTORE)) {
            return;
        }
        int x = layout.contentLeft() + 9;
        int available = Math.max(120, width - x - 9);
        boolean single = session.scope() == MudTuningScope.SINGLE;
        int typeWidth = single ? available : Math.max(72, (available * 3) / 5 - 2);
        Button type = MireflowButton.builder(fit(Component.translatable(
                        "gui.mirebound.tuning.restore_source_type", object.name()),
                        typeWidth - 8),
                ignored -> mutateAdaptive(false, false))
                .tone(Tone.CONVERTED)
                .bounds(x, y, typeWidth, 20).build();
        configureAdaptiveAction(type);
        addRenderableWidget(type);
        if (single) {
            return;
        }
        int allX = x + typeWidth + 4;
        Button all = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.tuning.restore_all_sources"),
                ignored -> mutateAdaptive(false, true))
                .tone(Tone.CONVERTED)
                .bounds(allX, y, Math.max(48, x + available - allX), 20).build();
        configureAdaptiveAction(all);
        addRenderableWidget(all);
    }

    private void configureAdaptiveAction(Button button) {
        boolean unlocked = MudTuningClientSettings.conversionUnlocked();
        button.active = session.editable() && unlocked;
        if (!unlocked) {
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.mirebound.tuning.conversion_locked",
                    Component.translatable("hud.mirebound.tuning.mode.convert")
                            .withStyle(ChatFormatting.RED))));
        }
    }

    private void addIncompatibleGridButtons() {
        List<ObjectModel> objects = filteredObjects();
        int columns = incompatibleColumns();
        int cellWidth = incompatibleCellWidth(columns);
        int start = rowScroll * columns;
        int visible = Math.min(objects.size() - start, layout.visibleRows() * columns);
        for (int index = 0; index < visible; index++) {
            int absolute = start + index;
            ObjectModel object = objects.get(absolute);
            int column = index % columns;
            int row = index / columns;
            int x = layout.contentLeft() + 8 + column * (cellWidth + 4);
            int y = layout.contentTop() + row * layout.rowHeight() + 5;
            Button entry = MireflowButton.builder(
                    Component.literal("   ").append(object.name()), ignored -> {
                    }).tone(Tone.INCOMPATIBLE).bounds(x, y, cellWidth, 20).build();
            entry.active = false;
            addRenderableWidget(entry);
        }
    }

    private void addShapeControl(ObjectModel object, int y) {
        int x = layout.contentLeft() + labelWidth() + 9;
        int right = width - 7;
        int resetWidth = layout.compact() ? 38 : 52;
        int resetX = right - resetWidth;
        int fieldWidth = layout.compact() ? 40 : 48;
        int fieldX = resetX - fieldWidth - 3;
        int sliderWidth = Math.max(28, fieldX - x - 3);
        EditBox height = new MireflowEditBox(font, fieldX, y, fieldWidth, 20,
                Component.translatable("gui.mirebound.physics.block_height"));
        height.setFilter(value -> value.isEmpty() || value.matches("(?:[1-9]|1[0-6])"));
        height.setValue(Integer.toString(object.blockHeight()));
        Button reset = MireflowButton.builder(Component.translatable(
                        "gui.mirebound.physics.reset_short"), ignored -> {
                    object.resetBlockHeight();
                    rebuildWidgets();
                }).bounds(resetX, y, resetWidth, 20).build();
        reset.active = object.blockHeightDiffersFromBaseline();
        MudTuningSlider slider = new MudTuningSlider(x, y, sliderWidth, 20,
                1.0D, 16.0D, 1.0D, 0, object.blockHeight(), next -> {
                    object.setBlockHeight((int) Math.round(next));
                    String text = Integer.toString(object.blockHeight());
                    if (!height.getValue().equals(text)) {
                        height.setValue(text);
                    }
                    reset.active = object.blockHeightDiffersFromBaseline();
                });
        height.setResponder(value -> {
            if (!value.isBlank()) {
                object.setBlockHeight(Integer.parseInt(value));
                slider.setParameterValue(object.blockHeight());
                reset.active = object.blockHeightDiffersFromBaseline();
            }
        });
        addRenderableWidget(slider);
        addRenderableWidget(height);
        addRenderableWidget(reset);
    }

    private void addParameterControl(ObjectModel object, MudPhysicsParameter parameter, int y) {
        int x = layout.contentLeft() + labelWidth() + 9;
        int right = width - 7;
        int resetWidth = layout.compact() ? 38 : 52;
        int stepWidth = 18;
        int fieldWidth = layout.compact() ? 48 : 68;
        int resetX = right - resetWidth;
        int plusX = resetX - stepWidth - 3;
        int fieldX = plusX - fieldWidth - 3;
        int minusX = fieldX - stepWidth - 3;
        int sliderWidth = Math.max(28, minusX - x - 3);
        double value = object.values()[parameter.ordinal()];
        double displayValue = MudTuningSlider.snapValue(
                parameter.minimum(), parameter.maximum(), parameter.step(), value);
        boolean editable = selectedPage != MudTuningNavigation.Page.PHYSICS_DEPTH
                || object.canEditDepthParameters();
        if (parameter.isToggle()) {
            Button toggle = new MireflowToggleButton(
                    x, y, Math.max(54, resetX - x - 3), 20, value >= 0.5D,
                    ignored -> {
                        object.set(parameter, value >= 0.5D ? 0.0D : 1.0D);
                        rebuildWidgets();
                    });
            toggle.active = editable;
            if (parameter == MudPhysicsParameter.GRAVITY_FALLING_ENABLED
                    || parameter == MudPhysicsParameter.FLOW_ENABLED) {
                toggle.setTooltip(Tooltip.create(Component.translatable(
                        "gui.mirebound.tuning.block_motion_exclusive.tooltip")));
            }
            addRenderableWidget(toggle);
        } else if (parameter.isHarvestToolChoice()) {
            Button choice = MireflowButton.builder(harvestToolLabel(displayValue), ignored -> {
                int count = MudHarvestTool.values().length;
                object.set(parameter, (Math.round(displayValue) + 1) % count);
                rebuildWidgets();
            }).bounds(x, y, Math.max(54, resetX - x - 3), 20).build();
            choice.active = editable;
            addRenderableWidget(choice);
        } else {
            MudTuningSlider slider = new MudTuningSlider(x, y, sliderWidth, 20,
                    parameter, value, next -> {
                        object.set(parameter, next);
                        rebuildWidgets();
                    });
            slider.active = editable;
            addRenderableWidget(slider);
            Button minus = MireflowButton.builder(Component.literal("-"), ignored -> {
                object.set(parameter, object.values()[parameter.ordinal()] - parameter.step());
                rebuildWidgets();
            }).bounds(minusX, y, stepWidth, 20).build();
            minus.active = editable;
            addRenderableWidget(minus);
            EditBox field = new MireflowEditBox(font, fieldX, y, fieldWidth, 20,
                    Component.translatable(parameter.translationKey()));
            field.setFilter(MudPhysicsTuningScreen::validNumberInput);
            field.setValue(format(parameter, displayValue));
            field.setEditable(editable);
            field.active = editable;
            field.setResponder(text -> {
                if (syncingDepthEditors) {
                    return;
                }
                Double parsed = parseDouble(text);
                if (parsed != null) {
                    object.set(parameter, slider.snapValue(parsed));
                    slider.setParameterValue(object.values()[parameter.ordinal()]);
                }
            });
            addRenderableWidget(field);
            if (editable) {
                editors.add(new RowEditor(parameter, field));
            }
            Button plus = MireflowButton.builder(Component.literal("+"), ignored -> {
                object.set(parameter, object.values()[parameter.ordinal()] + parameter.step());
                rebuildWidgets();
            }).bounds(plusX, y, stepWidth, 20).build();
            plus.active = editable;
            addRenderableWidget(plus);
        }
        Button reset = MireflowButton.builder(Component.translatable("gui.mirebound.physics.reset_short"),
                ignored -> {
                    object.reset(parameter);
                    rebuildWidgets();
                }).bounds(resetX, y, resetWidth, 20).build();
        reset.active = editable && object.differsFromBaseline(parameter);
        addRenderableWidget(reset);
    }

    private void syncDepthControls(ObjectModel object, EditBox source) {
        if (syncingDepthEditors) {
            return;
        }
        syncingDepthEditors = true;
        try {
            if (maximumDepthSlider != null) {
                maximumDepthSlider.setParameterValue(object.maximumSinkingDepth());
            }
            if (maximumDepthField != null && maximumDepthField != source) {
                maximumDepthField.setValue(formatMaximumDepth(
                        object.maximumSinkingDepth()));
            }
            if (naturalDepthSlider != null) {
                naturalDepthSlider.setParameterValue(object.naturalSinkingDepth());
            }
            if (naturalDepthField != null && naturalDepthField != source) {
                naturalDepthField.setValue(formatMaximumDepth(
                        object.naturalSinkingDepth()));
            }
        } finally {
            syncingDepthEditors = false;
        }
    }

    private void normalizeUnfocusedEditors() {
        ObjectModel object = current();
        if (object == null || syncingDepthEditors) {
            return;
        }
        if (maximumDepthField != null && !maximumDepthField.isFocused()) {
            normalizeDepthField(object, maximumDepthField, false);
        }
        if (naturalDepthField != null && !naturalDepthField.isFocused()) {
            normalizeDepthField(object, naturalDepthField, true);
        }
        for (RowEditor editor : editors) {
            if (!editor.field.isFocused()) {
                Double parsed = parseDouble(editor.field.getValue());
                if (parsed != null) {
                    double normalized = MudTuningSlider.snapValue(
                            editor.parameter.minimum(), editor.parameter.maximum(),
                            editor.parameter.step(), parsed);
                    editor.field.setValue(format(editor.parameter, normalized));
                }
            }
        }
    }

    private void normalizeDepthField(ObjectModel object, EditBox field,
            boolean natural) {
        Double parsed = parseDouble(field.getValue());
        if (parsed == null) {
            return;
        }
        setDepthValue(object, natural, MudTuningSlider.snapValue(
                MudSinkingDepthControl.MINIMUM, MudSinkingDepthControl.MAXIMUM,
                MudSinkingDepthControl.STEP, parsed));
        field.setValue(formatMaximumDepth(depthValue(object, natural)));
    }

    private void addFooterButtons() {
        int y = height - 27;
        int closeWidth = 68;
        int applyWidth = 68;
        int followWidth = layout.compact() ? 72 : 96;
        addRenderableWidget(MireflowButton.builder(Component.translatable("gui.mirebound.physics.cancel"),
                ignored -> onClose()).bounds(width - closeWidth - 7, y, closeWidth, 20).build());
        Button apply = MireflowButton.builder(Component.translatable("gui.mirebound.physics.apply"),
                ignored -> apply()).bounds(width - closeWidth - applyWidth - 11,
                        y, applyWidth, 20).build();
        apply.active = canApply();
        addRenderableWidget(apply);
        Button follow = MireflowButton.builder(Component.translatable("gui.mirebound.physics.follow_world"),
                ignored -> followBaseline())
                .bounds(width - closeWidth - applyWidth - followWidth - 15,
                        y, followWidth, 20).build();
        follow.active = canApply() && session.scope() != MudTuningScope.WORLD
                && current() != null && current().anyLocal();
        follow.setTooltip(Tooltip.create(Component.translatable(
                "gui.mirebound.physics.follow_world.tooltip")));
        addRenderableWidget(follow);
    }

    private void addTentacleFooterButtons() {
        int y = height - 27;
        int closeWidth = 74;
        int applyWidth = 74;
        addRenderableWidget(MireflowButton.builder(Component.translatable("gui.mirebound.physics.cancel"),
                ignored -> onClose()).bounds(width - closeWidth - 7, y, closeWidth, 20).build());
        Button apply = MireflowButton.builder(Component.translatable("gui.mirebound.physics.apply"),
                ignored -> apply()).bounds(width - closeWidth - applyWidth - 11,
                        y, applyWidth, 20).build();
        apply.active = canApply();
        addRenderableWidget(apply);
    }

    private void renderHeaderText(GuiGraphics graphics) {
        if (tentacleSession()) {
            graphics.drawString(font, Component.translatable(
                    "gui.mirebound.tuning.tentacle_settings"), 8, 10, COLOR_TEXT, false);
            if (!session.editable()) {
                graphics.drawString(font, Component.translatable("gui.mirebound.physics.read_only"),
                        width - font.width(Component.translatable(
                                "gui.mirebound.physics.read_only")) - 8,
                        10, 0xFFFF8974, false);
            }
            return;
        }
        int textWidth = Math.max(40, headerButtonLeft() - 14);
        graphics.drawString(font, fit(title, textWidth), 8, 7, COLOR_TEXT, false);
        graphics.drawString(font, fit(scopeTitle(), textWidth), 8, 19, 0xFFABC4AF, false);
        if (!session.editable()) {
            graphics.drawString(font, Component.translatable("gui.mirebound.physics.read_only"),
                    8, 31, 0xFFFF8974, false);
        }
    }

    private void renderObjectTabs(GuiGraphics graphics) {
        if (incompatibleView()) {
            return;
        }
        List<ObjectModel> filtered = filteredObjects();
        int count = Math.min(visibleTabCount(), Math.max(0, filtered.size() - tabScroll));
        int startX = Math.max(layout.sidebarWidth() + 8,
                (width + layout.sidebarWidth() - count * TAB_WIDTH) / 2);
        int y = layout.headerHeight() - TAB_HEIGHT - 2;
        for (int slot = 0; slot < count; slot++) {
            ObjectModel object = filtered.get(tabScroll + slot);
            if (!object.icon().isEmpty()) {
                graphics.renderItem(object.icon(), startX + slot * TAB_WIDTH + 3, y + 3);
            }
        }
    }

    private void renderIncompatibleGridIcons(GuiGraphics graphics) {
        if (!incompatibleView()) {
            return;
        }
        List<ObjectModel> objects = filteredObjects();
        int columns = incompatibleColumns();
        int cellWidth = incompatibleCellWidth(columns);
        int start = rowScroll * columns;
        int visible = Math.min(objects.size() - start, layout.visibleRows() * columns);
        for (int index = 0; index < visible; index++) {
            ObjectModel object = objects.get(start + index);
            if (object.icon().isEmpty()) {
                continue;
            }
            int column = index % columns;
            int row = index / columns;
            int x = layout.contentLeft() + 10 + column * (cellWidth + 4);
            int y = layout.contentTop() + row * layout.rowHeight() + 7;
            graphics.renderItem(object.icon(), x, y);
        }
    }

    private void renderRowBackgrounds(GuiGraphics graphics) {
        if (incompatibleView()) {
            return;
        }
        int count = Math.min(layout.visibleRows(),
                Math.max(0, pageRowCount() - rowScroll));
        for (int slot = 0; slot < count; slot++) {
            int y = layout.contentTop() + slot * layout.rowHeight();
            graphics.fill(layout.contentLeft() + 5, y + 2, width - 5,
                    y + layout.rowHeight() - 2, slot % 2 == 0 ? 0x7A222824 : 0x7A1C211E);
            graphics.hLine(layout.contentLeft() + 5, width - 6,
                    y + layout.rowHeight() - 2, 0x66444D47);
        }
    }

    private void renderMaximumDepthSeparator(GuiGraphics graphics) {
        int depthControlRows = depthControlRowCount();
        if (depthControlRows == 0) {
            return;
        }
        int separatorSlot = convertedRestoreRowCount() + depthControlRows - rowScroll;
        int y = layout.contentTop() + separatorSlot * layout.rowHeight() - 2;
        if (y < layout.contentTop() || y >= layout.contentBottom()) {
            return;
        }
        graphics.hLine(layout.contentLeft() + 7, width - 8, y, COLOR_DIVIDER);
    }

    private void renderParameterLabels(GuiGraphics graphics) {
        int x = layout.contentLeft() + 9;
        int labelWidth = labelWidth() - 4;
        if (incompatibleView()) {
            graphics.drawString(font, Component.translatable(
                            "gui.mirebound.tuning.incompatible_heading"),
                    x, layout.contentTop() - 15, 0xFFFF8B79, false);
            return;
        }
        if (adaptiveActionView()) {
            graphics.drawString(font, Component.translatable(
                            "gui.mirebound.tuning.source_actions"),
                    x, layout.contentTop() - 15, COLOR_MUTED, false);
            return;
        }
        if (current() == null
                || !current().capabilities().has(MudTuningCapabilities.EDIT_PARAMETERS)) {
            Component message = session.objects().isEmpty()
                    ? Component.translatable("gui.mirebound.tuning.targets_invalid")
                    : Component.translatable("gui.mirebound.tuning.source_read_only");
            graphics.drawString(font, message, x, layout.contentTop() + 10,
                    COLOR_DISABLED, false);
            return;
        }
        if (selectedPage.finiteFlow()
                && !current().capabilities().has(MudTuningCapabilities.FINITE_FLOW)) {
            graphics.drawString(font, Component.translatable(
                    "gui.mirebound.tuning.sable_flow_unsupported"),
                    x, layout.contentTop() + 10, 0xFFFFB26F, false);
            return;
        }
        List<MudPhysicsParameter> rows = parameterRows();
        int restoreRows = convertedRestoreRowCount();
        int depthControlRows = depthControlRowCount();
        int shapeRows = shapeRowCount();
        int count = Math.min(layout.visibleRows(), Math.max(0, pageRowCount() - rowScroll));
        for (int slot = 0; slot < count; slot++) {
            int logicalRow = rowScroll + slot;
            if (logicalRow < restoreRows) {
                continue;
            }
            int contentRow = logicalRow - restoreRows;
            Component label;
            int color = COLOR_TEXT;
            if (contentRow < depthControlRows) {
                label = Component.translatable(switch (contentRow) {
                    case 0 -> "gui.mirebound.tuning.depth_control_mode";
                    case 1 -> "gui.mirebound.tuning.maximum_sinking_depth";
                    default -> "gui.mirebound.tuning.natural_sinking_depth";
                });
                if (contentRow > 0 && current().canEditDepthParameters()) {
                    color = COLOR_DISABLED;
                }
            } else {
                int parameterRow = contentRow - depthControlRows;
                label = parameterRow < shapeRows
                    ? Component.translatable("gui.mirebound.physics.block_height")
                    : Component.translatable(rows.get(parameterRow - shapeRows).translationKey());
                if (selectedPage == MudTuningNavigation.Page.PHYSICS_DEPTH
                        && !current().canEditDepthParameters()) {
                    color = COLOR_DISABLED;
                }
            }
            drawWrappedLabel(graphics, label, x,
                    layout.contentTop() + slot * layout.rowHeight() + 7, labelWidth, color);
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        if (!status.getString().isEmpty()) {
            graphics.drawString(font, fit(status, Math.max(40, footerButtonLeft() - 12)),
                    7, height - 21, statusColor, false);
        }
    }

    private void drawWrappedLabel(GuiGraphics graphics, Component label,
            int x, int y, int width, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(label, width);
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), x, y + index * 10, color, false);
        }
    }

    private List<MudPhysicsParameter> parameterRows() {
        ObjectModel object = current();
        if (object == null || selectedPage.finiteFlow()
                        && !object.capabilities().has(MudTuningCapabilities.FINITE_FLOW)) {
            return List.of();
        }
        return object.parameters(selectedPage).stream()
                .filter(parameter -> parameter != MudPhysicsParameter.SHAPE_TYPE
                        && parameter != MudPhysicsParameter.SURFACE_HEIGHT)
                .toList();
    }

    private int shapeRowCount() {
        ObjectModel object = current();
        return object != null && session.scope() != MudTuningScope.WORLD
                && selectedPage == MudTuningNavigation.Page.BASIC_SHAPE
                && object.capabilities().has(MudTuningCapabilities.EDIT_SHAPE) ? 1 : 0;
    }

    private int pageRowCount() {
        if (incompatibleView()) {
            return (filteredObjects().size() + incompatibleColumns() - 1)
                    / incompatibleColumns();
        }
        if (adaptiveActionView()) {
            return 1;
        }
        return convertedRestoreRowCount() + depthControlRowCount()
                + shapeRowCount() + parameterRows().size();
    }

    private int depthControlRowCount() {
        ObjectModel object = current();
        return selectedPage == MudTuningNavigation.Page.PHYSICS_DEPTH
                && object != null
                && object.accepts(MudPhysicsParameter.MAX_DEPTH_FACTOR)
                && object.accepts(MudPhysicsParameter.COLUMN_MARGIN)
                && object.accepts(MudPhysicsParameter.SIMPLE_MAXIMUM_SINKING_DEPTH)
                && object.accepts(MudPhysicsParameter.SIMPLE_NATURAL_SINKING_DEPTH)
                && object.accepts(MudPhysicsParameter.SINKING_DEPTH_CONTROL_MODE) ? 3 : 0;
    }

    private int convertedRestoreRowCount() {
        ObjectModel object = current();
        return !adaptiveActionView() && selectedPage == MudTuningNavigation.Page.BASIC_SHAPE
                && object != null
                && object.id().kind() == MudTuningObjectId.Kind.CONVERTED_BLOCK
                && object.capabilities().has(MudTuningCapabilities.RESTORE) ? 1 : 0;
    }

    private List<MudTuningNavigation.Group> visibleGroups() {
        if (adaptiveActionView()) {
            return List.of(MudTuningNavigation.Group.BASIC);
        }
        List<MudTuningNavigation.Group> groups = new ArrayList<>();
        for (MudTuningNavigation.Group group : MudTuningNavigation.Group.values()) {
            if (group != MudTuningNavigation.Group.TENTACLE
                    && !availablePages(group).isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    private List<MudTuningNavigation.Page> availablePages(MudTuningNavigation.Group group) {
        if (adaptiveActionView()) {
            return group == MudTuningNavigation.Group.BASIC
                    ? List.of(MudTuningNavigation.Page.BASIC_SHAPE) : List.of();
        }
        ObjectModel object = current();
        if (object == null || !object.capabilities().has(MudTuningCapabilities.EDIT_PARAMETERS)) {
            return object != null && group == MudTuningNavigation.Group.BASIC
                    ? List.of(MudTuningNavigation.Page.BASIC_SHAPE) : List.of();
        }
        if (group == MudTuningNavigation.Group.LIVING_SLIME
                && !object.capabilities().has(MudTuningCapabilities.LIVING_SLIME)) {
            return List.of();
        }
        return MudTuningNavigation.pages(group).stream()
                .filter(page -> page.finiteFlow()
                        ? object.id().kind() == MudTuningObjectId.Kind.NATIVE_MEDIUM
                        : !object.parameters(page).isEmpty())
                .toList();
    }

    private boolean pageEditable() {
        ObjectModel object = current();
        return object != null && object.capabilities().has(MudTuningCapabilities.EDIT_PARAMETERS)
                && (!selectedPage.finiteFlow()
                        || object.capabilities().has(MudTuningCapabilities.FINITE_FLOW));
    }

    private boolean canApply() {
        return !adaptiveActionView() && session.editable() && pageEditable();
    }

    private void selectFilter(ObjectFilter filter) {
        if (!commitEditors(true) || !hasFilter(filter)) {
            return;
        }
        objectFilter = filter;
        convertedActionView = false;
        pendingAdaptiveAction = null;
        pendingAdaptiveSource = null;
        tabScroll = 0;
        List<ObjectModel> filtered = filteredObjects();
        selectedObject = filtered.isEmpty() ? null : filtered.getFirst().id();
        resetNavigationForObject();
        rebuildWidgets();
    }

    private void selectObject(MudTuningObjectId id) {
        if (commitEditors(true)) {
            selectedObject = id;
            convertedActionView = false;
            pendingAdaptiveAction = null;
            pendingAdaptiveSource = null;
            resetNavigationForObject();
            rebuildWidgets();
        }
    }

    private void selectGroup(MudTuningNavigation.Group group) {
        if (!commitEditors(true)) {
            return;
        }
        convertedActionView = false;
        List<MudTuningNavigation.Page> pages = availablePages(group);
        if (!pages.isEmpty()) {
            selectedGroup = group;
            selectedPage = pages.getFirst();
            pageScroll = 0;
            rowScroll = 0;
            rebuildWidgets();
        }
    }

    private void selectPage(MudTuningNavigation.Page page) {
        if (commitEditors(true)) {
            convertedActionView = false;
            selectedPage = page;
            rowScroll = 0;
            rebuildWidgets();
        }
    }

    private void resetNavigationForObject() {
        if (tentacleSession()) {
            selectedGroup = MudTuningNavigation.Group.TENTACLE;
            List<MudTuningNavigation.Page> pages = availablePages(selectedGroup);
            selectedPage = pages.isEmpty()
                    ? MudTuningNavigation.Page.TENTACLE_CORE : pages.getFirst();
            rowScroll = 0;
            pageScroll = 0;
            groupScroll = 0;
            return;
        }
        selectedGroup = MudTuningNavigation.Group.BASIC;
        if (incompatibleView()) {
            selectedPage = MudTuningNavigation.Page.BASIC_SHAPE;
            rowScroll = 0;
            pageScroll = 0;
            groupScroll = 0;
            return;
        }
        List<MudTuningNavigation.Page> pages = availablePages(selectedGroup);
        selectedPage = pages.isEmpty()
                ? MudTuningNavigation.Page.BASIC_SHAPE : pages.getFirst();
        rowScroll = 0;
        pageScroll = 0;
        groupScroll = 0;
    }

    private void chooseInitialObject() {
        ObjectModel first = session.objects().isEmpty() ? null : session.objects().getFirst();
        selectedObject = first == null ? null : first.id();
        if (first != null) {
            objectFilter = filterFor(first);
        }
        resetNavigationForObject();
    }

    private void clampNavigation() {
        tabScroll = clamp(tabScroll, 0,
                Math.max(0, filteredObjects().size() - visibleTabCount()));
        List<MudTuningNavigation.Group> groups = visibleGroups();
        boolean hiddenSessionGroup = tentacleSession()
                && selectedGroup == MudTuningNavigation.Group.TENTACLE;
        if (!groups.isEmpty() && !groups.contains(selectedGroup) && !hiddenSessionGroup) {
            resetNavigationForObject();
            groups = visibleGroups();
        }
        groupScroll = clamp(groupScroll, 0,
                Math.max(0, groups.size() - visibleGroupCount()));
        List<MudTuningNavigation.Page> pages = availablePages(selectedGroup);
        if (!pages.contains(selectedPage) && !pages.isEmpty()) {
            selectedPage = pages.getFirst();
        }
        int visiblePages = visiblePageCount(pages);
        pageScroll = clamp(pageScroll, 0, Math.max(0, pages.size() - visiblePages));
        int selectedPageIndex = pages.indexOf(selectedPage);
        if (selectedPageIndex >= 0 && selectedPageIndex < pageScroll) {
            pageScroll = selectedPageIndex;
        } else if (selectedPageIndex >= pageScroll + visiblePages) {
            pageScroll = selectedPageIndex - visiblePages + 1;
        }
        rowScroll = clamp(rowScroll, 0,
                Math.max(0, pageRowCount() - layout.visibleRows()));
    }

    private List<ObjectModel> filteredObjects() {
        return session.objects().stream().filter(objectFilter::accepts).toList();
    }

    private boolean hasFilter(ObjectFilter filter) {
        return session.objects().stream().anyMatch(filter::accepts);
    }

    private ObjectModel current() {
        return selectedObject == null ? null : session.find(selectedObject);
    }

    private boolean sourceView() {
        return current() != null
                && current().id().kind() == MudTuningObjectId.Kind.SOURCE_BLOCK;
    }

    private boolean adaptiveActionView() {
        return sourceView() || convertedActionView && current() != null
                && current().id().kind() == MudTuningObjectId.Kind.CONVERTED_BLOCK;
    }

    private boolean incompatibleView() {
        return objectFilter == ObjectFilter.INCOMPATIBLE;
    }

    private boolean tentacleSession() {
        ObjectModel object = current();
        return session.objects().size() == 1 && object != null
                && object.id().kind() == MudTuningObjectId.Kind.TENTACLE;
    }

    private int incompatibleColumns() {
        return Math.max(1, (width - layout.contentLeft() - 12) / 164);
    }

    private int incompatibleCellWidth(int columns) {
        return Math.max(96,
                (width - layout.contentLeft() - 12 - (columns - 1) * 4) / columns);
    }

    private void mutateAdaptive(boolean convert) {
        mutateAdaptive(convert, session.scope() != MudTuningScope.SINGLE);
    }

    private void mutateAdaptive(boolean convert, boolean allSources) {
        if (!MudTuningClientSettings.conversionUnlocked()
                || !session.editable() || session.scope() == MudTuningScope.WORLD) {
            return;
        }
        ObjectModel object = current();
        if (object == null || !object.id().hasSourceBlock()) {
            return;
        }
        net.minecraft.resources.ResourceLocation sourceBlockId =
                allSources ? AdaptiveMudActionPayload.ALL_SOURCE_BLOCKS
                        : object.id().sourceBlockId();
        AdaptiveMudActionPayload.Action action = convert
                ? AdaptiveMudActionPayload.Action.CONVERT
                : AdaptiveMudActionPayload.Action.RESTORE;
        PacketDistributor.sendToServer(new AdaptiveMudActionPayload(
                action, session.scope(),
                session.first(), session.second(), SinkingMedium.MUD.id(), sourceBlockId));
        pendingAdaptiveAction = action;
        pendingAdaptiveSource = object.id().sourceBlockId();
        status = Component.translatable(convert
                ? "gui.mirebound.tuning.conversion_started"
                : "gui.mirebound.tuning.restoration_started");
        statusColor = 0xFFFFCF73;
    }

    private ObjectModel pendingAdaptiveTarget() {
        if (pendingAdaptiveAction == null || pendingAdaptiveSource == null) {
            return null;
        }
        MudTuningObjectId id = pendingAdaptiveAction == AdaptiveMudActionPayload.Action.CONVERT
                ? MudTuningObjectId.convertedBlock(pendingAdaptiveSource)
                : MudTuningObjectId.sourceBlock(pendingAdaptiveSource);
        return session.find(id);
    }

    private void apply() {
        if (!canApply() || !commitEditors(true)) {
            return;
        }
        ObjectModel object = current();
        PacketDistributor.sendToServer(new MudTuningApplyPayload(session.scope(), object.id(),
                session.first(), session.second(), object.blockVariant(), object.blockHeight(),
                object.shapeChanged(), false,
                Arrays.copyOf(object.values(), object.values().length),
                Arrays.copyOf(object.changed(), object.changed().length)));
        status = Component.translatable("gui.mirebound.physics.saving");
        statusColor = 0xFFFFCF73;
        saving = true;
    }

    private void followBaseline() {
        if (!canApply() || current() == null) {
            return;
        }
        ObjectModel object = current();
        PacketDistributor.sendToServer(new MudTuningApplyPayload(session.scope(), object.id(),
                session.first(), session.second(), object.blockVariant(), object.blockHeight(),
                false, true, Arrays.copyOf(object.values(), object.values().length),
                new boolean[MudPhysicsParameter.COUNT]));
        status = Component.translatable("gui.mirebound.physics.saving");
        statusColor = 0xFFFFCF73;
        saving = true;
    }

    private void switchScope() {
        MudTuningRequestPayload.Action action = session.scope() == MudTuningScope.WORLD
                ? returnScope == MudTuningScope.SINGLE
                        ? MudTuningRequestPayload.Action.OPEN_SINGLE
                        : MudTuningRequestPayload.Action.OPEN_RANGE
                : MudTuningRequestPayload.Action.OPEN_WORLD;
        PacketDistributor.sendToServer(new MudTuningRequestPayload(action, session.first()));
    }

    private boolean commitEditors(boolean reportError) {
        ObjectModel object = current();
        if (object == null) {
            return true;
        }
        if (maximumDepthField != null
                && parseDouble(maximumDepthField.getValue()) == null) {
            if (reportError) {
                status = Component.translatable("gui.mirebound.physics.invalid",
                        Component.translatable(
                                "gui.mirebound.tuning.maximum_sinking_depth"));
                statusColor = 0xFFFF786C;
                maximumDepthField.setFocused(true);
            }
            return false;
        }
        if (naturalDepthField != null
                && parseDouble(naturalDepthField.getValue()) == null) {
            if (reportError) {
                status = Component.translatable("gui.mirebound.physics.invalid",
                        Component.translatable(
                                "gui.mirebound.tuning.natural_sinking_depth"));
                statusColor = 0xFFFF786C;
                naturalDepthField.setFocused(true);
            }
            return false;
        }
        if (maximumDepthField != null) {
            normalizeDepthField(object, maximumDepthField, false);
        }
        if (naturalDepthField != null) {
            normalizeDepthField(object, naturalDepthField, true);
        }
        for (RowEditor editor : editors) {
            Double parsed = parseDouble(editor.field.getValue());
            if (parsed == null) {
                if (reportError) {
                    status = Component.translatable("gui.mirebound.physics.invalid",
                            Component.translatable(editor.parameter.translationKey()));
                    statusColor = 0xFFFF786C;
                    editor.field.setFocused(true);
                }
                return false;
            }
            double normalized = MudTuningSlider.snapValue(
                    editor.parameter.minimum(), editor.parameter.maximum(),
                    editor.parameter.step(), parsed);
            object.set(editor.parameter, normalized);
            editor.field.setValue(format(editor.parameter,
                    object.values()[editor.parameter.ordinal()]));
        }
        return true;
    }

    private Component scopeTitle() {
        return switch (session.scope()) {
            case SINGLE -> session.first().isSable()
                    ? Component.translatable("gui.mirebound.physics.scope.single_sable")
                    : Component.translatable("gui.mirebound.physics.scope.single",
                            session.first().pos().getX(), session.first().pos().getY(),
                            session.first().pos().getZ());
            case RANGE -> session.first().isSable()
                    ? Component.translatable("gui.mirebound.physics.scope.range_sable")
                    : Component.translatable("gui.mirebound.physics.scope.range",
                            session.first().pos().getX(), session.first().pos().getY(),
                            session.first().pos().getZ(), session.second().pos().getX(),
                            session.second().pos().getY(), session.second().pos().getZ());
            case WORLD -> Component.translatable("gui.mirebound.physics.scope.world");
        };
    }

    private Component scopeSwitchLabel() {
        return session.scope() == MudTuningScope.WORLD
                ? Component.translatable("gui.mirebound.physics.back_local")
                : Component.translatable("gui.mirebound.physics.open_world");
    }

    private Component harvestToolLabel(double value) {
        int index = (int) Math.round(value);
        MudHarvestTool[] tools = MudHarvestTool.values();
        return Component.translatable(tools[Math.floorMod(index, tools.length)].translationKey());
    }

    private int labelWidth() {
        int content = width - layout.contentLeft() - 12;
        return Math.max(72, Math.min(layout.compact() ? 94 : 190, content / 3));
    }

    private int visibleTabCount() {
        return Math.max(1, (width - layout.sidebarWidth() - 18) / TAB_WIDTH);
    }

    private int visibleGroupCount() {
        return Math.max(1,
                (layout.contentBottom() - layout.headerHeight() - 10) / GROUP_ROW_HEIGHT);
    }

    private int visiblePageCount(List<MudTuningNavigation.Page> pages) {
        return layout.visiblePageCount(pages.size());
    }

    private int headerButtonLeft() {
        int worldWidth = layout.compact() ? 66 : 104;
        return width - worldWidth - 6;
    }

    private int footerButtonLeft() {
        int closeWidth = 68;
        int applyWidth = 68;
        int followWidth = layout.compact() ? 72 : 96;
        return width - closeWidth - applyWidth - followWidth - 15;
    }

    private Component fit(Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String suffix = "...";
        return Component.literal(font.plainSubstrByWidth(
                text.getString(), Math.max(0, width - font.width(suffix))) + suffix);
    }

    private static ObjectFilter filterFor(ObjectModel object) {
        return switch (object.id().kind()) {
            case NATIVE_MEDIUM, ADAPTIVE_DEFAULT, TENTACLE -> ObjectFilter.NATIVE;
            case SOURCE_BLOCK -> ObjectFilter.SOURCE;
            case CONVERTED_BLOCK -> ObjectFilter.CONVERTED;
            case INCOMPATIBLE_BLOCK -> ObjectFilter.INCOMPATIBLE;
        };
    }

    private static Tone filterTone(ObjectFilter filter) {
        return switch (filter) {
            case NATIVE -> Tone.NATIVE;
            case SOURCE -> Tone.SOURCE;
            case CONVERTED -> Tone.CONVERTED;
            case INCOMPATIBLE -> Tone.INCOMPATIBLE;
        };
    }

    private LocalNavigation captureNavigation() {
        commitEditors(false);
        return new LocalNavigation(selectedObject, objectFilter, selectedGroup, selectedPage,
                rowScroll, tabScroll, pageScroll, groupScroll, convertedActionView);
    }

    private void restoreNavigation(LocalNavigation navigation) {
        if (navigation == null) {
            return;
        }
        objectFilter = navigation.objectFilter();
        selectedObject = session.find(navigation.selectedObject()) == null
                ? null : navigation.selectedObject();
        if (selectedObject == null || !objectFilter.accepts(session.find(selectedObject))) {
            List<ObjectModel> filtered = filteredObjects();
            selectedObject = filtered.isEmpty() ? null : filtered.getFirst().id();
        }
        selectedGroup = navigation.selectedGroup();
        selectedPage = navigation.selectedPage();
        rowScroll = navigation.rowScroll();
        tabScroll = navigation.tabScroll();
        pageScroll = navigation.pageScroll();
        groupScroll = navigation.groupScroll();
        convertedActionView = navigation.convertedActionView();
    }

    private static boolean validNumberInput(String value) {
        return value.isEmpty() || value.matches("-?(?:\\d+(?:\\.\\d*)?|\\.\\d*)");
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

    private static String format(MudPhysicsParameter parameter, double value) {
        return String.format(Locale.ROOT, "%." + parameter.decimals() + "f", value);
    }

    private static String formatMaximumDepth(double value) {
        return String.format(Locale.ROOT,
                "%." + MudSinkingDepthControl.DECIMALS + "f", value);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record RowEditor(MudPhysicsParameter parameter, EditBox field) {
    }

    private record LocalNavigation(
            MudTuningObjectId selectedObject,
            ObjectFilter objectFilter,
            MudTuningNavigation.Group selectedGroup,
            MudTuningNavigation.Page selectedPage,
            int rowScroll,
            int tabScroll,
            int pageScroll,
            int groupScroll,
            boolean convertedActionView) {
    }
}
