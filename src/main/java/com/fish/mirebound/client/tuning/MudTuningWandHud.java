package com.fish.mirebound.client.tuning;

import com.fish.mirebound.adaptive.AdaptiveMudBlock;
import com.fish.mirebound.adaptive.AdaptiveMudEligibility;
import com.fish.mirebound.client.AdaptiveMudClientCache;
import com.fish.mirebound.client.MudTuningWandCoreTexture;
import com.fish.mirebound.client.generation.MudTerrainGenerationController;
import com.fish.mirebound.mud.tuning.MudTuningSelectionElement;
import com.fish.mirebound.network.payload.MudTuningSelectionPayload;
import com.fish.mirebound.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Bottom-safe contextual controls and mode selector for the tuning wand. */
public final class MudTuningWandHud {
    private static final int LINE_HEIGHT = 11;
    private static final int PANEL_BACKGROUND = 0xC80B100E;
    private static final int TEXT = 0xFFE8EEE8;
    private static final int MUTED = 0xFF91A198;
    private static final int CONTROL_BACKGROUND = 0xB8000000;
    private static final int LOCK_BACKGROUND = 0xD0521111;
    private static final int LOCK_FILL = 0xFFE33D3D;

    private MudTuningWandHud() {
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MudTuningHudEditorScreen) {
            return;
        }
        renderHud(minecraft, event.getGuiGraphics(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    static void renderEditorPreview(GuiGraphics graphics, float partialTick) {
        renderHud(Minecraft.getInstance(), graphics, partialTick);
    }

    private static void renderHud(Minecraft minecraft, GuiGraphics graphics,
            float partial) {
        boolean editor = minecraft.screen instanceof MudTuningHudEditorScreen;
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null && !editor
                || !editor && MudTuningInputController.heldWandHand(minecraft.player) == null) {
            return;
        }
        double time = minecraft.level.getGameTime() + partial;
        int accent = 0xFF000000 | MudTuningWandCoreTexture.hudColor(time);
        if (editor || MudTuningHudLayout.enabled(MudTuningHudElement.CENTER)) {
            renderGroup(graphics, minecraft, MudTuningHudElement.CENTER,
                    centerBounds(minecraft, graphics.guiWidth(), graphics.guiHeight()),
                    editor && !MudTuningHudLayout.enabled(MudTuningHudElement.CENTER),
                    () -> renderCenter(graphics, minecraft, accent));
        }
        if (editor || MudTuningHudLayout.enabled(MudTuningHudElement.CONTROLS)) {
            renderGroup(graphics, minecraft, MudTuningHudElement.CONTROLS,
                    controlsBounds(minecraft, graphics.guiWidth(), graphics.guiHeight()),
                    editor && !MudTuningHudLayout.enabled(MudTuningHudElement.CONTROLS),
                    () -> renderControls(graphics, minecraft));
        }
    }

    private static void renderGroup(GuiGraphics graphics, Minecraft minecraft,
            MudTuningHudElement element, MudTuningHudLayout.HudBounds bounds,
            boolean dimmed, Runnable renderer) {
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x(), bounds.y(), 0.0F);
        float scale = (float) bounds.scale();
        graphics.pose().scale(scale, scale, 1.0F);
        if (dimmed) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 0.34F);
        }
        renderer.run();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private static void renderCenter(GuiGraphics graphics, Minecraft minecraft, int accent) {
        int width = MudTuningHudLayout.modeWidth(0);
        int modeY = centerModeY(minecraft);
        boolean editor = minecraft.screen instanceof MudTuningHudEditorScreen;
        MudTuningInputController.ConversionUnlockStage unlockStage =
                MudTuningInputController.conversionUnlockStage();
        boolean conversionMode = MudTuningClientState.mode() == MudTuningWandMode.CONVERT;
        boolean standardLocked = conversionMode
                && unlockStage == MudTuningInputController.ConversionUnlockStage.STANDARD;
        boolean unrestrictedUnlocking = conversionMode
                && unlockStage == MudTuningInputController.ConversionUnlockStage.UNRESTRICTED
                && MudTuningInputController.conversionUnlockProgress() > 0.0F;
        List<SummaryLine> summary = !editor && standardLocked
                ? List.of() : summaryLines(minecraft);
        MudTuningModeSelectorRenderer.render(
                graphics, minecraft.font, 0, modeY, width, accent);
        if (editor) {
            if (!summary.isEmpty()) {
                renderSummary(graphics, minecraft, summary, 0, modeY, width, accent);
            }
            int summaryHeight = summary.isEmpty() ? 0 : summary.size() * LINE_HEIGHT + 9;
            renderConversionLockPreview(graphics, minecraft, 0,
                    modeY - summaryHeight, width);
        } else if (standardLocked) {
            renderConversionLock(graphics, minecraft, 0, modeY, width);
        } else {
            if (!summary.isEmpty()) {
                renderSummary(graphics, minecraft, summary, 0, modeY, width, accent);
            }
            if (unrestrictedUnlocking) {
                int summaryHeight = summary.isEmpty()
                        ? 0 : summary.size() * LINE_HEIGHT + 9;
                renderConversionLock(graphics, minecraft,
                        0, modeY - summaryHeight, width);
            }
        }
    }

    static MudTuningHudLayout.HudBounds centerBounds(
            Minecraft minecraft, int guiWidth, int guiHeight) {
        int width = MudTuningHudLayout.modeWidth(0);
        int height = centerHeight(minecraft);
        return MudTuningHudLayout.bounds(minecraft, MudTuningHudElement.CENTER,
                guiWidth, guiHeight, width, height);
    }

    static MudTuningHudLayout.HudBounds controlsBounds(
            Minecraft minecraft, int guiWidth, int guiHeight) {
        Font font = minecraft.font;
        int width = controls(minecraft).stream()
                .mapToInt(line -> controlLineWidth(font, line))
                .max().orElse(60);
        int height = controls(minecraft).size() * LINE_HEIGHT;
        return MudTuningHudLayout.bounds(minecraft, MudTuningHudElement.CONTROLS,
                guiWidth, guiHeight, Math.max(24, width), Math.max(LINE_HEIGHT, height));
    }

    private static int centerHeight(Minecraft minecraft) {
        return MudTuningHudLayout.modeHeight(0) + centerAboveHeight(minecraft);
    }

    private static int centerModeY(Minecraft minecraft) {
        return centerAboveHeight(minecraft);
    }

    private static int centerAboveHeight(Minecraft minecraft) {
        boolean editor = MudTuningHudEditorState.active();
        boolean locked = MudTuningClientState.mode() == MudTuningWandMode.CONVERT
                && MudTuningInputController.conversionLocked();
        if (locked && !editor) {
            return LINE_HEIGHT + 5 + 4;
        }
        int summaryHeight = summaryLines(minecraft).size() * LINE_HEIGHT + 5;
        boolean unrestrictedUnlocking = MudTuningClientState.mode()
                == MudTuningWandMode.CONVERT
                && MudTuningInputController.conversionUnlockStage()
                        == MudTuningInputController.ConversionUnlockStage.UNRESTRICTED
                && MudTuningInputController.conversionUnlockProgress() > 0.0F;
        int summary = summaryHeight > 5 ? summaryHeight + 4 : 0;
        int lock = editor || unrestrictedUnlocking ? LINE_HEIGHT + 5 + 4 : 0;
        return lock + summary;
    }

    private static void renderConversionLock(GuiGraphics graphics, Minecraft minecraft,
            int x, int modeY, int width) {
        MudTuningInputController.ConversionUnlockStage stage =
                MudTuningInputController.conversionUnlockStage();
        renderConversionLock(graphics, minecraft, x, modeY, width, stage,
                MudTuningInputController.conversionUnlockProgress(), true);
    }

    private static void renderConversionLockPreview(GuiGraphics graphics,
            Minecraft minecraft, int x, int modeY, int width) {
        renderConversionLock(graphics, minecraft, x, modeY, width,
                MudTuningInputController.ConversionUnlockStage.STANDARD, 0.62F, false);
    }

    private static void renderConversionLock(GuiGraphics graphics, Minecraft minecraft,
            int x, int modeY, int width,
            MudTuningInputController.ConversionUnlockStage stage,
            float rawProgress, boolean animate) {
        boolean unrestricted = stage
                == MudTuningInputController.ConversionUnlockStage.UNRESTRICTED;
        int height = LINE_HEIGHT + 5;
        float progress = Mth.clamp(rawProgress, 0.0F, 1.0F);
        int panelX = x + (unrestricted
                && animate ? MudTuningInputController.unrestrictedUnlockShake(
                        Math.round(progress * MudTuningInputController.CONVERSION_UNLOCK_TICKS),
                        false) : 0);
        int top = modeY - height - 4 + (unrestricted
                && animate ? MudTuningInputController.unrestrictedUnlockShake(
                        Math.round(progress * MudTuningInputController.CONVERSION_UNLOCK_TICKS),
                        true) : 0);
        int background = unrestricted ? 0xD06A0C43 : LOCK_BACKGROUND;
        int fillColor = unrestricted ? 0xFFFF267E : LOCK_FILL;
        graphics.fill(panelX, top, panelX + width, top + height,
                withConfiguredAlpha(background));
        Component message = progress > 0.0F
                ? Component.translatable(
                        unrestricted
                                ? "hud.mirebound.tuning.convert_unrestricted.progress"
                                : "hud.mirebound.tuning.convert_lock.progress",
                        Math.round(progress * 100.0F))
                : Component.translatable(
                        unrestricted
                                ? "hud.mirebound.tuning.convert_unrestricted.warning"
                                : "hud.mirebound.tuning.convert_lock.warning");
        if (progress > 0.0F) {
            int fill = Math.round((width - 4) * progress);
            graphics.fill(panelX + 2, top + 2, panelX + 2 + fill, top + height - 2,
                    withConfiguredAlpha(fillColor));
        }
        Component fitted = fit(minecraft.font, message, width - 8);
        graphics.drawString(minecraft.font, fitted,
                panelX + (width - minecraft.font.width(fitted)) / 2,
                top + 3, 0xFFFFFFFF, false);
    }

    private static void renderSummary(GuiGraphics graphics, Minecraft minecraft,
            List<SummaryLine> lines, int x, int modeY, int width, int accent) {
        int height = lines.size() * LINE_HEIGHT + 5;
        int top = modeY - height - 4;
        graphics.fill(x, top, x + width, top + height,
                withConfiguredAlpha(0xA80A0E0C));
        graphics.fill(x, top, x + 2, top + height, withConfiguredAlpha(accent));
        for (int index = 0; index < lines.size(); index++) {
            SummaryLine line = lines.get(index);
            graphics.drawString(minecraft.font,
                    fit(minecraft.font, line.text(), width - 9),
                    x + 6, top + 3 + index * LINE_HEIGHT, line.color(), false);
        }
    }

    private static List<SummaryLine> summaryLines(Minecraft minecraft) {
        List<SummaryLine> lines = new ArrayList<>(3);
        switch (MudTuningClientState.mode()) {
            case RANGE -> appendRangeSummary(lines);
            case SINGLE -> {
                TargetInfo target = targetInfo(minecraft);
                lines.add(new SummaryLine(target.name(), TEXT));
                if (!target.status().getString().isEmpty()) {
                    lines.add(new SummaryLine(target.status(), target.statusColor()));
                }
            }
            case CONVERT -> {
                TargetInfo target = targetInfo(minecraft);
                lines.add(new SummaryLine(target.name(), TEXT));
                if (!target.status().getString().isEmpty()) {
                    lines.add(new SummaryLine(target.status(), target.statusColor()));
                }
                if (!MudTuningClientSettings.unrestrictedConversionUnlocked()) {
                    lines.add(new SummaryLine(Component.translatable(
                            "hud.mirebound.tuning.convert_unrestricted.status_locked"),
                            0xFFFF8974));
                } else if (MudTuningClientSettings.unrestrictedConversionEnabled()) {
                    lines.add(new SummaryLine(Component.translatable(
                            "hud.mirebound.tuning.convert_unrestricted.status_enabled"),
                            0xFFFF8A24));
                } else {
                    lines.add(new SummaryLine(Component.translatable(
                            "hud.mirebound.tuning.convert_unrestricted.status_disabled"),
                            MUTED));
                }
            }
            case SUMMON -> appendSummonSummary(lines, minecraft);
            case GENERATION -> appendGenerationSummary(lines, minecraft);
            case SETTINGS -> lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.settings.summary"), TEXT));
        }
        return lines.subList(0, Math.min(3, lines.size()));
    }

    private static void appendSummonSummary(
            List<SummaryLine> lines, Minecraft minecraft) {
        MudTuningSummonType type = MudTuningClientState.summonType();
        lines.add(new SummaryLine(Component.translatable(
                "hud.mirebound.tuning.summon.current",
                Component.translatable(type.translationKey())), TEXT));
        switch (type) {
            case TENTACLE -> {
                var tentacle = MudTuningTentacleTargeting.target(minecraft);
                if (tentacle != null) {
                    lines.add(new SummaryLine(Component.translatable(
                            "hud.mirebound.tuning.tentacle_selected",
                            tentacle.instanceId()), 0xFF8FE8D1));
                    return;
                }
                Vec3 target = MudTuningSpatialPlacement.target(minecraft);
                if (target != null) {
                    lines.add(new SummaryLine(Component.translatable(
                            "hud.mirebound.tuning.tentacle_target",
                            String.format(java.util.Locale.ROOT, "%.1f  %.1f  %.1f",
                                    target.x, target.y, target.z)), MUTED));
                }
            }
        }
    }

    private static void appendRangeSummary(List<SummaryLine> lines) {
        if (!MudTuningClientState.hasFirst()) {
            lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.no_selection"), MUTED));
            return;
        }
        if (!MudTuningClientState.hasSecond()) {
            lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.first",
                    coordinates(MudTuningClientState.first().pos())), TEXT));
            lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.awaiting_second"), MUTED));
            return;
        }
        BlockPos first = MudTuningClientState.first().pos();
        BlockPos second = MudTuningClientState.second().pos();
        long sizeX = Math.abs((long) second.getX() - first.getX()) + 1L;
        long sizeY = Math.abs((long) second.getY() - first.getY()) + 1L;
        long sizeZ = Math.abs((long) second.getZ() - first.getZ()) + 1L;
        MudTuningSelectionPayload.SelectionSummary summary = MudTuningClientState.summary();
        lines.add(new SummaryLine(Component.translatable(
                "hud.mirebound.tuning.points",
                coordinates(first), coordinates(second)), TEXT));
        MudTuningSelectionElement selected = MudTuningClientState.selectedElement();
        if (selected != MudTuningSelectionElement.NONE) {
            lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.adjusting." + selected.name().toLowerCase(
                            java.util.Locale.ROOT)), 0xFF66EEF1));
            return;
        }
        lines.add(new SummaryLine(Component.translatable(
                "hud.mirebound.tuning.dimensions",
                sizeX, sizeY, sizeZ, summary.volume()), MUTED));
    }

    private static void appendGenerationSummary(
            List<SummaryLine> lines, Minecraft minecraft) {
        lines.add(new SummaryLine(Component.translatable(
                "hud.mirebound.tuning.generation.current",
                Component.translatable(
                        MudTuningClientSettings.generationType().translationKey()),
                MudTerrainGenerationController.centerLocked()
                        ? Component.translatable(
                                "hud.mirebound.tuning.generation.fixed_center")
                        : Component.translatable(
                                "hud.mirebound.tuning.generation.spatial_distance",
                                String.format(java.util.Locale.ROOT, "%.1f",
                                        MudTuningSpatialPlacement.distance())),
                Component.translatable(
                        "hud.mirebound.tuning.generation.axis",
                        MudTerrainGenerationController.rotationAxis().name())), TEXT));
        if (MudTuningInputController.conversionLocked()) {
            lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.generation.locked"), 0xFFFF8974));
            return;
        }
        switch (MudTerrainGenerationController.status()) {
            case EMPTY -> lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.generation.empty"), 0xFFFFB46A));
            case READY -> {
                BlockPos center = MudTerrainGenerationController.previewCenter(minecraft);
                if (center != null) {
                    lines.add(new SummaryLine(Component.translatable(
                            "hud.mirebound.tuning.generation.preview",
                            coordinates(center),
                            MudTerrainGenerationController.previewCellCount(minecraft)),
                            MUTED));
                }
            }
            default -> lines.add(new SummaryLine(Component.translatable(
                    "hud.mirebound.tuning.generation.no_target"), MUTED));
        }
    }

    private static void renderControls(GuiGraphics graphics, Minecraft minecraft) {
        List<ControlLine> controls = controls(minecraft);
        Font font = minecraft.font;
        int desiredWidth = controls.stream()
                .mapToInt(line -> controlLineWidth(font, line))
                .max().orElse(60);
        int availableWidth = Math.max(24, desiredWidth);
        for (int index = 0; index < controls.size(); index++) {
            ControlLine line = controls.get(index);
            int lineY = index * LINE_HEIGHT;
            int keyWidth = font.width(line.key());
            int actionSpace = Math.max(4, availableWidth - keyWidth - 7);
            Component action = fit(font, line.action(), actionSpace);
            int rowWidth = Math.min(availableWidth,
                    keyWidth + 4 + font.width(action) + 4);
            int alignedX = switch (controlsAlignment()) {
                case LEFT -> 0;
                case CENTER -> (availableWidth - rowWidth) / 2;
                case RIGHT -> availableWidth - rowWidth;
            };
            graphics.fill(alignedX, lineY, alignedX + rowWidth, lineY + LINE_HEIGHT,
                    withConfiguredAlpha(CONTROL_BACKGROUND,
                            MudTuningHudElement.CONTROLS));
            graphics.drawString(font, line.key(), alignedX + 2, lineY + 1,
                    0xFFF1E8D2, false);
            graphics.drawString(font, action, alignedX + keyWidth + 6, lineY + 1,
                    TEXT, false);
        }
    }

    private static ControlsAlignment controlsAlignment() {
        return controlsAlignment(MudTuningHudLayout.x(MudTuningHudElement.CONTROLS));
    }

    static ControlsAlignment controlsAlignment(double x) {
        if (x < 1.0D / 3.0D) {
            return ControlsAlignment.LEFT;
        }
        if (x >= 2.0D / 3.0D) {
            return ControlsAlignment.RIGHT;
        }
        return ControlsAlignment.CENTER;
    }

    private static int controlLineWidth(Font font, ControlLine line) {
        return font.width(line.key()) + 4 + font.width(line.action()) + 4;
    }

    private static List<ControlLine> controls(Minecraft minecraft) {
        Component attack = minecraft.options.keyAttack.getTranslatedKeyMessage();
        Component use = minecraft.options.keyUse.getTranslatedKeyMessage();
        Component mode = MudTuningInputController.modeKey().getTranslatedKeyMessage();
        Component nudge = MudTuningInputController.nudgeKey().getTranslatedKeyMessage();
        Component openRange = MudTuningInputController.openRangeKey().getTranslatedKeyMessage();
        Component selectElement = MudTuningInputController.selectElementKey()
                .getTranslatedKeyMessage();
        Component quickSummon = MudTuningInputController.quickSummonKey()
                .getTranslatedKeyMessage();
        Component generationVolumeUp = MudTuningInputController
                .generationVolumeUpKey().getTranslatedKeyMessage();
        Component generationVolumeDown = MudTuningInputController
                .generationVolumeDownKey().getTranslatedKeyMessage();
        Component generationReroll = MudTuningInputController
                .generationRerollKey().getTranslatedKeyMessage();
        Component generationAxis = MudTuningInputController
                .generationAxisKey().getTranslatedKeyMessage();
        Component generationRotate = MudTuningInputController
                .generationRotateKey().getTranslatedKeyMessage();
        List<ControlLine> result = new ArrayList<>(12);
        result.add(new ControlLine(Component.translatable(
                "hud.mirebound.tuning.key_scroll", mode), Component.translatable(
                "hud.mirebound.tuning.action.switch_mode")));
        switch (MudTuningClientState.mode()) {
            case RANGE -> {
                result.add(new ControlLine(selectElement, Component.translatable(
                        "hud.mirebound.tuning.action.select_element")));
                result.add(new ControlLine(Component.translatable(
                        "hud.mirebound.tuning.key_scroll", nudge), Component.translatable(
                        "hud.mirebound.tuning.action.nudge_element")));
                result.add(new ControlLine(attack, Component.translatable(
                        "hud.mirebound.tuning.action.first")));
                result.add(new ControlLine(use, Component.translatable(
                        "hud.mirebound.tuning.action.second")));
                result.add(new ControlLine(openRange, Component.translatable(
                        "hud.mirebound.tuning.action.open_range")));
            }
            case SINGLE -> {
                result.add(new ControlLine(attack, Component.translatable(
                        "hud.mirebound.tuning.action.open_single")));
                result.add(new ControlLine(use, Component.translatable(
                        "hud.mirebound.tuning.action.open_single")));
            }
            case CONVERT -> {
                if (MudTuningInputController.conversionLocked()) {
                    result.add(new ControlLine(Component.translatable(
                            "hud.mirebound.tuning.key_both_mouse", attack, use),
                            Component.translatable(
                                    "hud.mirebound.tuning.convert_lock.unlock")));
                } else {
                    result.add(new ControlLine(attack, Component.translatable(
                            "hud.mirebound.tuning.action.convert_single")));
                    result.add(new ControlLine(use, Component.translatable(
                            "hud.mirebound.tuning.action.restore_single")));
                    result.add(new ControlLine(Component.translatable(
                            "hud.mirebound.tuning.key_both_mouse", attack, use),
                            Component.translatable(MudTuningClientSettings
                                    .unrestrictedConversionUnlocked()
                                            ? MudTuningInputController
                                                    .unrestrictedConversionEnabled()
                                                            ? "hud.mirebound.tuning.convert_unrestricted.disable"
                                                            : "hud.mirebound.tuning.convert_unrestricted.enable"
                                            : "hud.mirebound.tuning.convert_unrestricted.unlock")));
                }
            }
            case SUMMON -> appendSummonControls(result, minecraft,
                    attack, use, nudge, openRange, selectElement, quickSummon);
            case GENERATION -> {
                result.add(new ControlLine(openRange, Component.translatable(
                        "hud.mirebound.tuning.action.switch_generation_type")));
                result.add(new ControlLine(selectElement, Component.translatable(
                        "hud.mirebound.tuning.action.undo_generation")));
                if (MudTerrainGenerationController.centerLocked()) {
                    result.add(new ControlLine(Component.translatable(
                            "hud.mirebound.tuning.key_scroll", nudge),
                            Component.translatable(
                                    "hud.mirebound.tuning.action.move_generation_center")));
                } else {
                    result.add(new ControlLine(Component.translatable(
                            "hud.mirebound.tuning.key_scroll", nudge),
                            Component.translatable(
                                    "hud.mirebound.tuning.action.generation_distance")));
                }
                result.add(new ControlLine(attack, Component.translatable(
                        MudTerrainGenerationController.centerLocked()
                                ? "hud.mirebound.tuning.action.unlock_generation_center"
                                : "hud.mirebound.tuning.action.lock_generation_center")));
                result.add(new ControlLine(use, Component.translatable(
                        "hud.mirebound.tuning.action.open_generation")));
                result.add(new ControlLine(quickSummon, Component.translatable(
                        "hud.mirebound.tuning.action.generate")));
                result.add(new ControlLine(Component.translatable(
                        "hud.mirebound.tuning.key_pair",
                        generationVolumeUp, generationVolumeDown),
                        Component.translatable(
                                "hud.mirebound.tuning.action.generation_volume")));
                result.add(new ControlLine(generationReroll, Component.translatable(
                        "hud.mirebound.tuning.action.generation_reroll")));
                result.add(new ControlLine(generationAxis, Component.translatable(
                        "hud.mirebound.tuning.action.switch_generation_axis")));
                result.add(new ControlLine(generationRotate, Component.translatable(
                        "hud.mirebound.tuning.action.rotate_generation")));
            }
            case SETTINGS -> {
                result.add(new ControlLine(attack, Component.translatable(
                        "hud.mirebound.tuning.action.open_settings")));
                result.add(new ControlLine(use, Component.translatable(
                        "hud.mirebound.tuning.action.open_settings")));
            }
        }
        return result;
    }

    private static void appendSummonControls(List<ControlLine> result,
            Minecraft minecraft, Component attack, Component use, Component nudge,
            Component choose, Component secondary, Component quickSummon) {
        result.add(new ControlLine(choose, Component.translatable(
                "hud.mirebound.tuning.action.choose_summon")));
        result.add(new ControlLine(quickSummon, Component.translatable(
                "hud.mirebound.tuning.action.quick_summon")));
        switch (MudTuningClientState.summonType()) {
            case TENTACLE -> {
                boolean targeted = MudTuningTentacleTargeting.target(minecraft) != null;
                result.add(new ControlLine(secondary, Component.translatable(
                        "hud.mirebound.tuning.action.tentacle_snap_toggle")));
                if (!targeted) {
                    result.add(new ControlLine(Component.translatable(
                            "hud.mirebound.tuning.key_scroll", nudge), Component.translatable(
                            "hud.mirebound.tuning.action.tentacle_distance")));
                    result.add(new ControlLine(use, Component.translatable(
                            "hud.mirebound.tuning.action.tentacle_summon")));
                } else {
                    result.add(new ControlLine(attack, Component.translatable(
                            "hud.mirebound.tuning.action.tentacle_remove")));
                    result.add(new ControlLine(use, Component.translatable(
                            "hud.mirebound.tuning.action.tentacle_configure")));
                }
            }
        }
    }

    private static TargetInfo targetInfo(Minecraft minecraft) {
        BlockHitResult hit = MudTuningWandTargeting.blockHit(minecraft);
        BlockPos pos = hit == null ? null : hit.getBlockPos();
        if (pos == null) {
            return new TargetInfo(Component.translatable(
                    "hud.mirebound.tuning.no_target"), Component.empty(), MUTED);
        }
        BlockState actual = minecraft.level.getBlockState(pos);
        BlockState source = actual.getBlock() instanceof AdaptiveMudBlock
                ? AdaptiveMudClientCache.sourceState(minecraft.level, pos) : null;
        Component name = source == null ? actual.getBlock().getName()
                : Component.translatable("block.mirebound.adaptive_named",
                        source.getBlock().getName());
        if (actual.getBlock() instanceof AdaptiveMudBlock) {
            return new TargetInfo(name, Component.translatable(
                    "hud.mirebound.tuning.converted_target"), 0xFFFF77C5);
        }
        if (MudTuningInputController.unrestrictedConversionEnabled()) {
            return new TargetInfo(name, Component.translatable(
                    "hud.mirebound.tuning.convert_unrestricted.force_target"),
                    0xFFFF4B96);
        }
        AdaptiveMudEligibility.Result eligibility =
                AdaptiveMudEligibility.check(minecraft.level, pos, actual);
        return new TargetInfo(name, Component.translatable(eligibility.translationKey()),
                eligibility.supported() ? 0xFF91E49B : 0xFFFFB46A);
    }

    private static Component fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String suffix = "...";
        int available = Math.max(0, width - font.width(suffix));
        return Component.literal(font.plainSubstrByWidth(text.getString(), available) + suffix);
    }

    private static Component coordinates(BlockPos pos) {
        return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private static int withConfiguredAlpha(int color) {
        return withConfiguredAlpha(color, MudTuningHudElement.CENTER);
    }

    static float configuredHudOpacity(MudTuningHudElement element) {
        if (MudTuningHudEditorState.active()) {
            return element == MudTuningHudElement.CONTROLS
                    ? (float) MudTuningHudEditorState.controlsOpacity()
                    : (float) MudTuningHudEditorState.hudOpacity();
        }
        return element == MudTuningHudElement.CONTROLS
                ? MudTuningClientSettings.controlsHudOpacity()
                : MudTuningClientSettings.hudOpacity();
    }

    private static int withConfiguredAlpha(int color, MudTuningHudElement element) {
        int sourceAlpha = color >>> 24;
        float opacity = configuredHudOpacity(element);
        int alpha = Math.round(sourceAlpha * opacity);
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private record ControlLine(Component key, Component action) {
    }

    private record SummaryLine(Component text, int color) {
    }

    private record TargetInfo(Component name, Component status, int statusColor) {
    }

    enum ControlsAlignment {
        LEFT,
        CENTER,
        RIGHT
    }
}
