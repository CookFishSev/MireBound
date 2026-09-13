package com.fish.mirebound.client.worldgen;

import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowGuiTheme;
import com.fish.mirebound.generation.natural.NaturalMudCoveragePattern;
import com.fish.mirebound.generation.natural.NaturalMudCoverageRule;
import com.fish.mirebound.mud.SinkingMedium;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Categorized coverage controls; preview sampling runs only when the rule changes. */
final class NaturalMudCoveragePanel {
    private static final int ROW_HEIGHT = 44;
    private final int[] scroll = new int[Category.values().length];
    private final List<RowLabel> labels = new ArrayList<>();
    private Category category = Category.COVERAGE;
    private int left, top, width, bottom, visibleRows, rowsTop, mapSize, rowCount;
    private NaturalMudCoverageRule previewRule;
    private final int[] colors = new int[32 * 32];
    private SinkingMedium selectedWeight = SinkingMedium.MUD;

    void widgets(int left, int top, int width, int bottom, boolean dimensionEnabled,
            Supplier<NaturalMudCoverageRule> get, Consumer<NaturalMudCoverageRule> set,
            Consumer<AbstractWidget> add, Runnable rebuild) {
        this.left = left; this.top = top; this.width = width; this.bottom = bottom;
        labels.clear();
        mapSize = Math.clamp(bottom - top - 180, 16, 64);
        int categoryTop = top + mapSize + 24;
        int columns = Math.clamp(width / 76, 3, 5);
        int tabWidth = Math.max(20, (width - 8) / columns);
        for (Category choice : Category.values()) {
            int index = choice.ordinal();
            Component title = text("category." + choice.name().toLowerCase(Locale.ROOT));
            var button = MireflowButton.builder(title, ignored -> {
                category = choice;
                rebuild.run();
            }).selected(category == choice)
                    .bounds(left + 4 + index % columns * tabWidth,
                            categoryTop + index / columns * 23, tabWidth - 3, 20).build();
            button.setTooltip(Tooltip.create(title));
            button.active = dimensionEnabled;
            add.accept(button);
        }
        rowsTop = categoryTop + (Category.values().length + columns - 1) / columns * 23 + 6;
        visibleRows = Math.max(0, (bottom - rowsTop) / ROW_HEIGHT);
        NaturalMudCoverageRule rule = get.get();
        Parameter[] parameters = category.parameters;
        boolean toggleRow = category == Category.COVERAGE || category == Category.TREES;
        boolean modeRow = category == Category.MIX;
        int prefix = toggleRow || modeRow ? 1 : 0;
        rowCount = category == Category.WEIGHTS ? SinkingMedium.COUNT : prefix + parameters.length;
        scroll[category.ordinal()] = Math.clamp(scroll[category.ordinal()], 0, Math.max(0, rowCount - visibleRows));
        for (int slot = 0; slot < visibleRows && slot + offset() < rowCount; slot++) {
            int row = slot + offset(), y = rowsTop + slot * ROW_HEIGHT;
            if (category == Category.WEIGHTS) {
                SinkingMedium medium = SinkingMedium.values()[row];
                int weight = rule.weights().getOrDefault(medium, 0);
                var title = Component.translatable("block.mirebound." + medium.serializedName());
                var button = new WorldgenChoiceButton(left + 4, y, width - 12, title, weight > 0,
                        medium == selectedWeight, () -> { selectedWeight = medium; rebuild.run(); }, () -> {
                            set.accept(get.get().withWeight(medium,
                                    get.get().weights().getOrDefault(medium, 0) > 0 ? 0 : 100));
                            rebuild.run();
                        });
                button.active = dimensionEnabled && rule.enabled();
                add.accept(button);
                new WorldgenNumericControl(Minecraft.getInstance().font, left + 4, y + 22, width - 12,
                        title, 1, 1000, 1, 0, weight,
                        dimensionEnabled && rule.enabled() && weight > 0,
                        value -> set.accept(get.get().withWeight(medium, (int) value)), add);
            } else if (row == 0 && toggleRow) {
                boolean enabled = category == Category.COVERAGE ? rule.enabled() : rule.deeperNearTrees();
                var label = text(category == Category.COVERAGE ? "enabled" : "trees").copy()
                        .append(": ").append(text(enabled ? "on" : "off"));
                var toggle = new WorldgenChoiceButton(left + 4, y, width - 12, label, enabled, false,
                        () -> {}, () -> {
                            var r = get.get();
                            set.accept(category == Category.COVERAGE ? r.withEnabled(!r.enabled())
                                    : new NaturalMudCoverageRule(r.enabled(), r.coverage(), r.coverageVariation(),
                                            r.depth(), !r.deeperNearTrees(), r.treeDepth(), r.treeRadius(),
                                            r.depthVariation(), r.mixMode(), r.patchSize(), r.mixVariation(), r.weights(),
                                            r.generationChance(), r.replacementLayers()));
                            rebuild.run();
                        });
                toggle.active = dimensionEnabled && (category == Category.COVERAGE || rule.enabled());
                add.accept(toggle);
            } else if (row == 0 && modeRow) {
                int half = (width - 16) / 2;
                for (var mode : NaturalMudCoverageRule.MixMode.values()) {
                    var button = MireflowButton.builder(text(mode.name().toLowerCase(Locale.ROOT)), ignored -> {
                        var r = get.get();
                        set.accept(new NaturalMudCoverageRule(r.enabled(), r.coverage(), r.coverageVariation(), r.depth(),
                                r.deeperNearTrees(), r.treeDepth(), r.treeRadius(), r.depthVariation(), mode,
                                r.patchSize(), r.mixVariation(), r.weights(), r.generationChance(), r.replacementLayers()));
                        rebuild.run();
                    }).selected(rule.mixMode() == mode).bounds(left + 4 + mode.ordinal() * (half + 4), y, half, 20).build();
                    button.active = dimensionEnabled && rule.enabled();
                    add.accept(button);
                }
            } else {
                Parameter parameter = parameters[row - prefix];
                boolean enabled = dimensionEnabled && rule.enabled()
                        && (category != Category.TREES || rule.deeperNearTrees())
                        && (parameter != Parameter.PATCH_SIZE || rule.mixMode() == NaturalMudCoverageRule.MixMode.PATCHES);
                Component label = text(parameter.key);
                labels.add(new RowLabel(label, y + 2, enabled));
                new WorldgenNumericControl(Minecraft.getInstance().font, left + 4, y + 17, width - 12,
                        label, parameter == Parameter.TREE_DEPTH ? rule.depth() : parameter.min,
                        parameter.max, parameter.step, parameter.decimals, parameter.value(rule), enabled,
                        value -> set.accept(parameter.update(get.get(), value)), add);
            }
        }
    }

    private int offset() { return scroll[category.ordinal()]; }

    boolean scroll(double x, double y, double amount) {
        if (x < left || x >= left + width || y < rowsTop || y >= bottom) return false;
        scroll[category.ordinal()] -= (int) Math.signum(amount);
        return true;
    }

    void render(GuiGraphics g, NaturalMudCoverageRule rule, Component biome, boolean enabled) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, font.plainSubstrByWidth(biome.getString(), Math.max(20, width - 8)), left + 4, top, MireflowGuiTheme.TEXT, false);
        if (!rule.equals(previewRule)) {
            previewRule = rule;
            for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                int wx = x * 2 - 32, wz = z * 2 - 32;
                var medium = NaturalMudCoveragePattern.covered(rule, 5371L, wx, wz)
                        ? NaturalMudCoveragePattern.medium(rule, 5371L, wx, wz) : null;
                int color = 0xFF3C4836;
                if (medium != null) {
                    var c = medium.particleColor();
                    double shade = 1 - NaturalMudCoveragePattern.depth(rule, 5371L, wx, wz, Math.hypot(wx, wz)) * .065;
                    color = 0xFF000000 | (int) (c.x * 255 * shade) << 16 | (int) (c.y * 255 * shade) << 8 | (int) (c.z * 255 * shade);
                }
                colors[x * 32 + z] = color;
            }
        }
        int mapLeft = left + width - mapSize - 6, mapTop = top + 18;
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++)
            g.fill(mapLeft + x * mapSize / 32, mapTop + z * mapSize / 32,
                    mapLeft + (x + 1) * mapSize / 32, mapTop + (z + 1) * mapSize / 32, colors[x * 32 + z]);
        g.fill(mapLeft + mapSize / 2 - 1, mapTop + mapSize / 2 - 1,
                mapLeft + mapSize / 2 + 2, mapTop + mapSize / 2 + 2, 0xFFB98C51);
        int y = top + 19;
        for (var line : font.split(text("preview_hint"), Math.max(35, width - mapSize - 18))) {
            if (y > top + mapSize + 12) break;
            g.drawString(font, line, left + 4, y, MireflowGuiTheme.MUTED, false);
            y += 10;
        }
        for (int slot = 0; slot < visibleRows && slot + offset() < rowCount; slot++) {
            int rowY = rowsTop + slot * ROW_HEIGHT;
            g.fill(left + 2, rowY - 1, left + width - 5, rowY + ROW_HEIGHT - 3,
                    slot % 2 == 0 ? MireflowGuiTheme.FULLSCREEN_ROW_A : MireflowGuiTheme.FULLSCREEN_ROW_B);
        }
        for (RowLabel label : labels) g.drawString(font,
                font.plainSubstrByWidth(label.text.getString(), Math.max(20, width - 12)),
                left + 4, label.y, label.enabled ? MireflowGuiTheme.TEXT : MireflowGuiTheme.DISABLED, false);
        if (rowCount > visibleRows && visibleRows > 0) {
            int trackHeight = Math.max(1, bottom - rowsTop);
            int thumb = Math.max(8, trackHeight * visibleRows / rowCount);
            int thumbY = rowsTop + (trackHeight - thumb) * offset() / (rowCount - visibleRows);
            g.fill(left + width - 2, rowsTop, left + width, bottom, 0xFF343B3A);
            g.fill(left + width - 2, thumbY, left + width, thumbY + thumb, MireflowGuiTheme.ACCENT);
        }
        if (!enabled) g.fill(left, top, left + width, bottom, 0x88000000);
    }

    static Component text(String key) { return Component.translatable("gui.mirebound.worldgen.coverage." + key); }
    private record RowLabel(Component text, int y, boolean enabled) {}

    private enum Category {
        COVERAGE(Parameter.GENERATION_CHANCE, Parameter.COVERAGE, Parameter.COVERAGE_VARIATION),
        DEPTH(Parameter.REPLACEMENT_LAYERS, Parameter.DEPTH, Parameter.DEPTH_VARIATION),
        TREES(Parameter.TREE_DEPTH, Parameter.TREE_RADIUS),
        MIX(Parameter.PATCH_SIZE, Parameter.MIX_VARIATION),
        WEIGHTS;
        final Parameter[] parameters;
        Category(Parameter... parameters) { this.parameters = parameters; }
    }

    private enum Parameter {
        GENERATION_CHANCE("generation_chance", 0, 1, .01, 2),
        REPLACEMENT_LAYERS("replacement_layers", 1, 12, 1, 0),
        COVERAGE("amount", 0, 1, .01, 2), COVERAGE_VARIATION("amount_variation", 0, 1, .01, 2),
        DEPTH("depth", .0625, 6, .0625, 4), TREE_DEPTH("tree_depth", .0625, 6, .0625, 4),
        TREE_RADIUS("tree_radius", 1, 8, 1, 0), DEPTH_VARIATION("depth_variation", 0, 3, .01, 2),
        PATCH_SIZE("patch_size", 4, 96, 1, 0), MIX_VARIATION("mix_variation", 0, 1, .01, 2);
        final String key; final double min, max, step; final int decimals;
        Parameter(String key, double min, double max, double step, int decimals) {
            this.key = key; this.min = min; this.max = max; this.step = step; this.decimals = decimals;
        }
        double value(NaturalMudCoverageRule r) {
            return switch (this) {
                case GENERATION_CHANCE -> r.generationChance(); case REPLACEMENT_LAYERS -> r.replacementLayers();
                case COVERAGE -> r.coverage(); case COVERAGE_VARIATION -> r.coverageVariation();
                case DEPTH -> r.depth(); case TREE_DEPTH -> r.treeDepth(); case TREE_RADIUS -> r.treeRadius();
                case DEPTH_VARIATION -> r.depthVariation(); case PATCH_SIZE -> r.patchSize(); case MIX_VARIATION -> r.mixVariation();
            };
        }
        NaturalMudCoverageRule update(NaturalMudCoverageRule r, double value) {
            return new NaturalMudCoverageRule(r.enabled(), this == COVERAGE ? value : r.coverage(),
                    this == COVERAGE_VARIATION ? value : r.coverageVariation(), this == DEPTH ? value : r.depth(),
                    r.deeperNearTrees(), this == TREE_DEPTH ? value : r.treeDepth(),
                    this == TREE_RADIUS ? (int) value : r.treeRadius(), this == DEPTH_VARIATION ? value : r.depthVariation(),
                    r.mixMode(), this == PATCH_SIZE ? (int) value : r.patchSize(),
                    this == MIX_VARIATION ? value : r.mixVariation(), r.weights(),
                    this == GENERATION_CHANCE ? value : r.generationChance(),
                    this == REPLACEMENT_LAYERS ? (int) value : r.replacementLayers());
        }
    }
}
