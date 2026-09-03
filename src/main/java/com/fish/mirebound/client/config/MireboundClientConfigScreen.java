package com.fish.mirebound.client.config;

import com.fish.mirebound.client.config.MireboundClientSettings.ClientOption;
import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.client.gui.MireflowToggleButton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Local-only visual settings opened from NeoForge's mod list. */
public final class MireboundClientConfigScreen extends Screen {
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 34;
    private static final int SIDEBAR_WIDTH = 104;
    private static final int ROW_HEIGHT = 40;

    private final Screen parent;
    private EnumMap<ClientOption, Boolean> saved;
    private EnumMap<ClientOption, Boolean> draft;
    private Section section = Section.WORLD;
    private int scroll;
    private MireflowGuiTheme.Panel panel;

    public MireboundClientConfigScreen(Screen parent) {
        super(Component.translatable("gui.mirebound.client.title"));
        this.parent = parent;
        saved = MireboundClientSettings.clientOptions();
        draft = new EnumMap<>(saved);
    }

    @Override
    protected void init() {
        panel = MireflowGuiTheme.centeredPanel(width, height, 560, 380);
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        addSectionButtons();
        addOptionButtons();
        addFooterButtons();
    }

    private void addSectionButtons() {
        int y = contentTop();
        for (Section candidate : Section.values()) {
            Component label = Component.translatable(candidate.translationKey());
            Button button = MireflowButton.builder(label, ignored -> {
                section = candidate;
                scroll = 0;
                rebuildWidgets();
            }).selected(candidate == section)
                    .bounds(panel.left() + 6, y, SIDEBAR_WIDTH - 12, 20).build();
            addRenderableWidget(button);
            y += 23;
        }
    }

    private void addOptionButtons() {
        List<OptionRow> options = options(section);
        int visibleRows = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, options.size() - visibleRows));
        int right = panel.right() - 8;
        int buttonWidth = panel.width() < 400 ? 86 : 96;
        for (int slot = 0; slot < Math.min(visibleRows, options.size() - scroll); slot++) {
            OptionRow row = options.get(scroll + slot);
            int y = rowY(slot);
            boolean enabled = draft.getOrDefault(
                    row.option, row.option.defaultEnabled());
            MireflowToggleButton toggle = new MireflowToggleButton(
                    right - buttonWidth, y + 9, buttonWidth, 21,
                    enabled, ignored -> {
                        draft.put(row.option, !draft.getOrDefault(
                                row.option, row.option.defaultEnabled()));
                        rebuildWidgets();
                    });
            toggle.setTooltip(Tooltip.create(Component.translatable(
                    row.descriptionKey())));
            addRenderableWidget(toggle);
        }
    }

    private void addFooterButtons() {
        int y = panel.bottom() - 27;
        Button defaults = MireflowButton.builder(Component.translatable(
                "gui.mirebound.client.defaults"), ignored -> {
                    for (ClientOption option : ClientOption.values()) {
                        draft.put(option, option.defaultEnabled());
                    }
                    rebuildWidgets();
                }).bounds(panel.left() + 7, y, 86, 20).build();
        defaults.active = !isDefault();
        addRenderableWidget(defaults);

        addRenderableWidget(MireflowButton.builder(Component.translatable(
                "gui.mirebound.physics.cancel"), ignored -> onClose())
                .bounds(panel.right() - 151, y, 68, 20).build());
        Button apply = MireflowButton.builder(Component.translatable(
                "gui.mirebound.physics.apply"), ignored -> apply())
                .bounds(panel.right() - 76, y, 68, 20).build();
        apply.active = !draft.equals(saved);
        addRenderableWidget(apply);
    }

    private void apply() {
        MireboundClientSettings.applyClientOptions(draft);
        saved = new EnumMap<>(draft);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
            double scrollX, double scrollY) {
        if (mouseX < sidebarRight() || mouseY < contentTop()
                || mouseX >= panel.right() || mouseY >= footerTop()
                || Math.abs(scrollY) < 1.0E-6D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        List<OptionRow> options = options(section);
        int maximum = Math.max(0, options.size() - visibleRows());
        int next = Mth.clamp(scroll + (scrollY > 0.0D ? -1 : 1), 0, maximum);
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick) {
        // The opaque pixel surface avoids the vanilla blur and remains cheap in-game.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick) {
        renderSurface(graphics);
        renderRows(graphics);
        renderHeader(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSurface(GuiGraphics graphics) {
        MireflowGuiTheme.drawPanel(graphics, panel);
        graphics.fill(panel.left(), panel.top(), panel.right(), headerBottom(),
                MireflowGuiTheme.HEADER);
        graphics.fill(panel.left(), headerBottom(), sidebarRight(), footerTop(),
                MireflowGuiTheme.SIDEBAR);
        graphics.fill(panel.left(), footerTop(), panel.right(), panel.bottom(),
                MireflowGuiTheme.FOOTER);
        graphics.hLine(panel.left(), panel.right() - 1,
                headerBottom() - 1, MireflowGuiTheme.DIVIDER);
        graphics.hLine(panel.left(), panel.right() - 1,
                footerTop(), MireflowGuiTheme.DIVIDER);
        graphics.vLine(sidebarRight(), headerBottom() - 1,
                footerTop(), MireflowGuiTheme.DIVIDER);
        graphics.fill(sidebarRight(), headerBottom() - 1,
                sidebarRight() + 3, footerTop(), MireflowGuiTheme.ACCENT);
    }

    private void renderRows(GuiGraphics graphics) {
        List<OptionRow> options = options(section);
        int visible = Math.min(visibleRows(), options.size() - scroll);
        int right = panel.right() - 8;
        int textRight = right - (panel.width() < 400 ? 94 : 104);
        for (int slot = 0; slot < visible; slot++) {
            OptionRow row = options.get(scroll + slot);
            int y = rowY(slot);
            graphics.fill(sidebarRight() + 8, y, right, y + ROW_HEIGHT - 2,
                    (slot & 1) == 0 ? MireflowGuiTheme.ROW_A : MireflowGuiTheme.ROW_B);
            graphics.fill(sidebarRight() + 8, y,
                    sidebarRight() + 11, y + ROW_HEIGHT - 2, row.cost.color);
            int textX = sidebarRight() + 17;
            Component title = Component.translatable(row.titleKey());
            graphics.drawString(font,
                    fit(title, Math.max(20, textRight - textX)),
                    textX, y + 6, MireflowGuiTheme.TEXT, false);
            Component description = Component.translatable(row.descriptionKey());
            graphics.drawString(font,
                    fit(description, Math.max(20, textRight - textX)),
                    textX, y + 21, MireflowGuiTheme.MUTED, false);
        }
        if (scroll > 0) {
            graphics.fill(panel.right() - 4, contentTop(), panel.right() - 2,
                    contentTop() + 8, MireflowGuiTheme.ACCENT);
        }
        if (scroll + visibleRows() < options.size()) {
            graphics.fill(panel.right() - 4, footerTop() - 9,
                    panel.right() - 2, footerTop() - 1, MireflowGuiTheme.ACCENT);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.drawString(font, title, panel.left() + 10, panel.top() + 7,
                MireflowGuiTheme.TEXT, false);
        graphics.drawString(font,
                Component.translatable("gui.mirebound.client.local_only"),
                panel.left() + 10, panel.top() + 19, MireflowGuiTheme.MUTED, false);
        int enabled = 0;
        for (ClientOption option : ClientOption.values()) {
            if (draft.getOrDefault(option, option.defaultEnabled())) {
                enabled++;
            }
        }
        Component summary = Component.translatable(
                "gui.mirebound.client.enabled_count",
                enabled, ClientOption.values().length);
        graphics.drawString(font, summary,
                Math.max(sidebarRight() + 9, panel.right() - font.width(summary) - 9),
                panel.top() + 12, MireflowGuiTheme.ACCENT, false);
    }

    private int visibleRows() {
        return Math.max(1,
                (footerTop() - contentTop()) / ROW_HEIGHT);
    }

    private int rowY(int slot) {
        return contentTop() + slot * ROW_HEIGHT;
    }

    private boolean isDefault() {
        for (ClientOption option : ClientOption.values()) {
            if (draft.getOrDefault(option, option.defaultEnabled())
                    != option.defaultEnabled()) {
                return false;
            }
        }
        return true;
    }

    private Component fit(Component component, int maximumWidth) {
        String text = component.getString();
        if (font.width(text) <= maximumWidth) {
            return component;
        }
        return Component.literal(font.plainSubstrByWidth(text, maximumWidth - 8) + "...");
    }

    private int headerBottom() {
        return panel.top() + HEADER_HEIGHT;
    }

    private int contentTop() {
        return panel.top() + 42;
    }

    private int footerTop() {
        return panel.bottom() - FOOTER_HEIGHT;
    }

    private int sidebarRight() {
        return panel.left() + SIDEBAR_WIDTH;
    }

    private static List<OptionRow> options(Section section) {
        List<OptionRow> result = new ArrayList<>();
        for (OptionRow option : OptionRow.values()) {
            if (option.section == section) {
                result.add(option);
            }
        }
        return result;
    }

    private enum Section {
        WORLD("gui.mirebound.client.section.world"),
        CHARACTER("gui.mirebound.client.section.character"),
        SCREEN("gui.mirebound.client.section.screen"),
        ADVANCED("gui.mirebound.client.section.advanced");

        private final String translationKey;

        Section(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return translationKey;
        }
    }

    private enum Cost {
        LOW(0xFF6FAF76),
        MEDIUM(0xFFE0B96C),
        HIGH(0xFFE36A58);

        private final int color;

        Cost(int color) {
            this.color = color;
        }
    }

    private enum OptionRow {
        SURFACE_EFFECTS(ClientOption.SURFACE_EFFECTS, Section.WORLD, Cost.HIGH),
        SPLASH_EFFECTS(ClientOption.SPLASH_EFFECTS, Section.WORLD, Cost.MEDIUM),
        ERUPTION_EFFECTS(ClientOption.ERUPTION_EFFECTS, Section.WORLD, Cost.MEDIUM),
        SURFACE_DECALS(ClientOption.SURFACE_DECALS, Section.WORLD, Cost.MEDIUM),
        INSECT_SURFACE(ClientOption.INSECT_SURFACE, Section.WORLD, Cost.MEDIUM),
        TENTACLES(ClientOption.TENTACLES, Section.WORLD, Cost.HIGH),
        PLAYER_COVERAGE(ClientOption.PLAYER_COVERAGE, Section.CHARACTER, Cost.MEDIUM),
        ENTITY_COVERAGE(ClientOption.ENTITY_COVERAGE, Section.CHARACTER, Cost.HIGH),
        MUD_SCREEN(ClientOption.MUD_SCREEN, Section.SCREEN, Cost.MEDIUM),
        ASSIMILATION_SCREEN(ClientOption.ASSIMILATION_SCREEN, Section.SCREEN, Cost.LOW),
        SWARM_SCREEN(ClientOption.SWARM_SCREEN, Section.SCREEN, Cost.MEDIUM),
        PRECISE_MODEL_GEOMETRY(
                ClientOption.PRECISE_MODEL_GEOMETRY, Section.ADVANCED, Cost.HIGH),
        ANIMATED_CONTACT_GEOMETRY(
                ClientOption.ANIMATED_CONTACT_GEOMETRY, Section.ADVANCED, Cost.MEDIUM),
        TENTACLE_SHADER_SHADOWS(
                ClientOption.TENTACLE_SHADER_SHADOWS, Section.ADVANCED, Cost.HIGH);

        private final ClientOption option;
        private final Section section;
        private final Cost cost;

        OptionRow(ClientOption option, Section section, Cost cost) {
            this.option = option;
            this.section = section;
            this.cost = cost;
        }

        String titleKey() {
            return "gui.mirebound.client.option."
                    + option.name().toLowerCase(Locale.ROOT);
        }

        String descriptionKey() {
            return titleKey() + ".desc";
        }
    }

}
