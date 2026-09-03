package com.fish.mirebound.client.tuning;

/** The two movable groups that make up the tuning-wand HUD. */
public enum MudTuningHudElement {
    CENTER("center", 0.50D, 0.80D, 1.0D),
    CONTROLS("controls", 0.0D, 1.0D, 1.0D);

    private final String id;
    private final double defaultX;
    private final double defaultY;
    private final double defaultScale;

    MudTuningHudElement(String id, double defaultX, double defaultY,
            double defaultScale) {
        this.id = id;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.defaultScale = defaultScale;
    }

    public String id() {
        return id;
    }

    public double defaultX() {
        return defaultX;
    }

    public double defaultY() {
        return defaultY;
    }

    public double defaultScale() {
        return defaultScale;
    }

    public String translationKey() {
        return "gui.mirebound.tuning.hud_editor.element." + id;
    }
}
