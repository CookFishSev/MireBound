package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.gui.MireflowButton;
import com.fish.mirebound.client.gui.MireflowToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Full-screen editor for the two movable tuning-wand HUD groups. */
public final class MudTuningHudEditorScreen extends Screen {
    private static final int HANDLE_SIZE = 8;
    private static final int TOP_CONTROL_Y = 36;
    private static final int TOP_BUTTON_WIDTH = 72;
    private static final int TOP_BUTTON_GAP = 4;
    private static final int SNAP_THRESHOLD = 10;
    private static final int OVERLAP_MARGIN = 3;
    private static final int DRAG_THRESHOLD = 3;
    private static final int SETTING_WIDTH = 132;
    private static final int SETTING_GAP = 8;
    private static final int SETTING_MARGIN = 6;
    private static final int BORDER = 0xFFE5B84B;
    private static final int BORDER_DIMMED = 0xFF69736D;
    private static final int HANDLE = 0xFFF5D47A;
    private static final int HELP_COLOR = 0xFFD3DDD6;
    private static final int GUIDE_COLOR = 0x705F7A70;
    private static final int SNAPPED_GUIDE_COLOR = 0xFF4AD17C;
    private static final int BACKGROUND_DIM = 0x52000000;

    private final Screen parent;
    private MudTuningHudElement selected;
    private boolean dragging;
    private boolean resizing;
    private boolean pendingDrag;
    private boolean alignmentSnapping;
    private boolean snappedToVerticalCenter;
    private boolean snappedToHorizontalCenter;
    private double dragOffsetX;
    private double dragOffsetY;
    private double resizeStartScale;
    private double pressX;
    private double pressY;
    private Button resetButton;
    private Button settingToggle;
    private MudTuningSlider opacitySlider;
    private int settingX;
    private int settingY;

    private MudTuningHudEditorScreen(Screen parent) {
        super(Component.translatable("gui.mirebound.tuning.hud_editor.title"));
        this.parent = parent;
        MudTuningHudEditorState.begin();
    }

    public static void open(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MudTuningHudEditorScreen) {
            return;
        }
        minecraft.setScreen(new MudTuningHudEditorScreen(parent));
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        settingToggle = null;
        opacitySlider = null;
        int topGroupWidth = TOP_BUTTON_WIDTH * 2 + TOP_BUTTON_GAP;
        int topLeft = Math.max(6, (width - topGroupWidth) / 2);
        resetButton = MireflowButton.builder(
                Component.translatable("gui.mirebound.tuning.hud_editor.reset"), ignored -> {
                    MudTuningHudEditorState.resetToDefaults();
                    rebuildWidgets();
                }).bounds(topLeft, TOP_CONTROL_Y, TOP_BUTTON_WIDTH, 20).build();
        updateResetButton();
        addRenderableWidget(resetButton);
        Button alignment = MireflowButton.builder(Component.translatable(
                "gui.mirebound.tuning.hud_editor.alignment"), ignored -> {
                    alignmentSnapping = !alignmentSnapping;
                    snappedToVerticalCenter = false;
                    snappedToHorizontalCenter = false;
                    rebuildWidgets();
                }).tone(alignmentSnapping
                        ? MireflowButton.Tone.POSITIVE : MireflowButton.Tone.DANGER)
                .selected(true)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable(
                                "gui.mirebound.tuning.hud_editor.alignment.tooltip")))
                .bounds(topLeft + TOP_BUTTON_WIDTH + TOP_BUTTON_GAP,
                        TOP_CONTROL_Y, TOP_BUTTON_WIDTH, 20).build();
        addRenderableWidget(alignment);
        if (selected == null) {
            return;
        }
        positionSettings(bounds(selected));
        if (selected == MudTuningHudElement.CENTER) {
            opacitySlider = new MudTuningSlider(settingX, settingY + 14,
                    SETTING_WIDTH, 20, 0.20D, 1.0D, 0.05D, 2,
                    MudTuningHudEditorState.hudOpacity(),
                    value -> {
                        MudTuningHudEditorState.setHudOpacity(value);
                        updateResetButton();
                    });
            addRenderableWidget(opacitySlider);
        } else {
            boolean enabled = MudTuningHudEditorState.enabled(selected);
            settingToggle = new MireflowToggleButton(settingX, settingY,
                    SETTING_WIDTH, 21, enabled,
                    ignored -> {
                        MudTuningHudEditorState.setEnabled(selected,
                                !MudTuningHudEditorState.enabled(selected));
                        rebuildWidgets();
                    });
            settingToggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.mirebound.tuning.hud_editor.enabled.tooltip")));
            addRenderableWidget(settingToggle);
            opacitySlider = new MudTuningSlider(settingX, settingY + 36,
                    SETTING_WIDTH, 20, 0.20D, 1.0D, 0.05D, 2,
                    MudTuningHudEditorState.controlsOpacity(),
                    value -> {
                        MudTuningHudEditorState.setControlsOpacity(value);
                        updateResetButton();
                    });
            addRenderableWidget(opacitySlider);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick) {
        // Keep the world visible while the HUD is being edited.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND_DIM);
        if (alignmentSnapping && dragging) {
            graphics.fill(width / 2, 0, width / 2 + 1, height,
                    snappedToVerticalCenter ? SNAPPED_GUIDE_COLOR : GUIDE_COLOR);
            graphics.fill(0, height / 2, width, height / 2 + 1,
                    snappedToHorizontalCenter ? SNAPPED_GUIDE_COLOR : GUIDE_COLOR);
        }
        MudTuningWandHud.renderEditorPreview(graphics, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (MudTuningHudElement element : MudTuningHudElement.values()) {
            MudTuningHudLayout.HudBounds bounds = bounds(element);
            boolean enabled = MudTuningHudEditorState.enabled(element);
            int color = element == selected && enabled ? BORDER
                    : enabled ? 0xA8D5DCD5 : BORDER_DIMMED;
            drawBorder(graphics, bounds, color);
            if (element == selected) {
                drawHandle(graphics, bounds);
            }
        }
        Component help = Component.translatable("gui.mirebound.tuning.hud_editor.help");
        graphics.drawCenteredString(font, fit(help, width - 16),
                width / 2, TOP_CONTROL_Y + 26, HELP_COLOR);
        if (selected != null) {
            graphics.drawString(font,
                    Component.translatable(selected.translationKey()),
                    8, 8, selected == MudTuningHudElement.CENTER
                            ? BORDER : 0xFF9FD9C6, true);
        }
        if (selected != null && opacitySlider != null) {
            graphics.drawString(font,
                    Component.translatable(selected == MudTuningHudElement.CENTER
                            ? "gui.mirebound.tuning.settings.hud_opacity"
                            : "gui.mirebound.tuning.settings.controls_opacity"),
                    settingX, selected == MudTuningHudElement.CENTER
                            ? settingY + 2 : settingY + 24,
                    HELP_COLOR, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        MudTuningHudElement hit = elementAt(mouseX, mouseY);
        selected = hit;
        dragging = false;
        resizing = false;
        pendingDrag = hit != null;
        if (hit != null) {
            MudTuningHudLayout.HudBounds bounds = bounds(hit);
            pressX = mouseX;
            pressY = mouseY;
            if (bounds.containsResizeHandle(mouseX, mouseY)) {
                resizing = true;
                pendingDrag = false;
                resizeStartScale = bounds.scale();
            } else {
                dragOffsetX = mouseX - bounds.x();
                dragOffsetY = mouseY - bounds.y();
            }
        }
        rebuildWidgets();
        return hit != null;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (button != 0 || selected == null) {
            return false;
        }
        if (pendingDrag) {
            double distance = Math.hypot(mouseX - pressX, mouseY - pressY);
            if (distance < DRAG_THRESHOLD) {
                return true;
            }
            dragging = true;
            pendingDrag = false;
            snappedToVerticalCenter = false;
            snappedToHorizontalCenter = false;
        }
        if (resizing) {
            MudTuningHudLayout.HudBounds bounds = bounds(selected);
            double horizontal = (mouseX - bounds.x()) / Math.max(1.0D, bounds.baseWidth());
            double vertical = (mouseY - bounds.y()) / Math.max(1.0D, bounds.baseHeight());
            double next = Math.max(horizontal, vertical);
            if (!Double.isFinite(next) || next <= 0.0D) {
                next = resizeStartScale;
            }
            next = Math.min(next, MudTuningHudLayout.maxScale(
                    width, height, bounds.baseWidth(), bounds.baseHeight()));
            if (trySetSelectedLayout(MudTuningHudLayout.x(selected),
                    MudTuningHudLayout.verticalPosition(selected), next)) {
                repositionSettings();
            }
            return true;
        }
        if (dragging) {
            MudTuningHudLayout.HudBounds bounds = bounds(selected);
            double availableWidth = Math.max(1.0D,
                    width - bounds.width() - MudTuningHudLayout.SCREEN_MARGIN * 2.0D);
            double left = mouseX - dragOffsetX;
            double nextX = (left - MudTuningHudLayout.SCREEN_MARGIN) / availableWidth;
            double availableHeight = Math.max(1.0D,
                    height - bounds.height() - MudTuningHudLayout.SCREEN_MARGIN * 2.0D);
            double top = mouseY - dragOffsetY;
            double nextY = (top - MudTuningHudLayout.SCREEN_MARGIN) / availableHeight;
            boolean snapX = false;
            boolean snapY = false;
            if (alignmentSnapping) {
                if (left <= MudTuningHudLayout.SCREEN_MARGIN + SNAP_THRESHOLD) {
                    nextX = 0.0D;
                } else if (left >= width - bounds.width()
                        - MudTuningHudLayout.SCREEN_MARGIN - SNAP_THRESHOLD) {
                    nextX = 1.0D;
                } else if (Math.abs(left + bounds.width() / 2.0D
                        - width / 2.0D) <= SNAP_THRESHOLD) {
                    nextX = 0.5D;
                    snapX = true;
                }
                if (top <= MudTuningHudLayout.SCREEN_MARGIN + SNAP_THRESHOLD) {
                    nextY = 0.0D;
                } else if (top >= height - bounds.height()
                        - MudTuningHudLayout.SCREEN_MARGIN - SNAP_THRESHOLD) {
                    nextY = 1.0D;
                } else if (Math.abs(top + bounds.height() / 2.0D
                        - height / 2.0D) <= SNAP_THRESHOLD) {
                    nextY = 0.5D;
                    snapY = true;
                }
            }
            if (trySetSelectedLayout(nextX, nextY,
                    MudTuningHudLayout.scale(selected))) {
                snappedToVerticalCenter = snapX;
                snappedToHorizontalCenter = snapY;
                repositionSettings();
            } else {
                snappedToVerticalCenter = false;
                snappedToHorizontalCenter = false;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
            resizing = false;
            pendingDrag = false;
            snappedToVerticalCenter = false;
            snappedToHorizontalCenter = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        MudTuningHudEditorState.commit();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private MudTuningHudElement elementAt(double mouseX, double mouseY) {
        for (MudTuningHudElement element : MudTuningHudElement.values()) {
            if (bounds(element).contains(mouseX, mouseY)) {
                return element;
            }
        }
        return null;
    }

    private MudTuningHudLayout.HudBounds bounds(MudTuningHudElement element) {
        return element == MudTuningHudElement.CENTER
                ? MudTuningWandHud.centerBounds(minecraft, width, height)
                : MudTuningWandHud.controlsBounds(minecraft, width, height);
    }

    private void positionSettings(MudTuningHudLayout.HudBounds bounds) {
        int panelHeight = selected == MudTuningHudElement.CENTER ? 36 : 60;
        int x = bounds.x() + bounds.width() + SETTING_GAP;
        if (x + SETTING_WIDTH > width - SETTING_MARGIN) {
            x = bounds.x() - SETTING_WIDTH - SETTING_GAP;
        }
        settingX = Mth.clamp(x, SETTING_MARGIN,
                Math.max(SETTING_MARGIN, width - SETTING_WIDTH - SETTING_MARGIN));
        settingY = Mth.clamp(bounds.y(), SETTING_MARGIN,
                Math.max(SETTING_MARGIN, height - panelHeight - SETTING_MARGIN));
    }

    private void repositionSettings() {
        if (selected == null) {
            return;
        }
        positionSettings(bounds(selected));
        if (settingToggle != null) {
            settingToggle.setX(settingX);
            settingToggle.setY(settingY);
        }
        if (opacitySlider != null) {
            opacitySlider.setX(settingX);
            opacitySlider.setY(settingY
                    + (selected == MudTuningHudElement.CENTER ? 14 : 36));
        }
    }

    private boolean trySetSelectedLayout(double x, double y, double scale) {
        if (selected == null) {
            return false;
        }
        double oldX = MudTuningHudEditorState.x(selected);
        double oldY = MudTuningHudEditorState.y(selected);
        double oldScale = MudTuningHudEditorState.scale(selected);
        MudTuningHudEditorState.setLayout(selected, x, y, scale);
        if (overlapsOtherHud(selected)) {
            MudTuningHudEditorState.setLayout(selected, oldX, oldY, oldScale);
            return false;
        }
        updateResetButton();
        return true;
    }

    private boolean overlapsOtherHud(MudTuningHudElement element) {
        MudTuningHudLayout.HudBounds candidate = bounds(element);
        for (MudTuningHudElement other : MudTuningHudElement.values()) {
            if (other != element
                    && candidate.overlaps(bounds(other), OVERLAP_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    private void updateResetButton() {
        if (resetButton != null) {
            resetButton.active = !MudTuningHudEditorState.isDefault();
        }
    }

    private Component fit(Component text, int availableWidth) {
        if (font.width(text) <= availableWidth) {
            return text;
        }
        String suffix = "...";
        int textWidth = Math.max(0, availableWidth - font.width(suffix));
        return Component.literal(font.plainSubstrByWidth(text.getString(), textWidth)
                + suffix);
    }

    private static void drawBorder(GuiGraphics graphics,
            MudTuningHudLayout.HudBounds bounds, int color) {
        int x = bounds.x();
        int y = bounds.y();
        int right = x + bounds.width();
        int bottom = y + bounds.height();
        graphics.fill(x, y, right, y + 1, color);
        graphics.fill(x, bottom - 1, right, bottom, color);
        graphics.fill(x, y, x + 1, bottom, color);
        graphics.fill(right - 1, y, right, bottom, color);
    }

    private static void drawHandle(GuiGraphics graphics,
            MudTuningHudLayout.HudBounds bounds) {
        int x = bounds.x() + bounds.width() - HANDLE_SIZE;
        int y = bounds.y() + bounds.height() - HANDLE_SIZE;
        graphics.fill(x, y, x + HANDLE_SIZE, y + HANDLE_SIZE, HANDLE);
        graphics.fill(x + 2, y + 2, x + HANDLE_SIZE, y + 3, 0xFF3E4C44);
        graphics.fill(x + 2, y + 4, x + HANDLE_SIZE, y + 5, 0xFF3E4C44);
    }
}
