package com.fish.mirebound.client.tuning;

import com.fish.mirebound.client.config.MireboundClientSettings;
import java.util.EnumMap;
import java.util.Map;

/** In-memory draft for the wand HUD editor; persisted only when the editor closes. */
final class MudTuningHudEditorState {
    private static final EnumMap<MudTuningHudElement, Draft> DRAFTS =
            new EnumMap<>(MudTuningHudElement.class);
    private static double hudOpacity;
    private static double controlsOpacity;
    private static boolean active;

    private MudTuningHudEditorState() {
    }

    static void begin() {
        DRAFTS.clear();
        hudOpacity = MudTuningClientSettings.hudOpacity();
        controlsOpacity = MudTuningClientSettings.controlsHudOpacity();
        for (MudTuningHudElement element : MudTuningHudElement.values()) {
            DRAFTS.put(element, new Draft(
                    element == MudTuningHudElement.CENTER
                            || MudTuningClientSettings.hudElementEnabled(element),
                    MudTuningClientSettings.hudElementX(element),
                    MudTuningClientSettings.hudElementY(element),
                    MudTuningClientSettings.hudElementScale(element)));
        }
        active = true;
    }

    static boolean active() {
        return active;
    }

    static boolean enabled(MudTuningHudElement element) {
        return element == MudTuningHudElement.CENTER || draft(element).enabled;
    }

    static void setEnabled(MudTuningHudElement element, boolean enabled) {
        if (element != MudTuningHudElement.CENTER) {
            draft(element).enabled = enabled;
        }
    }

    static double hudOpacity() {
        return hudOpacity;
    }

    static void setHudOpacity(double value) {
        hudOpacity = clamp(value, 0.20D, 1.0D);
    }

    static double controlsOpacity() {
        return controlsOpacity;
    }

    static void setControlsOpacity(double value) {
        controlsOpacity = clamp(value, 0.20D, 1.0D);
    }

    static double x(MudTuningHudElement element) {
        return draft(element).x;
    }

    static double y(MudTuningHudElement element) {
        return draft(element).y;
    }

    static double scale(MudTuningHudElement element) {
        return draft(element).scale;
    }

    static void setLayout(MudTuningHudElement element,
            double x, double y, double scale) {
        Draft draft = draft(element);
        draft.x = clamp(x, 0.0D, 1.0D);
        draft.y = clamp(y, 0.0D, 1.0D);
        draft.scale = clamp(scale, 0.50D, 2.0D);
    }

    static boolean isDefault() {
        for (MudTuningHudElement element : MudTuningHudElement.values()) {
            Draft draft = draft(element);
            if ((element != MudTuningHudElement.CENTER && !draft.enabled)
                    || Math.abs(draft.x - element.defaultX()) > 1.0E-9D
                    || Math.abs(draft.y - element.defaultY()) > 1.0E-9D
                    || Math.abs(draft.scale - element.defaultScale()) > 1.0E-9D) {
                return false;
            }
        }
        return Math.abs(hudOpacity - MireboundClientSettings.DEFAULT_TUNING_HUD_OPACITY)
                        <= 1.0E-9D
                && Math.abs(controlsOpacity
                        - MireboundClientSettings.DEFAULT_TUNING_CONTROLS_OPACITY)
                        <= 1.0E-9D;
    }

    static void resetToDefaults() {
        for (MudTuningHudElement element : MudTuningHudElement.values()) {
            Draft draft = draft(element);
            draft.enabled = true;
            draft.x = element.defaultX();
            draft.y = element.defaultY();
            draft.scale = element.defaultScale();
        }
        hudOpacity = MireboundClientSettings.DEFAULT_TUNING_HUD_OPACITY;
        controlsOpacity = MireboundClientSettings.DEFAULT_TUNING_CONTROLS_OPACITY;
    }

    static void commit() {
        if (active) {
            MudTuningClientSettings.setHudOpacity(hudOpacity);
            MudTuningClientSettings.setControlsHudOpacity(controlsOpacity);
            for (Map.Entry<MudTuningHudElement, Draft> entry : DRAFTS.entrySet()) {
                Draft draft = entry.getValue();
                MireboundClientSettings.setTuningHudElementEnabled(
                        entry.getKey(), draft.enabled);
                MireboundClientSettings.setTuningHudElementLayout(
                        entry.getKey(), draft.x, draft.y, draft.scale);
            }
            MireboundClientSettings.saveTuningHudSettings();
        }
        clear();
    }

    static void discard() {
        clear();
    }

    private static void clear() {
        DRAFTS.clear();
        active = false;
    }

    private static Draft draft(MudTuningHudElement element) {
        MudTuningHudElement key = element == null
                ? MudTuningHudElement.CENTER : element;
        return DRAFTS.computeIfAbsent(key, ignored -> new Draft(
                true, key.defaultX(), key.defaultY(), key.defaultScale()));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Draft {
        private boolean enabled;
        private double x;
        private double y;
        private double scale;

        private Draft(boolean enabled, double x, double y, double scale) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }
}
